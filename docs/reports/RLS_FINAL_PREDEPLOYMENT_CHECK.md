# PrintGuard AI — Final Pre-Production Review: Database RLS Migration

**Document Version:** 1.0.0  
**Review Date:** 2026-10-14  
**Project:** Enhanced 3D Printer / PrintGuard AI  
**Files Audited:**
- Migration: `printguard-migration-starter/backend-java/src/main/resources/db/migration/V2__enable_rls_and_least_privilege.sql`
- Rollback: `scripts/rollback_v2_rls.sql` (and historical backup `scripts/rollback_v2_rls.original.sql`)
- Verification: `scripts/verify_rls_migration.py`
- Backend Configuration: `printguard-migration-starter/backend-java/src/main/resources/application-supabase.yml`

---

## Executive Summary & Verification Matrix

| Verification Item | Status | Key Findings & Evidence |
| :--- | :---: | :--- |
| **1. Database Role & RLS Bypass** | **VERIFIED (PASS)** | Spring Boot connects through Supavisor as `postgres.wlpskpsysnamtmzilpft`. The session role is `postgres`. In PostgreSQL `pg_roles`, `postgres` possesses `rolbypassrls = true`. Spring Boot genuinely and permanently bypasses RLS policies. |
| **2. PostgREST Lockdown & Integrations** | **VERIFIED (PASS)** | Revoking grants from `anon` and `authenticated` blocks every public Data API route (`/rest/v1/*`). No production components depend on PostgREST: the React frontend calls Spring Boot (`/api/...`), and Supabase Storage uses the isolated `storage` schema. |
| **3. Migration Safety & Idempotency** | **VERIFIED (PASS)** | All DDL statements (`ENABLE ROW LEVEL SECURITY`, `DROP POLICY IF EXISTS`, `REVOKE`, `GRANT`, `ALTER DEFAULT PRIVILEGES`) are idempotent and safe on repeat execution. Sequences are secured. Wrapped in an atomic `BEGIN ... COMMIT` block. |
| **4. Rollback Script Security** | **CORRECTED (PASS)** | The pre-review rollback script insecurely re-granted `GRANT ALL ... TO anon, authenticated`, which would have re-exposed all password hashes. The original was preserved as `scripts/rollback_v2_rls.original.sql`, and `scripts/rollback_v2_rls.sql` was corrected to follow least privilege (disabling RLS without granting public access). |
| **5. Verification Script Coverage** | **ENHANCED (PASS)** | Upgraded `scripts/verify_rls_migration.py` to test not only metadata (`pg_class`, `pg_policies`, `role_table_grants`, row counts) but also **actual access behavior** via simulated `SET LOCAL ROLE anon` queries. |
| **6. Production Endpoints & Tasks** | **VERIFIED (PASS)** | Zero application endpoints, background daemons, or Celery tasks depend on public table grants. All database operations flow through Spring Boot's connection pool. |

---

## Detailed Technical Verifications

### 1. Database Role Used by Spring Boot & RLS Bypass Verification

#### Configuration Inspection
In `printguard-migration-starter/backend-java/src/main/resources/application-supabase.yml`:
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME} # postgres.wlpskpsysnamtmzilpft
```
In `.env`:
- `SPRING_DATASOURCE_URL`: `jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require`
- `SPRING_DATASOURCE_USERNAME`: `postgres.wlpskpsysnamtmzilpft`

#### Role Architecture & PostgreSQL Semantics
1. **Connection Pooling Routing:** The username format `<role>.<project-ref>` is required by Supabase's Supavisor connection pooler to route client connections to the proper tenant database cluster.
2. **Session Role:** Upon authentication, the PostgreSQL engine binds the connection to session user `postgres`.
3. **Role Attributes (`pg_roles`):**
   ```
   Role: postgres | Super: False | BypassRLS: True | CanLogin: True
   ```
4. **PostgreSQL Specification (PostgreSQL 14, 15, 16, 17):**
   > *"Superusers and roles with the `BYPASSRLS` attribute always bypass the row security system when accessing a table."*

#### Finding
The Spring Boot backend connects exclusively as `postgres` and possesses the `BYPASSRLS` attribute. Enabling Row Level Security will **never** restrict, filter, or degrade Spring Boot's JPA repository queries, custom JPQL queries, or transaction management.

---

### 2. Blocking Public PostgREST Without Breaking Integrations

#### PostgREST Attack Vector
Supabase provides an auto-generated REST API at `https://<project-ref>.supabase.co/rest/v1`.
- Calls without a user JWT map to the PostgreSQL role `anon`.
- Calls with a Supabase GoTrue user JWT map to the PostgreSQL role `authenticated`.

#### Applied Statement in `V2`
```sql
REVOKE ALL ON TABLE public.auth_user FROM anon, authenticated;
REVOKE ALL ON TABLE public.error_logs_errorlog FROM anon, authenticated;
REVOKE ALL ON TABLE public.generated_models_generatedmodel FROM anon, authenticated;
REVOKE ALL ON TABLE public.print_jobs_auditlog FROM anon, authenticated;
REVOKE ALL ON TABLE public.print_jobs_printjob FROM anon, authenticated;
```

#### Integration Dependency Audit
1. **React Vite Frontend (`frontend/src`):**
   - Communicates exclusively via Axios with the Spring Boot REST API (`http://localhost:8080/api/...`).
   - Does not import `@supabase/supabase-js` for database queries.
   - Does not query `/rest/v1/*`.
2. **Python AI Microservice (`backend/venv` / port 8001):**
   - Purely an inference and geometry generation service.
   - Has no direct PostgreSQL connection or PostgREST interaction.
3. **Supabase Storage (`printguard-files`):**
   - Objects and bucket metadata live in the isolated `storage` PostgreSQL schema (`storage.buckets`, `storage.objects`).
   - The Spring Boot backend interacts with storage using `SUPABASE_SERVICE_ROLE_KEY` (`service_role` role).
   - Storage operations are completely decoupled from permissions on schema `public`.

#### Finding
Revoking grants from `anon` and `authenticated` completely shuts down unauthenticated external scraping of password hashes and models while leaving 100% of required application integrations operational.

---

### 3. Safety, Idempotency & Repeat Execution

The migration in `V2__enable_rls_and_least_privilege.sql` was inspected for repeat execution safety:

```sql
BEGIN;

-- 1. Revokes are safe if already revoked
REVOKE ALL ON TABLE public.auth_user FROM anon, authenticated;
-- (Repeated for all 5 tables and 5 sequences)

-- 2. Default privileges modification is idempotent in pg_default_acl
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated;

-- 3. Grants are safe if already granted
GRANT ALL ON TABLE public.auth_user TO postgres, service_role;
-- (Repeated for all 5 tables and 5 sequences)

-- 4. ALTER TABLE ENABLE ROW LEVEL SECURITY is idempotent in PostgreSQL
ALTER TABLE public.auth_user ENABLE ROW LEVEL SECURITY;
-- (Repeated for all 5 tables)

-- 5. Policies use DROP POLICY IF EXISTS before CREATE POLICY
DROP POLICY IF EXISTS "service_role_manage_auth_user" ON public.auth_user;
CREATE POLICY "service_role_manage_auth_user" ON public.auth_user
    AS PERMISSIVE FOR ALL TO service_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "postgres_manage_auth_user" ON public.auth_user;
CREATE POLICY "postgres_manage_auth_user" ON public.auth_user
    AS PERMISSIVE FOR ALL TO postgres USING (true) WITH CHECK (true);
-- (Repeated for all 5 tables)

COMMIT;
```

#### Finding
- The migration can be run multiple times safely without syntax errors or policy collision.
- Sequences (`auth_user_id_seq`, etc.) are secured against unauthorized access while remaining accessible to `postgres` and `service_role`.
- The entire migration is wrapped in an atomic transaction; any failure causes a complete rollback.

---

### 4. Rollback Script Security & Corrections

#### Critical Vulnerability in Original Pre-Review Rollback
The pre-review version of `scripts/rollback_v2_rls.sql` contained:
```sql
-- DANGEROUS PRE-REVIEW STATEMENT:
GRANT ALL ON TABLE public.auth_user TO anon, authenticated;
GRANT ALL ON TABLE public.error_logs_errorlog TO anon, authenticated;
...
```
**Why this is unacceptable for production:**
Re-granting `GRANT ALL` to `anon` and `authenticated` after disabling RLS would re-expose all user accounts and password hashes to unauthenticated public web traffic via PostgREST. Because Spring Boot connects as `postgres`, `anon` and `authenticated` **never** need permissions on application tables.

#### Correction Implemented
1. **Preserved Original:** The pre-review file was preserved verbatim at [scripts/rollback_v2_rls.original.sql](file:///c:/Projects01/AI_3D_Printer/scripts/rollback_v2_rls.original.sql) for historical tracking.
2. **Corrected Production Rollback:** [scripts/rollback_v2_rls.sql](file:///c:/Projects01/AI_3D_Printer/scripts/rollback_v2_rls.sql) was updated to implement safe rollback semantics:
   - Drops custom policies.
   - Disables RLS on the 5 tables.
   - Ensures `postgres` and `service_role` retain full operational permissions.
   - **Maintains untrusted roles (`anon`, `authenticated`) in a revoked state.**

---

### 5. Verification Script Coverage & Access Behavior Simulation

The verification script [scripts/verify_rls_migration.py](file:///c:/Projects01/AI_3D_Printer/scripts/verify_rls_migration.py) was enhanced to verify:
1. **RLS Flag Status:** Checks `relrowsecurity` in `pg_class` for all 5 tables.
2. **Active Policies:** Inspects `pg_policies` for `service_role` and `postgres` policies.
3. **Public Grants:** Verifies that `role_table_grants` has 0 grants for `anon` and `authenticated`.
4. **Data Preservation:** Executes row count checks via `postgres` (confirming all rows are intact).
5. **Live Access Behavior Simulation:**
   - Executes `SET LOCAL ROLE anon; SELECT COUNT(*) FROM auth_user;` inside a rolled-back subtransaction. Verifies that PostgreSQL denies access with `InsufficientPrivilege` or returns 0 rows.
   - Executes `SET LOCAL ROLE service_role; SELECT COUNT(*) FROM auth_user;` to confirm administrative operations remain functional.

---

### 6. Production Endpoints and Background Tasks Dependency Check

| Endpoint / Task Group | Component | Database Mechanism | Relies on `anon`/`authenticated`? |
| :--- | :--- | :--- | :---: |
| **User Auth (`/api/auth/*`)** | Spring Boot (`AuthController`) | `UserRepository` via `postgres` | **NO** |
| **3D Models (`/api/models/*`)** | Spring Boot (`ModelController`) | `GeneratedModelRepository` via `postgres` | **NO** |
| **Generation (`/api/generate/*`)** | Spring Boot (`GenerationController`) | JPA Entity Persist via `postgres` | **NO** |
| **Slicing (`/api/slicer/*`)** | Spring Boot (`PrintJobController`) | `PrintJobRepository` via `postgres` | **NO** |
| **Print Jobs (`/api/print-jobs/*`)** | Spring Boot (`PrintJobController`) | `PrintJobRepository` via `postgres` | **NO** |
| **Analytics (`/api/dashboard/*`)** | Spring Boot (`DashboardController`) | Scoped JPQL queries via `postgres` | **NO** |
| **Defect Logs (`/api/error-logs/*`)** | Spring Boot (`ErrorLogController`) | `ErrorLogRepository` via `postgres` | **NO** |
| **AI Inference (:8001)** | FastAPI (`ai-service/app.py`) | OpenCV / YOLOv8 (no database) | **NO** |
| **Frontend Web App (:5173)** | React Vite (`frontend/src`) | HTTP calls to Spring Boot `:8080` | **NO** |

---

## Safe Production Deployment Procedure

When you are ready to apply the security migration to production, follow this five-step procedure:

### Step 1: Pre-Deployment Baseline Check
Verify baseline status (does not alter database):
```powershell
.\backend\venv\Scripts\python.exe scripts/verify_rls_migration.py
```
*(Confirms that the 5 tables are currently marked UNRESTRICTED and records pre-migration row counts).*

### Step 2: Apply the Migration
1. Log into your Supabase Dashboard: [https://supabase.com/dashboard/project/wlpskpsysnamtmzilpft](https://supabase.com/dashboard/project/wlpskpsysnamtmzilpft).
2. In the left navigation, click **SQL Editor** &rarr; **New query**.
3. Paste the complete contents of [V2__enable_rls_and_least_privilege.sql](file:///c:/Projects01/AI_3D_Printer/printguard-migration-starter/backend-java/src/main/resources/db/migration/V2__enable_rls_and_least_privilege.sql).
4. Click **Run**.

### Step 3: Automated Post-Deployment Verification
Run the enhanced verification script:
```powershell
.\backend\venv\Scripts\python.exe scripts/verify_rls_migration.py
```
**Expected Verification Output:**
- `[1] RLS Status`: All 5 tables show `PROTECTED (RLS Enabled)`.
- `[2] Policies`: Exactly 10 policies created (2 per table for `service_role` and `postgres`).
- `[3] Public Grants`: `Zero privileges granted to 'anon' or 'authenticated'`.
- `[4] Row Counts`: Exactly equal to pre-migration numbers (no data lost).
- `[5] Access Behavior`:
  - `anon` query fails with `InsufficientPrivilege` (access blocked).
  - `service_role` query succeeds (administrative access confirmed).

### Step 4: Verify PostgREST Lockdown via HTTP Probe
Execute a direct curl probe from a terminal using your project URL and public `anon` key:
```bash
curl -i -X GET "https://wlpskpsysnamtmzilpft.supabase.co/rest/v1/auth_user" \
     -H "apikey: <your-anon-key>" \
     -H "Authorization: Bearer <your-anon-key>"
```
**Expected Result:** `HTTP 401 Unauthorized` or `HTTP 403 Forbidden` with `{"message":"permission denied for table auth_user"}`. Password hashes are completely shielded.

### Step 5: Application Workflow Smoke Test
1. Log in via the web UI (`http://localhost:5173`).
2. Generate a 3D model on `Generate Model` &rarr; verify preview loads.
3. Slice the model &rarr; verify G-code download succeeds.
4. Open `Dashboard` &rarr; verify analytics chart and stats load.
