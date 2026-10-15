# Dockerfile for PrintGuard AI Python Microservice (FastAPI + YOLOv8)
FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY printguard-migration-starter/ai-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY printguard-migration-starter/ai-service/ .
COPY ai-model/ /app/ai-model/

ENV PORT=8001 \
    YOLO_MODEL_PATH=/app/ai-model/best.pt

EXPOSE 8001

CMD ["python", "-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8001"]
