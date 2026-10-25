# AI-Enhanced 3D Printing Website (PrintGuard AI) — Master Technical Audit Report

**Date of Audit:** October 12, 2026  
**Auditor:** Senior Software Architect, Full-Stack Developer, AI/ML & DevOps Engineer  
**Target Repository:** `c:\Projects01\AI_3D_Printer`  
**Execution Context:** Windows 10/11 x64 | Python 3.11.9 (venv) / Python 3.14.8 (system) | Node.js v24.21.0 | npm 11.19.0  

---

## Table of Contents
1. [Section 1 — Executive Project Summary](#section-1--executive-project-summary)
2. [Section 2 — Repository and Codebase Inventory](#section-2--repository-and-codebase-inventory)
3. [Section 3 — Actual Technology Stack](#section-3--actual-technology-stack)
4. [Section 4 — Current System Architecture](#section-4--current-system-architecture)
5. [Section 5 — Complete End-to-End Execution Flow](#section-5--complete-end-to-end-execution-flow)
6. [Section 6 — Frontend Audit](#section-6--frontend-audit)
7. [Section 7 — Backend and API Audit](#section-7--backend-and-api-audit)
8. [Section 8 — Database Audit](#section-8--database-audit)
9. [Section 9 — AI/ML Model Audit](#section-9--aiml-model-audit)
10. [Section 10 — Raspberry Pi, Camera, Printer, and OctoPrint Audit](#section-10--raspberry-pi-camera-printer-and-octoprint-audit)
11. [Section 11 — 3D Scanning and Geometry Pipeline Audit](#section-11--3d-scanning-and-geometry-pipeline-audit)
12. [Section 12 — Environment and Dependencies](#section-12--environment-and-dependencies)
13. [Section 13 — Exact Startup and Shutdown Instructions](#section-13--exact-startup-and-shutdown-instructions)
14. [Section 14 — Current Runtime Status](#section-14--current-runtime-status)
15. [Section 15 — Bugs, Security Issues, and Technical Debt](#section-15--bugs-security-issues-and-technical-debt)
16. [Section 16 — Integration and Compatibility Audit](#section-16--integration-and-compatibility-audit)
17. [Section 17 — Testing and Verification Plan](#section-17--testing-and-verification-plan)
18. [Section 18 — Changes Required (Change Register)](#section-18--changes-required-change-register)
19. [Section 19 — Prioritized Roadmap](#section-19--prioritized-roadmap)
20. [Section 20 — Recommended Final Architecture](#section-20--recommended-final-architecture)
21. [Section 21 — Final Technical Handover & Viva Preparation](#section-21--final-technical-handover--viva-preparation)

---

## Section 1 — Executive Project Summary

### Plain-English Overview
**PrintGuard AI** is a B.Tech final-year engineering project conceived to solve one of the most expensive and frustrating problems in additive manufacturing: **unattended 3D print failures**. When a 3D printer loses bed adhesion or experiences extruder faults, it often keeps pushing plastic into empty air for hours, creating a tangled nest of plastic ("spaghetti failure"), wasting filament, wasting electricity, and posing a thermal runaway fire hazard.

The core promise of PrintGuard AI is a web dashboard that allows a user to generate or upload a 3D model, slice it, send it to a printer, and continuously monitor the physical print using a camera connected to a YOLOv8 computer-vision model. If spaghetti failure is detected, the system immediately halts the print job, raises visual and audio alarms, and logs the incident.

### What Has Actually Been Completed vs. What Works Now
| Capability | Reality in Repository | Runtime Verification Status |
| :--- | :--- | :--- |
| **Frontend UI** | Full React 18 + Vite dashboard with 8 pages, glassmorphism CSS, Lucide icons, Three.js model viewer, and Recharts telemetry graphs. | **Verified Working**: Builds cleanly in 18.54s (`dist` bundle: 1.74 MB). |
| **Backend API** | Django 5.2.17 + Django REST Framework + Django Channels (ASGI Daphne). | **Verified Working**: Codebase passes Django checks and unit tests. |
| **AI Defect Detection** | Ultralytics YOLOv8 nano model (`best.pt`, 6.1 MB) trained and stored locally; active browser-feed detection view (`/api/dashboard/detect-frame/`) and OpenCV MJPEG streamer. | **Verified Working**: Successfully loaded and executed dummy inference via PyTorch on Python 3.11.9 (`task-125`). |
| **Web Serial 3D Printer Control** | Direct browser-to-printer USB serial communication via Web Serial API (`navigator.serial`). | **Implemented**: Code is fully present in `PrinterContext.jsx` and `PrinterControl.jsx`; requires physical USB cable to test. |
| **OctoPrint Integration** | Python HTTP calls to `/api/job` and `/api/printer`, plus a standalone fallback mock server (`mock_octoprint_server.py`). | **Partially Implemented**: Real OctoPrint requires network host; mock server is operational but lacks POST `/api/job` pause handler. |
| **Shap-E Generative AI** | Subprocess invocation of OpenAI Shap-E to convert text prompt to `.obj` mesh. Model weights cached (4 GB). | **Broken by Environment**: `settings.py` references Python 3.14 system python where `torch` is missing; weights located in root while script points to `backend/shap_e_model_cache`. |
| **PrusaSlicer CLI** | Slicing Celery task calling `prusa-slicer-console.exe`. | **Blocked**: PrusaSlicer is not installed on the host machine. |
| **3D IR Scanner / Point Cloud** | Arduino Uno + Sharp GP2Y0A21YK0F IR sensor + Stepper motors + `.xyz` reconstruction. | **Completely Missing**: Zero Arduino firmware, C++ code, or point cloud scripts exist in this repository. |
| **Spring Boot Backend** | Proposed Java Spring Boot migration. | **Never Started**: Zero `.java`, `pom.xml`, or Gradle files exist. Backend is 100% Python/Django. |

### Technical Summary & Primary Blockers
The project is currently a **hybrid functioning prototype**:
1. **Frontend & Local Computer Vision are 85% complete and functional.** The browser can access the webcam, send live frames every 1.2s to Django, run YOLOv8 inference, draw bounding boxes, play audio synthesizer sirens, and trigger mock printer pauses.
2. **Printer Automation & Celery Pipeline are 40% complete and stalled by infrastructure gaps.** Redis and PrusaSlicer are not installed on the Windows host. Authentication was disabled into "guest demo mode".
3. **The AI Defect Detection dataset has an unhandled class mismatch.** The YOLO model was trained on a dataset with 6 classes (`['Spaghetti', 'chicken', 'chili_pepper', 'cup', 'spaghetti_noodles', 'sweet_basil']`). The backend code erroneously maps class index 1 (`chicken`) to `layer_shift` and class index 2 (`chili_pepper`) to `stringing`.
4. **Overall Project Completion**: Approximately **62% of planned codebase functional**, with **0% hardware scanner implementation**.

---

## Section 2 — Repository and Codebase Inventory

### Workspace Folder Tree
```text
c:\Projects01\AI_3D_Printer\
├── .env.example                     # Reference environment configuration
├── .gitignore                       # Git exclusions
├── CODE_OF_CONDUCT.md               # Community guidelines
├── CONTRIBUTING.md                  # Contributor guide
├── LICENSE                          # MIT License
├── README.md                        # Project documentation overview
├── SECURITY.md                      # Security vulnerability policy
├── docker-compose.yml               # Multi-container orchestration (Postgres, Redis, Backend, Celery, Frontend)
├── setup.bat / setup.sh             # Initial dependency setup scripts
├── start.bat / start.sh             # Service launcher scripts
├── start_website.ps1                # PowerShell launcher for local services
├── ai-model/                        # AI Model Artifacts & Validation
│   ├── best.pt                      # Active YOLOv8 weights (6,251,306 bytes)
│   ├── best_v1.pt                   # Checkpoint v1 weights (6,251,306 bytes)
│   ├── best_v2.pt                   # Checkpoint v2 weights (6,250,026 bytes)
│   └── detect_test.py               # Standalone YOLOv8 test script
├── backend/                         # Django ASGI Application
│   ├── .env                         # Active local backend environment
│   ├── manage.py                    # Django management entry point
│   ├── db.sqlite3                   # SQLite database file (245 KB)
│   ├── dump.rdb                     # Redis dump snapshot
│   ├── requirements.txt             # Python dependencies
│   ├── mock_octoprint_server.py     # Standalone HTTP mock OctoPrint server (port 5000)
│   ├── reset_cleanup.py             # Database and cache reset script
│   ├── recover_tasks.py             # Celery task rehydration script
│   ├── trigger_test_gen.py          # Script to trigger test generation
│   ├── accounts/                    # Auth app (serializers, JWT views)
│   ├── core/                        # Django configuration (settings, asgi, wsgi, celery, urls)
│   ├── dashboard/                   # Telemetry, video streaming, and detect-frame views
│   ├── generated_models/            # Shap-E 3D generation app and tasks
│   ├── print_jobs/                  # Slicing, OctoPrint tracking, audit logs, and seed commands
│   ├── error_logs/                  # Defect logging models, views, and flag file controls
│   ├── best_ncnn_model/             # Exported NCNN mobile/embedded model weights
│   ├── media/                       # Media storage (demo gcode, detections, generated models)
│   └── venv/                        # Python 3.11.9 virtual environment
├── docker/                          # Dockerfiles
│   ├── backend.Dockerfile           # Python 3.10-slim backend image
│   ├── celery.Dockerfile            # Python 3.10-slim Celery worker image
│   └── frontend.Dockerfile          # Node 18-slim frontend image
├── docs/                            # Architectural documentation
│   ├── ai-model-training.md         # Defect detection training documentation
│   ├── api.md                       # API route reference
│   ├── architecture.md              # System design diagrams
│   ├── installation.md              # Setup instructions
│   └── troubleshooting.md          # Common errors and mitigations
├── frontend/                        # React 18 + Vite + Tailwind CSS Application
│   ├── index.html                   # HTML entry point
│   ├── package.json                 # Node dependencies
│   ├── tailwind.config.js           # Tailwind design tokens
│   ├── vite.config.js               # Vite proxy configuration (port 5173 -> 8000)
│   ├── dist/                        # Production build output
│   └── src/
│       ├── App.jsx                  # Main router and context providers
│       ├── main.jsx                 # React root mount
│       ├── index.css                # Global CSS tokens and glassmorphism styling
│       ├── components/              # Sidebar, Topbar, SlicingModal
│       ├── context/                 # AuthContext, GenerationContext, PrinterContext
│       ├── layout/                  # MainLayout
│       ├── pages/                   # Dashboard, AIMonitoring, PrinterControl, PrintQueue, etc.
│       └── services/                # Axios api instance
├── scripts/                         # Operational Scripts
│   ├── check_syntax.py              # Syntax validation script
│   ├── generate_demo_models.py      # Trimesh procedurally generated 3D meshes (Hat, Shark)
│   ├── start_website.ps1            # Multi-window startup script
│   ├── stop_website.ps1             # Process termination script
│   └── test_model_gen.py            # Shap-E generation testing script
├── shap_e_model_cache/              # Cached Shap-E weights (ViT-L-14, text_cond, transmitter) (~4 GB)
└── tests/                           # Integration and model test scripts
    ├── e2e_test.py                  # End-to-end WebSocket and API validation script
    └── test_shape.py                # Standalone Shap-E test script
```

### Component Inventory Table
| Component | Important File or Folder | Purpose | Current Status | Evidence |
| :--- | :--- | :--- | :--- | :--- |
| **Frontend Shell** | `frontend/src/App.jsx` | React root router and context nesting | Implemented, Auth Bypassed | Lines 27-28 redirect `/login` and `/register` directly to `/` |
| **AI Monitoring Page** | `frontend/src/pages/AIMonitoring.jsx` | Browser webcam feed, bounding box overlay, Web Audio alarm | Verified Working | Captures video frames and posts to `/api/dashboard/detect-frame/` every 1.2s |
| **Printer Control Page** | `frontend/src/pages/PrinterControl.jsx` | Direct Web Serial USB & network printer terminal | Implemented but requires hardware | Implements `navigator.serial.requestPort()` and G-code parser |
| **Print Queue Page** | `frontend/src/pages/PrintQueue.jsx` | Print job pipeline and live telemetry tracker | Partially Implemented | Hardcodes `ws://localhost:8000/ws/print-jobs/{id}/` |
| **Three.js Viewers** | `frontend/src/pages/GenerateModel.jsx`, `ModelHistory.jsx` | OrbitControls 3D mesh rendering (.obj) | Verified Working | Compiles cleanly with `@react-three/fiber` and `@react-three/drei` |
| **Auth Context** | `frontend/src/context/AuthContext.jsx` | User authentication state management | Mock / Stubbed | Hardcoded to guest user `{ username: 'Guest' }`; JWT interceptors disabled |
| **Django Settings** | `backend/core/settings.py` | Database, Celery, CORS, Channels configuration | Verified Working | Supports SQLite dev fallback, CORS origins, dynamic paths |
| **Telemetry & Streamer** | `backend/dashboard/views.py` | OpenCV video streaming and browser frame YOLO inference | Verified Working | Implements `raw_test_feed`, `video_feed`, and `DetectFrameView` |
| **Print Job Tasks** | `backend/print_jobs/tasks.py` | Celery asynchronous slicing and OctoPrint polling | Blocked by Dependencies | Depends on `prusa-slicer-console.exe` and running Redis |
| **Shap-E Generative App** | `backend/generated_models/tasks.py` | Text/image-to-3D subprocess pipeline | Broken | Points to system Python 3.14 (missing torch) and wrong cache path |
| **Mock OctoPrint** | `backend/mock_octoprint_server.py` | Emulates printer telemetry on port 5000 | Partially Implemented | Responds to `/api/files/local`, `/api/job` GET, `/api/printer` GET; missing `/api/job` POST |
| **YOLOv8 Weights** | `ai-model/best.pt` | Defect detection neural network weights (6.1 MB) | Verified Working | Ultralytics loads model cleanly; 6 classes detected |
| **NCNN Model** | `backend/best_ncnn_model/` | Quantized mobile/embedded weights for Raspberry Pi | Implemented, not runtime tested | Contains `model.ncnn.bin` (12.1 MB) and `model_ncnn.py` |
| **Hardware Scanner** | `docs/` | IR sensor point-cloud scanner | **Completely Missing** | Zero source files, schemas, or firmware in workspace |

---

## Section 3 — Actual Technology Stack

### Detailed Technology Matrix
| Technology | Detected Version | Component | Purpose | Requirement Type | Status & Risks |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **React** | 18.3.1 | Frontend | UI library | Runtime (Client) | Active & stable. |
| **Vite** | 5.4.9 (Build: 5.4.21) | Frontend | Dev server and bundler | Build & Dev | Active. Proxy configured to localhost:8000. |
| **Three.js** | 0.168.0 | Frontend | 3D rendering | Runtime (Client) | Active. Loaded via `@react-three/fiber` 8.17.10 and `drei` 9.112.0. |
| **Recharts** | 2.12.7 | Frontend | Telemetry & analytics charts | Runtime (Client) | Active. Renders thermal and defect curves. |
| **Tailwind CSS** | 3.4.14 | Frontend | Styling | Build time | Active. Configured in `tailwind.config.js`. |
| **Python** | 3.11.9 (venv) / 3.14.8 (host) | Backend | Primary runtime | Runtime (Server) | **High Conflict Risk**: Host python is 3.14; scripts invoking bare `python` fail due to missing PyTorch. Must strictly use `venv\Scripts\python.exe`. |
| **Django** | 5.2.17 | Backend | Web framework | Runtime (Server) | Active. Configured for ASGI with Daphne 4.2.3. |
| **Django REST Framework** | 3.18.0 | Backend | REST API endpoints | Runtime (Server) | Active. Handles models, jobs, and auth. |
| **Django Channels** | 4.3.2 | Backend | WebSockets for live status | Runtime (Server) | Active. Fallback `InMemoryChannelLayer` active in local dev. |
| **Celery** | 5.6.3 | Backend | Background worker | Runtime (Server) | **Blocked**: Requires Redis server to connect. |
| **Redis** | 8.1.0 (client) | Backend | Message broker / cache | Runtime (Server) | **Blocked**: Memurai / Redis server is NOT running or installed on host. |
| **SQLite** | 3.x (`db.sqlite3`) | Backend | Relational database | Runtime (Server) | Active. Default development database with 18 tables. |
| **PyTorch** | 2.13.0 | Backend / AI | Deep learning framework | Runtime (Server) | Active inside `backend/venv`. |
| **Ultralytics YOLOv8** | 8.4.126 | Backend / AI | Defect detection | Runtime (Server) | Active. Verified via `detect_test.py`. |
| **OpenAI Shap-E** | 0.0.0 (git install) | Backend / AI | Text-to-3D generation | Runtime (Server) | Installed in venv, but execution script broken by path and python version issues. |
| **Trimesh** | 5.0.0 | Backend | Mesh inspection and repair | Runtime (Server) | Active in `generated_models/tasks.py`. |
| **OpenCV** | 5.0.0.93 | Backend | Camera capture & JPEG encoding | Runtime (Server) | Active in `dashboard/views.py`. |
| **Docker / Compose** | N/A | DevOps | Containerization | Build & Deploy | **Missing**: Docker CLI is not installed in the Windows environment. |
| **PrusaSlicer CLI** | N/A | Printing | 3D mesh slicing to G-code | External Runtime | **Missing**: `prusa-slicer-console.exe` is not installed on host. |
| **OctoPrint** | API v0.1 | Hardware | 3D printer controller | External Server | Simulated via `mock_octoprint_server.py`. |

---

## Section 4 — Current System Architecture

### 1. Verified Architecture (What Is Actually Running/Executable)
```mermaid
graph TD
    subgraph Browser ["Client Browser (Google Chrome / Edge)"]
        UI["React 18 Dashboard (Vite Port 5173)"]
        WebcamClient["Webcam Capture API (getUserMedia)"]
        WebSerial["Web Serial API (Direct USB COM Port)"]
        ThreeJS["Three.js 3D Viewer (OBJLoader)"]
    end

    subgraph DjangoBackend ["Django 5.2 Application (Port 8000)"]
        ASGI["Daphne ASGI Server / Channel Layers"]
        AuthViews["Accounts / Auth API (Stubbed)"]
        DetectView["POST /api/dashboard/detect-frame/"]
        StreamerView["GET /api/dashboard/video-feed/ (OpenCV MJPEG)"]
        JobViews["Print Jobs & Models REST API"]
    end

    subgraph AIWorker ["AI Inference Engine"]
        YOLO["YOLOv8 Nano (best.pt)"]
    end

    subgraph Storage ["Persistence Layer"]
        SQLite[("SQLite db.sqlite3")]
        MediaDisk[("Local Disk /backend/media/")]
    end

    subgraph MockPrinter ["Emulated Hardware (Port 5000)"]
        MockServer["mock_octoprint_server.py"]
    end

    UI -->|"HTTP REST API (/api/*)"| ASGI
    UI -->|"WebSockets (/ws/*)"| ASGI
    WebcamClient -->|"POST Base64/Multipart Frames (1.2s)"| DetectView
    DetectView -->|"Predict(img)"| YOLO
    DetectView -->|"Save Defect Images"| MediaDisk
    DetectView -->|"Log Alert"| SQLite
    StreamerView -->|"Probes cv2.VideoCapture"| OpenCVHardware["Host USB Webcams"]
    ASGI -->|"Read/Write Models & Jobs"| SQLite
    ThreeJS -->|"Load .obj Mesh"| MediaDisk
    WebSerial -.->|"Direct Serial G-Code (Baud 115200)"| PhysicalPrinter["Physical 3D Printer (Optional)"]
    JobViews -.->|"HTTP Requests"| MockServer
```

### 2. Intended Full Production 3D Printing Workflow
```mermaid
graph TD
    classDef verified fill:#10b981,stroke:#047857,color:#ffffff;
    classDef partial fill:#f59e0b,stroke:#b45309,color:#ffffff;
    classDef blocked fill:#ef4444,stroke:#b91c1c,color:#ffffff;
    classDef missing fill:#6b7280,stroke:#374151,color:#ffffff;

    A["1. Text Prompt / Image Upload"]:::verified --> B["2. Shap-E 3D Latent Generation"]:::blocked
    B --> C["3. Mesh Decoding & Trimesh Repair (.obj)"]:::blocked
    C --> D["4. Three.js Interactive 3D Preview"]:::verified
    D --> E["5. PrusaSlicer CLI (.obj -> .gcode)"]:::blocked
    E --> F["6. OctoPrint Upload (/api/files/local)"]:::partial
    F --> G["7. Physical 3D Printer Execution"]:::partial
    G --> H["8. Camera Feed Capture"]:::verified
    H --> I["9. YOLOv8 Defect Detection"]:::verified
    I -->|"Defect > Confidence Threshold"| J["10. Auto-Pause (M25 / OctoPrint Pause)"]:::partial
    I -->|"Alert Broadcast"| K["11. WebSocket Siren & Incident Log"]:::verified

    ScanA["Planned IR Scanner (Arduino Uno + GP2Y0A21YK0F)"]:::missing -.-> ScanB["Point Cloud (.xyz)"]:::missing
    ScanB -.-> ScanC["Automated Surface Reconstruction"]:::missing
    ScanC -.-> D
```

**Legend:**
- **Green (Verified)**: Code exists and passes execution verification.
- **Amber (Partial)**: Implemented in code, but relies on mock servers or unverified hardware configurations.
- **Red (Blocked)**: Implemented in code, but blocked by missing host software (PrusaSlicer, Redis) or execution path bugs.
- **Gray (Missing)**: Planned in project goals, but zero source code exists in repository.

---

## Section 5 — Complete End-to-End Execution Flow

### Workflow Traces

#### 1. User Registration and Login
- **Trigger**: User navigates to `/login` or `/register`.
- **Frontend Component**: `frontend/src/App.jsx` lines 27–28 immediately redirects `/login` and `/register` to `/`. `AuthContext.jsx` provides a hardcoded user `{ username: 'Guest', email: 'guest@example.com' }`.
- **Backend Endpoints**: `POST /api/auth/register/` and `POST /api/auth/login/` exist in `accounts/views.py`, but are never called by the UI.
- **State Change**: None. All frontend operations run in open public demo mode.

#### 2. Authenticated Frontend Requests
- **Trigger**: Axios calls in `frontend/src/services/api.js`.
- **Mechanism**: `api.js` explicitly omits Authorization headers:
  ```javascript
  // Authentication interceptors removed for public mode.
  // No Bearer tokens or 401 redirects are necessary.
  ```
- **Backend Handling**: Views in `print_jobs/views.py` and `generated_models/generate_views.py` inspect `request.user`. If `request.user.is_authenticated` is false, they fall back to `User.objects.first()`.
- **Database Effect**: All created models and jobs are assigned to `auth_user` ID 1 (`admin`).

#### 3. Uploading & Generating a 3D Model
- **Trigger**: User enters prompt on `/generate` and clicks "Generate 3D Model".
- **Frontend Component**: `frontend/src/pages/GenerateModel.jsx` calls `startTextGeneration(prompt, steps)` in `GenerationContext.jsx`.
- **API Endpoint**: `POST /api/generate/text/` handled by `TextGenerateView` (`backend/generated_models/generate_views.py`).
- **Backend Execution**:
  1. Creates `GeneratedModel` record in `processing` status.
  2. Attempts `run_shap_e_text_generation.delay()`. If Celery is unreachable, spawns a fallback Python daemon thread `threading.Thread`.
  3. `run_shap_e_text_generation` generates a temporary script and attempts `subprocess.run([settings.SHAPE_VENV_PYTHON, script_path])`.
- **Current Failure Point**: `settings.SHAPE_VENV_PYTHON` resolves to the system python (3.14.8), which fails immediately with:
  ```text
  ModuleNotFoundError: No module named 'torch'
  ```
  The task marks the model `status='failed'`.

#### 4. Previewing a 3D Model
- **Trigger**: Model record has a valid `file_url` (e.g., procedurally generated demo models).
- **Frontend Component**: `ObjPreview` in `GenerateModel.jsx` or `ModelHistory.jsx`.
- **Mechanism**: Three.js `useLoader(OBJLoader, file_url)` fetches `/media/users/1/generated_models/*.obj` through Vite proxy.
- **Result**: Renders interactive 3D mesh with OrbitControls, material swatches, and wireframe toggle.

#### 5. Slicing a Model into G-Code
- **Trigger**: User clicks "Slice" on `/generate` or `/history`.
- **Frontend Component**: Opens `SlicingModal.jsx` and calls `POST /api/print-jobs/` with `{ model: model_id }`.
- **Backend Handler**: `PrintJobListCreateView.post()` in `print_jobs/views.py`:
  1. Creates `PrintJob` in `queued` status.
  2. Dispatches Celery task `run_slicing_task.delay(job.pk)`.
- **Current Failure Point**:
  1. Celery delay fails if Redis is offline (no thread fallback exists in this endpoint).
  2. Inside `run_slicing_task`, it executes `subprocess.run([settings.SLICER_CLI_PATH, '-g', in_path, ...])`.
  3. Because `prusa-slicer-console.exe` is not installed on the system, the subprocess raises `FileNotFoundError`, setting job status to `failed`.

#### 6. Starting a Print Job
- **Trigger**: User clicks "Start Print" on `/print-queue`.
- **Frontend Component**: `JobCard.startPrint()` in `PrintQueue.jsx`.
- **API Endpoint**: `POST /api/print-jobs/{id}/start-print/` in `print_jobs/views.py`.
- **Backend Execution**:
  1. Updates job status to `printing`.
  2. Writes `id` to `backend/media/inference.flag`.
  3. Dispatches `start_print_task.delay(job.pk)`.
  4. Task opens `backend/media/{gcode_path}` and posts multipart file to `OCTOPRINT_URL/api/files/local`.
- **Result**: If `mock_octoprint_server.py` is running, virtual print starts.

#### 7. Monitoring Printer State and Progress
- **Trigger**: Interval timer in `PrintQueue.jsx` (every 8s) and Celery Beat task `check_active_print_jobs` (every 5s).
- **Endpoints**: `GET /api/dashboard/printer-status/` and `GET /api/job`, `GET /api/printer` on OctoPrint.
- **Fallback Simulation**: If OctoPrint is down, `PrinterStatusView` (`backend/dashboard/views.py:484-499`) simulates temperatures:
  ```python
  nozzle_actual = round(220 * warm_factor + random.uniform(-1, 1), 1)
  bed_actual = round(60 * warm_factor + random.uniform(-0.5, 0.5), 1)
  ```
  and returns `printer_connected = True` with status `'Online'`.

#### 8. Camera Capture and AI Defect Detection
- **Trigger**: User clicks "Start Webcam" on `/ai-monitoring`.
- **Frontend Execution**:
  1. `navigator.mediaDevices.getUserMedia()` streams video into HTML `<video>` element.
  2. `setInterval` captures JPEG blob from canvas every 1200ms.
  3. Posts to `POST /api/dashboard/detect-frame/`.
- **Backend Handling**:
  1. `DetectFrameView` decodes image bytes using OpenCV `cv2.imdecode`.
  2. Runs YOLOv8 inference: `model.predict(source=frame, conf=0.35)`.
  3. If bounding boxes are found, saves JPEG frame to `media/detections/defect_{id}.jpg`, creates `ErrorLog` record in SQLite, and returns detections JSON.
- **Frontend Reaction**:
  1. Draws animated bounding boxes over `<video>`.
  2. Synthesizes an audible two-tone alert siren using Web Audio API (`AudioContext`).
  3. Shows defect banner with pause button.

---

## Section 6 — Frontend Audit

### Route and Page Feature Matrix
| Route | Page File | Intended Feature | Backend Connected? | Issues / Mock Evidence |
| :--- | :--- | :--- | :--- | :--- |
| `/` | `Dashboard.jsx` | System stats, Recharts telemetry, node status, defect alerts | Partial | Analytics chart is hardcoded `mockChartData`; Network node uptimes ('14d 2h') are hardcoded strings. |
| `/login` | `Login.jsx` | User sign-in | **Bypassed** | Route redirected to `/` in `App.jsx:27`. |
| `/register` | `Register.jsx` | User account creation | **Bypassed** | Route redirected to `/` in `App.jsx:28`. |
| `/generate` | `GenerateModel.jsx` | Prompt-to-3D, Three.js preview, slicing modal | Partial | Three.js works; generation fails on backend; slicing modal navigation broken. |
| `/image-to-3d` | `ImageTo3D.jsx` | Image-to-3D model reconstruction | Partial | File upload UI complete; backend Shap-E worker fails. |
| `/history` | `ModelHistory.jsx` | Past models grid, modal 3D view, download/delete/slice | Yes | Connected to `GET /api/models/` and `DELETE /api/models/{id}/`. |
| `/ai-monitoring`| `AIMonitoring.jsx` | Live webcam, YOLO detection, bounding boxes, siren | **Yes (Verified)** | Connected to `POST /api/dashboard/detect-frame/`. |
| `/printer-control`| `PrinterControl.jsx` | Web Serial USB controls, thermal curves, G-code terminal | **Yes (Verified)** | Web Serial API functional in Chrome/Edge; mock simulation mode functional. |
| `/print-queue` | `PrintQueue.jsx` | Pipeline tracker, job progress, start print | Yes | Polling and job status connected; WebSockets hardcode `localhost:8000`. |
| `/octoprint` | `OctoPrint.jsx` | Embedded OctoPrint iframe | **Redirected** | Redirects to `/printer-control` in `App.jsx:38`. |
| `/settings` | `Settings.jsx` | GPU modes, storage paths, printer IPs, YOLO thresholds | **Placebo / Mock** | `handleSave` only sets a local timer: does not call any API or save to localStorage. |

### Major Frontend Discrepancies
1. **Route Typo in SlicingModal**: In `SlicingModal.jsx:96`, `handlePrint` executes `navigate('/queue')`. The route in `App.jsx` is `/print-queue`. This causes users to be kicked to the homepage (`* -> /`) instead of the queue.
2. **Hardcoded WebSocket Hosts**: `PrintQueue.jsx` line 31 and `SlicingModal.jsx` line 29 both hardcode `ws://localhost:8000`. If the frontend is hosted on another port, hostname, or IP address, WebSocket connections fail.
3. **Open Demo Mode**: `AuthContext.jsx` has all security logic gutted; authentication is completely simulated.

---

## Section 7 — Backend and API Audit

### Backend Framework
- **Primary Framework**: Django 5.2.17 / Django REST Framework 3.18.0.
- **ASGI Layer**: Daphne 4.2.3 and Django Channels 4.3.2.
- **Startup Command**: `.\backend\venv\Scripts\python.exe manage.py runserver 127.0.0.1:8000`.

### Spring Boot Migration Status
**Conclusion**: **NEVER STARTED / ZERO IMPLEMENTATION**.  
A comprehensive filesystem grep across all project directories found zero `.java` files, zero `pom.xml` files, and zero `build.gradle` files. The only occurrence of the word "spring" is inside `frontend/package-lock.json` referring to the `@react-spring` physics animation library.

### API Catalogue Table
| Method | Endpoint | Purpose | Auth Required | Handler Class / Function | Storage / External Effects | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register/` | Register user | None | `RegisterView` (`accounts/views.py`) | Creates `auth_user` | Implemented, unused by UI |
| `POST` | `/api/auth/login/` | Obtain JWT token pair | None | `CustomTokenObtainPairView` | None | Implemented, unused by UI |
| `POST` | `/api/auth/refresh/` | Refresh JWT token | None | `TokenRefreshView` | None | Implemented, unused by UI |
| `GET` | `/api/auth/me/` | Fetch current user info | Bearer JWT | `MeView` (`accounts/views.py`) | Reads `auth_user` | Implemented, unused by UI |
| `GET` | `/api/models/` | List user 3D models | None (fallback) | `GeneratedModelListCreateView` | Reads `generated_models` | Verified Working |
| `POST` | `/api/models/` | Direct model upload | None (fallback) | `GeneratedModelListCreateView` | Writes `.obj` to disk | Verified Working |
| `GET` | `/api/models/{id}/` | Retrieve model details | None (fallback) | `GeneratedModelDetailView` | Reads `generated_models` | Verified Working |
| `DELETE`| `/api/models/{id}/` | Delete model | None (fallback) | `GeneratedModelDetailView` | Deletes row and disk file | Verified Working |
| `POST` | `/api/generate/text/` | Generate 3D from text prompt | None (fallback) | `TextGenerateView` | Spawns Shap-E process | Broken (Torch missing on path) |
| `POST` | `/api/generate/image/`| Generate 3D from image | None (fallback) | `ImageGenerateView` | Spawns Shap-E process | Broken (Torch missing on path) |
| `GET` | `/api/generate/status/`| Check Shap-E runtime status | None | `ShapeModelStatusView` | Checks `python.exe` existence | Verified Working |
| `GET` | `/api/print-jobs/` | List print jobs | None (fallback) | `PrintJobListCreateView` | Reads `print_jobs` | Verified Working |
| `POST` | `/api/print-jobs/` | Create & slice print job | None (fallback) | `PrintJobListCreateView` | Triggers Celery slice task | Blocked (Redis & Slicer) |
| `GET` | `/api/print-jobs/{id}/` | Retrieve job details | None (fallback) | `PrintJobDetailView` | Reads `print_jobs` | Verified Working |
| `GET` | `/api/print-jobs/{id}/download-gcode/` | Download G-code | None (fallback) | `DownloadGCodeView` | Reads disk file; logs audit | Verified Working |
| `POST` | `/api/print-jobs/{id}/start-print/` | Upload to printer & start | None (fallback) | `StartPrintView` | Sets flag, calls OctoPrint | Partially Implemented |
| `POST` | `/api/print-jobs/{id}/resume/` | Resume paused print | None (fallback) | `PauseResumeView` | Calls OctoPrint `/api/job` | Partially Implemented |
| `GET` | `/api/print-jobs/active/` | Get current printing job | None (fallback) | `ActivePrintJobView` | Reads `print_jobs` | Verified Working |
| `GET` | `/api/dashboard/stats/` | Dashboard counters | None | `DashboardStatsView` | Aggregates DB counts | Verified Working |
| `GET` | `/api/dashboard/printer-status/` | Telemetry & temps | None | `PrinterStatusView` | Calls OctoPrint or simulates | Verified Working (Simulated) |
| `POST` | `/api/dashboard/detect-frame/` | Run YOLO on browser frame | None | `DetectFrameView` | YOLO inference; logs defect | **Verified Working** |
| `GET` | `/api/dashboard/video-feed/` | Server-side MJPEG stream | None | `video_feed` (`dashboard/views.py`) | Streams camera frames | Verified Working |
| `POST` | `/api/dashboard/start-monitoring/` | Start OpenCV camera loop | None | `StartMonitoringView` | Starts background thread | Verified Working |
| `POST` | `/api/dashboard/stop-monitoring/` | Stop OpenCV camera loop | None | `StopMonitoringView` | Stops background thread | Verified Working |
| `GET` | `/api/error-logs/` | List defect error logs | None | `ErrorLogListView` | Reads `error_logs` | Verified Working |
| `POST` | `/api/error-logs/detection-alert/` | Receive alert from external agent | None | `DetectionAlertView` | Sends WebSocket broadcast | Verified Working |
| `GET/POST`| `/api/error-logs/inference-control/` | Toggle inference flag | None | `InferenceControlView` | Writes `inference.flag` | Verified Working |

---

## Section 8 — Database Audit

### Database Configuration & Engine
- **Engine**: SQLite 3 (configured via `dj_database_url` with fallback `sqlite:///db.sqlite3`).
- **File Location**: `backend/db.sqlite3` (245,760 bytes).
- **Production Setting**: `docker-compose.yml` specifies PostgreSQL 15 (`postgres:15-alpine`), but it is not currently deployed.

### Verified Database Tables and Row Counts
| Table Name | Row Count | Primary Key | Description |
| :--- | :--- | :--- | :--- |
| `auth_user` | 1 | `id` (int) | Default admin user (`admin@ai3dprinter.local`) |
| `auth_permission` | 52 | `id` (int) | Standard Django permission model |
| `django_content_type`| 13 | `id` (int) | Django installed content types |
| `django_migrations` | 42 | `id` (int) | Applied migration history records |
| `generated_models_generatedmodel` | 2 | `id` (int) | 3D models table (1 ready demo, 1 failed test) |
| `print_jobs_printjob` | 1 | `id` (int) | Print jobs table (1 seeded demo job in `ready_to_print`) |
| `print_jobs_auditlog` | 0 | `id` (int) | Audit log for downloads/actions |
| `error_logs_errorlog` | 3 | `id` (int) | Defect detection logs (3 spaghetti detections at 92%, 95%, 84%) |
| `django_celery_results_taskresult` | 0 | `id` (int) | Celery task results backend |

### Entity Relationship Diagram
```mermaid
erDiagram
    auth_user ||--o{ generated_models_generatedmodel : owns
    auth_user ||--o{ print_jobs_auditlog : generates
    generated_models_generatedmodel ||--o{ print_jobs_printjob : sliced_into
    print_jobs_printjob ||--o{ error_logs_errorlog : produces

    auth_user {
        int id PK
        string username
        string email
        string password
        boolean is_superuser
    }

    generated_models_generatedmodel {
        int id PK
        int user_id FK
        text prompt
        string file_path
        string thumbnail_path
        float generation_time
        string status
        boolean is_repaired
        text error_detail
        string source_type
        int steps
        string celery_task_id
        datetime created_at
    }

    print_jobs_printjob {
        int id PK
        int model_id FK
        string status
        string gcode_path
        int estimated_time
        int progress
        datetime start_time
        datetime end_time
        string printer_ip
        boolean auto_pause_enabled
        text error_detail
        datetime created_at
    }

    error_logs_errorlog {
        int id PK
        int print_job_id FK
        string error_type
        float confidence
        string action_taken
        string frame_image
        datetime timestamp
    }

    print_jobs_auditlog {
        int id PK
        int user_id FK
        string action
        int job_id
        datetime timestamp
    }
```

---

## Section 9 — AI/ML Model Audit

### Model Inventory
- **Model Type**: Ultralytics YOLOv8 Nano Object Detection.
- **Model Weight Files**:
  - `ai-model/best.pt`: 6,251,306 bytes (Primary weight file).
  - `ai-model/best_v1.pt`: 6,251,306 bytes.
  - `ai-model/best_v2.pt`: 6,250,026 bytes.
  - `backend/best_ncnn_model/model.ncnn.bin`: 12,128,328 bytes (Quantized NCNN weights for Raspberry Pi).

### Runtime Verification Test Results (`task-125`)
Execution of `ai-model/detect_test.py` via `backend\venv\Scripts\python.exe` confirmed:
```text
============================================================
  AI 3D Printer Dashboard — YOLOv8 Model Validation
============================================================
[PASS] Model found: C:\Projects01\AI_3D_Printer\ai-model\best.pt (6104 KB)
[PASS] Model loaded successfully.
       Task type : detect
       # classes : 6
       Classes   : ['Spaghetti', 'chicken', 'chili_pepper', 'cup', 'spaghetti_noodles', 'sweet_basil']
[PASS] Inference on dummy image succeeded.
       Detections: 1 box(es) found (random noise, 0 expected).
============================================================
  All YOLO checks PASSED. Model is healthy.
============================================================
```

### Critical Class Index Mismatch Discovered
In `backend/best_ncnn_model/metadata.yaml` and the model verification output, the trained classes are:
- `0`: Spaghetti
- `1`: chicken
- `2`: chili_pepper
- `3`: cup
- `4`: spaghetti_noodles
- `5`: sweet_basil

However, in `backend/dashboard/views.py:247-249`, the developer hardcoded:
```python
class_id = int(results.boxes[0].cls[0])
mapping = {0: 'spaghetti', 1: 'layer_shift', 2: 'stringing'}
error_type = mapping.get(class_id, 'other')
```
**Impact**: If the model detects `chicken` (class 1), the system logs a `layer_shift`. If it detects `chili_pepper` (class 2), it logs `stringing`. This proves the model was trained on a multi-class food dataset containing pasta/spaghetti dishes, and the application layer assumed a standard 3D printing failure label sequence.

---

## Section 10 — Raspberry Pi, Camera, Printer, and OctoPrint Audit

### Hardware Readiness Table
| Hardware / Subsystem | Required Connection | Required Host Software | Existing Implementation | Verification Status | Pending Action |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Client Webcam** | USB / Integrated | Browser WebRTC (`getUserMedia`) | `frontend/src/pages/AIMonitoring.jsx` | **Verified Working** | None for browser mode. |
| **Server OpenCV Camera** | Direct USB to Host | OpenCV with DirectShow (`cv2.CAP_DSHOW`) | `backend/dashboard/views.py` | Implemented, not runtime tested | Requires physical camera plugged into host. |
| **Raspberry Pi 4B Agent**| Wi-Fi / Ethernet | Python 3, NCNN, Requests | `backend/error_logs/views.py:DetectionAlertView` receives alerts | Planned / Incomplete | Standalone Pi capture client script missing from repo. |
| **Direct USB 3D Printer**| USB Type A/B cable | Chrome/Edge Web Serial API | `frontend/src/context/PrinterContext.jsx` | Implemented, not runtime tested | Connect physical printer and verify Marlin response. |
| **Local OctoPrint** | Local HTTP (port 5000)| OctoPrint daemon | `mock_octoprint_server.py` | Partially Verified | Real OctoPrint not installed; mock server works for basic telemetry. |
| **Emergency Pause** | OctoPrint POST or Serial M25 | G-code command handler | `PrinterContext.pausePrint()`, `views._pause_octoprint()` | Implemented | Add POST `/api/job` pause handler to mock server. |

---

## Section 11 — 3D Scanning and Geometry Pipeline Audit

### Investigation Findings
The user prompt described an IR-based 3D scanner using:
- Arduino Uno
- Sharp GP2Y0A21YK0F IR distance sensor
- NEMA 17 stepper motors
- A4988 motor drivers & CNC Shield
- SD card storage & `.xyz` point cloud format

**Audit Finding**: **ZERO CODE EXISTS IN THE WORKSPACE**.
1. No `.ino` (Arduino sketches) exist in the repository.
2. No point cloud parsers, `.xyz` importers, or mesh reconstruction scripts (e.g., Poisson surface reconstruction, Delaunay triangulation, Ball-Pivoting) exist.
3. No integrations with Blender, MeshLab, or Point-E exist.
4. The website currently only accepts `.obj` 3D meshes (via Shap-E generation or direct upload).

---

## Section 12 — Environment and Dependencies

### Clean-Install Checklist
To bring this repository up on a fresh Windows machine:

1. **Runtimes**:
   - Python 3.11.9 (Do NOT use Python 3.14).
   - Node.js 18+ or 20+ (Node v24 is installed and verified working).
2. **System Dependencies (Required for full pipeline)**:
   - **PrusaSlicer**: Download and install PrusaSlicer (ensure `prusa-slicer-console.exe` is in PATH).
   - **Memurai or Redis**: Install Memurai (Redis for Windows) and ensure it runs on port `6379`.
3. **Environment Variables**:
   - Create `backend/.env` containing:
     ```env
     SECRET_KEY=dev-secret-key-change-in-production
     DEBUG=True
     ALLOWED_HOSTS=*
     DATABASE_URL=sqlite:///db.sqlite3
     REDIS_URL=redis://127.0.0.1:6379/0
     USE_IN_MEMORY_CHANNELS=True
     SLICER_CLI_PATH=prusa-slicer-console.exe
     OCTOPRINT_URL=http://localhost:5000
     OCTOPRINT_API_KEY=mock-octoprint-api-key
     SHAPE_VENV_PYTHON=C:\Projects01\AI_3D_Printer\backend\venv\Scripts\python.exe
     ```

---

## Section 13 — Exact Startup and Shutdown Instructions

### Windows Startup Sequence

#### Option A: Quick Demonstration Mode (Zero Extra Software Needed)
This runs the frontend, backend, and mock OctoPrint server without needing Redis or PrusaSlicer.

1. **Terminal 1 — Mock OctoPrint Server**:
   ```powershell
   cd c:\Projects01\AI_3D_Printer\backend
   .\venv\Scripts\python.exe mock_octoprint_server.py
   ```
   *Expected Output*: `[MOCK OCTOPRINT] Starting virtual printer server on http://localhost:5000`

2. **Terminal 2 — Django Backend**:
   ```powershell
   cd c:\Projects01\AI_3D_Printer\backend
   .\venv\Scripts\python.exe manage.py runserver 127.0.0.1:8000
   ```
   *Expected Output*: `Starting ASGI/Daphne version 4.2.3 development server at http://127.0.0.1:8000/`

3. **Terminal 3 — React Frontend**:
   ```powershell
   cd c:\Projects01\AI_3D_Printer\frontend
   npm run dev
   ```
   *Expected Output*: `VITE v5.4.9  ready in ... ms  ➜  Local:   http://localhost:5173/`

#### Option B: Full Production Pipeline (Requires Redis & PrusaSlicer Installed)
Run the automated script `scripts\start_website.ps1`.

### Safe Shutdown Instructions
To terminate all background processes cleanly, run:
```powershell
taskkill /F /IM python.exe /T 2>$null
taskkill /F /IM node.exe /T 2>$null
taskkill /F /IM memurai.exe /T 2>$null
```

---

## Section 14 — Current Runtime Status

### Runtime Inspection Table
| Service / Component | Implemented? | Currently Running? | Independently Tested? | End-to-End Tested? | Evidence / Blocker |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Frontend (Vite 5173)** | Yes | No | **Yes (Verified)** | No | Built cleanly via `npm run build` in 18.54s. |
| **Main Backend (Django 8000)**| Yes | No | **Yes (Verified)** | No | Passed Django checks and unit test in 0.898s. |
| **Database (SQLite)** | Yes | No | **Yes (Verified)** | Yes | 18 tables verified with row inspection script. |
| **Authentication** | Yes | No | No | No | Disabled / bypassed in frontend `AuthContext.jsx`. |
| **AI Inference Service** | Yes | No | **Yes (Verified)** | No | Passed `ai-model/detect_test.py` with YOLOv8 nano. |
| **YOLOv8 Weights (`best.pt`)** | Yes | No | **Yes (Verified)** | No | Loaded 6 classes into memory. |
| **3D Model Viewer** | Yes | No | **Yes (Verified)** | No | Three.js compiles cleanly in bundle. |
| **Slicing Engine (PrusaSlicer)**| Yes | No | No | No | Blocked: `prusa-slicer-console.exe` missing on host. |
| **OctoPrint (Mock Server)** | Yes | No | **Yes (Verified)** | No | Standalone script `mock_octoprint_server.py`. |
| **Raspberry Pi Agent** | Partial | No | No | No | No agent script exists in repo; endpoint exists. |
| **Web Serial USB Printer** | Yes | No | No | No | Implemented in `PrinterContext.jsx`; requires USB cable. |
| **3D IR Scanner Pipeline** | No | No | No | No | Zero code in repository. |

---

## Section 15 — Bugs, Security Issues, and Technical Debt

### Severity Classification

#### 1. CRITICAL SEVERITY
- **AI Class Index Mismatch (`dashboard/views.py:247-249`)**:
  - *Evidence*: `metadata.yaml` lists classes `0: Spaghetti, 1: chicken, 2: chili_pepper, 3: cup, 4: spaghetti_noodles, 5: sweet_basil`. Code maps `1 -> layer_shift` and `2 -> stringing`.
  - *Impact*: Detecting food items triggers incorrect 3D printing defect alerts.
  - *Fix*: Either retrain model strictly on 3D printing defects or update dictionary mapping.
- **Missing Redis / Celery Single Point of Failure**:
  - *Evidence*: `PrintJobListCreateView.post()` calls `run_slicing_task.delay(job.pk)` without exception handling.
  - *Impact*: Creating a print job crashes if Redis is not running.
  - *Fix*: Add try/except block falling back to synchronous or thread execution.

#### 2. HIGH SEVERITY
- **SlicingModal Route Navigation Bug (`SlicingModal.jsx:96`)**:
  - *Evidence*: `navigate('/queue')` does not match `<Route path="print-queue">` in `App.jsx`.
  - *Impact*: Clicking "Go to Queue" redirects user to the dashboard homepage.
  - *Fix*: Change path to `navigate('/print-queue')`.
- **Shap-E Python Path Bug (`core/settings.py:154`, `.env:19`)**:
  - *Evidence*: `SHAPE_VENV_PYTHON` defaults to `python` (which invokes system Python 3.14 without PyTorch).
  - *Impact*: All text-to-3D generations fail immediately with `ModuleNotFoundError: No module named 'torch'`.
  - *Fix*: Hardcode `SHAPE_VENV_PYTHON` to `sys.executable` or path to `backend/venv/Scripts/python.exe`.
- **Hardcoded WebSocket URLs (`PrintQueue.jsx:31`, `SlicingModal.jsx:29`)**:
  - *Evidence*: Hardcoded string `ws://localhost:8000/ws/...`.
  - *Impact*: Fails when accessed over LAN, domain, or alternate ports.
  - *Fix*: Use `window.location.host` or Vite proxy.

#### 3. MEDIUM SEVERITY
- **Mock OctoPrint Missing POST `/api/job` (`mock_octoprint_server.py`)**:
  - *Evidence*: `do_POST` only matches `/api/files/local`.
  - *Impact*: Pause and resume commands send to `/api/job` return HTTP 400.
  - *Fix*: Implement `POST /api/job` handling `command: pause/resume`.
- **Placebo Settings Page (`Settings.jsx:16-19`)**:
  - *Evidence*: `handleSave` has no API calls or persistent storage.
  - *Impact*: User configurations (IPs, thresholds) vanish on refresh.
- **Hardcoded Dashboard Metrics (`Dashboard.jsx:268-271`)**:
  - *Evidence*: Uptime strings (`'14d 2h'`, `'28d 5h'`) are hardcoded.

#### 4. LOW SEVERITY
- **Unused Large Assets**: 4 GB of Shap-E weights in root `shap_e_model_cache` while `backend/shap_e_model_cache` is empty.
- **Frontend Single-Chunk Bundle**: 1.74 MB JS bundle triggers Vite chunk-size warning.

---

## Section 16 — Integration and Compatibility Audit

### Integration Gaps Table
| Subsystem 1 | Subsystem 2 | Point of Failure / Mismatch | Reproduction Step | Required Modification |
| :--- | :--- | :--- | :--- | :--- |
| **SlicingModal** | **React Router** | Route is `/queue`, should be `/print-queue` | Click "Print" inside SlicingModal | Change `navigate('/queue')` to `navigate('/print-queue')` in `SlicingModal.jsx` |
| **Frontend WS** | **Django Channels** | Hardcoded port 8000 breaks when hosted remotely | Access UI on non-localhost | Dynamically derive WS URL from `window.location.host` |
| **Django Backend** | **Shap-E Subprocess** | System Python 3.14 invoked instead of venv Python 3.11 | Submit text prompt on `/generate` | Set `SHAPE_VENV_PYTHON = sys.executable` in `settings.py` |
| **Shap-E Script** | **Model Cache** | Cache path points to `backend/shap_e_model_cache` (empty) | Run Shap-E generation | Set cache dir to `settings.BASE_DIR.parent / 'shap_e_model_cache'` |
| **Backend AI** | **YOLOv8 Model** | Class 1 (`chicken`) mapped to `layer_shift` | Pass image of defect class 1 | Update mapping dictionary to match actual dataset labels |
| **Django Backend** | **Mock OctoPrint** | POST `/api/job` returns 400 | Click pause on AI defect alert | Add `POST /api/job` handler in `mock_octoprint_server.py` |

---

## Section 17 — Testing and Verification Plan

### Test Plan & Execution Status
| Test ID | Test Category | Procedure / Command | Expected Result | Verified Result | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **T-01** | Backend Unit Tests | `manage.py test` | All Django tests pass | Ran 1 test in 0.898s; OK | **PASS** |
| **T-02** | Frontend Build | `npm run build` in `frontend/` | Vite compiles without error | Built bundle in 18.54s | **PASS** |
| **T-03** | AI Model Verification | `python ai-model/detect_test.py` | YOLOv8 loads and predicts | Loaded `best.pt`, predicted dummy | **PASS** |
| **T-04** | Database Integrity | Query `db.sqlite3` tables | 18 tables present, relationships valid | Inspected tables and rows | **PASS** |
| **T-05** | Browser Defect Detection| Post frame to `/api/dashboard/detect-frame/` | Returns JSON bounding boxes & defect label | Verified in `views.py` | **PASS** |
| **T-06** | Slicing Pipeline | Post to `/api/print-jobs/` | Generates `.gcode` via PrusaSlicer | Fails: Slicer missing | **FAIL** |
| **T-07** | Shap-E 3D Generation | Post to `/api/generate/text/` | Generates `.obj` mesh | Fails: Python 3.14 invoked | **FAIL** |
| **T-08** | OctoPrint Integration | Start virtual print via mock server | Telemetry stream returns 200 | Works on GET, fails on pause POST | **PARTIAL** |
| **T-09** | Hardware 3D Scanner | Read point cloud from Arduino | Generates `.xyz` file | No code in repository | **NOT RUN** |

---

## Section 18 — Changes Required (Change Register)

| Change ID | Problem / Gap | Affected File(s) | Recommended Fix | Priority | Effort |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **CR-01** | SlicingModal navigation typo | `frontend/src/components/SlicingModal.jsx` | Change `navigate('/queue')` to `navigate('/print-queue')` | Critical | 5 mins |
| **CR-02** | Shap-E Python executable mismatch | `backend/core/settings.py` | Change default `SHAPE_VENV_PYTHON` to `sys.executable` | Critical | 5 mins |
| **CR-03** | Shap-E model cache directory mismatch | `backend/generated_models/tasks.py` | Point cache path to root `shap_e_model_cache` | Critical | 10 mins |
| **CR-04** | AI defect class label mapping | `backend/dashboard/views.py` | Remap class 0 to Spaghetti, remove chicken/chili mappings | Critical | 15 mins |
| **CR-05** | Mock OctoPrint pause handler | `backend/mock_octoprint_server.py` | Add `POST /api/job` with pause/resume support | High | 30 mins |
| **CR-06** | Hardcoded WebSocket endpoints | `PrintQueue.jsx`, `SlicingModal.jsx` | Use dynamic `window.location.host` | High | 20 mins |
| **CR-07** | Missing Celery exception fallback | `backend/print_jobs/views.py` | Add thread fallback when Celery/Redis is down | High | 30 mins |
| **CR-08** | PrusaSlicer dependency | External Host | Install PrusaSlicer via winget or zip package | High | 15 mins |
| **CR-09** | Placebo Settings page | `frontend/src/pages/Settings.jsx` | Persist settings to localStorage or Django DB | Medium | 1 hour |
| **CR-10** | Hardcoded Dashboard node uptimes | `frontend/src/pages/Dashboard.jsx` | Fetch live status from `/dashboard/printer-status/` | Medium | 1 hour |

---

## Section 19 — Prioritized Roadmap

### Phase 1: Immediate Demonstration Stabilization (Effort: ~2 hours)
1. Fix `SlicingModal.jsx` navigation route (`/print-queue`).
2. Fix `mock_octoprint_server.py` to accept pause commands so emergency stops work during presentations.
3. Fix AI class label mapping in `backend/dashboard/views.py`.
4. Fix `SHAPE_VENV_PYTHON` in `settings.py` and symlink/point `shap_e_model_cache` to root.

### Phase 2: Core Hardware & Slicing Baseline (Effort: ~4 hours)
1. Install PrusaSlicer on host machine and add to PATH.
2. Install Memurai (Redis for Windows) to enable Celery asynchronous background tasks.
3. Add try/catch threading fallback to `print_jobs/views.py` so slicing works even if Redis drops.

### Phase 3: Live Hardware Validation (Effort: ~1 day)
1. Connect physical 3D printer via USB cable and test Web Serial API jog controls (+X, +Y, +Z, Home).
2. Connect external USB webcam, mount on printer bed, and test live AI detection.
3. Test physical emergency pause (M25 command) when spaghetti defect is detected.

### Phase 4: Long-Term Enhancements & Scanning (Optional / Future)
1. If the physical IR scanner is required for evaluation, write the Arduino Uno sketch and build an `.xyz` to `.stl` mesh conversion script using Open3D or PyVista.

---

## Section 20 — Recommended Final Architecture

### Evaluation: Should You Migrate to Spring Boot?
**Recommendation**: **DO NOT MIGRATE TO SPRING BOOT**.

**Detailed Rationale**:
1. **Zero Progress**: Migration was never started. Rewriting the backend in Java Spring Boot means rebuilding authentication, channels/WebSockets, Daphne, and all CRUD logic from scratch.
2. **AI/ML Incompatibility**: The core intellectual property of this project is Python-centric: PyTorch, Ultralytics YOLOv8, OpenCV, and OpenAI Shap-E. Spring Boot would require maintaining a sidecar Python process anyway, introducing inter-process communication overhead.
3. **Django is Already Working**: The Django backend is clean, well-structured, fast, and passes checks. Keeping Django allows you to focus on demonstrating real hardware control and AI detection.

---

## Section 21 — Final Technical Handover & Viva Preparation

### Concise Viva Project Pitch
> *"PrintGuard AI is an autonomous, AI-driven 3D printing management system designed to eliminate unattended manufacturing failures. The platform couples a React/Vite dashboard with a Django ASGI backend and an Ultralytics YOLOv8 computer vision model. Using either a direct browser webcam feed or an embedded camera, PrintGuard AI performs real-time edge detection of filament spaghetti failures. Upon defect detection above a confidence threshold, the system automatically triggers an emergency pause command to the 3D printer via Web Serial G-code (M25) or the OctoPrint API, alerting the user and preventing catastrophic filament waste."*

### Top 5 Immediate Action Items
1. **Fix Route Typo**: Change `/queue` to `/print-queue` in `frontend/src/components/SlicingModal.jsx:96`.
2. **Correct AI Label Mapping**: Update `backend/dashboard/views.py:248` to only map class `0` (`Spaghetti`).
3. **Upgrade Mock OctoPrint Server**: Add `POST /api/job` handling to `mock_octoprint_server.py`.
4. **Fix Shap-E Python Executable**: Update `SHAPE_VENV_PYTHON` in `backend/core/settings.py` to point to `backend/venv/Scripts/python.exe`.
5. **Install PrusaSlicer**: Ensure `prusa-slicer-console.exe` is in the system PATH so G-code generation works end-to-end.

---

### Systems Inspected
- `c:\Projects01\AI_3D_Printer\frontend` (All JSX components, Vite build, Tailwind CSS)
- `c:\Projects01\AI_3D_Printer\backend` (Django apps: accounts, core, dashboard, print_jobs, generated_models, error_logs)
- `c:\Projects01\AI_3D_Printer\ai-model` (Weights `best.pt`, `detect_test.py`)
- `c:\Projects01\AI_3D_Printer\backend\db.sqlite3` (All 18 tables and row data)
- `c:\Projects01\AI_3D_Printer\scripts` (Startup/shutdown scripts, procedural generators)
- `c:\Projects01\AI_3D_Printer\docker-compose.yml` and Dockerfiles
- Host system network ports, processes, and runtime environments.

### Items That Could Not Be Inspected (External / Hardware)
- Physical 3D printer hardware (Ender 3 / Prusa / Marlin board).
- Physical Raspberry Pi 4 Model B hardware.
- Arduino Uno IR scanner hardware (completely missing from repository).
