# Prompt 18 — 3D Model Generation and PrusaSlicer Remediation Report

**Project:** Enhanced 3D Printer / PrintGuard AI  
**Project Root:** `C:\Projects01\AI_3D_Printer`  
**Date:** October 14, 2026  
**Status:** Completed & Validated  

---

## Executive Summary

During Prompt 18, we conducted an end-to-end diagnostic and remediation of the two primary software defects reported on the PrintGuard AI platform:
1. **Repeated 3D Model Generation:** When users submitted different prompts (e.g., `"cup"` followed by `"hat"`), the platform generated identical 3D geometries and re-displayed the previous preview.
2. **PrusaSlicer Warning & Status Anomalies:** Valid sliced models displayed an alarming red warning (`[Slicer Warnings] 69 => Alert if supports needed;`), while print jobs in the Print Queue displayed confusing statuses (`ready_to_print` and `cancelled`) alongside error boxes.

Both issues have been diagnosed to root cause and resolved with targeted, production-grade fixes across the procedural CAD engine, Spring Boot backend, and React frontend. Live testing confirmed that **10 out of 10 distinct prompts now produce 10 unique, watertight, and manifold 3D meshes (10/10 unique SHA-256 hashes)**, and slicing generates genuine production G-code without false warning alerts.

---

## Phase 1 & 3 — Root Cause Diagnoses

### 1. Root Causes of Repeated Model Generation

| Factor | Diagnostic Finding | Confirmed Root Cause? |
| :--- | :--- | :--- |
| **Procedural CAD Template Scope** | In [chess_generator.py](file:///c:/Projects01/AI_3D_Printer/backend/geometry/chess_generator.py), the generator previously only implemented 6 Staunton chess pieces. Any other prompt (such as `"cup"`, `"hat"`, `"gear"`, `"vase"`) fell into an `else` branch that **silently returned the exact same Staunton Chess King mesh**. | **YES (Primary Backend Cause)** |
| **Naive Substring Keyword Matching** | In [chess_generator.py](file:///c:/Projects01/AI_3D_Printer/backend/geometry/chess_generator.py), keywords were matched using raw substrings (`if k in lower`). For example, `"cog"` matched `"unrecognized"`, causing arbitrary prompts to trigger unrelated geometries. | **YES (Prompt Matching Flaw)** |
| **Forced Cube Fallback in Spring Boot** | In [GenerationController.java](file:///c:/Projects01/AI_3D_Printer/printguard-migration-starter/backend-java/src/main/java/com/printguard/models/GenerationController.java), line 67 hardcoded `|| prompt.toLowerCase().contains("cube")` as `isExplicitDemo`. Entering `"cube"` or copying the default UI placeholder (`"A colorful Rubik's cube..."`) bypassed CAD generation completely and generated a static 335-byte demo cube OBJ, which was then rejected by SlicerService. | **YES (Prompt Parsing Flaw)** |
| **Frontend Asynchronous State Hang** | In [GenerationContext.jsx](file:///c:/Projects01/AI_3D_Printer/frontend/src/context/GenerationContext.jsx), `startTextGeneration` unconditionally set `state: 'polling'` and opened a WebSocket even when Spring Boot returned synchronously with `status: 'ready'`. Because the WebSocket event was dispatched before the client connected, the UI hung in a polling spinner for 15 seconds until the fallback poll interval fired. | **YES (Frontend State Cause)** |
| **Prompt Input Overwrite on Reset** | In [GenerationContext.jsx](file:///c:/Projects01/AI_3D_Printer/frontend/src/context/GenerationContext.jsx), calling `resetText()` re-read `gen_text_prompt` from `localStorage`, wiping out any new prompt the user had typed into the textarea. | **YES (UX State Bug)** |
| **Missing Generate Button in Done State** | In [GenerateModel.jsx](file:///c:/Projects01/AI_3D_Printer/frontend/src/pages/GenerateModel.jsx), the "Generate 3D Model" button was only rendered `if (state === 'idle')`. When a model was already ready (`state === 'done'`), the button vanished, forcing users to click "New" (which restored the previous prompt). | **YES (UI Workflow Bug)** |
| **Three.js Loader Caching** | Three.js `OBJLoader` caches loaded geometries by URL. While filenames are UUID-based, without clearing preview state or setting explicit loading overlays, users could not determine whether the viewer was rendering a new or previous model. | **YES (Visual Ambiguity)** |

---

### 2. Root Causes of PrusaSlicer Warnings & Queue Statuses

| Factor | Diagnostic Finding | Confirmed Root Cause? |
| :--- | :--- | :--- |
| **Progress Milestone Misinterpreted as Warning** | PrusaSlicer CLI (`prusa-slicer-console.exe`) prints stage progress milestones to stdout in the format `<percentage> => <stage description>`. At 69% completion, it outputs: `69 => Alert if supports needed`. | **YES (Core Slicer Bug)** |
| **Greedy Alert Pattern Matching** | In [SlicerService.java](file:///c:/Projects01/AI_3D_Printer/printguard-migration-starter/backend-java/src/main/java/com/printguard/slicer/SlicerService.java), line 164 checked `if (line.toLowerCase().contains("warning") || line.toLowerCase().contains("alert"))`. It matched the word `"Alert"` in `69 => Alert if supports needed` and appended it to `warnings`. | **YES (Regex/Parsing Bug)** |
| **ErrorDetail Contamination on Success** | In [PrintJobController.java](file:///c:/Projects01/AI_3D_Printer/printguard-migration-starter/backend-java/src/main/java/com/printguard/jobs/PrintJobController.java), line 139 stored `[Slicer Warnings] 69 => Alert if supports needed;` into `initial.setErrorDetail(...)` even though slicing completed with exit code 0 and generated valid G-code. | **YES (Backend Logic Bug)** |
| **Queue Displaying Warnings as Fatal Red Cards** | In [PrintQueue.jsx](file:///c:/Projects01/AI_3D_Printer/frontend/src/pages/PrintQueue.jsx), any job with a non-empty `error_detail` rendered a red `bg-danger/10 text-danger border-danger/20` card, falsely signaling failure to the user for jobs marked `ready_to_print`. | **YES (Frontend Severity Bug)** |
| **Cancelled Job Status Confusion** | When a user clicked "Cancel Job" on a `ready_to_print` job in Print Queue, `PATCH /print-jobs/{id}/` changed the status to `cancelled`, but left the old `[Slicer Warnings]` string in `error_detail`. Consequently, one job appeared as `cancelled` while another appeared as `ready_to_print`, both displaying the red warning box. | **YES (Status / Workflow Bug)** |

---

## Phase 2 & 4 — Implementations & Code Changes

### Files Modified

1. [backend/geometry/chess_generator.py](file:///c:/Projects01/AI_3D_Printer/backend/geometry/chess_generator.py)
   - Expanded procedural CAD generation with 8 new parametric shapes:
     - `generate_parametric_cup()`: Watertight hollow cup with 2.5mm wall, solid 4mm base, and C-handle.
     - `generate_parametric_vase()`: Fluted vase/urn with bulbous waist and flared lip.
     - `generate_parametric_hat()`: Top hat/fedora with circular brim and cylindrical crown.
     - `generate_mechanical_gear()`: 12-tooth involute spur gear with center hub.
     - `generate_calibration_cube()`: Standard 20x20x20mm test cube with top marker.
     - `generate_parametric_cylinder()`: Chamfered column/rod.
     - `generate_parametric_pyramid()`: 4-sided pyramid with stepped plinth base.
     - `generate_calibration_pedestal()`: Stepped disc calibration pedestal with honest metadata disclosure for prompts outside specialized CAD templates.
   - Refactored prompt parsing to use regular expressions with word boundaries (`\b(keyword)\b`), eliminating substring collision bugs (such as `"cog"` matching `"unrecognized"`).
   - Added `is_watertight` validation to all returned geometry dictionaries.

2. [GenerationController.java](file:///c:/Projects01/AI_3D_Printer/printguard-migration-starter/backend-java/src/main/java/com/printguard/models/GenerationController.java)
   - Removed `prompt.toLowerCase().contains("cube")` from demo fallback checks; `"cube"` now generates the genuine, sliceable CAD calibration cube.
   - Added `ProceduralResult` record to extract `pieceName` and `metadataDescription` from the geometry engine.
   - Replaced silent fallback substitution: if procedural generation fails, the backend returns HTTP 422 Unprocessable Entity with a clean, honest error message rather than silently substituting a dummy cube.

3. [SlicerService.java](file:///c:/Projects01/AI_3D_Printer/printguard-migration-starter/backend-java/src/main/java/com/printguard/slicer/SlicerService.java)
   - Filtered out progress milestones matching `^\d+\s*=>.*` from warning collection, permanently eliminating the `69 => Alert if supports needed` false positive.
   - Restricted warning capture to genuine compiler/slicer warning lines (`warning:`, `[warning]`, or `error:`).
   - Verified that generic slicing profile operates reliably across all procedural meshes.

4. [PrintJobController.java](file:///c:/Projects01/AI_3D_Printer/printguard-migration-starter/backend-java/src/main/java/com/printguard/jobs/PrintJobController.java)
   - Updated successful slice advisory prefix from `[Slicer Warnings]` to `[Slicer Notice]`, leaving `errorDetail` empty when no real warnings exist.
   - Updated cancellation handling in both `PATCH /print-jobs/{id}/` and `POST /print-jobs/{id}/cancel` to set `errorDetail = "Print job was cancelled by user."`, preventing lingering slicer warnings on cancelled records.

5. [GenerationContext.jsx](file:///c:/Projects01/AI_3D_Printer/frontend/src/context/GenerationContext.jsx)
   - Handled synchronous `ready` responses from Spring Boot immediately: transitions to `state: 'done'` without opening hanging WebSockets or triggering 15-second polling delays.
   - Added `previousTask` tracking in context so that a failed generation preserves the previous model while clearly labeling it as previous.
   - Updated `resetText(preserveCurrentPrompt)` to prevent overwriting the user's active textarea input.

6. [GenerateModel.jsx](file:///c:/Projects01/AI_3D_Printer/frontend/src/pages/GenerateModel.jsx)
   - Added `TEMPLATE_SUGGESTIONS` pill bar allowing users to click and test all supported CAD shapes instantly.
   - Enabled the primary "Generate 3D Model" button in all non-loading states, allowing continuous regeneration without fighting the UI.
   - Added a backdrop blur loading overlay with animated spinner over the 3D viewer during generation, ensuring the user immediately recognizes that new geometry is being generated.
   - Updated 3D canvas `key` to `activeDisplayTask.file_url` to prevent stale Three.js cache retention.
   - Added amber banner in 3D viewer when displaying previous models after an error (`Showing Previous Model #{id}`).

7. [PrintQueue.jsx](file:///c:/Projects01/AI_3D_Printer/frontend/src/pages/PrintQueue.jsx)
   - Redesigned status and notice cards:
     - `ready_to_print` with advisories: Renders an AMBER advisory card with `AlertTriangle` icon explaining that slicing succeeded and G-code is valid.
     - `cancelled`: Renders a muted status box indicating the job was cancelled by the user.
     - `failed`: Renders a RED failure alert with detailed error explanation.

---

## Phase 6 — Verification Test Evidence

### 1. Before vs. After Hash Comparison (Model Uniqueness Test)

#### Before Remediation (Baseline):
| Prompt Tested | Returned Piece | File Size | SHA-256 Hash (First 16 chars) | Result |
| :--- | :--- | :--- | :--- | :--- |
| `"Staunton Chess King"` | Chess King | 62,932 B | `14a050594d82c4c1` | Piece Generated |
| `"cup"` | Staunton piece | 62,932 B | `14a050594d82c4c1` | **REPEATED KING** |
| `"hat"` | Staunton piece | 62,932 B | `14a050594d82c4c1` | **REPEATED KING** |
| `"A colorful Rubik's cube..."` | Demo Fallback Cube | 335 B | `1ed42a2659c6d40e` | Static Dummy Mesh |

#### After Remediation (Live Verification on Oct 14, 2026):
| # | Prompt Submitted | Model ID | Generation Method | Vertices | Faces | File Size | SHA-256 Hash (First 16 chars) | Unique? |
| :-: | :--- | :-: | :--- | :-: | :-: | :-: | :--- | :-: |
| 1 | Staunton Chess King with cross | 34 | `procedural_cad` | 978 | 1,944 | 62,932 B | `14a050594d82c4c1` | **UNIQUE** |
| 2 | Staunton Chess Knight horse head | 35 | `procedural_cad` | 528 | 1,036 | 33,320 B | `c29359655a386d93` | **UNIQUE** |
| 3 | Parametric Coffee Cup with handle | 36 | `procedural_cad` | 626 | 1,248 | 40,025 B | `fbc5092a46b1d366` | **UNIQUE** |
| 4 | Parametric Ceramic Vase | 37 | `procedural_cad` | 626 | 1,248 | 40,025 B | `ab736eebefabfe8f` | **UNIQUE** |
| 5 | Parametric Top Hat | 38 | `procedural_cad` | 242 | 480 | 15,488 B | `b73fa6d7aa11f561` | **UNIQUE** |
| 6 | Mechanical Spur Gear 12-tooth | 39 | `procedural_cad` | 186 | 364 | 11,812 B | `9da1f8c5ac502ed0` | **UNIQUE** |
| 7 | Calibration Test Cube 20mm | 40 | `procedural_cad` | 74 | 140 | 4,580 B | `b70e7b01f389c745` | **UNIQUE** |
| 8 | Parametric Cylinder column | 41 | `procedural_cad` | 242 | 480 | 15,488 B | `ee6a13ad06997ee2` | **UNIQUE** |
| 9 | Parametric Pyramid plinth | 42 | `procedural_cad` | 13 | 18 | 744 B | `762b4874e23be53c` | **UNIQUE** |
| 10 | An abstract decorative desk ornament | 43 | `procedural_cad` | 338 | 672 | 21,632 B | `2b266d169c8987c1` | **UNIQUE** |

**Result:** **10 out of 10 models produced distinct, unique SHA-256 hashes.** Zero duplicate geometries.

---

### 2. PrusaSlicer Slicing & G-Code Verification Test

Each of the 10 unique models was sliced through Spring Boot `POST /api/print-jobs/` and PrusaSlicer CLI (`prusa-slicer-console.exe`):

| Model ID | Prompt | Slicer Exit Code | Job ID | Job Status | G-Code Size | Layers | Slicer Notice / Warning |
| :-: | :--- | :-: | :-: | :--- | :-: | :-: | :--- |
| 34 | Staunton Chess King with cross | 0 | 24 | `ready_to_print` | 1,237.1 KB | 233 | *(None — Clean)* |
| 35 | Staunton Chess Knight horse head | 0 | 25 | `ready_to_print` | 569.7 KB | 187 | *(None — Clean)* |
| 36 | Parametric Coffee Cup with handle | 0 | 26 | `ready_to_print` | 1,754.5 KB | 140 | *(None — Clean)* |
| 37 | Parametric Ceramic Vase | 0 | 27 | `ready_to_print` | 1,570.8 KB | 180 | *(None — Clean)* |
| 38 | Parametric Top Hat | 0 | 28 | `ready_to_print` | 687.2 KB | 93 | *(None — Clean)* |
| 39 | Mechanical Spur Gear 12-tooth | 0 | 29 | `ready_to_print` | 497.6 KB | 40 | `[Slicer Notice] print warning: Detected print stability issues:;` *(Rendered in AMBER)* |
| 40 | Calibration Test Cube 20mm | 0 | 30 | `ready_to_print` | 156.7 KB | 67 | *(None — Clean)* |
| 41 | Parametric Cylinder column | 0 | 31 | `ready_to_print` | 711.1 KB | 120 | *(None — Clean)* |
| 42 | Parametric Pyramid plinth | 0 | 32 | `ready_to_print` | 218.4 KB | 107 | *(None — Clean)* |
| 43 | An abstract decorative desk ornament | 0 | 33 | `ready_to_print` | 383.7 KB | 53 | *(None — Clean)* |

**Warning Verification:**
- The spurious warning `[Slicer Warnings] 69 => Alert if supports needed;` is **100% eliminated**.
- Slicing exit code is 0 across all 10 models.
- All 10 jobs produced genuine, complete G-code files with valid extruder (`G1`, `G0`) commands, layer markers, and estimated printing times.

---

### 3. Job Cancellation Flow Verification

- **Action:** Issued `PATCH /api/print-jobs/33/` with body `{"status": "cancelled"}`.
- **HTTP Response:** `200 OK`.
- **Returned Status:** `"cancelled"`.
- **Returned Error Detail:** `"Print job was cancelled by user."` (Cleanly replaced old slicer advisory).
- **Result:** **PASS**.

---

### 4. File Download & Regression Test Evidence

- **G-Code Download Test:** `GET /api/print-jobs/24/download-gcode/` returned HTTP 200 with 1,266,770 bytes of binary G-code containing headers and extrusion coordinates (`G1`, `G0`, `E`).
- **OBJ Mesh Download Test:** `GET /media/models/ae7329d0.obj` returned HTTP 200 with 62,932 bytes of valid Wavefront OBJ geometric vertices (`v`) and polygonal faces (`f`).
- **Regression:** **PASS**.

---

### 5. Automated Test Suites Execution

- **Spring Boot Maven Tests:**
  `mvn test -Dtest=*`
  - Total tests run: **44**
  - Failures: **0**, Errors: **0**, Skipped: **0**
  - Status: **BUILD SUCCESS** (Time elapsed: 29.685s).
- **Python AI Microservice Tests:**
  `python -m unittest test_ai_service.py`
  - Total tests run: **4**
  - Status: **OK** (Ran in 0.051s).
- **Frontend Production Build:**
  `npm run build` (Vite 5.4)
  - 3,318 modules transformed.
  - Zero syntax or bundling errors.
  - Status: **SUCCESS** (Time elapsed: 14.63s).

---

## Phase 5 — Hosted Cloud Architecture Recommendation

### Current Local vs. Cloud Deployment Comparison

| Architecture Step | How It Operates Locally Today | Production Cloud Hosting Architecture |
| :--- | :--- | :--- |
| **1. Request Ingestion** | Vite React app proxies API requests to Spring Boot on `localhost:8080`. | React static bundle served via CDN (Vercel, Cloudflare Pages, or AWS S3+CloudFront) communicating with Spring Boot on a containerized service (AWS ECS / Render / Fly.io). |
| **2. Model Synthesis Engine** | Procedural CAD generator executes locally via Python/FastAPI microservice (`localhost:8001`) or direct subprocess invocation. | **Dual-engine architecture:**<br>• *Procedural CAD (Instant):* Containerized Python CAD worker running on standard CPU container (Trimesh / NumPy / SciPy). Zero GPU costs.<br>• *Generative Text-to-3D (AI):* Async task submitted to a decoupled serverless GPU endpoint (e.g., RunPod Serverless, Modal, or Replicate running Shap-E or TripoSR). Spins down to $0 when idle; user's laptop RTX 2050 is never touched. |
| **3. Mesh Storage** | Saved locally to disk at `backend/media/models/<uuid>.obj`. | Uploaded to S3-compatible cloud object storage (e.g., Supabase Storage bucket, AWS S3, or Cloudflare R2) with CDN presigned download URLs. |
| **4. 3D Preview** | React Three Fiber fetches `/media/models/<uuid>.obj` from local Spring Boot static handler. | React Three Fiber streams OBJ directly from CDN/S3 bucket with cache headers. |
| **5. Slicing Engine** | Headless PrusaSlicer Windows console executable invoked as subprocess on laptop host. | Headless PrusaSlicer Linux binary inside a Docker container (e.g. Alpine/Debian base with `prusa-slicer` package) running on standard cloud CPU without display/X11 dependencies. |
| **6. G-Code Distribution** | Sliced `.gcode` saved to `backend/media/gcode/` and downloaded via Spring Boot endpoint. | Sliced `.gcode` saved to secure S3/Supabase storage bucket and made available via presigned download link. |
| **7. Webcam AI Defect Monitoring** | YOLOv8 OpenCV microservice running on port 8001; processes video feed frame-by-frame. | Dedicated AI inference microservice deployed on a small GPU/CPU container instance with WebRTC or WebSocket streaming for real-time spaghetti defect detection. |

### Decoupling from Local RTX 2050 GPU
- **Key Assurance:** The application's procedural CAD generation and slicing pipeline runs entirely on standard CPU instructions using NumPy, SciPy, Trimesh, and PrusaSlicer CLI.
- No local GPU inference is required for college demonstration, evaluation, or future online deployment.
- Future AI generative text-to-3D can be integrated asynchronously via external API webhooks without incurring local hardware lock-in or ongoing idle server costs.

---

## Supported Procedural CAD Capabilities & Scope

PrintGuard AI clearly and honestly labels its 3D modeling as **Procedural CAD Synthesis**. The engine dynamically parses input prompts into 14 distinct watertight, printable parametric geometries:

1. **Staunton Chess King** (`king`, `raja`) — Flared coronet crown, finial cross, waisted column, pedestal base.
2. **Staunton Chess Knight** (`knight`, `horse`, `ghoda`, `equine`) — Equine silhouette, arched neck, ears, snout, circular base.
3. **Staunton Chess Queen** (`queen`, `rani`) — Coronet rim, spherical finial, flared column, pedestal base.
4. **Staunton Chess Rook** (`rook`, `castle`, `kila`, `tower`) — Crenellated parapet tower, cylindrical column, stepped base.
5. **Staunton Chess Bishop** (`bishop`, `miter`, `mitre`) — Oval miter, top finial sphere, pedestal base.
6. **Staunton Chess Pawn** (`pawn`, `pyada`) — Spherical head, collar ring, tapered waist, pedestal base.
7. **Parametric Coffee Cup / Mug** (`cup`, `mug`, `chalice`, `goblet`, `tea cup`) — Hollow cylindrical cavity, 2.5mm wall, solid base, ergonomic C-handle.
8. **Parametric Ceramic Vase** (`vase`, `urn`, `pot`, `vessel`, `bottle`) — Bulbous waist, contoured neck, flared lip.
9. **Parametric Top Hat** (`hat`, `cap`, `fedora`) — Wide circular brim, cylindrical crown, rounded dome.
10. **Mechanical Spur Gear** (`gear`, `cog`, `sprocket`, `wheel`, `pinion`) — 12-tooth involute profile, central shaft bore, raised hub.
11. **Calibration Test Cube** (`cube`, `box`, `block`, `rubik`, `die`) — 20x20x20mm test cube with top circular orientation marker.
12. **Parametric Cylinder** (`cylinder`, `tube`, `pipe`, `rod`, `pillar`) — Solid column with chamfered base and crown.
13. **Parametric Pyramid** (`pyramid`, `cone`, `funnel`, `prism`) — 4-sided pyramid with stepped plinth base.
14. **Calibration Pedestal (Honest Fallback)** — Standard stepped disc pedestal (36mm diam x 16mm height) generated for arbitrary unrecognized prompts, with transparent metadata explaining available template keywords.

---

## Remaining Considerations & Deployment Prerequisites

1. **Physical Hardware Demonstration:**
   - Physical 3D printing remains intentionally out of scope for this software demonstration project. The platform successfully proves 3D modeling, slicing, G-code export, and AI webcam monitoring.
2. **OctoPrint Simulation:**
   - The Print Queue displays a clear "Integration in Progress (Coming Soon)" banner. Real hardware commands are properly guarded and will not crash or hang when a physical printer is absent.
3. **Legacy Preservation:**
   - Database records, Supabase connection profiles, and legacy Django/SQLite implementations remain completely intact. No git commits or unauthorized external deployments were made.
