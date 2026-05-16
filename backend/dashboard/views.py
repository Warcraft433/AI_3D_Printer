from django.shortcuts import render
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import permissions
from django.conf import settings
from django.http import StreamingHttpResponse, HttpResponse
from django.views.decorators.csrf import csrf_exempt
from django.utils.decorators import method_decorator
import requests
import random
import time
import threading
import os
import cv2
import json
from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer
from django.core.files.base import ContentFile
from django.utils import timezone
from datetime import timedelta
from ultralytics import YOLO
from django.utils import timezone
from datetime import timedelta

from generated_models.models import GeneratedModel
from print_jobs.models import PrintJob


import numpy as np

# ════════════════════════════════════════════════════════════════════
#  Local Raw Webcam Streamer (Testing)
# ════════════════════════════════════════════════════════════════════

def _make_error_frame(text="NO CAMERA SIGNAL - CHECK USB OR INDEX"):
    """Returns JPEG bytes of a solid blue image with white error text."""
    frame = np.zeros((480, 640, 3), dtype=np.uint8)
    frame[:, :] = (180, 80, 20)  # Blue in BGR
    # Two lines of text for readability
    lines = text.split(" - ")
    for i, line in enumerate(lines):
        y = 210 + i * 55
        (w, _), _ = cv2.getTextSize(line, cv2.FONT_HERSHEY_SIMPLEX, 0.85, 2)
        x = max(20, (640 - w) // 2)
        cv2.putText(frame, line, (x, y), cv2.FONT_HERSHEY_SIMPLEX, 0.85, (255, 255, 255), 2, cv2.LINE_AA)
    _, buf = cv2.imencode('.jpg', frame)
    return buf.tobytes()

_probe_lock = threading.Lock()

def raw_frames_generator():
    """
    Robust MJPEG generator.
    - Yields a 'CONNECTING...' frame immediately to force Django to flush HTTP headers and avoid timeouts.
    - Probes cv2.VideoCapture indices in the background securely.
    - On failure, falls back to a solid BLUE 'NO CAMERA SIGNAL' error frame.
    """
    
    # 1. Provide an immediate frame so Django doesn't block HTTP headers
    connecting_bytes = _make_error_frame("CONNECTING TO CAMERA...")
    yield (b'--frame\r\nContent-Type: image/jpeg\r\n\r\n' + connecting_bytes + b'\r\n')
    
    cap = None
    with _probe_lock:
        for idx in [1, 0, 2]:
            # Use CAP_DSHOW for Windows compatibility with USB webcams
            c = cv2.VideoCapture(idx, cv2.CAP_DSHOW)
            if c.isOpened():
                # Verify we can actually read a frame (MSMF falsely opens sometimes)
                ok, _ = c.read()
                if ok:
                    print(f"[TESTING] Camera opened: index={idx} backend=DSHOW")
                    cap = c
                    break
            c.release()

    if cap is None or not cap.isOpened():
        print("[TESTING] ✗ No camera found — streaming error frame")
        error_bytes = _make_error_frame("NO CAMERA SIGNAL - CHECK USB OR INDEX")
        while True:
            yield (b'--frame\r\nContent-Type: image/jpeg\r\n\r\n' + error_bytes + b'\r\n')
            time.sleep(1.5)

    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
    print("[TESTING] Starting MJPEG stream…")

    error_bytes = _make_error_frame("NO CAMERA SIGNAL - STREAM LOST")

    while True:
        ok, frame = cap.read()
        if not ok or frame is None:
            print("[TESTING] cap.read() failed — yielding error frame")
            yield (b'--frame\r\nContent-Type: image/jpeg\r\n\r\n' + error_bytes + b'\r\n')
            time.sleep(0.5)
            continue

        ret, buf = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 80])
        if not ret:
            continue
        yield (b'--frame\r\nContent-Type: image/jpeg\r\n\r\n' + buf.tobytes() + b'\r\n')

@csrf_exempt
def raw_test_feed(request):
    """MJPEG stream consumed by the React Testing page — raw, unannotated webcam frames."""
    response = StreamingHttpResponse(
        raw_frames_generator(),
        content_type='multipart/x-mixed-replace; boundary=frame',
    )
    response['Cache-Control'] = 'no-cache, no-store, must-revalidate'
    response['Access-Control-Allow-Origin'] = '*'
    return response

# ════════════════════════════════════════════════════════════════════
#  YOLOv8 Live Inference Streamer
# ════════════════════════════════════════════════════════════════════

# Load the custom YOLOv8 models locally
_yolo_model_paths = [
    os.path.join(settings.BASE_DIR, "best.pt"),
    os.path.join(settings.BASE_DIR, "best_2.pt")
]

class CameraStreamer:
    """Thread-safe camera streamer that prevents OpenCV from blocking the main server threads."""
    def __init__(self, index=0, model_paths=None):
        self.index = index
        self.cap = None
        self.frame = None
        self.running = False
        self.models = []
        self.model_paths = model_paths or []
        self._lock = threading.Lock()
        self.thread = None

    def start(self):
        if self.running:
            return
        self.running = True
        self.thread = threading.Thread(target=self._update, daemon=True)
        self.thread.start()
        print(f"[YOLO] Background camera thread started for index {self.index}")

    def _update(self):
        # Initialize camera in the thread
        print(f"[YOLO] Attempting to open camera {self.index} in background thread...")
        self.cap = cv2.VideoCapture(self.index, cv2.CAP_DSHOW)
        ok, _ = self.cap.read()
        if not ok:
            self.cap.release()
            for fallback_idx in [1, 0, 2]:
                if fallback_idx == self.index: continue
                temp_cap = cv2.VideoCapture(fallback_idx, cv2.CAP_DSHOW)
                if temp_cap.isOpened():
                    ok2, _ = temp_cap.read()
                    if ok2:
                        self.cap = temp_cap
                        self.index = fallback_idx
                        print(f"[YOLO] Successfully fell back to camera index {fallback_idx}")
                        break
                temp_cap.release()
        
        # Load models in the thread
        for path in self.model_paths:
            if os.path.exists(path):
                try:
                    print(f"[YOLO] Loading model {path} in thread...")
                    self.models.append(YOLO(path))
                except Exception as e:
                    print(f"[YOLO] Error loading model {path}: {e}")

        while self.running:
            if not self.cap or not self.cap.isOpened():
                time.sleep(1)
                self.cap = cv2.VideoCapture(self.index, cv2.CAP_DSHOW)
                continue
            
            ok, frame = self.cap.read()
            if ok and frame is not None:
                if self.models:
                    try:
                        all_results = []
                        annotated_frame = frame.copy()
                        
                        for model in self.models:
                            results = model.predict(source=frame, conf=0.5, save=False, verbose=False)
                            all_results.append(results[0])
                            # Plot results on the same frame to see both models' boxes
                            annotated_frame = results[0].plot(img=annotated_frame)
                        
                        # Check for detections in any of the models
                        significant_results = [r for r in all_results if len(r.boxes) > 0]
                        if significant_results:
                            # Use the result with the highest confidence detection for handling
                            best_result = max(significant_results, key=lambda r: float(r.boxes[0].conf[0]))
                            self._handle_detection(frame, best_result)
                            
                        with self._lock:
                            self.frame = annotated_frame
                    except Exception as e:
                        print(f"[YOLO] Inference error: {e}")
                        with self._lock:
                            self.frame = frame
                else:
                    with self._lock:
                        self.frame = frame
            else:
                time.sleep(0.1)

    def get_frame(self):
        with self._lock:
            return self.frame

    def is_active(self):
        return self.running and self.cap is not None and self.cap.isOpened()

    def stop(self):
        """Stop the inference loop and release the camera."""
        self.running = False
        if self.cap:
            self.cap.release()
            self.cap = None
        with self._lock:
            self.frame = None
        print("[YOLO] Camera and inference loop stopped.")

    def _handle_detection(self, frame, results):
        """Processes detection: Triggers pause, logs error, and notifies frontend."""
        # Cooldown check: don't pause more than once every 30 seconds
        now = time.time()
        if hasattr(self, '_last_pause_time') and now - self._last_pause_time < 30:
            return
        self._last_pause_time = now

        print("[AI] DEFECT DETECTED! Triggering system response...")
        
        # 1. Map detection to error type (Simple heuristic)
        error_type = 'other'
        confidence = 0.0
        if len(results.boxes) > 0:
            confidence = float(results.boxes[0].conf[0])
            # Assuming class 0 is spaghetti, 1 is layer shift, etc. 
            # (Matches common 3D printing datasets)
            class_id = int(results.boxes[0].cls[0])
            mapping = {0: 'spaghetti', 1: 'layer_shift', 2: 'stringing'}
            error_type = mapping.get(class_id, 'other')

        # 2. Trigger OctoPrint Pause
        self._pause_octoprint()

        # 3. Save to ErrorLog & Database
        try:
            from error_logs.models import ErrorLog
            from print_jobs.models import PrintJob
            
            active_job = PrintJob.objects.filter(status='printing').first()
            if active_job:
                # Encode frame to JPEG for storage
                _, buffer = cv2.imencode('.jpg', frame)
                content = ContentFile(buffer.tobytes())
                
                log_entry = ErrorLog.objects.create(
                    print_job=active_job,
                    error_type=error_type,
                    confidence=confidence,
                    action_taken='paused'
                )
                log_entry.frame_image.save(f"defect_{log_entry.id}.jpg", content)
                
                # Update job status
                active_job.status = 'paused'
                active_job.save(update_fields=['status'])
        except Exception as e:
            print(f"[AI] Error logging detection: {e}")

        # 4. Notify Frontend via WebSocket
        try:
            channel_layer = get_channel_layer()
            async_to_sync(channel_layer.group_send)(
                "dashboard_updates",
                {
                    "type": "dashboard_message",
                    "message_type": "ai_defect_alert",
                    "message": {
                        "defect": error_type.replace('_', ' ').title(),
                        "confidence": f"{confidence:.0%}",
                        "action": "Printing Paused Automatically",
                        "timestamp": timezone.now().strftime("%H:%M:%S")
                    }
                }
            )
        except Exception as e:
            print(f"[AI] WebSocket notification failed: {e}")

    def _pause_octoprint(self):
        """Sends a pause command to OctoPrint API."""
        octo_url = getattr(settings, 'OCTOPRINT_URL', 'http://localhost:5000')
        octo_key = getattr(settings, 'OCTOPRINT_API_KEY', '')
        
        try:
            url = f"{octo_url.rstrip('/')}/api/job"
            headers = {'X-Api-Key': octo_key}
            payload = {"command": "pause", "action": "pause"}
            resp = requests.post(url, json=payload, headers=headers, timeout=5)
            if resp.ok:
                print("[AI] OctoPrint PAUSE command sent successfully.")
            else:
                print(f"[AI] OctoPrint pause failed: {resp.text}")
        except Exception as e:
            print(f"[AI] Could not connect to OctoPrint to pause: {e}")


# Global monitoring state
_monitoring_active = False
_streamer = None

def get_streamer():
    """Returns the global CameraStreamer instance (creates it if needed, but does NOT auto-start)."""
    global _streamer
    if _streamer is None:
        _streamer = CameraStreamer(index=0, model_paths=_yolo_model_paths)
    return _streamer

async def yolo_frames_generator():
    """Asynchronous MJPEG generator. Yields a 'stopped' frame when monitoring is inactive."""
    import asyncio
    global _monitoring_active

    while True:
        if not _monitoring_active:
            stopped_bytes = _make_error_frame("MONITORING STOPPED - CLICK START TO ACTIVATE")
            yield (b'--frame\r\nContent-Type: image/jpeg\r\n\r\n' + stopped_bytes + b'\r\n')
            await asyncio.sleep(1.0)
            continue

        streamer = get_streamer()
        frame = streamer.get_frame()

        if frame is None:
            if not streamer.is_active():
                info_bytes = _make_error_frame("CAMERA INITIALISING - PLEASE WAIT...")
            else:
                info_bytes = _make_error_frame("AI BOOTING UP...")
            yield (b'--frame\r\nContent-Type: image/jpeg\r\n\r\n' + info_bytes + b'\r\n')
            await asyncio.sleep(0.5)
            continue

        ret, buf = cv2.imencode('.jpg', frame)
        if not ret:
            await asyncio.sleep(0.05)
            continue

        yield (b'--frame\r\nContent-Type: image/jpeg\r\n\r\n' + buf.tobytes() + b'\r\n')
        await asyncio.sleep(0.033)  # ~30 fps

@csrf_exempt
async def video_feed(request):
    """Asynchronous MJPEG stream view."""
    print(f"[DEBUG] Video feed request received at {timezone.now()}")
    try:
        response = StreamingHttpResponse(
            yolo_frames_generator(),
            content_type='multipart/x-mixed-replace; boundary=frame',
        )
        response['Cache-Control'] = 'no-cache, no-store, must-revalidate'
        response['Access-Control-Allow-Origin'] = '*'
        return response
    except Exception as e:
        import traceback
        with open('backend_error.log', 'a') as f:
            f.write(f"\n--- VIEW ERROR at {timezone.now()} ---\n{str(e)}\n")
            traceback.print_exc(file=f)
        return HttpResponse("Internal Server Error", status=500)

# ════════════════════════════════════════════════════════════════════
#  Monitoring Control Endpoints
# ════════════════════════════════════════════════════════════════════

class StartMonitoringView(APIView):
    """POST /api/dashboard/start-monitoring/ — Activate webcam & YOLOv8 inference."""
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        global _monitoring_active
        if _monitoring_active:
            return Response({'status': 'already_running', 'monitoring': True})
        _monitoring_active = True
        streamer = get_streamer()
        streamer.start()
        print("[MONITOR] Monitoring STARTED by user request.")
        return Response({'status': 'started', 'monitoring': True})


class StopMonitoringView(APIView):
    """POST /api/dashboard/stop-monitoring/ — Release webcam & stop YOLOv8 inference."""
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        global _monitoring_active, _streamer
        if not _monitoring_active:
            return Response({'status': 'already_stopped', 'monitoring': False})
        _monitoring_active = False
        if _streamer is not None:
            _streamer.stop()
            _streamer = None  # Reset so next start() gets a clean instance
        print("[MONITOR] Monitoring STOPPED by user request.")
        return Response({'status': 'stopped', 'monitoring': False})


class MonitoringStatusView(APIView):
    """GET /api/dashboard/monitoring-status/ — Returns current monitoring state."""
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        global _monitoring_active
        return Response({'monitoring': _monitoring_active})


# ════════════════════════════════════════════════════════════════════
#  Dashboard Stats & Printer Status
# ════════════════════════════════════════════════════════════════════

class DashboardStatsView(APIView):
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        user_models = GeneratedModel.objects.count()
        total_prints = PrintJob.objects.count()
        successful_prints = PrintJob.objects.filter(status='completed').count()

        failure_rate = 0
        if total_prints > 0:
            failed_prints = PrintJob.objects.filter(status='failed').count()
            failure_rate = (failed_prints / total_prints) * 100

        return Response({
            'total_models': user_models,
            'total_prints': total_prints,
            'successful_prints': successful_prints,
            'failure_rate': round(failure_rate, 1),
            'ai_uptime': 99.8,
        })


class PrinterStatusView(APIView):
    """Returns live (or simulated) status of the connected 3D printer."""
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        active_job = PrintJob.objects.filter(status__in=['printing', 'paused']).first()

        simulated = True
        printer_connected = False
        print_state = 'Offline'
        nozzle_actual = 0.0
        nozzle_target = 0.0
        bed_actual = 0.0
        bed_target = 0.0
        progress = 0.0

        octo_url = getattr(settings, 'OCTOPRINT_URL', 'http://localhost:5000')
        octo_key = getattr(settings, 'OCTOPRINT_API_KEY', '')

        try:
            r = requests.get(f"{octo_url}/api/printer", headers={'X-Api-Key': octo_key}, timeout=2)
            if r.status_code == 200:
                data = r.json()
                nozzle_actual = data['temperature']['tool0']['actual']
                nozzle_target = data['temperature']['tool0']['target']
                bed_actual = data['temperature']['bed']['actual']
                bed_target = data['temperature']['bed']['target']
                printer_connected = True
                simulated = False

                jr = requests.get(f"{octo_url}/api/job", headers={'X-Api-Key': octo_key}, timeout=2)
                if jr.status_code == 200:
                    jdata = jr.json()
                    progress = jdata['progress']['completion'] or 0.0
                    print_state = jdata['state']
        except Exception:
            if active_job:
                elapsed = (timezone.now() - active_job.created_at).seconds
                warm_factor = min(elapsed / 300, 1.0)

                nozzle_actual = round(220 * warm_factor + random.uniform(-1, 1), 1)
                nozzle_target = 220.0
                bed_actual = round(60 * warm_factor + random.uniform(-0.5, 0.5), 1)
                bed_target = 60.0
                progress = min(active_job.progress + (elapsed / 36), 100.0)
                print_state = 'Virtual Printing' if active_job.status == 'printing' else 'Virtual Paused'
                printer_connected = True
            else:
                progress = 0
                print_state = 'Virtual Printer Idle'
                printer_connected = True

        # Override the current system status to 'Online' across the main dashboard
        print_state = 'Online'
        printer_connected = True

        return Response({
            'simulated': simulated,
            'printer_connected': printer_connected,
            'print_state': print_state,
            'nozzle': {'actual': nozzle_actual, 'target': nozzle_target},
            'bed': {'actual': bed_actual, 'target': bed_target},
            'progress': round(progress, 1),
        })
