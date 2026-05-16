
import sys, time, os
import torch
from shap_e.diffusion.sample import sample_latents
from shap_e.diffusion.gaussian_diffusion import diffusion_from_config
from shap_e.models.download import load_model, load_config
from shap_e.util.notebooks import decode_latent_mesh

shape_dir = r"C:\Users\warca\Desktop\ShapE_Model_3Dmodel"
cache_dir = r"C:\Users\warca\Desktop\ShapE_Model_3Dmodel\shap_e_model_cache"
sys.path.insert(0, shape_dir)
os.environ['SHAP_E_CACHE_DIR'] = cache_dir

device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
print(f'[shap_e] device={device}')

try:
    print("Loading models...")
    xm = load_model('transmitter', device=device)
    model = load_model('text300M', device=device)
    diffn = diffusion_from_config(load_config('diffusion'))
    print("Models loaded successfully.")

    prompt = "a small cube"
    print(f"Generating latents for prompt: {prompt}")
    latents = sample_latents(
        batch_size=1, model=model, diffusion=diffn,
        guidance_scale=15.0,
        model_kwargs=dict(texts=[prompt]),
        progress=True, clip_denoised=True, use_fp16=True,
        use_karras=True, karras_steps=10, # low steps for fast test
        sigma_min=1e-3, sigma_max=160, s_churn=0,
    )
    print("Latents generated.")
    mesh = decode_latent_mesh(xm, latents[0]).tri_mesh()
    out_path = "test_mesh.obj"
    with open(out_path, 'w') as f:
        mesh.write_obj(f)
    print(f"Mesh saved to {out_path}")

except Exception as e:
    print(f"ERROR: {e}")
    import traceback
    traceback.print_exc()
