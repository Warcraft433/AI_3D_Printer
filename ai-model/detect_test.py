"""
detect_test.py — Standalone YOLOv8 model validation script.
Run from the project root:  python detect_test.py
"""
import os
import sys
import numpy as np

MODEL_PATH = os.path.join(os.path.dirname(__file__), "backend", "best.pt")

print("=" * 60)
print("  AI 3D Printer Dashboard — YOLOv8 Model Validation")
print("=" * 60)

# 1. Check model file
if not os.path.exists(MODEL_PATH):
    print(f"[FAIL] Model not found: {MODEL_PATH}")
    sys.exit(1)
print(f"[PASS] Model found: {MODEL_PATH} ({os.path.getsize(MODEL_PATH)//1024} KB)")

# 2. Load YOLO
try:
    from ultralytics import YOLO
    model = YOLO(MODEL_PATH)
    print(f"[PASS] Model loaded successfully.")
    print(f"       Task type : {model.task}")
    print(f"       # classes : {len(model.names)}")
    print(f"       Classes   : {list(model.names.values())}")
except Exception as e:
    print(f"[FAIL] Failed to load model: {e}")
    sys.exit(1)

# 3. Run inference on a dummy image (random noise)
try:
    dummy = np.random.randint(0, 255, (480, 640, 3), dtype=np.uint8)
    results = model.predict(source=dummy, conf=0.25, save=False, verbose=False)
    boxes = results[0].boxes
    print(f"[PASS] Inference on dummy image succeeded.")
    print(f"       Detections: {len(boxes)} box(es) found (random noise, 0 expected).")
except Exception as e:
    print(f"[FAIL] Inference failed: {e}")
    sys.exit(1)

print("=" * 60)
print("  All YOLO checks PASSED. Model is healthy.")
print("=" * 60)
