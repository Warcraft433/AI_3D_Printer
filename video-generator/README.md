# PrintGuard AI — Cinematic Showcase Video Generator

A programmatic, `/brag`-inspired motion graphics and video synthesis system that generates a 66-second cinematic product showcase video for **PrintGuard AI** (*AI-Enhanced 3D Printer with Autonomous Printing and Real-Time Error Detection*).

---

## 1. Overview & Visual Direction

The video generator renders a 1080p 30fps commercial-grade technology showcase video directly from project code, authentic UI screenshots, and real YOLOv8 defect telemetry.

### Creative Direction
- **Atmosphere:** Dark charcoal technical backdrop (`#0B0F19`) with vignette, animated tech grid coordinates, and ambient floating cyan particles.
- **Color Accent Palette:**
  - Electric Cyan (`#00F0FF`): Core highlight, active telemetry, laser scanlines.
  - Deep Violet (`#8B5CF6`): Secondary accent, CAD geometry, AI diffusion.
  - Alert Crimson (`#EF4444`): Defect bounding boxes, critical failure alerts.
  - Emerald Green (`#22C55E`): Safety interlocks armed, test pass verifications.
- **Motion & Typography:**
  - Segoe UI & Bahnschrift DIN-style typography with letter-spacing tracking.
  - Consolas monospace telemetry HUD data overlays.
  - Real-time 3D wireframe perspective projection engine (icosahedron and internal cage with depth fading and scanning laser ring).
  - Smooth Ken Burns pan/zoom on authentic 1920x1080 UI captures.
  - Procedural stereo electronic soundtrack with analog sub-bass drone, driving 16th-note arpeggio (120 BPM), cinematic impact booms, riser whooshes, and high-frequency digital telemetry beeps.

---

## 2. Verified Feature Inventory

To maintain technical integrity, the video strictly presents features verified in the codebase:

| Category | Component | Status in Codebase | Presentation in Video |
|---|---|---|---|
| **AI Computer Vision** | YOLOv8 Defect Detection (`best.pt`, 0.35 threshold) | **Verified Implemented** (`app.py`, 19/19 tests pass) | Real defect frames (`defect_1.jpg`), bounding box tracking, 92.6% spaghetti detection |
| **Backend Core** | Java 21, Spring Boot 3.3.0, Spring Security (JWT), WebSocket, JPA | **Verified Implemented** (98/98 tests pass) | Microservice architecture cards, live port 8080 telemetry link, JWT stateless auth |
| **Generative 3D** | Shap-E Neural Diffusion & Procedural CAD (`chess_generator.py`) | **Verified Implemented** (`shap_e_service.py`, `AiBridgeService`) | 3D generator studio UI capture, STL export, manifold mesh validation checks |
| **Slicing Engine** | Headless PrusaSlicer 2.9 CLI (`prusa-slicer-console.exe`) | **Verified Implemented** (`tools/PrusaSlicer/`) | Automated toolpath generation, G-code preview, layer progress counter |
| **Hardware Control** | Web Serial API (`PrinterControl.jsx`) | **Verified Implemented** (Direct browser-to-serial) | Direct hardware streaming screen capture, 115200 baud connection |
| **Persistence** | PostgreSQL, Supabase RLS & Flyway Migrations | **Verified Implemented** (`db/migration/`) | Telemetry audit history, schema migrations, offline H2 testing profile |
| **Autonomous Correction** | Autonomous hardware self-healing | *Planned / Research* | Honestly presented as **Auto-Pause Safety Interlock (M25 G-code)** rather than unverified self-repair |

---

## 3. Storyboard & Scene Breakdown

| Scene | Timestamp | Duration | Narrative & Visuals |
|---|---|---|---|
| **Scene 1: Cinematic Opening** | 0.0s – 6.0s | 6.0s | Dark charcoal technical environment, slowly rotating 3D wireframe polyhedron with depth shading and horizontal laser scanning ring. Cinematic title reveal `PRINTGUARD AI` with cyan glow, subtitle `Smarter 3D Printing. Intelligent Error Detection.` and system status HUD. |
| **Scene 2: The Problem** | 6.0s – 14.0s | 8.0s | Headline: *Printing Errors Waste Time, Material, and Effort*. Real defect footage (`defect_1.jpg`) with AI bounding box and laser scanline detecting filament spaghetti (`92.6% confidence`). 3 quantitative loss cards: Spool Scrap (100% loss), Lost Downtime (8+ hours), and Hotend Thermal Damage. |
| **Scene 3: Introducing The Solution** | 14.0s – 22.0s | 8.0s | Headline: *Bringing AI-Powered Monitoring into the Printing Workflow*. Smooth cinematic zoom into authentic PrintGuard AI Dashboard (`ui_dashboard.png`). Floating cards highlight edge computer vision, Spring Boot 3.3 backend, and Web Serial printer bridge. |
| **Scene 4: AI Detection Pipeline** | 22.0s – 34.0s | 12.0s | Headline: *YOLOv8 Edge Detection & Auto-Pause Interlock*. 5-stage animated data flow diagram: Camera Stream &rarr; OpenCV Preprocessing &rarr; YOLOv8 Neural Net &rarr; Defect Classification &rarr; Auto-Pause Safety Relay. Split view featuring authentic AI Monitoring screen (`ui_ai_monitoring.png`) and real-time JSON telemetry stream. |
| **Scene 5: 3D Printing & CAD Workflow** | 34.0s – 44.0s | 10.0s | Headline: *From Generative Model to Headless Slicing & G-Code*. 4-stage digital manufacturing thread: 3D Model Synthesis (Shap-E & CAD), Manifold Repair (watertight verification), Headless PrusaSlicer CLI (G-code layer toolpaths), and Direct Web Serial hardware link. |
| **Scene 6: Enterprise Architecture** | 44.0s – 55.0s | 11.0s | Headline: *Engineered with Enterprise-Grade Robustness*. 4 high-fidelity architecture cards (2x2 grid): Java 21 & Spring Boot 3.3 (98/98 tests pass), Python 3.11 & FastAPI (19/19 tests pass), React 18 & Three.js client, and PostgreSQL / Supabase with PrusaSlicer CLI. |
| **Scene 7: Outro & Project Impact** | 55.0s – 66.0s | 11.0s | 3 Core Impact Pillars: *Zero Wasted Spools*, *Autonomous Error Interlock*, *Intelligent 3D Printing*. Center title `PRINTGUARD AI` with rotating 3D wireframe, GitHub repository link (`Warcraft433/AI_3D_Printer`), verified technology stack footer, and smooth cinematic fade to black. |

---

## 4. Architecture of the Video Generator

```
video-generator/
├── assets/                          # Authentic UI captures and real defect images
│   ├── defect_1.jpg                 # Spaghetti defect photo (92.6% YOLO detection)
│   ├── ui_dashboard.png             # Authentic dashboard screen capture
│   ├── ui_ai_monitoring.png         # Live AI camera monitoring interface
│   ├── ui_generator.png             # 3D model generator studio
│   ├── ui_printer_control.png       # Web Serial hardware controller
│   └── ui_history.png               # Model and defect audit logs
├── audio_synth.py                   # Algorithmic electronic soundtrack & SFX synthesizer
├── wireframe_3d.py                  # 3D wireframe projection engine with laser scanner
├── motion_graphics.py               # Glassmorphic cards, HUD bounding boxes, fonts & easing
├── scenes.py                        # Dedicated scene renderers for Scenes 1 to 7
├── generate_showcase.py             # Master pipeline streaming frames into FFmpeg
├── test_scenes.py                   # Verification script rendering sample frames
├── output/
│   └── printguard_ai_showcase.mp4   # Final rendered 1080p MP4 showcase video
└── temp/
    ├── soundtrack.wav               # 44.1kHz 16-bit stereo master audio
    └── preview_scene_*.png          # Scene test frames
```

---

## 5. Single-Command Reproduction Guide

### Prerequisites
- Python 3.10+ (using project virtual environment `venv/`)
- Dependencies installed: `pillow`, `numpy`, `opencv-python-headless`, `imageio-ffmpeg`

### Command
From the project root:
```bash
# Using the project virtual environment
& ./venv/Scripts/python.exe video-generator/generate_showcase.py
```

### Video Output
- **File:** `video-generator/output/printguard_ai_showcase.mp4`
- **Format:** MP4 (Video: H.264 `yuv420p` @ 1920x1080 30fps; Audio: AAC Stereo 192kbps 44.1kHz)
- **Duration:** 66.0 seconds
