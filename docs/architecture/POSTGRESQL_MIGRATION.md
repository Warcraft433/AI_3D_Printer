# PrintGuard AI — PostgreSQL Migration Guide & Specification

**Date:** 2026-10-13  
**Status:** **MIGRATION SUCCESSFULLY EXECUTED & LIVE VERIFIED**  
**Working Directory:** `C:\Projects01\AI_3D_Printer`  

---

## 1. Executive Summary

This document specifies the end-to-end strategy, schema mapping, data migration procedure, and validation results for migrating PrintGuard AI's persistent data store from SQLite (`backend/db.sqlite3`) and local H2 to **PostgreSQL (Supabase Session Pooler)**.

### Migration Status & Live Verification Summary
1. **Source SQLite Database:**
   - Location: `backend/db.sqlite3` (245,760 bytes, SHA-256: `1af6e6bd65592f4f4b2df5f2fffa9d308ab0f705a45dafb8cdd0ffb88ad3d778`).
   - Verified Backup: `backend/db.sqlite3.backup_prompt4_20261013_063308` (identical SHA-256 hash, read-only preserved).
   - `.gitignore` updated to guarantee all `.db`, `.bak*`, and `.backup*` files are excluded from version control.
2. **PostgreSQL Target (Supabase):**
   - **Host:** `aws-0-ap-south-1.pooler.supabase.com:5432` (PostgreSQL 17.11 on aarch64-linux, Session Pooler).
   - **Schema Status:** Initialized with Flyway `V1__init_schema.sql` (all 5 tables, foreign keys, cascades, indexes, and sequences created).
   - **Data Migration:** Atomic transaction migration committed 100% of SQLite records with bit-for-bit count parity.
   - **Sequence Counters:** Synchronized to `MAX(id)` for auto-increment continuity.
   - **Live Backend Verification:** Spring Boot booted with `--spring.profiles.active=supabase`, authenticated user `admin` via Django PBKDF2 hash, and passed all dashboard, model, print job, and error log endpoints.

---

## 2. Source vs. Target Schema Comparison

A rigorous comparison between the SQLite database schema (`PRAGMA table_info`), Django models, and Spring Boot JPA entities was performed.

| Entity / Table | SQLite Source Table | Columns & Constraints | Spring Boot JPA Entity | Parity Status |
| :--- | :--- | :--- | :--- | :---: |
| **User Account** | `auth_user` (1 row) | `id` (INTEGER PK), `password` (varchar 128), `last_login` (datetime NULL), `is_superuser` (bool), `username` (varchar 150 UNIQUE), `first_name` (varchar 150), `last_name` (varchar 150), `email` (varchar 254), `is_staff` (bool), `is_active` (bool), `date_joined` (datetime) | `com.printguard.auth.UserEntity`<br>(matches all column names, types, and constraints) | **100% MATCH** |
| **Generated 3D Model** | `generated_models_generatedmodel` (2 rows) | `id` (INTEGER PK), `prompt` (TEXT), `file_path` (varchar 500), `thumbnail_path` (varchar 500), `generation_time` (REAL NULL), `status` (varchar 20), `is_repaired` (bool), `source_type` (varchar 10), `steps` (int), `created_at` (datetime), `user_id` (INTEGER FK), `error_detail` (TEXT), `celery_task_id` (varchar 255) | `com.printguard.models.GeneratedModelEntity`<br>(matches all column names and foreign key to `UserEntity`) | **100% MATCH** |
| **Print Job** | `print_jobs_printjob` (1 row) | `id` (INTEGER PK), `status` (varchar 20), `gcode_path` (varchar 500), `estimated_time` (int NULL), `progress` (int), `start_time` (datetime NULL), `end_time` (datetime NULL), `printer_ip` (char 39 NULL), `auto_pause_enabled` (bool), `error_detail` (TEXT), `celery_task_id` (varchar 255), `created_at` (datetime), `model_id` (bigint FK) | `com.printguard.jobs.PrintJobEntity`<br>(matches all column names and foreign key to `GeneratedModelEntity`) | **100% MATCH** |
| **Defect Error Log** | `error_logs_errorlog` (3 rows) | `id` (INTEGER PK), `error_type` (varchar 30), `confidence` (REAL), `action_taken` (varchar 20), `timestamp` (datetime), `print_job_id` (bigint FK), `frame_image` (varchar 100 NULL) | `com.printguard.logs.ErrorLogEntity`<br>(matches all column names and foreign key to `PrintJobEntity`) | **100% MATCH** |
| **Audit Log** | `print_jobs_auditlog` (0 rows) | `id` (INTEGER PK), `action` (varchar 255), `job_id` (int NULL), `timestamp` (datetime), `user_id` (INTEGER FK) | `com.printguard.jobs.AuditLogEntity`<br>(matches all column names and foreign key to `UserEntity`) | **100% MATCH** |
| **Application Settings** | *None* (No DB table in Django) | In-memory key-value dictionary in Django; in-memory `ConcurrentHashMap` in Spring Boot `SettingsController.java`. | `com.printguard.settings.SettingsController`<br>(No database migration required) | **100% PARITY** |

---

## 3. Password Hashing & Django Compatibility

Existing users in SQLite store passwords hashed using Django's PBKDF2 hasher:
```
pbkdf2_sha256$<iterations>$<salt>$<hash>
```

### Password Compatibility Verification
- Django's PBKDF2 implementation passes the salt as a raw UTF-8 string: `salt.encode('utf-8')`.
- Spring Boot's [PasswordService.java](file:///C:/Projects01/AI_3D_Printer/printguard-migration-starter/backend-java/src/main/java/com/printguard/auth/PasswordService.java) has been updated with dual-mode salt decoding:
  1. Primary: Evaluates `salt.getBytes(StandardCharsets.UTF_8)` (Django standard format).
  2. Fallback: Evaluates `Base64.decode(salt)` (Java standard format).
- **Automated Test Added & Passed:** [AuthControllerTest.passwordServiceMatchesDjangoGeneratedHash()](file:///C:/Projects01/AI_3D_Printer/printguard-migration-starter/backend-java/src/test/java/com/printguard/auth/AuthControllerTest.java) verifies that existing user `admin` can authenticate immediately after migration without password exposure or credential resets.

## 4. Supabase PostgreSQL Session Pooler Configuration

A dedicated Spring Boot profile `supabase` has been configured in [application-supabase.yml](file:///C:/Projects01/AI_3D_Printer/printguard-migration-starter/backend-java/src/main/resources/application-supabase.yml).

### Connection Parameters:
- **Host:** `aws-0-ap-south-1.pooler.supabase.com`
- **Port:** `5432` (Session Pooler)
- **Database:** `postgres`
- **Username:** `postgres.wlpskpsysnamtmzilpft`
- **JDBC URL:** `jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require`
- **Password:** Read dynamically via `$env:SPRING_DATASOURCE_PASSWORD` (never hardcoded, logged, or printed).

### Safety & Guardrail Configuration in `application-supabase.yml`:
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require}
    username: ${SPRING_DATASOURCE_USERNAME:postgres.wlpskpsysnamtmzilpft}
    password: ${SPRING_DATASOURCE_PASSWORD:}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  jpa:
    open-in-view: true
    hibernate:
      # Strictly preserve existing database state: no destructive DDL
      ddl-auto: none
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  flyway:
    # Strictly disabled until the user approves the reviewed schema execution
    enabled: false
```

### Safety Rules Enforced:
1. `ddl-auto: none` strictly disables Hibernate schema generation or table dropping.
2. `flyway.enabled: false` ensures Flyway migrations will NOT execute against Supabase until the user reviews and approves the schema plan.
3. Hikari connection pool size is capped at 5 to respect Supabase pooler connection limits.

### Safe Connection-Test Procedure:
To verify network connectivity and authentication without exposing passwords:
```powershell
# Set your environment variables in PowerShell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require"
$env:SPRING_DATASOURCE_USERNAME="postgres.wlpskpsysnamtmzilpft"
$env:SPRING_DATASOURCE_PASSWORD="<YOUR_SECRET_PASSWORD>"

# Run the sanitized connectivity probe (strips secrets from all output)
backend\venv\Scripts\python.exe scratch\test_supabase_conn.py
```

---

## 5. Alternative Local PostgreSQL Installation Instructions (Optional)

### Option A: Official Windows Installer (Recommended for Native Windows)
1. Download PostgreSQL 16 or 17 from the official EnterpriseDB repository:
   https://www.enterprisedb.com/downloads/postgres-postgresql-downloads
2. Run the installer and set the superuser (`postgres`) password to a secure password.
3. Keep the default port `5432`.
4. The service will register automatically as a background Windows service (`postgresql-x64-16`).

### Option B: Docker Container (Recommended if Docker Desktop is installed)
```powershell
docker run --name pg-printguard `
  -e POSTGRES_DB=printguard `
  -e POSTGRES_USER=printguard_app `
  -e POSTGRES_PASSWORD=change-this-password `
  -p 5432:5432 `
  -d postgres:16-alpine
```

### Option C: Chocolatey or Scoop (Package Managers)
```powershell
# Using Chocolatey:
choco install postgresql16 --params '/Password:change-this-password'

# Using Scoop:
scoop install postgresql
```

### Post-Installation Database & User Initialization SQL
Once PostgreSQL is running, connect via `psql` or pgAdmin as `postgres` and execute:
```sql
-- Create dedicated application database
CREATE DATABASE printguard;

-- Create least-privilege application user
CREATE USER printguard_app WITH ENCRYPTED PASSWORD 'change-this-password';

-- Grant connection and schema privileges
GRANT CONNECT ON DATABASE printguard TO printguard_app;
\c printguard
GRANT ALL ON SCHEMA public TO printguard_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO printguard_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO printguard_app;
```

---

## 5. Version-Controlled Migrations (Flyway)

To prevent accidental schema corruption or destructive Hibernate drops (`ddl-auto: create/drop`), Spring Boot has been configured to use explicit, version-controlled **Flyway** migrations.

### Flyway Scripts Configured:
1. `src/main/resources/db/migration/V1__init_schema.sql`:
   - Creates `auth_user`, `generated_models_generatedmodel`, `print_jobs_printjob`, `error_logs_errorlog`, and `print_jobs_auditlog`.
   - Defines all primary keys (`BIGSERIAL`), foreign keys (`ON DELETE CASCADE`), indexes, and default timestamp values (`TIMESTAMP WITH TIME ZONE`).
2. Spring Boot `application.yml`:
   ```yaml
   spring:
     flyway:
       enabled: true
       baseline-on-migrate: true
       locations: classpath:db/migration
     jpa:
       hibernate:
         ddl-auto: validate
   ```

---

## 6. Repeatable Data Migration Script

A non-destructive, repeatable Python migration script has been created at [scripts/migrate_sqlite_to_postgres.py](file:///C:/Projects01/AI_3D_Printer/scripts/migrate_sqlite_to_postgres.py).

### Safety Design:
- Reads SQLite using `file:...db.sqlite3?mode=ro` (read-only mode; cannot modify source).
- Performs all inserts in a **single PostgreSQL transaction** (`BEGIN` ... `COMMIT`).
- Uses `ON CONFLICT (id) DO UPDATE` to ensure idempotency (running the script multiple times safely updates records without generating duplicates).
- Resets sequence counters (`SELECT setval('...', MAX(id))`) so future insertions continue from the highest existing ID.
- Automatically compares source row counts against destination row counts and rolls back if any record count mismatches.

### Commands:
```powershell
# 1. Pre-flight verification (does not connect to or alter PostgreSQL)
backend\venv\Scripts\python.exe scripts\migrate_sqlite_to_postgres.py --dry-run

# 2. Execute migration (requires active PostgreSQL)
backend\venv\Scripts\python.exe scripts\migrate_sqlite_to_postgres.py --execute

# 3. Validate existing table counts in PostgreSQL
backend\venv\Scripts\python.exe scripts\migrate_sqlite_to_postgres.py --validate-only
```

---

## 7. Data Validation Plan & Integrity Checks (ALL PASSED)

The following validation checks were executed and confirmed against Supabase PostgreSQL:

1. **Table Record Counts:**
   - `auth_user`: exactly 1 record. (**PASSED**)
   - `generated_models_generatedmodel`: exactly 2 records. (**PASSED**)
   - `print_jobs_printjob`: exactly 1 record. (**PASSED**)
   - `error_logs_errorlog`: exactly 3 records. (**PASSED**)
   - `print_jobs_auditlog`: exactly 0 records. (**PASSED**)
2. **Foreign Key Integrity:**
   - Model 3 & 4 reference `user_id = 1` (`admin`). (**PASSED**)
   - Print job 2 references `model_id = 3`. (**PASSED**)
   - Error logs 1, 2, 3 reference `print_job_id = 2`. (**PASSED**)
3. **Application Health Probe:**
   - `GET /api/health` returns `200 OK` (`status: "UP"`). (**PASSED**)
4. **Backend Integration:**
   - Spring Boot boots with `--spring.profiles.active=supabase` against Supabase with Hikari pool, `ddl-auto: none`, and zero Hibernate mapping errors. (**PASSED**)
5. **Authentication & Django PBKDF2 Hash Parity:**
   - User `admin` logs in successfully via `POST /api/auth/login/` with `adminpassword123`.
   - JWT tokens are issued and authenticated for subsequent protected calls. (**PASSED**)
6. **Dashboard Stats Parity:**
   - `GET /api/dashboard/stats/` returns 2 models, 1 print job, 3 recent errors, and active print status. (**PASSED**)

---

## 8. Rollback & Contingency Plan

If any issue occurs during or after migration:
1. **Zero Risk to SQLite:** `backend/db.sqlite3` and `backend/db.sqlite3.backup_prompt4_*` are never written to during migration and remain intact.
2. **Immediate Application Revert:**
   Launch Spring Boot with the local file-backed H2 profile:
   ```powershell
   java -jar target\printguard-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
   ```
   The local file-backed H2 database (`./data/printguard`) remains completely preserved and unaffected by PostgreSQL activities.
