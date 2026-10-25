-- Flyway V2 / Supabase Migration: Enable RLS and Least-Privilege Access Controls
-- Target Tables: auth_user, error_logs_errorlog, generated_models_generatedmodel, print_jobs_auditlog, print_jobs_printjob
-- Database: PostgreSQL 14+ (Supabase)
--
-- Objective:
-- 1. Enable Row Level Security (RLS) on all 5 sensitive tables currently marked UNRESTRICTED.
-- 2. Revoke broad public PostgREST permissions from untrusted roles ('anon', 'authenticated').
-- 3. Enforce least-privilege access: permit full operational access only to trusted roles ('postgres', 'service_role').
-- 4. Preserve existing data and Spring Boot backend JDBC connectivity without downtime or breaking changes.
--
-- Architecture Note:
-- Spring Boot authenticates users with custom PBKDF2 passwords and signs custom JWTs (PRINTGUARD_JWT_SECRET).
-- Spring Boot user IDs are BIGINT, not Supabase GoTrue UUIDs.
-- The Spring Boot backend connects via JDBC as the 'postgres' role (which possesses rolbypassrls = true).
-- Therefore, all application queries and ownership checks continue to be enforced securely at the Spring Boot layer.
-- This migration seals the Supabase PostgREST HTTP layer to prevent unauthorized data dumping or password hash extraction.

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
