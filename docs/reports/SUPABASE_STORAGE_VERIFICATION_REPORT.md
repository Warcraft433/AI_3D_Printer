# PrintGuard AI — Supabase Storage Integration Verification Report

**Verification Run Date:** 2026-10-14 (Post-Correction Verification)  
**Project:** Enhanced 3D Printer / PrintGuard AI  
**Scope:** Supabase Storage Credentials, Backend Process Environment, Live Storage API Status, Cloud Object Roundtrip Probe, Model Generation Integration, Unit/Security Test Suite, and Security Advisor Posture.

---

## Executive Status Matrix

| Verification Check | Status | Key Evidence / Detail |
| :--- | :---: | :--- |
| **1. Configuration & `.env` State** | **PASS** | `SUPABASE_URL` (`https://wlpskpsysnamtmzilpft.supabase.co`), `SUPABASE_STORAGE_BUCKET` (`printguard-files`), and `SUPABASE_SERVICE_ROLE_KEY` (41-character server secret key) are present, non-empty, and valid. |
| **2. Process Environment Ingestion** | **PASS** | Active Spring Boot backend process (`PID 27696`) launched via `scripts/start_backend_supabase.ps1`, successfully ingesting all environment variables from `.env`. Log confirms: `Supabase Storage: Client configured for private bucket 'printguard-files'`. |
| **3. Live `/api/storage/status` Endpoint** | **PASS** | Queried live with an authenticated user JWT. Returned: `"storage_type": "supabase"`, `"is_cloud_active": true`, `"is_private": true`, `"service_key_configured": true`, `"status": "connected_private"`. **Cloud storage is 100% active; not using local fallback.** |
| **4. Cloud Bucket Accessibility & Privacy** | **PASS** | Verified via live Supabase Storage API (`GET /storage/v1/bucket/printguard-files`): returns `public: false` (**Strictly Private**). Database catalog `storage.buckets` also confirms privacy. |
| **5. Live Temporary Object Roundtrip Probe** | **PASS** | Uniquely named object `diagnostics/test_probe_1791944260.txt` uploaded (`HTTP 200`), downloaded via authenticated endpoint (`HTTP 200`), byte content verified to match exactly (`PASS`), and cleaned up (`HTTP 200`). Zero user files modified. |
| **6. End-to-End Application Cloud Storage** | **PASS** | Generated procedural CAD model via Spring Boot API (`POST /api/generate/text/`). Uploaded directly to private bucket as `users/22/models/9fadb0f7.obj`. Downloaded through `/api/models/44/download` (`HTTP 200`, 47,146 bytes). Safely cleaned up. |
| **7. Storage Tests (Unit & Security)** | **PASS** | `mvn test "-Dtest=SupabaseStorageServiceTest,StorageSecurityCrossUserTest"` passed: **19 tests run, 0 failures, 0 errors**. Per-user ownership and cross-user 403 blocks verified. |
| **8. Security Advisor Warnings** | **UNRESOLVED (PENDING MIGRATION)** | 5 tables (`auth_user`, `error_logs_errorlog`, `generated_models_generatedmodel`, `print_jobs_auditlog`, `print_jobs_printjob`) remain marked `UNRESTRICTED` in Supabase pending manual execution of `V2__enable_rls_and_least_privilege.sql`. |

---

## 1. Environment Variable Audit & Backend Process State

### 1.1 `.env` File Inspection
An automated audit of `c:\Projects01\AI_3D_Printer\.env` confirmed:
- `SPRING_DATASOURCE_URL`: **PRESENT** (Length: 84)
- `SPRING_DATASOURCE_USERNAME`: **PRESENT** (Length: 29)
- `SPRING_DATASOURCE_PASSWORD`: **PRESENT** (Length: 15)
- `SUPABASE_URL`: **PRESENT** (`https://wlpskpsysnamtmzilpft.supabase.co`)
- `SUPABASE_STORAGE_BUCKET`: **PRESENT** (`printguard-files`)
- `SUPABASE_SERVICE_ROLE_KEY`: **PRESENT** (Length: 41 characters, valid secret key format)

*Credential Safety:* No passwords, tokens, or secret keys are logged, exposed, or printed.

### 1.2 Backend Ingestion & Initialization
The backend was restarted with `scripts/start_backend_supabase.ps1` (`PID 27696`).  
Startup log confirmation:
```
2026-10-14T07:46:25.195+05:30  INFO 27696 --- [printguard-backend] [ main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
2026-10-14T07:46:31.132+05:30  INFO 27696 --- [printguard-backend] [ main] c.p.storage.SupabaseStorageService       : Supabase Storage: Client configured for private bucket 'printguard-files' (URL: https://wlpskpsysnamtmzilpft.supabase.co)
2026-10-14T07:46:33.164+05:30  INFO 27696 --- [printguard-backend] [ main] com.printguard.PrintGuardApplication     : Started PrintGuardApplication in 15.544 seconds
```

---

## 2. Live Storage Status Endpoint Evaluation

The live endpoint `GET http://localhost:8080/api/storage/status` was queried using a freshly issued JWT token:

```json
{
  "storage_type": "supabase",
  "bucket": "printguard-files",
  "is_cloud_active": true,
  "is_private": true,
  "supabase_url": "https://wlpskpsysnamtmzilpft.supabase.co",
  "service_key_configured": true,
  "status": "connected_private"
}
```

**Status:** **PASS**  
- `storage_type`: `supabase`
- `is_cloud_active`: `true`
- `is_private`: `true`
- `status`: `connected_private`

The backend is **actively connected to Supabase Cloud Storage** and is **not using local fallback**.

---

## 3. Real Temporary Object Roundtrip Probe

An automated live integration probe was executed using a temporary object:

- **Target Path:** `diagnostics/test_probe_1791944260.txt`
- **Payload:** `PrintGuard AI Storage Probe Verification - Timestamp: 1791944260\n`
- **1. Upload:**
  - Endpoint: `POST https://wlpskpsysnamtmzilpft.supabase.co/storage/v1/object/printguard-files/diagnostics/test_probe_1791944260.txt`
  - Response: `HTTP 200 OK` &rarr; `{"Key":"printguard-files/diagnostics/test_probe_1791944260.txt","Id":"e69a64fc-23e5-408a-a838-95dd33ddde42"}`
  - Status: **PASS**
- **2. Download:**
  - Endpoint: `GET https://wlpskpsysnamtmzilpft.supabase.co/storage/v1/object/authenticated/printguard-files/diagnostics/test_probe_1791944260.txt`
  - Response: `HTTP 200 OK` (65 bytes received)
  - Status: **PASS**
- **3. Content Verification:**
  - Downloaded bytes match uploaded bytes bit-for-bit.
  - Status: **PASS**
- **4. Cleanup:**
  - Endpoint: `DELETE https://wlpskpsysnamtmzilpft.supabase.co/storage/v1/object/printguard-files/diagnostics/test_probe_1791944260.txt`
  - Response: `HTTP 200 OK`
  - Status: **PASS**

Zero existing user files were touched or overwritten during the probe.

---

## 4. End-to-End Application Cloud Storage Test

To verify that the application layer operates with live cloud storage:

1. **User Authentication:** Registered test user and acquired JWT access token.
2. **Model Generation:** Submitted procedural CAD prompt `"Chess Pawn"` to `POST /api/generate/text/`.
   - Output: `ID: 44`, Status: `ready`
   - Stored Cloud Path: `users/22/models/9fadb0f7.obj`
   - Metadata: `[PROCEDURAL CAD] Generated parametric Staunton Chess Pawn mesh... [Storage: Supabase printguard-files]`
3. **Download Verification:** Streamed file via `GET /api/models/44/download` &rarr; `HTTP 200 OK` (47,146 bytes).
4. **Cleanup:** Executed `DELETE /api/models/44` &rarr; removed from database and deleted object from `printguard-files` (`HTTP 204 No Content`).

---

## 5. Storage Test Suite Results

Maven command executed:
```powershell
mvn test "-Dtest=SupabaseStorageServiceTest,StorageSecurityCrossUserTest"
```

### 5.1 Test Breakdown
- **`StorageSecurityCrossUserTest` (10/10 Passed):**
  - `testOwnerCanDownloadModel`: PASS
  - `testOwnerCanStreamModelFile`: PASS
  - `testCrossUserCannotDownloadModel` (Bob blocked from Alice's model: 403 Forbidden): PASS
  - `testCrossUserCannotStreamModel` (Bob blocked from Alice's 3D viewer stream: 403 Forbidden): PASS
  - `testUnauthenticatedCannotDownloadModel` (401 Unauthorized): PASS
  - `testNonExistentModelReturns404`: PASS
  - `testOwnerCanDownloadGCode`: PASS
  - `testCrossUserCannotDownloadGCode` (Bob blocked from Alice's G-code: 403 Forbidden): PASS
  - `testUnauthenticatedCannotDownloadGCode` (401 Unauthorized): PASS
  - `testAdminCanAccessAcrossUsers`: PASS
- **`SupabaseStorageServiceTest` (9/9 Passed):**
  - `testIsConfigured`: PASS
  - `testVerifyBucketPrivacySuccess`: PASS
  - `testVerifyBucketPrivacyRejectsPublicBucket`: PASS
  - `testUploadObjFile`: PASS
  - `testUploadStlFile`: PASS
  - `testUploadGCodeFile`: PASS
  - `testDownloadFileSuccess`: PASS
  - `testDownloadFileMissingThrowsFileNotFound`: PASS
  - `testCreateSignedUrl`: PASS

**Total Test Result:** `19 Tests Run, 0 Failures, 0 Errors, 0 Skipped` (**BUILD SUCCESS**).

---

## 6. Security Warnings & Unresolved Items

1. **UNRESTRICTED Supabase Tables:**
   - The tables `auth_user`, `error_logs_errorlog`, `generated_models_generatedmodel`, `print_jobs_auditlog`, and `print_jobs_printjob` remain marked `UNRESTRICTED` in Supabase Studio.
   - **Resolution:** A complete, safe, and idempotent SQL migration is available at [printguard-migration-starter/backend-java/src/main/resources/db/migration/V2__enable_rls_and_least_privilege.sql](file:///c:/Projects01/AI_3D_Printer/printguard-migration-starter/backend-java/src/main/resources/db/migration/V2__enable_rls_and_least_privilege.sql).
   - Once executed in the Supabase SQL Editor, all table badges in Supabase Studio will turn green (**PROTECTED**).
2. **Password Hash Protection:**
   - Applying `V2__enable_rls_and_least_privilege.sql` revokes public PostgREST grants from `anon` and `authenticated`, securing `auth_user.password` from direct HTTP extraction.
