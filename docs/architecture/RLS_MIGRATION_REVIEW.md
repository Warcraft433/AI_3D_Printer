# PrintGuard AI — Production Database Security Migration Review

**Document Version:** 1.0.0  
**Review Date:** 2026-10-14  
**Project:** Enhanced 3D Printer / PrintGuard AI  
**Files Under Review:**
- Migration: `printguard-migration-starter/backend-java/src/main/resources/db/migration/V2__enable_rls_and_least_privilege.sql`
- Rollback: `scripts/rollback_v2_rls.sql`
- Target Environment: PostgreSQL 14+ on Supabase

---

## 1. Executive Summary & Verdict

The proposed database security migration (`V2__enable_rls_and_least_privilege.sql`) is **APPROVED FOR PRODUCTION APPLICATION**.

### Verdict Checklist
- [x] **Zero Destructive Statements:** No `DROP TABLE`, `TRUNCATE`, `ALTER COLUMN`, or column deletions exist.
- [x] **Zero Data Loss:** All existing rows (18 users, 40 models, 31 print jobs, 11 defect logs, 0 audit logs) are 100% preserved.
- [x] **Spring Boot Compatibility:** Spring Boot connects via JDBC as the `postgres` role, which possesses `rolbypassrls = true` and explicit `GRANT ALL`. All existing JPA repository queries, custom JPQL queries, and HikariCP connection pooling will continue to execute with zero degradation.
- [x] **Complete PostgREST Lockdown:** Broad public permissions granted by Supabase's default ACL to `anon` and `authenticated` roles are revoked. PostgREST endpoints at `/rest/v1/*` are completely sealed.
- [x] **Accurate Identity Architecture:** The migration correctly avoids assuming Supabase `auth.uid()`, preventing `bigint = uuid` type crashes and recognizing that user authentication is decoupled into Spring Boot's custom JWT system.
- [x] **Safe & Complete Rollback Script:** `scripts/rollback_v2_rls.sql` restores the exact pre-migration state (disables RLS, restores public grants and default privileges) within an atomic transaction.

---

## 2. Spring Boot Connection, Authentication & Architecture Analysis

### 2.1 Database Connection Role
In `application-supabase.yml` and `.env`:
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME} # postgres.wlpskpsysnamtmzilpft
```
- **Connected Role:** `postgres`
- **PostgreSQL Role Attributes:**
  - `rolsuper`: `false`
  - `rolcanlogin`: `true`
  - `rolbypassrls`: **`true`**
- **Implication:** In PostgreSQL, any role with `rolbypassrls = true` bypasses table-level Row Level Security policies automatically. Therefore, enabling RLS on tables will not intercept, filter, or block queries executed by the Spring Boot backend.

### 2.2 Decoupled Authentication Architecture
PrintGuard AI does not use Supabase GoTrue Auth (`auth.users`):
1. **User Storage:** Users are persisted in the custom table `public.auth_user` with a 64-bit integer identifier (`id BIGSERIAL`).
2. **Password Verification:** Handled inside Spring Boot via `PasswordService.java` using salted PBKDF2 with SHA-256 (`pbkdf2_sha256$260000$...`).
3. **Session Tokens:** Spring Boot issues custom signed JWTs signed with `PRINTGUARD_JWT_SECRET`.
4. **Client Flow:** The frontend communicates exclusively with Spring Boot REST endpoints (`/api/...`). It never queries the Supabase PostgREST API directly.

### 2.3 Why Avoiding `auth.uid()` is Required
In standard Supabase tutorials, policies often use:
```sql
-- DANGEROUS / DEFECTIVE FOR PRINTGUARD AI:
CREATE POLICY "user_policy" ON generated_models_generatedmodel
    FOR ALL TO authenticated USING (user_id = auth.uid());
```
If applied to PrintGuard AI, this would fail for two reasons:
1. **Data Type Mismatch:** `generated_models_generatedmodel.user_id` is a `BIGINT`. Supabase `auth.uid()` returns `UUID`. In PostgreSQL, evaluating `user_id = auth.uid()` aborts queries with `operator does not exist: bigint = uuid`.
2. **Empty Identity Context:** Because users authenticate against Spring Boot rather than Supabase GoTrue, Supabase's JWT claim `sub` is absent when external clients access the PostgREST gateway.
The proposed migration correctly implements a **default-deny boundary** at the database gateway, leaving user isolation to Spring Boot's validated application security tier.

---

## 3. Table-by-Table Security & Permission Audit

### 3.1 Table: `public.auth_user`

#### Applied Statements
```sql
REVOKE ALL ON TABLE public.auth_user FROM anon, authenticated;
REVOKE ALL ON SEQUENCE public.auth_user_id_seq FROM anon, authenticated;
GRANT ALL ON TABLE public.auth_user TO postgres, service_role;
GRANT ALL ON SEQUENCE public.auth_user_id_seq TO postgres, service_role;
ALTER TABLE public.auth_user ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "service_role_manage_auth_user" ON public.auth_user;
CREATE POLICY "service_role_manage_auth_user" ON public.auth_user
    AS PERMISSIVE FOR ALL TO service_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "postgres_manage_auth_user" ON public.auth_user;
CREATE POLICY "postgres_manage_auth_user" ON public.auth_user
    AS PERMISSIVE FOR ALL TO postgres USING (true) WITH CHECK (true);
```

#### Evaluation
- **Spring Boot Access:** **RETAINED (PASS)**. `UserRepository` calls (`findByUsername`, `findByEmail`, `findById`, `save`) execute via `postgres` role with full privileges.
- **Unauthorized Client Protection:** **SECURED (PASS)**. Direct HTTP requests to `/rest/v1/auth_user` with the public Supabase `anon` key or Supabase user tokens are rejected by PostgreSQL with `403 Forbidden` or return an empty array `[]`. Attackers cannot dump usernames, emails, or password hashes.
- **Production Safety:** **SAFE (PASS)**. No columns or constraints modified.
- **Rollback Feasibility:** **SAFE (PASS)**. Restores grants and disables RLS cleanly.

---

### 3.2 Table: `public.generated_models_generatedmodel`

#### Applied Statements
```sql
REVOKE ALL ON TABLE public.generated_models_generatedmodel FROM anon, authenticated;
REVOKE ALL ON SEQUENCE public.generated_models_generatedmodel_id_seq FROM anon, authenticated;
GRANT ALL ON TABLE public.generated_models_generatedmodel TO postgres, service_role;
GRANT ALL ON SEQUENCE public.generated_models_generatedmodel_id_seq TO postgres, service_role;
ALTER TABLE public.generated_models_generatedmodel ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "service_role_manage_generated_models" ON public.generated_models_generatedmodel;
CREATE POLICY "service_role_manage_generated_models" ON public.generated_models_generatedmodel
    AS PERMISSIVE FOR ALL TO service_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "postgres_manage_generated_models" ON public.generated_models_generatedmodel;
CREATE POLICY "postgres_manage_generated_models" ON public.generated_models_generatedmodel
    AS PERMISSIVE FOR ALL TO postgres USING (true) WITH CHECK (true);
```

#### Evaluation
- **Spring Boot Access:** **RETAINED (PASS)**. `GeneratedModelRepository` methods (`findAllByOrderByCreatedAtDesc`, `findByUserIdOrderByCreatedAtDesc`, `countByUserId`, `save`, `delete`) function normally.
- **Unauthorized Client Protection:** **SECURED (PASS)**. External clients cannot scrape proprietary 3D prompts, model paths, or generation metadata via PostgREST. Access is exclusively mediated by Spring Boot's `ModelController`, which enforces `SecurityUtils.verifyOwnershipOrAdmin` (returning HTTP 403 on cross-user attempts).
- **Production Safety:** **SAFE (PASS)**. Foreign key to `auth_user.id` is completely unaffected.

---

### 3.3 Table: `public.print_jobs_printjob`

#### Applied Statements
```sql
REVOKE ALL ON TABLE public.print_jobs_printjob FROM anon, authenticated;
REVOKE ALL ON SEQUENCE public.print_jobs_printjob_id_seq FROM anon, authenticated;
GRANT ALL ON TABLE public.print_jobs_printjob TO postgres, service_role;
GRANT ALL ON SEQUENCE public.print_jobs_printjob_id_seq TO postgres, service_role;
ALTER TABLE public.print_jobs_printjob ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "service_role_manage_print_jobs" ON public.print_jobs_printjob;
CREATE POLICY "service_role_manage_print_jobs" ON public.print_jobs_printjob
    AS PERMISSIVE FOR ALL TO service_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "postgres_manage_print_jobs" ON public.print_jobs_printjob;
CREATE POLICY "postgres_manage_print_jobs" ON public.print_jobs_printjob
    AS PERMISSIVE FOR ALL TO postgres USING (true) WITH CHECK (true);
```

#### Evaluation
- **Spring Boot Access:** **RETAINED (PASS)**. `PrintJobRepository` custom JPQL queries (`SELECT j FROM PrintJobEntity j WHERE j.model.user.id = :userId`) and status updates continue working seamlessly.
- **Unauthorized Client Protection:** **SECURED (PASS)**. G-code file paths, printer IP configurations (`printer_ip`), slicing estimates, and job progress are shielded from unauthorized PostgREST reading or tampering.
- **Production Safety:** **SAFE (PASS)**. Cascade constraints to `generated_models_generatedmodel` remain intact.

---

### 3.4 Table: `public.error_logs_errorlog`

#### Applied Statements
```sql
REVOKE ALL ON TABLE public.error_logs_errorlog FROM anon, authenticated;
REVOKE ALL ON SEQUENCE public.error_logs_errorlog_id_seq FROM anon, authenticated;
GRANT ALL ON TABLE public.error_logs_errorlog TO postgres, service_role;
GRANT ALL ON SEQUENCE public.error_logs_errorlog_id_seq TO postgres, service_role;
ALTER TABLE public.error_logs_errorlog ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "service_role_manage_error_logs" ON public.error_logs_errorlog;
CREATE POLICY "service_role_manage_error_logs" ON public.error_logs_errorlog
    AS PERMISSIVE FOR ALL TO service_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "postgres_manage_error_logs" ON public.error_logs_errorlog;
CREATE POLICY "postgres_manage_error_logs" ON public.error_logs_errorlog
    AS PERMISSIVE FOR ALL TO postgres USING (true) WITH CHECK (true);
```

#### Evaluation
- **Spring Boot Access:** **RETAINED (PASS)**. `ErrorLogRepository` queries for YOLOv8 defect detection monitoring and dashboard analytics operate without restriction.
- **Unauthorized Client Protection:** **SECURED (PASS)**. Prevents public enumeration of AI defect classifications, confidence scores, and webcam frame image references (`frame_image`).
- **Production Safety:** **SAFE (PASS)**. Foreign key to `print_jobs_printjob.id` preserved.

---

### 3.5 Table: `public.print_jobs_auditlog`

#### Applied Statements
```sql
REVOKE ALL ON TABLE public.print_jobs_auditlog FROM anon, authenticated;
REVOKE ALL ON SEQUENCE public.print_jobs_auditlog_id_seq FROM anon, authenticated;
GRANT ALL ON TABLE public.print_jobs_auditlog TO postgres, service_role;
GRANT ALL ON SEQUENCE public.print_jobs_auditlog_id_seq TO postgres, service_role;
ALTER TABLE public.print_jobs_auditlog ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "service_role_manage_audit_logs" ON public.print_jobs_auditlog;
CREATE POLICY "service_role_manage_audit_logs" ON public.print_jobs_auditlog
    AS PERMISSIVE FOR ALL TO service_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "postgres_manage_audit_logs" ON public.print_jobs_auditlog;
CREATE POLICY "postgres_manage_audit_logs" ON public.print_jobs_auditlog
    AS PERMISSIVE FOR ALL TO postgres USING (true) WITH CHECK (true);
```

#### Evaluation
- **Spring Boot Access:** **RETAINED (PASS)**. Full access for `AuditLogRepository`.
- **Unauthorized Client Protection:** **SECURED (PASS)**. Audit history records cannot be modified, deleted, or harvested by unauthorized actors via PostgREST.
- **Production Safety:** **SAFE (PASS)**.

---

## 4. Default Privilege Hardening

In addition to table-specific revokes, the migration alters default privileges for the `public` schema:
```sql
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated;
```
### Why This Matters
By default, Supabase creates new tables with full grants to `anon` and `authenticated`. This clause ensures that any future tables or sequences created by Spring Boot or Flyway will not automatically be exposed to the public internet before explicit RLS policies are attached.

---

## 5. Rollback Plan Evaluation (`scripts/rollback_v2_rls.sql`)

The rollback script performs four sequential operations inside an atomic transaction:

```sql
BEGIN;

-- 1. Drop service_role and postgres policies
DROP POLICY IF EXISTS "service_role_manage_auth_user" ON public.auth_user;
DROP POLICY IF EXISTS "postgres_manage_auth_user" ON public.auth_user;
-- (Repeated for error_logs_errorlog, generated_models_generatedmodel, print_jobs_auditlog, print_jobs_printjob)

-- 2. Disable Row Level Security (RLS) on all 5 target tables
ALTER TABLE public.auth_user DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.error_logs_errorlog DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.generated_models_generatedmodel DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.print_jobs_auditlog DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.print_jobs_printjob DISABLE ROW LEVEL SECURITY;

-- 3. Restore original grants to public / PostgREST roles
GRANT ALL ON TABLE public.auth_user TO anon, authenticated;
-- (Repeated for all 5 tables and their 5 backing sequences)

-- 4. Restore default privileges
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon, authenticated;

COMMIT;
```

### Rollback Verdict
- **Symmetry:** Exactly matches the inverse of `V2__enable_rls_and_least_privilege.sql`.
- **Atomicity:** Wrapped in `BEGIN ... COMMIT`, ensuring no partial rollback states.
- **Data Safety:** Completely non-destructive (drops zero data, alters zero columns).

---

## 6. Pre- and Post-Deployment Testing Checklist

When you choose to apply the migration, execute the following validation sequence:

1. **Pre-Deployment Check:**
   ```powershell
   .\backend\venv\Scripts\python.exe scripts/verify_rls_migration.py
   ```
   *(Verifies current baseline: 5 tables marked UNRESTRICTED, 70 public grants).*

2. **Execute Migration:**
   Paste `printguard-migration-starter/backend-java/src/main/resources/db/migration/V2__enable_rls_and_least_privilege.sql` into the **Supabase Dashboard SQL Editor** and click **Run**.

3. **Post-Deployment Database Verification:**
   ```powershell
   .\backend\venv\Scripts\python.exe scripts/verify_rls_migration.py
   ```
   - All 5 tables should report: `PROTECTED (RLS Enabled)`.
   - Public grants on target tables: `Zero privileges granted to 'anon' or 'authenticated'`.
   - Row counts: Exactly 18, 40, 31, 11, 0 (no data lost).

4. **Verify PostgREST Lockdown (Proof of Security):**
   ```bash
   curl -i -X GET "https://<your-project-ref>.supabase.co/rest/v1/auth_user" \
        -H "apikey: <your-anon-key>" \
        -H "Authorization: Bearer <your-anon-key>"
   ```
   - **Expected Result:** `HTTP 401 Unauthorized` or `HTTP 403 Forbidden` (`permission denied for table auth_user`), or an empty array `[]`.

5. **Verify Spring Boot Application Functionality:**
   - Log in via frontend (`http://localhost:5173`).
   - Generate a 3D model and verify 3D preview.
   - Slice a model and download G-code.
   - Inspect Print Analytics and Defect Logs.
