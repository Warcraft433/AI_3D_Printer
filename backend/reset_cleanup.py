import os
import django
import shutil
import subprocess
from pathlib import Path

# Setup Django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'core.settings')
django.setup()

from print_jobs.models import PrintJob
from generated_models.models import GeneratedModel
from django.conf import settings

BACKEND_DIR = Path(r"c:\Users\warca\Desktop\AI3DPrinter_Dashboard\backend")
FRONTEND_DIR = Path(r"c:\Users\warca\Desktop\AI3DPrinter_Dashboard\frontend")

def clean_pycache():
    print("Clearing Python __pycache__ directories...")
    for p in BACKEND_DIR.rglob("__pycache__"):
        if p.is_dir():
            try:
                shutil.rmtree(p, ignore_errors=True)
                print(f"  Deleted {p}")
            except Exception:
                pass

def clean_media_and_db():
    print("Clearing temporary files and resetting DB statuses...")
    
    # Reset stuck active models
    stuck_models = GeneratedModel.objects.filter(status='processing')
    for m in stuck_models:
        m.status = 'failed'
        m.error_detail = 'System Reset'
        m.save()
        print(f"  Reset model {m.id} to failed.")
        
    # Delete model files
    all_models = GeneratedModel.objects.exclude(file_path='')
    for m in all_models:
        fp = os.path.join(settings.MEDIA_ROOT, m.file_path)
        if os.path.exists(fp):
            os.remove(fp)
            print(f"  Deleted model file for {m.id}")
        m.file_path = ''
        m.save(update_fields=['file_path'])

    # Reset stuck active prints
    stuck_jobs = PrintJob.objects.filter(status__in=['queued', 'slicing', 'printing', 'paused'])
    for j in stuck_jobs:
        j.status = 'cancelled'
        j.save()
        print(f"  Reset print job {j.id} to cancelled.")
        
    # Delete gcode files
    all_jobs = PrintJob.objects.exclude(gcode_path='')
    for j in all_jobs:
        gp = os.path.join(settings.MEDIA_ROOT, j.gcode_path)
        if os.path.exists(gp):
            os.remove(gp)
            print(f"  Deleted gcode for job {j.id}")
        j.gcode_path = ''
        j.save(update_fields=['gcode_path'])

def clean_frontend_cache():
    print("Clearing Vite build cache...")
    vite_cache = FRONTEND_DIR / "node_modules" / ".vite"
    if vite_cache.exists():
        shutil.rmtree(vite_cache, ignore_errors=True)
        print("  Deleted frontend Vite cache.")

def flush_redis():
    print("Flushing Redis queues...")
    try:
        subprocess.run(["redis-cli", "flushall"], check=True, capture_output=True)
        print("  Redis flushed successfully.")
    except Exception as e:
        print(f"  Failed to flush Redis via redis-cli: {e}")

if __name__ == "__main__":
    print("=== STARTING SAFE SYSTEM RESET CLEANUP ===")
    clean_pycache()
    clean_media_and_db()
    clean_frontend_cache()
    flush_redis()
    print("=== CLEANUP COMPLETE ===")
