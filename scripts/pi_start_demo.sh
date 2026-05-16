#!/bin/bash
# =============================================================================
# pi_start_demo.sh — PrintGuard AI Demo Startup Script for Raspberry Pi
# =============================================================================
# Run this script on the Raspberry Pi before each demonstration to bring
# up a clean OctoPrint instance and MJPEG webcam stream.
# Usage: chmod +x pi_start_demo.sh && ./pi_start_demo.sh
# =============================================================================

# Do NOT use 'set -e' here — some steps are allowed to fail (e.g. pkill with no process)


echo ""
echo "======================================================"
echo "  PrintGuard AI — Raspberry Pi Demo Startup"
echo "======================================================"
echo ""

# ------------------------------------------------------------------------------
# OctoPrint runs from a Python venv on this Pi, NOT as a systemd service.
# Kill any existing OctoPrint process, then restart it in the background.
echo "[1/3] Starting OctoPrint (venv) on port 5000..."
pkill -f 'octoprint serve' 2>/dev/null || true
sleep 1
nohup ~/OctoPrint/venv/bin/octoprint serve --port 5000 \
    > ~/OctoPrint/octoprint_startup.log 2>&1 &
echo "      ✅ OctoPrint started (PID: $!). Log: ~/OctoPrint/octoprint_startup.log"
sleep 2

# ------------------------------------------------------------------------------
# Step 2: Kill any existing VLC/CVLC processes to free up /dev/video0.
# Without this step, the webcam may report "device busy" when we try to stream.
# ------------------------------------------------------------------------------
echo "[2/3] Releasing webcam device (/dev/video0)..."
pkill -x vlc  2>/dev/null || true
pkill -x cvlc 2>/dev/null || true
sleep 1
echo "      ✅ Old VLC processes cleared."

# ------------------------------------------------------------------------------
# Step 3: Launch the MJPEG webcam stream in the background using nohup.
# Flags explained:
#   v4l2:///dev/video0       — Capture from USB webcam via Video4Linux2
#   --v4l2-width/height      — Set capture resolution to 640x480
#   --live-caching=0         — Disable live input buffer for minimum latency
#   --network-caching=0      — Disable network output buffer for minimum latency
#   --sout-mux-caching=10    — Keep muxer cache at 10ms to reduce delay
#   --sout-keep              — Keep stream alive even if VLC UI would close
#   http{mux=mpjpeg,...}     — Broadcast as MJPEG over HTTP on port 8080
#   --no-audio               — We only need video, skip audio processing
# ------------------------------------------------------------------------------
echo "[3/3] Starting MJPEG stream on port 8080..."
nohup cvlc \
    v4l2:///dev/video0 \
    --v4l2-width=640 \
    --v4l2-height=480 \
    --live-caching=0 \
    --network-caching=0 \
    --sout-mux-caching=10 \
    --no-audio \
    --sout '#standard{access=http,mux=mpjpeg,dst=:8080/}' \
    > ~/stream.log 2>&1 &

STREAM_PID=$!
echo "      ✅ VLC launched (PID: ${STREAM_PID}). Log: ~/stream.log"
sleep 3

# Verify VLC is still running (didn't crash)
if kill -0 ${STREAM_PID} 2>/dev/null; then
    echo "      ✅ VLC is running. Stream is live!"
else
    echo "      ❌ VLC crashed! Check ~/stream.log for errors:"
    tail -20 ~/stream.log
    echo ""
    echo "      Try: sudo apt-get install vlc -y   then re-run this script."
fi

# ------------------------------------------------------------------------------
# Dynamically resolve the Pi's local IP address and print access URLs.
# hostname -I returns all IPs; we take the first one (local LAN IP).
# ------------------------------------------------------------------------------
PI_IP=$(hostname -I | awk '{print $1}')

echo ""
echo "======================================================"
echo "  ✅ Raspberry Pi Edge Setup Ready!"
echo "======================================================"
echo ""
echo "  OctoPrint Dashboard  →  http://${PI_IP}:5000"
echo "  Live Camera Feed     →  http://${PI_IP}:8080/"
echo ""
echo "  The centralized inference engine on your Windows"
echo "  laptop can now connect to the camera stream."
echo "  Make sure both devices are on the same Wi-Fi network."
echo ""
echo "======================================================"
