# PrintGuard AI — Project Structure & Operational Guide

## Overview

**PrintGuard AI** is a modular, AI-enhanced 3D printing software platform demonstrating:
1. **Procedural CAD Mesh Generation:** Parametric Staunton chess pieces (King, Knight, Queen, Rook, Bishop, Pawn) generated via Trimesh CAD geometry algorithms.
2. **Deterministic G-Code Slicing:** PrusaSlicer 2.9.6 CLI integration producing valid, layer-by-layer G-code toolpaths.
3. **Computer-Vision Defect Detection:** YOLOv8 neural network inference monitoring webcam video streams for printing defects (such as spaghetti failure).
4. **Hardware Simulation & Integration:** Telemetry monitoring and printer control protocols labeled **"Integration in Progress (Coming Soon)"** (physical 3D printer hardware is not required for project demonstration).
5. **Secure Authentication & Resource Ownership:** HMAC-SHA256 JWT tokens, Django-compatible PBKDF2 password hashing, Spring Security 6 authorization, and strict cross-user data isolation.

---

## Directory Architecture

```text
AI_3D_Printer/
├── frontend/                                   # React 18 + Vite 5 Single Page Application
│   ├── src/
│   │   ├── components/                         # UI components (Navbar, Sidebar, SlicingModal, Viewer, etc.)
│   │   ├── context/                            # AuthContext, PrinterContext, ThemeContext
│   │   ├── pages/                              # Dashboard, Generate, History, PrintQueue, PrinterControl, Login, Register
│   │   ├── services/                           # Axios API client, WebSocket URL helpers
│   │   ├── index.css                           # TailwindCSS & custom aesthetic styling
│   │   ├── App.jsx                             # Root routing and layout
│   │   └── main.jsx                            # Entry point
│   ├── public/                                 # Static assets & icons
│   ├── package.json                            # Node.js dependencies
│   └── vite.config.js                          # Vite bundler configuration
│
├── printguard-migration-starter/
│   ├── backend-java/                           # Production Spring Boot 3.4.1 (Java 21) Backend
│   │   ├── pom.xml                             # Maven build with Spring Security, JPA, Flyway, Test Starter
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/printguard/
│   │       │   │   ├── auth/                   # JWT service, Spring Security filter, PasswordService, AuthController
│   │       │   │   ├── config/                 # SecurityConfig, CorsConfig, WebSocketConfig
│   │       │   │   ├── dashboard/              # DashboardController (user-isolated stats & telemetry)
│   │       │   │   ├── health/                 # Public health check endpoint
│   │       │   │   ├── jobs/                   # PrintJobController, OctoPrintService, Slicer integration
│   │       │   │   ├── logs/                   # ErrorLogController (AI defect logs & alerts)
│   │       │   │   ├── models/                 # GenerationController, ModelController, CAD mesh handlers
│   │       │   │   ├── slicer/                 # PrusaSlicer execution service
│   │       │   │   └── websocket/              # Real-time STOMP event broadcaster
│   │       │   └── resources/
│   │       │       ├── application.yml         # Base configuration & default dev port
│   │       │       ├── application-supabase.yml # Live Supabase PostgreSQL 17 profile
│   │       │       ├── application-prod.yml     # Production profile (strictly requires PRINTGUARD_JWT_SECRET)
│   │       │       └── application-local.yml    # In-memory H2 profile for testing & offline mode
│   │       └── test/                           # 44 automated JUnit 5 & MockMvc unit/integration tests
│   │
│   └── ai-service/                             # Python 3.11 FastAPI Microservice
│       ├── app.py                              # YOLOv8 defect detection & procedural CAD generation endpoint
│       ├── test_ai_service.py                  # Test suite for FastAPI endpoints
│       └── requirements.txt                    # Python dependencies (fastapi, uvicorn, ultralytics, opencv-python)
│
├── backend/                                    # Legacy Django 5.1 & Shared Media Environment
│   ├── geometry/
│   │   └── chess_generator.py                  # Standalone procedural CAD script
│   ├── media/                                  # Canonical storage for OBJ models and sliced G-code files
│   │   ├── models/                             # Generated 3D OBJ meshes
│   │   ├── gcode/                              # PrusaSlicer sliced toolpath files
│   │   └── detections/                         # Defect snapshot images
│   ├── venv/                                   # Shared Python 3.11 virtual environment
│   ├── accounts/, core/, dashboard/, ...       # Preserved Django apps (backward-compatibility reference)
│   ├── db.sqlite3                              # Preserved SQLite database file
│   └── manage.py                               # Django CLI entrypoint
│
├── ai-model/                                   # Pre-trained YOLOv8 weights
│   ├── best.pt                                 # PyTorch model weights for 3D printer defect detection
│   └── best_ncnn_model/                        # NCNN exported model format
│
├── tools/
│   └── PrusaSlicer/
│       └── PrusaSlicer-2.9.6/                  # Native PrusaSlicer console executable (prusa-slicer-console.exe)
│
├── scripts/                                    # Operational & Migration Automation
│   ├── start_website.ps1                       # Unified 3-tier startup script
│   ├── start_backend_supabase.ps1              # Dedicated Spring Boot launcher with Supabase profile
│   ├── stop_website.ps1                        # Graceful process termination script
│   ├── migrate_sqlite_to_postgres.py           # SQLite to PostgreSQL schema & data migrator
│   └── test_model_gen.py                       # CLI test script for model generation
│
├── tests/                                      # Python CAD and System Regression Tests
│   ├── test_procedural_chess.py                # Watertightness, mesh size, vertex count verification
│   └── e2e_test.py                             # Full end-to-end workflow test
│
├── docs/                                       # Project Documentation & Audit Reports
│   ├── BASELINE_REPORT.md                      # Prompt 10 baseline audit
│   ├── PROMPT11_REMEDIATION_REPORT.md          # Slicer and procedural CAD remediation
│   ├── PROMPT12_VALIDATION_REPORT.md           # End-to-end verification report
│   ├── PROMPT13_PROJECT_READINESS_REPORT.md    # Readiness audit and identified security gaps
│   ├── PROMPT14_SECURITY_AND_STRUCTURE_REPORT.md # Security implementation & structure refactoring report
│   └── PROJECT_STRUCTURE.md                    # This document
│
├── start_website.ps1                           # Root convenience link to startup script
├── start.bat / setup.bat                       # Windows batch startup alternatives
├── .env.example                                # Documented template of required environment variables
├── .gitignore                                  # Git ignore rules protecting secrets and build outputs
└── README.md                                   # Root project overview
```

---

## Service Responsibilities & Ports

| Service | Technology | Port | Purpose |
| :--- | :--- | :--- | :--- |
| **Frontend Web UI** | React 18, Vite 5, TailwindCSS | `5173` | User interface: login, registration, 3D model generator, 3D Canvas viewer, slicing trigger, print queue, webcam monitoring, and direct control. |
| **Core Backend** | Java 21, Spring Boot 3.4.1 | `8080` | High-performance core API: Spring Security, JWT authentication, user isolation, database persistence, PrusaSlicer execution, WebSocket STOMP broadcaster. |
| **AI Microservice** | Python 3.11, FastAPI, YOLOv8 | `8001` | Vision & CAD inference: runs YOLOv8 defect detection on webcam frames; parametric chess CAD geometry generator. |
| **Database** | PostgreSQL 17 (Supabase) | `5432` / `6543` | Persistent relational store for users, generated models, print jobs, and error detection logs. (Falls back to H2 in-memory for testing). |
| **Virtual Printer / OctoPrint** | Python (Mock) | `5000` | Optional OctoPrint REST API simulation for printer hardware demonstration. |

---

## Local Startup Instructions

### 1. Prerequisites
- **Java:** JDK 21+ (`java -version`)
- **Maven:** Apache Maven 3.9+ (`mvn -version`)
- **Node.js:** Node.js 18+ and npm (`node -v`, `npm -v`)
- **Python:** Python 3.11 installed in `backend/venv` with `fastapi`, `uvicorn`, `ultralytics`, `trimesh`, `numpy`, `opencv-python`

---

### Option A: Unified Startup (Recommended)

Run the unified startup script from PowerShell at the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\start_website.ps1
```

This script automatically:
1. Loads environment variables from `.env` (including Supabase credentials if configured).
2. Spawns the **Python AI Microservice** on `http://127.0.0.1:8001`.
3. Spawns the **Java Spring Boot Backend** on `http://localhost:8080`.
4. Spawns the **React Vite Frontend** on `http://localhost:5173`.
5. Opens three independent terminal windows for live logging.

To stop all running services cleanly:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop_website.ps1
```

---

### Option B: Individual Service Startup

#### 1. Start Python AI Microservice
```powershell
cd C:\Projects01\AI_3D_Printer\printguard-migration-starter\ai-service
C:\Projects01\AI_3D_Printer\backend\venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8001
```
*Health Check:* `http://127.0.0.1:8001/api/inference/health`

#### 2. Start Java Spring Boot Backend
To run with live Supabase PostgreSQL (credentials in `.env` or system environment):
```powershell
cd C:\Projects01\AI_3D_Printer\printguard-migration-starter\backend-java
mvn spring-boot:run "-Dspring-boot.run.profiles=supabase"
```

To run offline in local mode (with H2 in-memory database):
```powershell
cd C:\Projects01\AI_3D_Printer\printguard-migration-starter\backend-java
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```
*Health Check:* `http://localhost:8080/api/health`

#### 3. Start React Vite Frontend
```powershell
cd C:\Projects01\AI_3D_Printer\frontend
npm run dev
```
*Web Access:* `http://localhost:5173`

---

## Running Test Suites

### 1. Spring Boot Backend Test Suite (Java 21)
Executes all 41 unit, integration, authentication, slicing, and ownership tests:
```powershell
cd C:\Projects01\AI_3D_Printer\printguard-migration-starter\backend-java
mvn test "-Dspring.profiles.active=local"
```
*Expected Result:* `Tests run: 41, Failures: 0, Errors: 0, Skipped: 0` (`BUILD SUCCESS`).

### 2. Frontend Production Build Verification
Compiles all JSX, CSS, and Three.js assets into optimized production bundles:
```powershell
cd C:\Projects01\AI_3D_Printer\frontend
npm run build
```
*Expected Result:* `✓ built in ~13s` (zero errors).

### 3. Python AI Microservice Test Suite
Executes FastAPI endpoint tests:
```powershell
cd C:\Projects01\AI_3D_Printer\printguard-migration-starter\ai-service
C:\Projects01\AI_3D_Printer\backend\venv\Scripts\python.exe test_ai_service.py
```
*Expected Result:* `Ran 4 tests in ~0.05s — OK`.

### 4. Python Procedural CAD Mesh Regression Test
Verifies parametric King and Knight manifold mesh creation, vertex counts, and file sizes:
```powershell
cd C:\Projects01\AI_3D_Printer
C:\Projects01\AI_3D_Printer\backend\venv\Scripts\python.exe tests/test_procedural_chess.py
```
*Expected Result:* `Non-identical test: same_verts=False, same_faces=False` (King: ~62 KB OBJ, Knight: ~33 KB OBJ).
