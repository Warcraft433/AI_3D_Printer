"""
PrintGuard AI Shap-E 3D Neural Synthesis Module
Supports OpenAI Shap-E Text-to-3D and Image-to-3D diffusion pipelines with:
- Lazy pipeline loading (cached on first call)
- Automatic device detection (CUDA fp16 vs CPU fp32)
- Trimesh post-processing (vertex scaling to ~60mm, centering, Z=0.0 bed alignment, OBJ/STL export)
- Robust graceful fallback to procedural CAD generator (chess_generator.py)
"""
import io
import logging
import os
import threading
from pathlib import Path
from typing import Any, Optional, Tuple, Union

import numpy as np
import torch
import trimesh
from PIL import Image

# Import procedural CAD generator as guaranteed fast/offline fallback
try:
    from geometry.chess_generator import generate_model as procedural_generate_model
except ImportError:
    try:
        from .geometry.chess_generator import generate_model as procedural_generate_model
    except ImportError:
        procedural_generate_model = None

logger = logging.getLogger("printguard.shap_e")
if not logger.handlers:
    logging.basicConfig(level=logging.INFO)

_lock = threading.Lock()
_text_pipeline = None
_text_pipeline_error = None
_image_pipeline = None
_image_pipeline_error = None

TARGET_EXTENT_MM = 40.0  # 3D printer ready default bounding box scale (40mm)


def is_shap_e_enabled() -> bool:
    """Checks whether neural Shap-E generation is enabled via environment variable."""
    val = os.getenv("SHAP_E_ENABLED", "true").strip().lower()
    return val in ("true", "1", "yes", "on")


def get_device_and_dtype() -> Tuple[str, torch.dtype]:
    """Detects available computing device and corresponding PyTorch tensor precision."""
    if torch.cuda.is_available():
        try:
            gpu_name = torch.cuda.get_device_name(0)
            vram_gb = torch.cuda.get_device_properties(0).total_memory / (1024 ** 3)
            logger.info("CUDA GPU detected: '%s' (%.1f GB VRAM). Using fp16 precision for diffusion.", gpu_name, vram_gb)
        except Exception:
            logger.info("CUDA GPU detected. Using fp16 precision for diffusion.")
        return "cuda", torch.float16
    return "cpu", torch.float32


def get_text_pipeline():
    """Lazily loads and caches the OpenAI Shap-E Text-to-3D Hugging Face pipeline."""
    global _text_pipeline, _text_pipeline_error
    if not is_shap_e_enabled():
        return None
    if _text_pipeline is not None:
        return _text_pipeline
    with _lock:
        if _text_pipeline is not None:
            return _text_pipeline
        try:
            from diffusers import ShapEPipeline
            device, dtype = get_device_and_dtype()
            logger.info("Initializing ShapEPipeline on device=%s, dtype=%s...", device, dtype)
            pipe = ShapEPipeline.from_pretrained("openai/shap-e", torch_dtype=dtype)
            pipe = pipe.to(device)
            if hasattr(pipe, "set_progress_bar_config"):
                pipe.set_progress_bar_config(disable=True)
            _text_pipeline = pipe
            logger.info("ShapEPipeline successfully loaded.")
            return _text_pipeline
        except Exception as exc:
            _text_pipeline_error = str(exc)
            logger.warning("Failed to load ShapEPipeline: %s. Will fallback to procedural CAD.", exc)
            return None


def get_image_pipeline():
    """Lazily loads and caches the OpenAI Shap-E Image-to-3D Hugging Face pipeline."""
    global _image_pipeline, _image_pipeline_error
    if not is_shap_e_enabled():
        return None
    if _image_pipeline is not None:
        return _image_pipeline
    with _lock:
        if _image_pipeline is not None:
            return _image_pipeline
        try:
            from diffusers import ShapEImg2ImgPipeline
            device, dtype = get_device_and_dtype()
            logger.info("Initializing ShapEImg2ImgPipeline on device=%s, dtype=%s...", device, dtype)
            pipe = ShapEImg2ImgPipeline.from_pretrained("openai/shap-e-img2img", torch_dtype=dtype)
            pipe = pipe.to(device)
            if hasattr(pipe, "set_progress_bar_config"):
                pipe.set_progress_bar_config(disable=True)
            _image_pipeline = pipe
            logger.info("ShapEImg2ImgPipeline successfully loaded.")
            return _image_pipeline
        except Exception as exc:
            _image_pipeline_error = str(exc)
            logger.warning("Failed to load ShapEImg2ImgPipeline: %s. Will fallback to procedural CAD.", exc)
            return None


def process_and_align_mesh(
    raw_mesh: Any,
    output_path: Optional[str] = None,
    fmt: str = "obj",
    target_extent: float = TARGET_EXTENT_MM
) -> dict[str, Any]:
    """
    Post-processes 3D mesh:
    1. Extracts vertices and faces from Diffusers MeshDecoderOutput or Trimesh.
    2. Scales the model to 3D-printer-ready bounding box (default ~60mm).
    3. Centers X and Y to origin (0, 0) and shifts min Z to 0.0 (sits flat on print bed).
    4. Fixes normals and verifies manifold / watertight topology.
    5. Exports to .obj or .stl.
    """
    fmt_clean = "stl" if fmt.lower() == "stl" else "obj"

    if hasattr(raw_mesh, "verts") and hasattr(raw_mesh, "faces"):
        verts = raw_mesh.verts.detach().cpu().numpy()
        faces = raw_mesh.faces.cpu().numpy()
        mesh = trimesh.Trimesh(vertices=verts, faces=faces, process=True)
    elif isinstance(raw_mesh, trimesh.Trimesh):
        mesh = raw_mesh.copy()
    else:
        raise ValueError(f"Unsupported mesh object type: {type(raw_mesh)}")

    # Apply Laplacian smoothing to organic neural diffusion mesh for smooth surface finish
    try:
        trimesh.smoothing.filter_laplacian(mesh, iterations=2)
    except Exception as smooth_err:
        logger.debug("Laplacian smoothing bypassed: %s", smooth_err)

    # Ensure outward-pointing normal vectors and watertight vertex normals
    trimesh.repair.fix_normals(mesh)

    # 1. Scale bounding box so max dimension equals target_extent (e.g. 60mm)
    extents = mesh.extents  # [extent_x, extent_y, extent_z]
    max_extent = float(np.max(extents)) if len(extents) > 0 else 0.0
    if max_extent > 1e-4:
        scale_factor = target_extent / max_extent
        mesh.apply_scale(scale_factor)

    # 2. Bed alignment: center (X, Y) to (0, 0) and translate min Z to 0.0
    bounds = mesh.bounds  # [[min_x, min_y, min_z], [max_x, max_y, max_z]]
    center_x = float((bounds[0][0] + bounds[1][0]) / 2.0)
    center_y = float((bounds[0][1] + bounds[1][1]) / 2.0)
    min_z = float(bounds[0][2])
    mesh.apply_translation([-center_x, -center_y, -min_z])

    # Recalculate dimensions & bounds after transformation
    dimensions_mm = [round(float(d), 2) for d in (mesh.bounds[1] - mesh.bounds[0])]
    final_bounds = [
        [round(float(x), 2) for x in mesh.bounds[0]],
        [round(float(x), 2) for x in mesh.bounds[1]]
    ]

    file_size = 0
    if output_path:
        out_file = Path(output_path).resolve()
        out_file.parent.mkdir(parents=True, exist_ok=True)
        mesh.export(str(out_file), file_type=fmt_clean)
        file_size = out_file.stat().st_size

    return {
        "success": True,
        "vertices": int(len(mesh.vertices)),
        "faces": int(len(mesh.faces)),
        "dimensions_mm": dimensions_mm,
        "bounds": final_bounds,
        "output_path": output_path or "",
        "file_size": file_size,
        "is_watertight": bool(mesh.is_watertight),
        "format": fmt_clean,
    }


def preprocess_image_for_shap_e(image: Image.Image) -> Image.Image:
    """
    Normalizes and isolates object image for Shap-E Image-to-3D:
    1. Removes background using rembg (or smart alpha/corner-boundary isolation).
    2. Crops to foreground bounding box with safety padding.
    3. Centers foreground on a clean neutral white canvas.
    4. Resizes to 256x256 required by Shap-E Img2Img diffusion.
    """
    # Step 1: Attempt smart background removal via rembg
    isolated_rgba: Optional[Image.Image] = None
    try:
        import onnxruntime
        import rembg
        isolated_rgba = rembg.remove(image)
        if isolated_rgba.mode != "RGBA":
            isolated_rgba = isolated_rgba.convert("RGBA")
    except BaseException as rembg_err:
        logger.debug("rembg background removal bypassed (%s), applying fallback isolation.", rembg_err)

    # Step 2: Fallback background isolation if rembg unavailable or failed
    if isolated_rgba is None:
        if image.mode in ("RGBA", "LA") or (image.mode == "P" and "transparency" in image.info):
            isolated_rgba = image.convert("RGBA")
        else:
            img_rgb = image.convert("RGB")
            arr = np.array(img_rgb)
            # Estimate background from corners
            corners = np.array([arr[0, 0], arr[0, -1], arr[-1, 0], arr[-1, -1]])
            bg_color = np.median(corners, axis=0)
            diff = np.linalg.norm(arr.astype(float) - bg_color.astype(float), axis=2)
            mask = (diff > 35).astype(np.uint8) * 255
            alpha = Image.fromarray(mask, mode="L")
            isolated_rgba = img_rgb.convert("RGBA")
            isolated_rgba.putalpha(alpha)

    # Step 3: Crop around foreground bounding box
    alpha_channel = isolated_rgba.split()[-1]
    bbox = alpha_channel.getbbox()
    if bbox:
        cropped = isolated_rgba.crop(bbox)
    else:
        cropped = isolated_rgba

    # Step 4: Center onto a square canvas with ~12% padding on all sides
    cw, ch = cropped.size
    max_dim = max(cw, ch)
    target_canvas_size = int(max_dim * 1.25)
    padded_canvas = Image.new("RGBA", (target_canvas_size, target_canvas_size), (255, 255, 255, 0))
    paste_x = (target_canvas_size - cw) // 2
    paste_y = (target_canvas_size - ch) // 2
    padded_canvas.paste(cropped, (paste_x, paste_y), cropped)

    # Step 5: Composite over solid white background and resize to 256x256
    final_white_bg = Image.new("RGBA", padded_canvas.size, (255, 255, 255, 255))
    composite = Image.alpha_composite(final_white_bg, padded_canvas).convert("RGB")
    return composite.resize((256, 256), Image.Resampling.LANCZOS)


def generate_text_to_3d(
    prompt: str,
    guidance_scale: float = 15.0,
    num_inference_steps: int = 32,
    engine: str = "shap-e",
    fmt: str = "obj",
    output_path: Optional[str] = None
) -> dict[str, Any]:
    """
    Generates a 3D model from natural language prompt.
    Supports engine="shap-e" (neural diffusion) with automatic fallback to engine="procedural".
    """
    clean_prompt = prompt.strip() if prompt else "3D Model"
    requested_shap_e = (engine.lower() == "shap-e")
    use_shap_e = requested_shap_e and is_shap_e_enabled()

    if use_shap_e:
        pipe = get_text_pipeline()
        if pipe is not None:
            try:
                logger.info("Executing Shap-E Text-to-3D diffusion: prompt='%s', steps=%d, guidance=%.1f",
                            clean_prompt, num_inference_steps, guidance_scale)
                output = pipe(
                    clean_prompt,
                    guidance_scale=float(guidance_scale),
                    num_inference_steps=int(num_inference_steps),
                    frame_size=64,
                    output_type="mesh"
                )
                raw_mesh = output.images[0]
                res = process_and_align_mesh(raw_mesh, output_path=output_path, fmt=fmt)
                res.update({
                    "piece_name": clean_prompt,
                    "prompt": clean_prompt,
                    "is_fallback": False,
                    "generation_method": "shap_e",
                    "metadata_description": f"[SHAP-E NEURAL] Text-to-3D neural synthesis via OpenAI Shap-E ({num_inference_steps} steps, guidance {guidance_scale}). Normalized and aligned to print bed."
                })
                return res
            except Exception as exc:
                logger.warning("Shap-E Text-to-3D generation failed: %s. Triggering procedural CAD fallback.", exc)

    # Fallback to procedural CAD
    if procedural_generate_model is not None:
        res = procedural_generate_model(clean_prompt, output_path=output_path, fmt=fmt)
        if requested_shap_e:
            res["is_fallback"] = True
            res["metadata_description"] = f"[PROCEDURAL CAD FALLBACK] Shap-E unavailable/disabled. Generated parametric CAD mesh for '{clean_prompt}'."
        return res

    raise RuntimeError("Neither Shap-E neural pipeline nor procedural generator is available.")


def generate_image_to_3d(
    image: Image.Image,
    guidance_scale: float = 3.0,
    num_inference_steps: int = 32,
    engine: str = "shap-e",
    fmt: str = "obj",
    output_path: Optional[str] = None,
    name: str = "Image-to-3D Model"
) -> dict[str, Any]:
    """
    Generates a 3D model from reference image input.
    Supports engine="shap-e" (vision diffusion) with automatic fallback to procedural CAD.
    """
    clean_name = name.strip() if name else "Image-to-3D Model"
    requested_shap_e = (engine.lower() == "shap-e")
    use_shap_e = requested_shap_e and is_shap_e_enabled()

    if use_shap_e:
        pipe = get_image_pipeline()
        if pipe is not None:
            try:
                processed_image = preprocess_image_for_shap_e(image)
                logger.info("Executing Shap-E Image-to-3D diffusion: name='%s', steps=%d, guidance=%.1f",
                            clean_name, num_inference_steps, guidance_scale)
                output = pipe(
                    processed_image,
                    guidance_scale=float(guidance_scale),
                    num_inference_steps=int(num_inference_steps),
                    frame_size=64,
                    output_type="mesh"
                )
                raw_mesh = output.images[0]
                res = process_and_align_mesh(raw_mesh, output_path=output_path, fmt=fmt)
                res.update({
                    "piece_name": clean_name,
                    "prompt": clean_name,
                    "is_fallback": False,
                    "generation_method": "shap_e_vision",
                    "metadata_description": f"[SHAP-E VISION] Image-to-3D neural synthesis via OpenAI Shap-E Vision ({num_inference_steps} steps, guidance {guidance_scale}). Centered and aligned to print bed."
                })
                return res
            except Exception as exc:
                logger.warning("Shap-E Image-to-3D generation failed: %s. Triggering procedural CAD fallback.", exc)

    # Fallback to procedural CAD
    if procedural_generate_model is not None:
        res = procedural_generate_model(clean_name, output_path=output_path, fmt=fmt)
        if requested_shap_e:
            res["is_fallback"] = True
            res["metadata_description"] = f"[PROCEDURAL CAD FALLBACK] Shap-E Vision unavailable/disabled. Generated parametric CAD mesh for '{clean_name}'."
        return res


    raise RuntimeError("Neither Shap-E Vision pipeline nor procedural generator is available.")
