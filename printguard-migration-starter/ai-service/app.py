import os
import sys
from pathlib import Path
from typing import Any

import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse
from pydantic import BaseModel

# Ensure backend directory is in sys.path for procedural geometry package
backend_dir = Path(__file__).resolve().parent.parent.parent / "backend"
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))

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
        Path(__file__).resolve().parent.parent.parent / "ai-model" / "best.pt",
        Path(__file__).resolve().parent.parent / "ai-model" / "best.pt",
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


@app.get("/health")
@app.get("/api/inference/health")
def health() -> dict[str, Any]:
    return {
        "status": "UP" if MODEL_PATH.is_file() else "DEGRADED",
        "service": "printguard-ai",
        "model_configured": MODEL_PATH.is_file(),
    }


@app.post("/api/inference/detect-frame")
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


@app.post("/api/geometry/generate")
def generate_geometry(req: GeometryRequest) -> dict[str, Any]:
    if generate_model is None:
        raise HTTPException(status_code=503, detail="Procedural geometry generator not loaded")
    try:
        return generate_model(req.prompt, req.output_path, req.format)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Geometry generation failed: {exc}")
