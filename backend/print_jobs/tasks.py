import os
import time
import subprocess
import requests
from celery import shared_task
from django.conf import settings
from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer

from print_jobs.models import PrintJob
from generated_models.models import GeneratedModel

def send_print_ws_update(job_id, status, error_detail=''):
    channel_layer = get_channel_layer()
    if channel_layer is None:
        return

    payload = {
        "type": "print_status_update",
        "job_id": job_id,
        "status": status,
        "error_detail": error_detail,
    }
    
    async_to_sync(channel_layer.group_send)(
        f"print_job_{job_id}",
        payload
    )


@shared_task(soft_time_limit=720, time_limit=780)
def run_slicing_task(job_id):
    send_print_ws_update(job_id, 'slicing_started')
    time.sleep(0.5)
    send_print_ws_update(job_id, 'preparing_model')
    
    try:
        job = PrintJob.objects.get(pk=job_id)
        job.status = 'slicing'
        job.save(update_fields=['status'])

        # --- DEMO FAILURE INJECTION ---
        if 'fail_slicer' in job.model.prompt.lower():
            raise RuntimeError("SIMULATED SLICER FAILURE: PrusaSlicer crashed with segfault.")
        # ------------------------------

        # Absolute paths
        in_path = os.path.join(settings.MEDIA_ROOT, job.model.file_path)
        
        # Simple destination replacing .obj with .gcode
        out_rel_path = job.model.file_path.replace('.obj', '.gcode')
        out_path = os.path.join(settings.MEDIA_ROOT, out_rel_path)

        if not os.path.isfile(in_path):
            raise FileNotFoundError(f"Source file not found: {in_path}")

        # Slicer Command (Assuming PrusaSlicer format: `prusa-slicer -g file.obj -o file.gcode`)
        # Wait, the plan asked to support CuraEngine or PrusaSlicer.
        # We installed PrusaSlicer via winget, so we're switching syntax to PrusaSlicer.
        # PrusaSlicer syntax is: `prusa-slicer-console.exe -g file.obj -o file.gcode`
        send_print_ws_update(job_id, 'running_prusaslicer')
        
        slicer_cmd = [
            settings.SLICER_CLI_PATH,
            '-g', in_path,
            '--scale-to-fit', '150,150,150', # Scale to fit 150x150x150mm volume
            '--center', '100,100',          # Center on a 200x200 bed
            '--align-xy', '100,100',        # Ensure Z=0 sits on the bed
            '--layer-height', '0.2',        # Force a standard layer height
            '-o', out_path
        ]

        result = subprocess.run(slicer_cmd, capture_output=True, text=True, timeout=600)

        if result.returncode == 0 and os.path.isfile(out_path):
            send_print_ws_update(job_id, 'generating_gcode')
            time.sleep(1.0)
            
            job.status = 'ready_to_print'
            job.gcode_path = out_rel_path
            job.save(update_fields=['status', 'gcode_path'])
            send_print_ws_update(job_id, 'ready_to_print')
        else:
            stderr_tail = (result.stderr or '').strip()[-2000:]
            raise RuntimeError(f"Slicing failed: {stderr_tail}")

    except Exception as e:
        try:
            job = PrintJob.objects.get(pk=job_id)
            job.status = 'failed'
            job.save(update_fields=['status'])
            send_print_ws_update(job_id, 'failed', error_detail=str(e))
        except Exception:
            pass


def _octoprint_headers():
    return {'X-Api-Key': settings.OCTOPRINT_API_KEY}


@shared_task(soft_time_limit=120, time_limit=180)
def start_print_task(job_id):
    """Uploads GCODE to OctoPrint and starts the print"""
    try:
        job = PrintJob.objects.get(pk=job_id)
        if not job.gcode_path:
            raise ValueError("No GCODE file path found for PrintJob")

        file_path = os.path.join(settings.MEDIA_ROOT, job.gcode_path)
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"GCODE file not found at {file_path}")

        send_print_ws_update(job_id, 'uploading_to_printer')

        headers = _octoprint_headers()

        # --- AUTO-CONNECT TO VIRTUAL PRINTER FOR DEMO ---
        connection_url = f"{settings.OCTOPRINT_URL.rstrip('/')}/api/connection"
        try:
            conn_resp = requests.get(connection_url, headers=headers, timeout=5)
            if conn_resp.ok:
                conn_data = conn_resp.json()
                # If not connected, or connected but to wrong port, try to connect to VIRTUAL
                if conn_data.get('current', {}).get('state') == 'Closed':
                    connect_payload = {
                        "command": "connect",
                        "port": "VIRTUAL",
                        "baudrate": 115200,
                        "save": True,
                        "autoconnect": True
                    }
                    requests.post(connection_url, json=connect_payload, headers=headers, timeout=5)
                    time.sleep(2) # Wait for connection to establish
        except Exception as e:
            print(f"Warning: Failed to auto-connect to virtual printer: {e}")
        # ------------------------------------------------

        # 1. Upload the file to OctoPrint
        # --- DEMO FAILURE INJECTION ---
        if 'fail_upload' in job.model.prompt.lower():
            raise RuntimeError("SIMULATED UPLOAD FAILURE: OctoPrint API timed out or refused connection.")
        
        if 'fail_print' in job.model.prompt.lower():
            headers['X-Simulate-Failure'] = 'true'
        # ------------------------------

        # Uses standard multipart upload
        filename = os.path.basename(file_path)
        upload_url = f"{settings.OCTOPRINT_URL.rstrip('/')}/api/files/local"
        
        with open(file_path, 'rb') as f:
            files = {'file': (filename, f, 'application/octet-stream')}
            payload = {'select': 'true', 'print': 'true'}
            
            resp = requests.post(upload_url, files=files, data=payload, headers=headers, timeout=30)
            
        if not resp.ok:
            raise RuntimeError(f"OctoPrint upload failed ({resp.status_code}): {resp.text}")

        job.status = 'printing'
        job.save(update_fields=['status'])
        send_print_ws_update(job_id, 'printing')

        # Celery Beat will automatically pick it up in `check_active_print_jobs`.

    except Exception as e:
        try:
            job = PrintJob.objects.get(pk=job_id)
            job.status = 'failed'
            job.save(update_fields=['status'])
            send_print_ws_update(job_id, 'failed', error_detail=str(e))
        except Exception:
            pass


@shared_task
def check_active_print_jobs():
    """Periodic Celery Beat task to check print progress via OctoPrint API"""
    active_jobs = PrintJob.objects.filter(status__in=['printing', 'paused'])
    if not active_jobs.exists():
        return  # Nothing to do

    job_url = f"{settings.OCTOPRINT_URL.rstrip('/')}/api/job"
    printer_url = f"{settings.OCTOPRINT_URL.rstrip('/')}/api/printer"

    for job in active_jobs:
        try:
            # Get Progress
            job_resp = requests.get(job_url, headers=_octoprint_headers(), timeout=2)
            progress_pct = job.progress  # fallback to last known
            
            if job_resp.ok:
                data = job_resp.json()
                progress_pct = data.get('progress', {}).get('completion') or 0
                state = data.get('state', '').lower()

                if 'completed' in state or 'finish' in state:
                    job.status = 'completed'
                    job.progress = 100
                    job.save(update_fields=['status', 'progress'])
                    send_print_ws_update(job.id, 'completed', progress=100)
                    continue
                
                if 'error' in state or 'fail' in state:
                    job.status = 'failed'
                    job.save(update_fields=['status'])
                    send_print_ws_update(job.id, 'failed', error_detail=f"Hardware Exception: {state}")
                    continue
                    
                job.progress = int(progress_pct)
                job.save(update_fields=['progress'])
            
            # Get Temperatures
            temps = {}
            printer_resp = requests.get(printer_url, headers=_octoprint_headers(), timeout=2)
            if printer_resp.ok:
                temps = printer_resp.json().get('temperature', {})

            send_print_ws_update(job.id, job.status, progress=int(progress_pct), temperatures=temps)

        except Exception as e:
            print(f"Error checking active print job {job.id}: {e}")
            # We don't necessarily fail the job here just because of a transient network blip.
            # Real production systems might count retries before failing.

@shared_task
def cleanup_old_files():
    """
    Periodic task to safely clean up old .obj and .gcode files.
    Keeps the exact 5 most recent generated models and print jobs to prevent disk space overflow.
    """
    # 1. Cleanup Generated Models (Keep last 5)
    latest_models = GeneratedModel.objects.order_by('-created_at')[:5]
    latest_model_ids = [m.id for m in latest_models]
    
    old_models = GeneratedModel.objects.exclude(id__in=latest_model_ids).exclude(file_path='')
    for model in old_models:
        file_path = os.path.join(settings.MEDIA_ROOT, model.file_path)
        if os.path.exists(file_path):
            try:
                os.remove(file_path)
                print(f"[Cleanup] Deleted old model file: {file_path}")
            except OSError as e:
                print(f"[Cleanup Error] {e}")
        
        # Clear path in DB to reflect expiration
        model.file_path = ''
        model.save(update_fields=['file_path'])

    # 2. Cleanup Print Jobs (Keep last 5)
    latest_jobs = PrintJob.objects.order_by('-created_at')[:5]
    latest_job_ids = [j.id for j in latest_jobs]
    
    old_jobs = PrintJob.objects.filter(
        status__in=['completed', 'failed', 'cancelled']
    ).exclude(id__in=latest_job_ids).exclude(gcode_path='')
    
    for job in old_jobs:
        gcode_path = os.path.join(settings.MEDIA_ROOT, job.gcode_path)
        if os.path.exists(gcode_path):
            try:
                os.remove(gcode_path)
                print(f"[Cleanup] Deleted old gcode file: {gcode_path}")
            except OSError as e:
                print(f"[Cleanup Error] {e}")
                
        # Clear path in DB
        job.gcode_path = ''
        job.save(update_fields=['gcode_path'])
