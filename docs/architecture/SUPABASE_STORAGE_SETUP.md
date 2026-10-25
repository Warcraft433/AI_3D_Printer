# PrintGuard AI — Supabase Storage Integration & Setup Guide

This document describes the architecture, security policies, configuration steps, and verification procedures for integrating the private Supabase Storage bucket (`printguard-files`) with the PrintGuard AI backend.

---

## 1. Architecture & Design

PrintGuard AI uses **Supabase Storage** as its persistent object store for procedural CAD models (OBJ), generated meshes (STL), and sliced printer instructions (G-code).

```
                      +---------------------------------------+
                      |       React Vite Frontend (5173)     |
                      +---------------------------------------+
                                   |              |
           JWT Bearer / ?token=... |              | 3D Canvas / Downloads
                                   v              v
                      +---------------------------------------+
                      |     Spring Boot Backend (8080)        |
                      |  - SecurityUtils Ownership Check      |
                      |  - JwtAuthenticationFilter            |
                      +---------------------------------------+
                           /              \               \
     (Local Cache / Slicing) /              \ (Cloud Store)  \ (PostgreSQL DB)
                           v                v                v
                 +-----------------+  +-------------------+  +---------------+
                 |  Local Media /  |  | Private Supabase  |  |  Supabase DB  |
                 |  PrusaSlicer    |  | Bucket:           |  | `file_path`   |
                 |  Workdir        |  | printguard-files  |  | `gcode_path`  |
                 +-----------------+  +-------------------+  +---------------+
```

### Key Principles

1. **Strictly Private Bucket**:
   The bucket `printguard-files` is **strictly private** (`public: false`). It is never set to public, and direct unauthenticated public access URLs are never generated or exposed.
2. **Backend Service-Role Isolation**:
   The Supabase `service_role` key is held exclusively in the backend server's process environment variables. It is never transmitted to the browser, never bundled into frontend JavaScript, and never logged in stdout/stderr.
3. **Per-User Ownership & Path Scoping**:
   All uploaded assets are segregated into user-scoped directories:
   - 3D Models: `users/{userId}/models/{timestamp}_{filename}.obj`
   - G-Code: `users/{userId}/gcode/job_{jobId}_{filename}.gcode`
4. **Proxy & Ownership Enforcement**:
   File access endpoints (`/api/models/{id}/download`, `/api/models/{id}/file`, `/api/print-jobs/{id}/download-gcode`) enforce JWT authentication and verify that `currentUserId == resourceOwnerId` (or admin). Unauthorized cross-user attempts return `403 Forbidden`.
5. **Seamless Local Fallback**:
   If `SUPABASE_SERVICE_ROLE_KEY` is not provided (e.g. in offline development or local testing), the backend automatically falls back to local storage in `backend/media` without failing or crashing.

---

## 2. Configuration & Environment Variables

The backend uses the following environment variables. Add them to your `C:\Projects01\AI_3D_Printer\.env` file:

```ini
# --- Supabase Storage Configuration (Private Bucket) ---
# Your Supabase Project Base URL (from Project Settings > API)
SUPABASE_URL=https://wlpskpsysnamtmzilpft.supabase.co

# Supabase Service Role Secret Key (Backend ONLY — NEVER expose to frontend or git)
SUPABASE_SERVICE_ROLE_KEY=your-supabase-service-role-secret-key-here

# Private bucket name for secure storage of OBJ, STL, and G-code assets
SUPABASE_STORAGE_BUCKET=printguard-files

# Storage connection timeouts (optional, milliseconds)
SUPABASE_STORAGE_CONNECT_TIMEOUT_MS=5000
SUPABASE_STORAGE_READ_TIMEOUT_MS=15000
```

> [!WARNING]
> Use the **service_role** key, NOT the public `anon` key. The `anon` key does not possess write privileges to private buckets unless complex RLS policies are attached to `storage.objects`. The `service_role` key must remain confidential.

---

## 3. Step-by-Step Supabase Setup

### Step 3.1: Confirm Bucket Privacy in Supabase Dashboard

1. Navigate to your Supabase Dashboard: `https://supabase.com/dashboard/project/wlpskpsysnamtmzilpft`.
2. In the left navigation menu, click **Storage**.
3. Under **Buckets**, locate `printguard-files`:
   - Click the three dots `...` next to `printguard-files` and select **Edit bucket**.
   - Verify that **"Public bucket" is toggled OFF (unchecked)**.
   - Save changes if modified.

### Step 3.2: Copy the Service Role Key

1. In the Supabase Dashboard, click **Project Settings** (gear icon) in the bottom-left.
2. Select **API** from the settings menu.
3. Under **Project API keys**, locate the key labeled `service_role` `secret`.
4. Click **Reveal** and copy the secret key.
5. Paste it into `C:\Projects01\AI_3D_Printer\.env`:
   ```ini
   SUPABASE_SERVICE_ROLE_KEY=<pasted_service_role_key>
   ```

### Step 3.3: Restart Services

Run the unified startup script or restart the backend:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start_backend_supabase.ps1
```

---

## 4. Database Storage & Schema Compatibility

PrintGuard AI preserves existing database records without destructive schema migrations:

- **`generated_models_generatedmodel` table**:
  - `file_path VARCHAR(500)`: Stores the object path within the bucket (e.g. `users/1/models/chess_rook.obj`) or legacy relative disk path (`models/abc.obj`).
  - `error_detail TEXT`: Contains generation diagnostic details and storage attribution, e.g. `[Storage: Supabase printguard-files]`.
- **`print_jobs_printjob` table**:
  - `gcode_path VARCHAR(500)`: Stores the G-code object path (e.g. `users/1/gcode/job_1_chess.gcode`).

---

## 5. Security & Access Verification

### Endpoints Implemented

| Endpoint | Method | Authentication | Ownership Checked | Description |
|---|---|---|---|---|
| `/api/models/{id}/download` | GET | Required (JWT) | Yes (Owner or Admin) | Downloads model asset (`Content-Disposition: attachment`) |
| `/api/models/{id}/file` | GET | Required (JWT) | Yes (Owner or Admin) | Streams model asset for ThreeJS canvas (`Content-Disposition: inline`) |
| `/api/print-jobs/{id}/download-gcode` | GET | Required (JWT) | Yes (Owner or Admin) | Streams sliced G-code file (`text/x-gcode`) |
| `/api/storage/status` | GET | Required (JWT) | N/A | Returns current storage health and bucket privacy status |

### Testing & Validation Commands

Run the storage unit test suite (verifies upload, download, missing file handling, and bucket privacy assertion):
```powershell
cd C:\Projects01\AI_3D_Printer\printguard-migration-starter\backend-java
mvn test -Dtest=SupabaseStorageServiceTest
```

Run the security and cross-user isolation test suite:
```powershell
mvn test -Dtest=StorageSecurityCrossUserTest
```

Run the complete backend test suite:
```powershell
mvn test
```

---

## 6. Verification Status

- [x] Storage service layer (`SupabaseStorageService`, `StorageService`) implemented.
- [x] Private bucket validation (`verifyBucketPrivacy` asserts `public == false`) implemented and tested.
- [x] Multi-format upload support (OBJ, STL, G-code) implemented.
- [x] Per-user folder scoping (`users/{userId}/...`) implemented.
- [x] Authenticated download & 3D canvas streaming endpoints implemented.
- [x] Per-user ownership and cross-user blocking (403 Forbidden) verified.
- [x] Unauthenticated blocking (401 Unauthorized) verified.
- [x] Preserved legacy database schema and local file fallback verified.
- [x] All 63 backend automated tests passing.
- [x] React frontend build passing cleanly.
