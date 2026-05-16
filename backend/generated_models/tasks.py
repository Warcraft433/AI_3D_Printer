import os
import subprocess
import tempfile
import uuid
import trimesh
from celery import shared_task
from django.conf import settings
from django.db import connection as _db_connection
from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer

from generated_models.models import GeneratedModel


def send_ws_update(model_id, status, error_detail='', duration=None, file_url=None):
    """
    Broadcast model status updates to the WebSocket group.
    """
    channel_layer = get_channel_layer()
    if channel_layer is None:
        return

    payload = {
        "type": "model_status_update",
        "model_id": model_id,
        "status": status,
        "error_detail": error_detail,
    }
    if duration is not None:
        payload["generation_time"] = duration
    if file_url:
        payload["file_url"] = file_url

    async_to_sync(channel_layer.group_send)(
        f"model_{model_id}",
        payload
    )


def _write_temp_script(content: str) -> str:
    fd, path = tempfile.mkstemp(suffix='.py', prefix='shap_e_gen_')
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as f:
            f.write(content)
    except Exception:
        os.close(fd)
        raise
    return path


def _text_script_content(out_path, prompt, steps):
    shape_dir = settings.SHAPE_PROJECT_DIR.replace('\\', '\\\\')
    cache_dir = os.path.join(settings.SHAPE_PROJECT_DIR, 'shap_e_model_cache').replace('\\', '\\\\')
    out_path_esc = out_path.replace('\\', '\\\\')
    return f"""\
import sys, time, os
sys.path.insert(0, '{shape_dir}')
os.environ['SHAP_E_CACHE_DIR'] = '{cache_dir}'
import torch
from shap_e.diffusion.sample import sample_latents
from shap_e.diffusion.gaussian_diffusion import diffusion_from_config
from shap_e.models.download import load_model, load_config
from shap_e.util.notebooks import decode_latent_mesh

device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
print(f'[shap_e] device={{device}}', flush=True)
xm     = load_model('transmitter', device=device)
model  = load_model('text300M',    device=device)
diffn  = diffusion_from_config(load_config('diffusion'))

prompt = {repr(prompt)}
t0 = time.time()
latents = sample_latents(
    batch_size=1, model=model, diffusion=diffn,
    guidance_scale=15.0,
    model_kwargs=dict(texts=[prompt]),
    progress=False, clip_denoised=True, use_fp16=True,
    use_karras=True, karras_steps={steps},
    sigma_min=1e-3, sigma_max=160, s_churn=0,
)
mesh = decode_latent_mesh(xm, latents[0]).tri_mesh()
with open('{out_path_esc}', 'w') as f:
    mesh.write_obj(f)
print(round(time.time() - t0, 2), flush=True)
"""


def _image_script_content(out_path, img_path, steps):
    shape_dir = settings.SHAPE_PROJECT_DIR.replace('\\', '\\\\')
    cache_dir = os.path.join(settings.SHAPE_PROJECT_DIR, 'shap_e_model_cache').replace('\\', '\\\\')
    out_path_esc = out_path.replace('\\', '\\\\')
    img_path_esc = img_path.replace('\\', '\\\\')
    return f"""\
import sys, time, os
sys.path.insert(0, '{shape_dir}')
os.environ['SHAP_E_CACHE_DIR'] = '{cache_dir}'
import torch
from PIL import Image
from shap_e.diffusion.sample import sample_latents
from shap_e.diffusion.gaussian_diffusion import diffusion_from_config
from shap_e.models.download import load_model, load_config
from shap_e.util.notebooks import decode_latent_mesh

device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
print(f'[shap_e] device={{device}}', flush=True)
xm     = load_model('transmitter',  device=device)
model  = load_model('image300M',    device=device)
diffn  = diffusion_from_config(load_config('diffusion'))

img = Image.open('{img_path_esc}').convert('RGB')

t0 = time.time()
latents = sample_latents(
    batch_size=1, model=model, diffusion=diffn,
    guidance_scale=3.0,
    model_kwargs=dict(images=[img]),
    progress=False, clip_denoised=True, use_fp16=True,
    use_karras=True, karras_steps={steps},
    sigma_min=1e-3, sigma_max=160, s_churn=0,
)
mesh = decode_latent_mesh(xm, latents[0]).tri_mesh()
with open('{out_path_esc}', 'w') as f:
    mesh.write_obj(f)
print(round(time.time() - t0, 2), flush=True)
"""


def repair_mesh(file_path):
    """Attempts to load, repair normals and holes, and re-save the mesh using trimesh."""
    try:
        mesh = trimesh.load(file_path, force='mesh')
        trimesh.repair.fix_normals(mesh)
        trimesh.repair.fill_holes(mesh)
        mesh.export(file_path)
        return True
    except Exception as e:
        print(f"Trimesh repair failed on {file_path}: {e}")
        return False


def _run_subprocess_generation(model_id, out_path, script_content):
    script_path = None
    try:
        script_path = _write_temp_script(script_content)

        # Let the frontend know we reached the latent generation stage
        send_ws_update(model_id, 'generating_latents')

        result = subprocess.run(
            [settings.SHAPE_VENV_PYTHON, script_path],
            capture_output=True, text=True, timeout=1200,
        )

        obj = GeneratedModel.objects.get(pk=model_id)

        if result.returncode == 0 and os.path.isfile(out_path):
            lines = [l.strip() for l in result.stdout.strip().splitlines() if l.strip()]
            try:
                duration = float(lines[-1])
            except (ValueError, IndexError):
                duration = None

            rel_path = os.path.relpath(out_path, settings.MEDIA_ROOT)
            obj.file_path = rel_path.replace('\\', '/')
            obj.generation_time = duration

            # Mesh Repair Phase
            send_ws_update(model_id, 'repairing_mesh')
            repair_success = repair_mesh(out_path)
            if repair_success:
                obj.is_repaired = True

            obj.status = 'ready'
            obj.error_detail = ''
            
            # File URL for websocket
            file_url = f"{settings.MEDIA_URL}{obj.file_path}"
            send_ws_update(model_id, 'ready', duration=duration, file_url=file_url)
        else:
            obj.status = 'failed'
            stderr_tail = (result.stderr or '').strip()[-2000:]
            stdout_tail = (result.stdout or '').strip()[-500:]
            obj.error_detail = stderr_tail or stdout_tail or f'Process exited with code {result.returncode}'
            send_ws_update(model_id, 'failed', error_detail=obj.error_detail)

        obj.save()

    except subprocess.TimeoutExpired:
        try:
            obj = GeneratedModel.objects.get(pk=model_id)
            obj.status = 'failed'
            obj.error_detail = 'Generation timed out after 20 minutes.'
            obj.save()
            send_ws_update(model_id, 'failed', error_detail=obj.error_detail)
        except Exception:
            pass

    except Exception as exc:
        try:
            obj = GeneratedModel.objects.get(pk=model_id)
            obj.status = 'failed'
            obj.error_detail = str(exc)
            obj.save()
            send_ws_update(model_id, 'failed', error_detail=obj.error_detail)
        except Exception:
            pass

    finally:
        if script_path and os.path.isfile(script_path):
            try:
                os.remove(script_path)
            except Exception:
                pass


@shared_task(soft_time_limit=1500, time_limit=1560)
def run_shap_e_text_generation(model_id, prompt, steps):
    send_ws_update(model_id, 'loading_models')
    try:
        obj = GeneratedModel.objects.get(pk=model_id)
        user_dir = os.path.join(
            settings.MEDIA_ROOT, 'users', str(obj.user_id), 'generated_models'
        )
        os.makedirs(user_dir, exist_ok=True)
        out_path = os.path.join(user_dir, f"{uuid.uuid4().hex}.obj")
        script = _text_script_content(out_path, prompt, steps)
        _run_subprocess_generation(model_id, out_path, script)
    except Exception as exc:
        try:
            obj = GeneratedModel.objects.get(pk=model_id)
            obj.status = 'failed'
            obj.error_detail = str(exc)
            obj.save()
            send_ws_update(model_id, 'failed', error_detail=obj.error_detail)
        except Exception:
            pass
    finally:
        _db_connection.close()


@shared_task(soft_time_limit=1500, time_limit=1560)
def run_shap_e_image_generation(model_id, img_path, steps):
    send_ws_update(model_id, 'loading_models')
    try:
        obj = GeneratedModel.objects.get(pk=model_id)
        user_dir = os.path.join(
            settings.MEDIA_ROOT, 'users', str(obj.user_id), 'generated_models'
        )
        os.makedirs(user_dir, exist_ok=True)
        out_path = os.path.join(user_dir, f"{uuid.uuid4().hex}.obj")
        script = _image_script_content(out_path, img_path, steps)
        _run_subprocess_generation(model_id, out_path, script)
    except Exception as exc:
        try:
            obj = GeneratedModel.objects.get(pk=model_id)
            obj.status = 'failed'
            obj.error_detail = str(exc)
            obj.save()
            send_ws_update(model_id, 'failed', error_detail=obj.error_detail)
        except Exception:
            pass
    finally:
        _db_connection.close()
        try:
            if os.path.isfile(img_path):
                os.remove(img_path)
        except Exception:
            pass
