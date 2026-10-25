# PrintGuard AI — Database Row Level Security (RLS) & Access Control Audit

**Document Version:** 1.0.0  
**Project:** Enhanced 3D Printer / PrintGuard AI  
**Scope:** Supabase PostgreSQL Schema, Grants, Roles, Spring Boot Authentication & RLS Migration

---

## 1. Executive Summary

Supabase Studio flags tables as **UNRESTRICTED** whenever Row Level Security (RLS) is disabled on a table in the `public` schema. In Supabase, the PostgreSQL database is automatically fronted by PostgREST at `https://<project-ref>.supabase.co/rest/v1`. 

Because PostgreSQL default privileges granted `SELECT, INSERT, UPDATE, DELETE, REFERENCES, TRIGGER, TRUNCATE` on all tables in schema `public` to the `anon` and `authenticated` roles, any client with the project URL and the public `anon` key could bypass the Spring Boot application layer entirely and query the Supabase REST API directly.

This audit assesses the five tables currently marked `UNRESTRICTED`:
1. `public.auth_user`
2. `public.error_logs_errorlog`
3. `public.generated_models_generatedmodel`
4. `public.print_jobs_auditlog`
5. `public.print_jobs_printjob`

This document outlines the schema, foreign keys, grants, roles, and Spring Boot query patterns, and provides a safe SQL migration (`V2__enable_rls_and_least_privilege.sql`) along with a full rollback plan and verification test suite.

---

## 2. Live Database Audit & Current State Analysis

A live diagnostic audit of the Supabase PostgreSQL database was conducted via script `scripts/verify_rls_migration.py`.

### 2.1 Table Row Counts & Preservation Target
Existing data in production:
- `auth_user`: **18 rows** (Active user accounts and salted PBKDF2 password hashes)
- `generated_models_generatedmodel`: **40 rows** (3D CAD models, prompts, file paths)
- `print_jobs_printjob`: **31 rows** (Slicing and printing jobs, G-code paths, statuses)
- `error_logs_errorlog`: **11 rows** (YOLOv8 defect detection records and frame references)
- `print_jobs_auditlog`: **0 rows** (Job audit logs)

*Constraint Check:* The proposed migration contains zero destructive DDL (`DROP`, `TRUNCATE`, `ALTER COLUMN`). All existing records are 100% preserved.

### 2.2 Table Schemas & Foreign Keys

```
+---------------------------------------------------------------------------------------------------+
| Table: auth_user                                                                                  |
+---------------------------------------------------------------------------------------------------+
| id           | BIGSERIAL (PK)                                                                     |
| username     | VARCHAR(150) UNIQUE NOT NULL                                                       |
| password     | VARCHAR(128) NOT NULL (Salted PBKDF2 with SHA-256)                                 |
| email        | VARCHAR(254) NOT NULL DEFAULT ''                                                  |
| first_name   | VARCHAR(150) NOT NULL DEFAULT ''                                                  |
| last_name    | VARCHAR(150) NOT NULL DEFAULT ''                                                  |
| is_active    | BOOLEAN NOT NULL DEFAULT TRUE                                                      |
| is_staff     | BOOLEAN NOT NULL DEFAULT FALSE                                                     |
| is_superuser | BOOLEAN NOT NULL DEFAULT FALSE                                                     |
| date_joined  | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP                                     |
| last_login   | TIMESTAMPTZ NULL                                                                   |
+---------------------------------------------------------------------------------------------------+
       |
       | 1-to-many (ON DELETE CASCADE)
       v
+---------------------------------------------------------------------------------------------------+
| Table: generated_models_generatedmodel                                                            |
+---------------------------------------------------------------------------------------------------+
| id              | BIGSERIAL (PK)                                                                  |
| prompt          | TEXT NOT NULL                                                                   |
| file_path       | VARCHAR(500) NOT NULL                                                           |
| thumbnail_path  | VARCHAR(500) NOT NULL                                                           |
| status          | VARCHAR(20) NOT NULL DEFAULT 'processing'                                       |
| source_type     | VARCHAR(10) NOT NULL DEFAULT 'text'                                             |
| steps           | INTEGER NOT NULL DEFAULT 32                                                     |
| generation_time | DOUBLE PRECISION NULL                                                           |
| is_repaired     | BOOLEAN NOT NULL DEFAULT FALSE                                                  |
| created_at      | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP                                  |
| user_id         | BIGINT NOT NULL (FK -> auth_user.id ON DELETE CASCADE)                          |
| error_detail    | TEXT NOT NULL DEFAULT ''                                                        |
| celery_task_id  | VARCHAR(255) NOT NULL DEFAULT ''                                                |
+---------------------------------------------------------------------------------------------------+
       |
       | 1-to-many (ON DELETE CASCADE)
       v
+---------------------------------------------------------------------------------------------------+
| Table: print_jobs_printjob                                                                        |
+---------------------------------------------------------------------------------------------------+
| id                 | BIGSERIAL (PK)                                                               |
| status             | VARCHAR(20) NOT NULL DEFAULT 'queued'                                        |
| gcode_path         | VARCHAR(500) NOT NULL                                                        |
| estimated_time     | INTEGER NULL                                                                 |
| progress           | INTEGER NOT NULL DEFAULT 0                                                   |
| start_time         | TIMESTAMPTZ NULL                                                             |
| end_time           | TIMESTAMPTZ NULL                                                             |
| printer_ip         | VARCHAR(39) NULL                                                             |
| auto_pause_enabled | BOOLEAN NOT NULL DEFAULT TRUE                                                |
| error_detail       | TEXT NOT NULL DEFAULT ''                                                     |
| celery_task_id     | VARCHAR(255) NOT NULL DEFAULT ''                                             |
| created_at         | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP                               |
| model_id           | BIGINT NOT NULL (FK -> generated_models_generatedmodel.id ON DELETE CASCADE)  |
+---------------------------------------------------------------------------------------------------+
       |
       | 1-to-many (ON DELETE CASCADE)
       v
+---------------------------------------------------------------------------------------------------+
| Table: error_logs_errorlog                                                                        |
+---------------------------------------------------------------------------------------------------+
| id           | BIGSERIAL (PK)                                                                     |
| error_type   | VARCHAR(30) NOT NULL                                                               |
| confidence   | DOUBLE PRECISION NOT NULL                                                          |
| action_taken | VARCHAR(20) NOT NULL DEFAULT 'alerted'                                             |
| timestamp    | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP                                     |
| print_job_id | BIGINT NOT NULL (FK -> print_jobs_printjob.id ON DELETE CASCADE)                   |
| frame_image  | VARCHAR(100) NULL                                                                  |
+---------------------------------------------------------------------------------------------------+

Also referencing auth_user:
+---------------------------------------------------------------------------------------------------+
| Table: print_jobs_auditlog                                                                        |
+---------------------------------------------------------------------------------------------------+
| id        | BIGSERIAL (PK)                                                                        |
| action    | VARCHAR(255) NOT NULL                                                                 |
| job_id    | INTEGER NULL                                                                          |
| timestamp | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP                                        |
| user_id   | BIGINT NOT NULL (FK -> auth_user.id ON DELETE CASCADE)                                |
+---------------------------------------------------------------------------------------------------+
```

### 2.3 Current Grant & Security Status
- **RLS Enabled (`relrowsecurity`):** `false` for all 5 tables.
- **RLS Enforced (`relforcerowsecurity`):** `false` for all 5 tables.
- **Active Policies in `pg_policies`:** `0` policies.
- **Public Grants:** Exactly **70 public grants** currently exist (7 privileges each: `SELECT, INSERT, UPDATE, DELETE, REFERENCES, TRIGGER, TRUNCATE` assigned to `anon` and `authenticated` across all 5 tables).

### 2.4 Database Roles & Bypass Capabilities
| Role Name | Can Login | Superuser | Bypass RLS | Purpose |
| :--- | :---: | :---: | :---: | :--- |
| `postgres` | **Yes** | No | **TRUE** | Used by Spring Boot backend via JDBC connection pool. |
| `service_role` | No | No | **TRUE** | Internal Supabase admin / server-side client role. |
| `anon` | No | No | **FALSE** | Assumed by PostgREST when called with anon key / unauthenticated. |
| `authenticated` | No | No | **FALSE** | Assumed by PostgREST when called with Supabase GoTrue JWT. |
| `authenticator` | **Yes** | No | **FALSE** | PostgREST HTTP proxy login user. |

---

## 3. Spring Boot Authentication & Architecture Analysis

### 3.1 Why Supabase `auth.uid()` Cannot Be Used
A common mistake in Supabase projects is writing RLS policies such as:
```sql
-- DANGEROUS / NON-FUNCTIONAL FOR THIS PROJECT:
CREATE POLICY "user_isolation" ON generated_models_generatedmodel 
    FOR ALL TO authenticated USING (user_id = auth.uid());
```
In PrintGuard AI, this approach fails fundamentally for two reasons:
1. **Identifier Type Incompatibility:**
   - PrintGuard AI user IDs are 64-bit auto-incrementing integers (`BIGINT` / Java `Long`).
   - Supabase `auth.uid()` returns a `UUID` referencing the internal Supabase GoTrue table `auth.users`.
   - Evaluating `user_id = auth.uid()` causes a PostgreSQL type error: `operator does not exist: bigint = uuid`.
2. **Independent Identity Provider:**
   - PrintGuard AI maintains its own custom user table (`auth_user`) with PBKDF2 password hashing.
   - User authentication issues custom signed JSON Web Tokens (`PRINTGUARD_JWT_SECRET`) issued by Spring Boot's `JwtService`.
   - The React frontend speaks exclusively to the Spring Boot REST API (`/api/auth/*`, `/api/models/*`, `/api/jobs/*`, `/api/dashboard/*`).
   - Supabase GoTrue Auth is not part of the active user login flow.

### 3.2 Where Security & Ownership Are Enforced
Ownership and authorization are actively enforced in the Spring Boot application tier:
- **`JwtAuthenticationFilter`**: Validates the Bearer token signature, expiration, and extracts the user principal.
- **`SecurityUtils.verifyOwnershipOrAdmin(owner, action)`**: Ensures regular users can only read, slice, modify, or delete their own models and jobs; admins (`isStaff` or `isSuperuser`) can inspect system-wide resources.
- **Scoped Repository Queries**:
  - `GeneratedModelRepository.findByUserIdOrderByCreatedAtDesc(userId)`
  - `PrintJobRepository.findByUserIdOrderByCreatedAtDesc(userId)`
  - `ErrorLogRepository.findByUserIdOrderByTimestampDesc(userId)`
- **Credential Protection**:
  - Password hashes are stored with SHA-256 PBKDF2 (`pbkdf2_sha256$260000$...`).
  - Response DTOs (`AuthResponse`, `ModelResponse`, `PrintJobResponse`, `ErrorLogResponse`) never serialize password fields.

### 3.3 Impact on Backend Spring Boot Operations
Because Spring Boot connects to Supabase PostgreSQL using the `postgres` role (which has `rolbypassrls = true`), enabling RLS and locking down PostgREST has **zero adverse impact** on Spring Boot:
- All JPA queries (`findById`, `save`, `findByUserIdOrderByCreatedAtDesc`, etc.) execute normally.
- HikariCP connection pooling functions without alteration.
- Backend performance and query execution plans remain identical.

---

## 4. Proposed SQL Migration (`V2__enable_rls_and_least_privilege.sql`)

The migration is stored in:
`printguard-migration-starter/backend-java/src/main/resources/db/migration/V2__enable_rls_and_least_privilege.sql`

```sql
-- Flyway V2 / Supabase Migration: Enable RLS and Least-Privilege Access Controls
-- Target Tables: auth_user, error_logs_errorlog, generated_models_generatedmodel, print_jobs_auditlog, print_jobs_printjob
-- Database: PostgreSQL 14+ (Supabase)

BEGIN;

-- ==============================================================================
-- 1. REVOKE PUBLIC / POSTGREST PERMISSIONS FROM UNTRUSTED ROLES
-- ==============================================================================

-- Revoke all table-level privileges from 'anon' and 'authenticated' on the 5 target tables
REVOKE ALL ON TABLE public.auth_user FROM anon, authenticated;
REVOKE ALL ON TABLE public.error_logs_errorlog FROM anon, authenticated;
REVOKE ALL ON TABLE public.generated_models_generatedmodel FROM anon, authenticated;
REVOKE ALL ON TABLE public.print_jobs_auditlog FROM anon, authenticated;
REVOKE ALL ON TABLE public.print_jobs_printjob FROM anon, authenticated;

-- Revoke sequence usage from 'anon' and 'authenticated' to prevent unauthorized ID enumeration
REVOKE ALL ON SEQUENCE public.auth_user_id_seq FROM anon, authenticated;
REVOKE ALL ON SEQUENCE public.error_logs_errorlog_id_seq FROM anon, authenticated;
REVOKE ALL ON SEQUENCE public.generated_models_generatedmodel_id_seq FROM anon, authenticated;
REVOKE ALL ON SEQUENCE public.print_jobs_auditlog_id_seq FROM anon, authenticated;
REVOKE ALL ON SEQUENCE public.print_jobs_printjob_id_seq FROM anon, authenticated;

-- Ensure future tables and sequences created in schema public do not automatically grant permissions to anon/authenticated
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated;


-- ==============================================================================
-- 2. GRANT EXPLICIT LEAST-PRIVILEGE PERMISSIONS TO TRUSTED OPERATIONAL ROLES
-- ==============================================================================

-- Ensure 'postgres' (Spring Boot backend JDBC connection) and 'service_role' retain full operational access
GRANT ALL ON TABLE public.auth_user TO postgres, service_role;
GRANT ALL ON TABLE public.error_logs_errorlog TO postgres, service_role;
GRANT ALL ON TABLE public.generated_models_generatedmodel TO postgres, service_role;
GRANT ALL ON TABLE public.print_jobs_auditlog TO postgres, service_role;
GRANT ALL ON TABLE public.print_jobs_printjob TO postgres, service_role;

GRANT ALL ON SEQUENCE public.auth_user_id_seq TO postgres, service_role;
GRANT ALL ON SEQUENCE public.error_logs_errorlog_id_seq TO postgres, service_role;
GRANT ALL ON SEQUENCE public.generated_models_generatedmodel_id_seq TO postgres, service_role;
GRANT ALL ON SEQUENCE public.print_jobs_auditlog_id_seq TO postgres, service_role;
GRANT ALL ON SEQUENCE public.print_jobs_printjob_id_seq TO postgres, service_role;


-- ==============================================================================
-- 3. ENABLE ROW LEVEL SECURITY (RLS) ON ALL TARGET TABLES
-- ==============================================================================

ALTER TABLE public.auth_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.error_logs_errorlog ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.generated_models_generatedmodel ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.print_jobs_auditlog ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.print_jobs_printjob ENABLE ROW LEVEL SECURITY;


-- ==============================================================================
-- 4. CONFIGURE DEFENSIVE ROW LEVEL SECURITY POLICIES
-- ==============================================================================
-- Under PostgreSQL RLS:
-- Any role without BYPASSRLS (e.g. anon, authenticated) is subjected to RLS evaluation.
-- With no permissive policies defined for anon/authenticated, PostgreSQL defaults to DENY ALL.
-- We explicitly define policies for 'service_role' and 'postgres' so that administrative
-- interfaces (such as the Supabase Studio dashboard and storage webhooks) can manage data.

-- 4.1 Table: auth_user
DROP POLICY IF EXISTS "service_role_manage_auth_user" ON public.auth_user;
CREATE POLICY "service_role_manage_auth_user" ON public.auth_user
    AS PERMISSIVE FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "postgres_manage_auth_user" ON public.auth_user;
CREATE POLICY "postgres_manage_auth_user" ON public.auth_user
    AS PERMISSIVE FOR ALL
    TO postgres
    USING (true)
    WITH CHECK (true);

-- 4.2 Table: error_logs_errorlog
DROP POLICY IF EXISTS "service_role_manage_error_logs" ON public.error_logs_errorlog;
CREATE POLICY "service_role_manage_error_logs" ON public.error_logs_errorlog
    AS PERMISSIVE FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "postgres_manage_error_logs" ON public.error_logs_errorlog;
CREATE POLICY "postgres_manage_error_logs" ON public.error_logs_errorlog
    AS PERMISSIVE FOR ALL
    TO postgres
    USING (true)
    WITH CHECK (true);

-- 4.3 Table: generated_models_generatedmodel
DROP POLICY IF EXISTS "service_role_manage_generated_models" ON public.generated_models_generatedmodel;
CREATE POLICY "service_role_manage_generated_models" ON public.generated_models_generatedmodel
    AS PERMISSIVE FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "postgres_manage_generated_models" ON public.generated_models_generatedmodel;
CREATE POLICY "postgres_manage_generated_models" ON public.generated_models_generatedmodel
    AS PERMISSIVE FOR ALL
    TO postgres
    USING (true)
    WITH CHECK (true);

-- 4.4 Table: print_jobs_auditlog
DROP POLICY IF EXISTS "service_role_manage_audit_logs" ON public.print_jobs_auditlog;
CREATE POLICY "service_role_manage_audit_logs" ON public.print_jobs_auditlog
    AS PERMISSIVE FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "postgres_manage_audit_logs" ON public.print_jobs_auditlog;
CREATE POLICY "postgres_manage_audit_logs" ON public.print_jobs_auditlog
    AS PERMISSIVE FOR ALL
    TO postgres
    USING (true)
    WITH CHECK (true);

-- 4.5 Table: print_jobs_printjob
DROP POLICY IF EXISTS "service_role_manage_print_jobs" ON public.print_jobs_printjob;
CREATE POLICY "service_role_manage_print_jobs" ON public.print_jobs_printjob
    AS PERMISSIVE FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "postgres_manage_print_jobs" ON public.print_jobs_printjob;
CREATE POLICY "postgres_manage_print_jobs" ON public.print_jobs_printjob
    AS PERMISSIVE FOR ALL
    TO postgres
    USING (true)
    WITH CHECK (true);

COMMIT;
```

---

## 5. Rollback Plan (`scripts/rollback_v2_rls.sql`)

If it is ever necessary to revert the migration, execute the rollback script located at `scripts/rollback_v2_rls.sql`:

```sql
-- PrintGuard AI — Rollback Script for V2 RLS Migration
BEGIN;

-- 1. Drop service_role and postgres policies
DROP POLICY IF EXISTS "service_role_manage_auth_user" ON public.auth_user;
DROP POLICY IF EXISTS "postgres_manage_auth_user" ON public.auth_user;

DROP POLICY IF EXISTS "service_role_manage_error_logs" ON public.error_logs_errorlog;
DROP POLICY IF EXISTS "postgres_manage_error_logs" ON public.error_logs_errorlog;

DROP POLICY IF EXISTS "service_role_manage_generated_models" ON public.generated_models_generatedmodel;
DROP POLICY IF EXISTS "postgres_manage_generated_models" ON public.generated_models_generatedmodel;

DROP POLICY IF EXISTS "service_role_manage_audit_logs" ON public.print_jobs_auditlog;
DROP POLICY IF EXISTS "postgres_manage_audit_logs" ON public.print_jobs_auditlog;

DROP POLICY IF EXISTS "service_role_manage_print_jobs" ON public.print_jobs_printjob;
DROP POLICY IF EXISTS "postgres_manage_print_jobs" ON public.print_jobs_printjob;

-- 2. Disable Row Level Security (RLS) on all 5 target tables
ALTER TABLE public.auth_user DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.error_logs_errorlog DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.generated_models_generatedmodel DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.print_jobs_auditlog DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.print_jobs_printjob DISABLE ROW LEVEL SECURITY;

-- 3. Restore original grants to anon and authenticated
GRANT ALL ON TABLE public.auth_user TO anon, authenticated;
GRANT ALL ON TABLE public.error_logs_errorlog TO anon, authenticated;
GRANT ALL ON TABLE public.generated_models_generatedmodel TO anon, authenticated;
GRANT ALL ON TABLE public.print_jobs_auditlog TO anon, authenticated;
GRANT ALL ON TABLE public.print_jobs_printjob TO anon, authenticated;

GRANT ALL ON SEQUENCE public.auth_user_id_seq TO anon, authenticated;
GRANT ALL ON SEQUENCE public.error_logs_errorlog_id_seq TO anon, authenticated;
GRANT ALL ON SEQUENCE public.generated_models_generatedmodel_id_seq TO anon, authenticated;
GRANT ALL ON SEQUENCE public.print_jobs_auditlog_id_seq TO anon, authenticated;
GRANT ALL ON SEQUENCE public.print_jobs_printjob_id_seq TO anon, authenticated;

-- 4. Restore default privileges
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon, authenticated;

COMMIT;
```

---

## 6. Pre- and Post-Deployment Testing Matrix

### Phase 1: Pre-Deployment Verification (Completed)
- [x] Run `python scripts/verify_rls_migration.py` to record baseline row counts and confirm current grants.
- [x] Verify that `postgres` user possesses `rolbypassrls = true`.
- [x] Verify that Spring Boot application connects via JDBC without using Supabase GoTrue Auth tokens.
- [x] Verify that no foreign keys or constraints are violated.

### Phase 2: Deployment Procedure
When ready to apply the migration, choose one of the following methods:
1. **Method A (Supabase Studio Web UI — Recommended):**
   - Open Supabase Project Dashboard -> **SQL Editor**.
   - Copy the contents of `printguard-migration-starter/backend-java/src/main/resources/db/migration/V2__enable_rls_and_least_privilege.sql`.
   - Click **Run**.
2. **Method B (Dedicated Python Script):**
   - Execute:
     ```powershell
     .\backend\venv\Scripts\python.exe scripts/apply_rls_migration.py
     ```

### Phase 3: Post-Deployment Verification
1. **Verification Script:**
   - Execute `python scripts/verify_rls_migration.py`.
   - Expected Output:
     - All 5 tables show: `PROTECTED (RLS Enabled)`.
     - Active policies: 10 policies (2 per table for `service_role` and `postgres`).
     - Grants for `anon`/`authenticated`: `Zero privileges granted`.
     - Active row counts: Exactly equal to pre-migration baseline (18, 11, 40, 0, 31).
2. **PostgREST Direct Access Test (Verifying the Fix):**
   - Execute a direct curl or HTTP GET request using the Supabase Project URL and anon key:
     ```bash
     curl -i -X GET "https://<your-project-ref>.supabase.co/rest/v1/auth_user" \
          -H "apikey: <your-anon-key>" \
          -H "Authorization: Bearer <your-anon-key>"
     ```
   - **Expected Result:** HTTP 401 Unauthorized or HTTP 403 Forbidden with `{ "message": "permission denied for table auth_user" }`, or an empty JSON list `[]`. Under no circumstance can password hashes or user details be returned.
3. **Application End-to-End Regression Test:**
   - **User Login:** Authenticate with an existing user; verify token issuance.
   - **Model Generation:** Submit a procedural CAD prompt; verify model record creation and 3D preview.
   - **Model Slicing:** Trigger PrusaSlicer conversion; verify print job creation and G-code link.
   - **Dashboard Analytics:** Inspect print analytics chart; verify user metrics load with strictly user-isolated data.
   - **Defect Detection Logs:** Verify error logs load for the authenticated user's jobs.
