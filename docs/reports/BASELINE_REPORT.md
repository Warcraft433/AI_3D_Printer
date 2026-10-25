# PrintGuard AI — Baseline Verification Report

**Date:** 2026-10-13  
**Status:** Baseline Audited & Verified  
**Branch / Working Copy:** `main` (with active migration workspace)  

---

## 1. Executive Summary

This report establishes the verified baseline of the **PrintGuard AI** project following Prompt 2A.  
The workspace contains a dual-architecture state:
1. The **original application** based on Django (port 8000), Celery/Redis, and SQLite.
2. The **migration target** based on Java Spring Boot 3.5.6 (port 8080), a dedicated Python FastAPI AI inference service (port 8001), and the unified React Vite frontend (port 5173).

All compilation, test, packaging, startup, and endpoint compatibility checks were executed without modifying existing code or resetting uncommitted changes.

---

## 2. Project Layout & Folder Verification

| Component | Path | Technology / Version | Role |
| :--- | :--- | :--- | :--- |
| **Project Root (Git Repo)** | `C:\Projects01\AI_3D_Printer` | Git repository root | Contains all source code, subprojects, and configs |
| **React Frontend** | `C:\Projects01\AI_3D_Printer\frontend` | React 18, Vite 5.4.9, TailwindCSS | Primary user interface |
| **Original Django Backend** | `C:\Projects01\AI_3D_Printer\backend` | Django 5.x, DRF, Channels | Legacy backend (SQLite, Celery) |
| **Python Virtual Environment** | `C:\Projects01\AI_3D_Printer\backend\venv` | Python 3.11.9 (Torch, OpenCV, FastAPI, Uvicorn) | Dedicated environment for backend & AI service |
| **AI Model Weights** | `C:\Projects01\AI_3D_Printer\ai-model\best.pt` | YOLOv8 nano defect model | Object detection weights for spaghetti defect identification |
| **Migration Starter Directory** | `C:\Projects01\AI_3D_Printer\printguard-migration-starter` | Subfolder (untracked in git) | Contains Spring Boot backend, FastAPI AI service, seed SQL |
| **Spring Boot Backend** | `...\printguard-migration-starter\backend-java` | Java 21, Spring Boot 3.5.6, Maven | Target production backend |
| **Python AI Microservice** | `...\printguard-migration-starter\ai-service` | FastAPI 0.115+, Uvicorn, OpenCV, YOLO | Standalone AI inference service on port 8001 |

> **Key Finding:** `printguard-migration-starter` is **not** an independent Git repository; it resides inside `C:\Projects01\AI_3D_Printer`. The active React frontend in `frontend/` has already had its Vite proxy redirected to target the Spring Boot backend (`http://127.0.0.1:8080`).

---

## 3. Git Status & Uncommitted Work Inspection

### 3.1 `git status --short` Summary
- **Tracked files modified (24 files):**
  - `README.md`
  - `ai-model/detect_test.py`
  - `backend/core/settings.py`
  - `backend/dashboard/urls.py`, `backend/dashboard/views.py`
  - `backend/generated_models/generate_views.py`, `backend/generated_models/tasks.py`
  - `backend/mock_octoprint_server.py`
  - `backend/print_jobs/management/commands/seed_demo_job.py`
  - `backend/reset_cleanup.py`
  - `docker-compose.yml`
  - `frontend/package-lock.json`, `frontend/src/App.jsx`, `frontend/src/components/Sidebar.jsx`, `frontend/src/components/SlicingModal.jsx`, `frontend/src/context/GenerationContext.jsx`, `frontend/src/pages/AIMonitoring.jsx`, `frontend/src/pages/PrintQueue.jsx`, `frontend/src/pages/Settings.jsx`, `frontend/vite.config.js`
  - `setup.bat`, `start.bat`, `start_website.ps1`
  - `tests/test_shape.py`
- **Tracked files deleted (4 files):**
  - `pi/config.py`, `pi/requirements_pi.txt`, `scripts/deploy_to_pi.ps1`, `scripts/pi_start_demo.sh`
- **Untracked paths (9 items):**
  - `backend/db.sqlite3.bak`
  - `docker/ai-service.Dockerfile`, `docker/backend-java.Dockerfile`
  - `frontend/src/context/PrinterContext.jsx`, `frontend/src/pages/PrinterControl.jsx`
  - `printguard-migration-starter/`
  - `scripts/generate_demo_models.py`, `scripts/test_model_gen.py`
  - `shap_e_model_cache/`

### 3.2 Java Changes Preservation Check
All improvements made in the preceding session were verified and are intact:
- `DatabaseInitializer.java`: Unused imports (`OffsetDateTime`, `List`) remain removed.
- `DashboardController.java`: Unused import (`File`) remains removed.
- `GenerationController.java`: Unused import (`OffsetDateTime`) remains removed.
- `@NonNull` Annotations: Properly retained on `WebMvcConfig.java`, `WebSocketConfig.java`, `DashboardWebSocketHandler.java`, `ModelGenerationWebSocketHandler.java`, and `PrintJobWebSocketHandler.java`.

### 3.3 IDE Configuration (`.vscode/settings.json`)
```json
{
    "java.compile.nullAnalysis.mode": "disabled",
    "java.configuration.updateBuildConfiguration": "automatic"
}
```
* **Effect:** Setting `nullAnalysis.mode` to `"disabled"` prevents Eclipse JDT from evaluating Spring's package-level `@NonNullApi` annotations against standard Java types (`Long`, `String`, `MediaType`). This eliminated ~35 false-positive compiler warnings on standard Spring Data repository invocations (`findById()`, `save()`, `deleteAll()`).

---

## 4. Spring Boot Backend Verification (`backend-java`)

All verification commands were executed from `C:\Projects01\AI_3D_Printer\printguard-migration-starter\backend-java`.

| Step | Command | Result | Notes |
| :--- | :--- | :---: | :--- |
| **Compile** | `mvn clean compile` | **PASS** | 29 Java source files compiled cleanly with 0 errors and 0 warnings (Total time: 7.9s). |
| **Unit Tests** | `mvn test` | **PASS** | `HealthControllerTest`: 1 test run, 0 failures, 0 errors, 0 skipped. |
| **Package** | `mvn package` | **PASS** | Produced `printguard-backend-0.1.0-SNAPSHOT.jar` (repackaged boot archive with nested `BOOT-INF/`). |
| **Default Profile Startup** | `java -jar ...` (default profile) | **BLOCKED** | Fails to start because `application.yml` requires PostgreSQL on `localhost:5432` (`ddl-auto: validate`), and port 5432 is closed. Hibernate throws `Cannot determine Dialect without JDBC metadata`. |
| **Local Profile Startup** | `java -jar ... --spring.profiles.active=local` | **PASS** | Successfully booted in 8.54s using H2 in-memory DB (`jdbc:h2:mem:printguard`). Tomcat initialized on port 8080. `DatabaseInitializer` seeded baseline records. |
| **Health Probe** | `curl http://localhost:8080/api/health` | **PASS** | Returned `{"service":"printguard-backend","timestamp":"...","status":"UP"}` with HTTP 200. |

### PostgreSQL Verification Statement
> **PostgreSQL Integration:** **BLOCKED / NOT RUN**.  
> PostgreSQL port 5432 is not currently listening on the local host. No PostgreSQL database has been initialized, validated, or claimed to be working.

---

## 5. Original Application & Database Baseline

### 5.1 SQLite Database Status
- Location: `C:\Projects01\AI_3D_Printer\backend\db.sqlite3` (240 KB)
- Backup: `C:\Projects01\AI_3D_Printer\backend\db.sqlite3.bak` (240 KB, identical size and timestamp)
- Verified Table Records in `db.sqlite3`:
  - `auth_user`: 1 record (`admin`)
  - `generated_models_generatedmodel`: 2 records (ID 3 "3D Printer Simulation — Spaghetti Error Demo", ID 4 "cup" [failed])
  - `print_jobs_printjob`: 1 record (ID 2, status: `ready_to_print`, G-code: `demo/demo_print.gcode`)
  - `error_logs_errorlog`: 3 records (spaghetti defect alerts with confidences 0.927, 0.952, 0.848)
  - `print_jobs_auditlog`: 0 records
- **Parity Status:** The Spring Boot `DatabaseInitializer.java` seeds the exact identical dataset on initial start if the user table is empty.

### 5.2 Python AI Service (`ai-service`)
- Framework: FastAPI + Uvicorn
- Model: `C:\Projects01\AI_3D_Printer\ai-model\best.pt` (verified present)
- Python runtime: `C:\Projects01\AI_3D_Printer\backend\venv\Scripts\python.exe` (Python 3.11.9, has `fastapi`, `uvicorn`, `torch`, `cv2`, `ultralytics` installed)
- Verified functional for frame defect inference on port 8001.

---

## 6. API Endpoint Compatibility Matrix

| Endpoint | Original Django View | Spring Boot Equivalent | Migration Status |
| :--- | :--- | :--- | :---: |
| `POST /api/auth/login/` | `CustomTokenObtainPairView` | `AuthController.login()` | **PASS** |
| `POST /api/auth/register/` | `RegisterView` | `AuthController.register()` | **PASS** |
| `GET /api/auth/me/` | `MeView` | `AuthController.me()` | **PASS** |
| `POST /api/auth/refresh/` | `TokenRefreshView` | `AuthController.refresh()` | **PASS** |
| `GET /api/models/` | `ModelListCreateView` | `ModelController.list()` | **PASS** |
| `GET /api/models/{id}/` | `ModelDetailView` | `ModelController.get()` | **PASS** |
| `DELETE /api/models/{id}/` | (Django DRF destroy) | `ModelController.delete()` | **PASS** |
| `GET /api/print-jobs/` | `PrintJobListCreateView` | `PrintJobController.list()` | **PASS** |
| `POST /api/print-jobs/` | `PrintJobListCreateView` | `PrintJobController.create()` | **PASS** |
| `GET /api/print-jobs/{id}/` | `PrintJobDetailView` | `PrintJobController.get()` | **PASS** |
| `PATCH /api/print-jobs/{id}/` | `PrintJobDetailView` | `PrintJobController.update()` | **PASS** |
| `POST /api/print-jobs/{id}/start-print/` | `StartPrintView` | `PrintJobController.startPrint()` | **PASS** |
| `GET /api/print-jobs/{id}/download-gcode/`| `DownloadGCodeView` | `PrintJobController.downloadGCode()` | **PASS** |
| `POST /api/print-jobs/clear-finished/` | `ClearFinishedJobsView` | `PrintJobController.clearFinished()` | **PASS** |
| `GET /api/print-jobs/active-job/` | `ActivePrintJobView` | `PrintJobController.getActiveJob()` | **PASS** |
| `GET /api/dashboard/stats/` | `DashboardStatsView` | `DashboardController.getStats()` | **PASS** |
| `GET /api/dashboard/printer-status/` | `PrinterStatusView` | `DashboardController.getPrinterStatus()` | **PASS** |
| `POST /api/dashboard/detect-frame/` | `DetectFrameView` | `DashboardController.detectFrame()` | **PASS** |
| `POST /api/dashboard/start-monitoring/` | `StartMonitoringView` | `DashboardController.startMonitoring()` | **PASS** |
| `POST /api/dashboard/stop-monitoring/` | `StopMonitoringView` | `DashboardController.stopMonitoring()` | **PASS** |
| `GET /api/dashboard/monitoring-status/` | `MonitoringStatusView` | `DashboardController.monitoringStatus()` | **PASS** |
| `GET /api/error-logs/` | `ErrorLogListView` | `ErrorLogController.list()` | **PASS** |
| `POST /api/error-logs/detection-alert/` | `DetectionAlertView` | `ErrorLogController.detectionAlert()` | **PASS** |
| `GET / POST /api/error-logs/inference-control/`| `InferenceControlView` | `ErrorLogController.get/setInferenceStatus()` | **PASS** |
| `GET / POST /api/settings/` | (Mock / file-based) | `SettingsController.get/save()` | **PASS** |
| `GET /api/health` | (Not in original Django) | `HealthController.health()` | **PASS** |
| `WS /ws/dashboard/` | Django Channels | `DashboardWebSocketHandler` | **PASS** |
| `WS /ws/print-jobs/{id}/` | Django Channels | `PrintJobWebSocketHandler` | **PASS** |
| `WS /ws/generation/{id}/` | Django Channels | `ModelGenerationWebSocketHandler` | **PASS** |
| `GET /api/dashboard/video-feed/` | Django OpenCV MJPEG Stream | Not present in Spring Boot | **PARTIAL** (Frontend uses WebRTC) |
| `GET /api/raw-test-feed/` | Django OpenCV MJPEG Stream | Not present in Spring Boot | **PARTIAL** |
| `POST /api/generate/text/` | Django Shap-E Celery task | Static 3D OBJ mesh fallback in Spring Boot | **MIGRATION INCOMPLETE** |
| `POST /api/generate/image/` | Django Shap-E Celery task | Static 3D OBJ mesh fallback in Spring Boot | **MIGRATION INCOMPLETE** |

---

## 7. Features and Gaps Summary

1. **Real 3D Mesh Generation (Shap-E):**
   Spring Boot currently writes a hardcoded test mesh (sample cube/pyramid OBJ) instead of invoking a real AI diffusion/Shap-E pipeline. The original Django project executed this via Celery and PyTorch.
2. **Database Persistence across Reboots:**
   In the `local` profile, Spring Boot uses an in-memory H2 database (`jdbc:h2:mem:printguard`). Seed data is restored on each boot, but runtime mutations (e.g. newly created print jobs or logs) are lost on server restart. A file-backed H2 or active PostgreSQL container is required for true persistence.
3. **MJPEG Camera Streaming:**
   The OpenCV MJPEG server endpoints (`video-feed` and `raw-test-feed`) are not in Spring Boot. The React frontend currently relies on browser `getUserMedia()` and frame POSTing.
4. **OctoPrint / Hardware Integration:**
   Hardware status is currently mocked/simulated in both Django and Spring Boot (`simulated: true`, `printer_connected: true`). Real serial / OctoPrint / Klipper integration is not yet wired to hardware.

---

## 8. Five Highest-Priority Issues to Address Next

1. **Configure Persistent Local Storage (File-backed H2 or Local PostgreSQL):**
   Switch `application-local.yml` from in-memory `jdbc:h2:mem` to a persistent file-backed database (`jdbc:h2:file:./data/printguard`) or start a local PostgreSQL container, ensuring data persists across Spring Boot restarts.
2. **Shap-E 3D Generation Pipeline Migration:**
   Connect `GenerationController.java` to an asynchronous AI generation service (or Python worker) to run actual 3D diffusion inference rather than the static OBJ fallback.
3. **OctoPrint / Moonraker Real Printer Connection:**
   Connect `PrinterContext.jsx` and `PrintJobController` to real printer API streams (or enhance `mock_octoprint_server.py` integration) for genuine extrusion progress and thermal monitoring.
4. **End-to-End Test Suite for Spring Boot:**
   Expand `src/test/java` beyond `HealthControllerTest` to include integration tests for `PrintJobController`, `DashboardController`, and `WebSocketEventPublisher`.
5. **Decouple or Clean Untracked Artifacts:**
   Formalize `printguard-migration-starter` within the Git repository (or migrate its contents into the standard top-level repository structure) to ensure clean version control tracking.

---

## 9. Prompt 3 Bug Fixes & Verification Report

### 9.1 Summary of Fixes Applied

| Priority | Issue / Area | Root Cause | Files Changed | Fix Applied | Status |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **P1** | Insecure Plaintext Auth & Fake Login Fallback | Passwords stored as plain text or insecure strings; `login` unconditionally fell back to admin without validating password; passwords returned in responses. | `com.printguard.auth.PasswordService.java`<br>`com.printguard.auth.AuthController.java`<br>`com.printguard.config.DatabaseInitializer.java` | Created `PasswordService` with salted PBKDF2WithHmacSHA256 (600k iterations). Enforced min 6-char passwords on registration. Verified real password hashes upon login, rejecting invalid passwords/users with 401 Unauthorized. Never returned password hashes. Seeded admin with salted hash. | **VERIFIED** |
| **P2** | Local In-Memory DB Reset & Data Loss | `application-local.yml` used `jdbc:h2:mem:printguard`, wiping all new records upon application shutdown. | `application-local.yml` | Switched to file-backed persistent H2 DB: `jdbc:h2:file:./data/printguard;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE`. Verified `DatabaseInitializer` seeds only once when database is empty, never duplicates or overwrites existing records on restart. | **VERIFIED** |
| **P3** | Incomplete Dashboard Stats & False Online Claims | `DashboardController.getStats()` omitted ready models, completed prints, recent errors, and active print object. `getPrinterStatus()` claimed online status without probing hardware. Print job operations (`start`, `pause`, `resume`, `cancel`) lacked hardware confirmation checks. | `com.printguard.jobs.OctoPrintService.java`<br>`com.printguard.jobs.PrintJobController.java`<br>`com.printguard.jobs.PrintJobResponse.java`<br>`com.printguard.dashboard.DashboardController.java`<br>`com.printguard.models.GeneratedModelRepository.java` | Created `OctoPrintService` to probe `/api/version` and `/api/job`. Added `countByStatus` to model repository. Wired all dashboard stats to real DB records. Printer telemetry now honestly reports `printer_connected: false`, `simulated: true`, `telemetry_source: "offline_simulated"`, and `limitation_note` when offline. Active print job operations now require hardware confirmation or return HTTP 503 explaining the limitation. Added dedicated `/pause`, `/resume`, `/cancel` endpoints. Added `model_prompt` and `gcode_url` to `PrintJobResponse`. | **VERIFIED** |
| **P4** | Misleading Generative 3D AI Output & Status | `GenerationController` generated static cube/tetrahedron OBJ meshes but claimed genuine 4.2s Shap-E generation without fallback warning. `getStatus()` hardcoded `shape_available: true`. | `com.printguard.models.GenerationController.java`<br>`com.printguard.models.ModelResponse.java`<br>`frontend/src/pages/GenerateModel.jsx`<br>`frontend/src/pages/ImageTo3D.jsx` | Updated `ModelResponse` with `@JsonProperty("is_fallback") Boolean isFallback`. Marked demo meshes with `[DEMO FALLBACK]` in `error_detail` and set `generation_time: 0.2s`. Probed Python AI microservice `/health` in `getStatus()`, reporting `shape_available: false`, `fallback_mode: true`, and honest limitation note explaining that YOLO defect inference is active but generative Shap-E synthesis is offline. Updated UI to display fallback badges. | **VERIFIED** |
| **P5** | Frontend Vite Build Failure | `frontend/src/pages/Settings.jsx` had a broken import `import api from '../api/client'` instead of `../services/api`. | `frontend/src/pages/Settings.jsx` | Fixed import path to `../services/api`. | **VERIFIED** |

### 9.2 Test Suite Execution Results

1. **Spring Boot Backend Test Suite (`mvn test`):**
   - Configured `src/test/resources/application-test.yml` using in-memory H2 for isolated, deterministic testing.
   - **Total Tests Run: 17** | **Failures: 0** | **Errors: 0** | **Skipped: 0**
   - Classes tested:
     - `AuthControllerTest`: 6 tests (PBKDF2 hashing & matching, valid login, wrong password rejection [401], nonexistent user [401], registration hashing, short password rejection [400]).
     - `PrintJobControllerTest`: 6 tests (list jobs, get active job, offline start-print rejection [503], offline pause-print rejection [503], offline resume-print rejection [503], inactive queue job cancellation [200]).
     - `DashboardControllerTest`: 2 tests (database-mapped statistics verification, honest offline printer telemetry).
     - `GenerationControllerTest`: 2 tests (honest status reporting with `shape_available: false`, demo fallback model generation with `is_fallback: true`).
     - `HealthControllerTest`: 1 test (actuator health probe).
2. **Spring Boot Packaging (`mvn package`):**
   - `printguard-backend-0.1.0-SNAPSHOT.jar` packaged successfully (Build Time: 6.25s).
3. **Frontend Production Build (`npm run build`):**
   - Transformed 3318 modules; bundled `dist/index.html`, `dist/assets/index-cytdm6ja.css` (37 kB), `dist/assets/index-zgCLnIpr.js` (1.7 MB). Exit code: 0.

### 9.3 Remaining Limitations
1. **PostgreSQL Migration:** PostgreSQL on port 5432 remains offline. Application runs locally against file-backed H2 (`./data/printguard`). PostgreSQL migration was intentionally not started as specified in rules.
2. **Shap-E Diffusion Generation:** Generative 3D model generation currently operates in verified demo fallback mode with static OBJ meshes clearly labeled `[DEMO FALLBACK]`. Running live Shap-E inference requires dedicated Python PyTorch GPU worker environment.
3. **Physical 3D Printer / OctoPrint Hardware:** Physical hardware or OctoPrint was offline during testing; backend correctly reports `printer_connected: false` and `simulated: true`, refusing to claim physical hardware execution.
4. **Git Workspace:** All changes remain uncommitted and unpushed as per instructions. Original `backend/db.sqlite3` remains pristine and untouched.

