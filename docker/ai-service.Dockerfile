# Dockerfile for PrintGuard AI Python Microservice (FastAPI + YOLOv8)
FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY ai-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY ai-service/ .
COPY ai-model/ /app/ai-model/

ENV PORT=8001 \
    ENVIRONMENT=production \
    YOLO_MODEL_PATH=/app/ai-model/best.pt

EXPOSE 8001

CMD ["sh", "-c", "exec python -m uvicorn app:app --host 0.0.0.0 --port ${PORT:-8001}"]
