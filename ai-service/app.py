import os
import secrets
import sys
from pathlib import Path
from typing import Any

import cv2
import numpy as np
from fastapi import Depends, FastAPI, File, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse
from pydantic import BaseModel

# Ensure current service directory and project root are in sys.path for geometry package
current_dir = Path(__file__).resolve().parent
project_root = current_dir.parent
for p in [current_dir, project_root / "backend"]:
    if str(p) not in sys.path:
        sys.path.insert(0, str(p))

try:
    from geometry.chess_generator import generate_model
except ImportError:
    generate_model = None

app = FastAPI(title="PrintGuard AI Inference Service", version="0.1.0")
_model = None
_model_error = None

def _resolve_model_path() -> Path:
    env_path = os.getenv("YOLO_MODEL_PATH")
    if env_path:
        return Path(env_path).resolve()
    candidates = [
        project_root / "ai-model" / "best.pt",
        current_dir / "models" / "best.pt",
        Path("../ai-model/best.pt").resolve(),
        Path("ai-model/best.pt").resolve(),
    ]
    for c in candidates:
        if c.is_file():
            return c
    return candidates[0]

MODEL_PATH = _resolve_model_path()
CONFIDENCE = float(os.getenv("YOLO_CONFIDENCE", "0.35"))

# The audited model classes are:
# 0 Spaghetti, 1 chicken, 2 chili_pepper, 3 cup,
# 4 spaghetti_noodles, 5 sweet_basil.
# Do NOT label unrelated food classes as layer_shift/stringing.
# This allow-list intentionally only treats class 0 as spaghetti.
SPAGHETTI_CLASS_NAMES = {"spaghetti"}


def get_model():
    global _model, _model_error
    if _model is None and _model_error is None:
        try:
            from ultralytics import YOLO
            if not MODEL_PATH.is_file():
                raise FileNotFoundError(f"YOLO model not found at {MODEL_PATH}")
            _model = YOLO(str(MODEL_PATH))
        except Exception as exc:
            _model_error = str(exc)
    if _model is None:
        raise HTTPException(status_code=503, detail=f"AI model unavailable: {_model_error}")
    return _model


def is_production_environment() -> bool:
    env = os.getenv("ENVIRONMENT", "").strip().lower()
    render_env = os.getenv("RENDER", "").strip().lower()
    strict_auth = os.getenv("STRICT_AUTH", "").strip().lower()
    return env in ("production", "prod") or render_env == "true" or strict_auth in ("true", "1")


def verify_internal_token(request: Request) -> None:
    expected_token = os.getenv("AI_SERVICE_API_KEY", "").strip()
    if not expected_token:
        if is_production_environment():
            raise HTTPException(
                status_code=500,
                detail="Security configuration error: AI_SERVICE_API_KEY must be configured in production"
            )
        # Internal token validation disabled when AI_SERVICE_API_KEY is not configured in local development
        return

    token = request.headers.get("X-Internal-Token")
    if not token:
        auth_header = request.headers.get("Authorization", "")
        if auth_header.startswith("Bearer "):
            token = auth_header[7:].strip()

    if not token or not secrets.compare_digest(token, expected_token):
        raise HTTPException(
            status_code=401,
            detail="Unauthorized: invalid or missing internal service token"
        )


@app.get("/health")
@app.get("/api/inference/health")
def health() -> dict[str, Any]:
    is_prod = is_production_environment()
    token_configured = bool(os.getenv("AI_SERVICE_API_KEY", "").strip())
    has_model = MODEL_PATH.is_file()

    is_healthy = has_model and (not is_prod or token_configured)
    return {
        "status": "UP" if is_healthy else ("DEGRADED" if has_model else "DOWN"),
        "service": "printguard-ai",
        "model_configured": has_model,
        "security_configured": token_configured or not is_prod,
    }


@app.post("/api/inference/detect-frame", dependencies=[Depends(verify_internal_token)])
async def detect_frame(file: UploadFile = File(...)) -> dict[str, Any]:
    if file.content_type not in {"image/jpeg", "image/png", "image/webp", "application/octet-stream"}:
        raise HTTPException(status_code=415, detail="Upload a JPEG, PNG, or WEBP frame")
    raw = await file.read()
    if not raw or len(raw) > 10 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="Frame is empty or exceeds 10 MB")
    image = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail="Invalid image frame")

    model = get_model()
    results = model.predict(source=image, conf=CONFIDENCE, verbose=False)
    detections = []
    for result in results:
        names = result.names
        if result.boxes is None:
            continue
        for box in result.boxes:
            class_id = int(box.cls[0].item())
            label = str(names.get(class_id, class_id))
            confidence = float(box.conf[0].item())
            xyxy = [round(float(v), 2) for v in box.xyxy[0].tolist()]
            detections.append({
                "classId": class_id,
                "label": label,
                "confidence": round(confidence, 4),
                "box": xyxy,
                "isPotentialSpaghetti": label.strip().lower() in SPAGHETTI_CLASS_NAMES,
            })
    return {"detections": detections, "modelClasses": results[0].names if results else {}}


class GeometryRequest(BaseModel):
    prompt: str
    output_path: str = ""
    format: str = "obj"


@app.post("/api/geometry/generate", dependencies=[Depends(verify_internal_token)])
def generate_geometry(req: GeometryRequest) -> dict[str, Any]:
    if generate_model is None:
        raise HTTPException(status_code=503, detail="Procedural geometry generator not loaded")
    try:
        return generate_model(req.prompt, req.output_path, req.format)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Geometry generation failed: {exc}")


class TextTo3DRequest(BaseModel):
    prompt: str
    guidance_scale: float = 15.0
    num_inference_steps: int = 32
    engine: str = "shap-e"
    format: str = "obj"
    output_path: str = ""


@app.post("/api/generate/text-to-3d", dependencies=[Depends(verify_internal_token)])
def generate_text_to_3d_endpoint(req: TextTo3DRequest) -> dict[str, Any]:
    if not req.prompt or not req.prompt.strip():
        raise HTTPException(status_code=400, detail="Prompt cannot be empty")
    try:
        import shap_e_service
        return shap_e_service.generate_text_to_3d(
            prompt=req.prompt,
            guidance_scale=req.guidance_scale,
            num_inference_steps=req.num_inference_steps,
            engine=req.engine,
            fmt=req.format,
            output_path=req.output_path or None,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Text-to-3D generation failed: {exc}")


@app.post("/api/generate/image-to-3d", dependencies=[Depends(verify_internal_token)])
async def generate_image_to_3d_endpoint(request: Request) -> dict[str, Any]:
    import base64
    import io
    from PIL import Image
    import shap_e_service

    raw_bytes = None
    target_engine = "shap-e"
    target_steps = 32
    target_guidance = 3.0
    target_format = "obj"
    target_output_path = ""
    target_name = "Image Model"

    content_type = request.headers.get("content-type", "").lower()

    if "application/json" in content_type:
        try:
            body = await request.json()
            b64_str = body.get("image") or body.get("image_base64") or body.get("file")
            if b64_str:
                if "," in b64_str:
                    b64_str = b64_str.split(",", 1)[1]
                raw_bytes = base64.b64decode(b64_str)
            target_engine = body.get("engine", target_engine)
            target_steps = int(body.get("num_inference_steps", target_steps))
            target_guidance = float(body.get("guidance_scale", target_guidance))
            target_format = body.get("format", target_format)
            target_output_path = body.get("output_path", target_output_path)
            target_name = body.get("name") or body.get("prompt") or target_name
        except Exception as exc:
            raise HTTPException(status_code=400, detail=f"Invalid JSON payload: {exc}")
    else:
        try:
            form = await request.form()
            for key in ["image", "file"]:
                field = form.get(key)
                if field is not None and hasattr(field, "read"):
                    raw_bytes = await field.read()
                    if hasattr(field, "filename") and field.filename:
                        target_name = Path(field.filename).stem or target_name
                    break
            if "name" in form:
                target_name = str(form["name"])
            if "engine" in form:
                target_engine = str(form["engine"])
            if "num_inference_steps" in form:
                target_steps = int(form["num_inference_steps"])
            elif "steps" in form:
                target_steps = int(form["steps"])
            if "guidance_scale" in form:
                target_guidance = float(form["guidance_scale"])
            elif "guidanceScale" in form:
                target_guidance = float(form["guidanceScale"])
            if "format" in form:
                target_format = str(form["format"])
            if "output_path" in form:
                target_output_path = str(form["output_path"])
        except Exception as exc:
            raise HTTPException(status_code=400, detail=f"Invalid multipart payload: {exc}")

    if not raw_bytes:
        raise HTTPException(status_code=400, detail="Image data required (upload file or base64)")

    try:
        pil_image = Image.open(io.BytesIO(raw_bytes))
        pil_image.load()
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Invalid image content: {exc}")

    try:
        return shap_e_service.generate_image_to_3d(
            image=pil_image,
            guidance_scale=target_guidance,
            num_inference_steps=target_steps,
            engine=target_engine,
            fmt=target_format,
            output_path=target_output_path or None,
            name=target_name,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Image-to-3D generation failed: {exc}")


