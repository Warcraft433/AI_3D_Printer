import os
import sys
import time
import torch

def generate_3d(prompt, out_obj_path, steps=16):
    print(f"Generating 3D model for prompt: '{prompt}' with {steps} steps...")
    os.environ['SHAP_E_CACHE_DIR'] = os.path.join(os.getcwd(), 'backend', 'shap_e_model_cache')
    os.makedirs(os.environ['SHAP_E_CACHE_DIR'], exist_ok=True)
    os.makedirs(os.path.dirname(os.path.abspath(out_obj_path)), exist_ok=True)

    from shap_e.diffusion.sample import sample_latents
    from shap_e.diffusion.gaussian_diffusion import diffusion_from_config
    from shap_e.models.download import load_model, load_config
    from shap_e.util.notebooks import decode_latent_mesh

    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    print(f"[Shap-E] Using compute device: {device}")

    t0 = time.time()
    print("Loading models (transmitter, text300M, diffusion)...")
    xm = load_model('transmitter', device=device)
    model = load_model('text300M', device=device)
    diffn = diffusion_from_config(load_config('diffusion'))
    print(f"Models loaded in {round(time.time() - t0, 2)}s")

    t1 = time.time()
    print("Sampling latents...")
    latents = sample_latents(
        batch_size=1,
        model=model,
        diffusion=diffn,
        guidance_scale=15.0,
        model_kwargs=dict(texts=[prompt]),
        progress=True,
        clip_denoised=True,
        use_fp16=(device.type == 'cuda'),
        use_karras=True,
        karras_steps=steps,
        sigma_min=1e-3,
        sigma_max=160,
        s_churn=0,
    )

    print("Decoding latent mesh to OBJ...")
    mesh = decode_latent_mesh(xm, latents[0]).tri_mesh()
    with open(out_obj_path, 'w') as f:
        mesh.write_obj(f)

    elapsed = round(time.time() - t1, 2)
    print(f"Successfully generated {out_obj_path} ({os.path.getsize(out_obj_path)} bytes) in {elapsed}s!")
    return out_obj_path

if __name__ == '__main__':
    prompt_to_test = sys.argv[1] if len(sys.argv) > 1 else 'a hat'
    out_file = sys.argv[2] if len(sys.argv) > 2 else 'media/models/test_hat.obj'
    generate_3d(prompt_to_test, out_file, steps=16)
