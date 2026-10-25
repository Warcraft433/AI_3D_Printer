-- PrintGuard AI — Safe Rollback Script for V2 RLS Migration (CORRECTED LEAST-PRIVILEGE VERSION)
-- Objective: Safely reverts RLS policies and table RLS enforcement WITHOUT re-exposing tables to public PostgREST roles.
--
-- Security Rationale:
-- The original pre-review rollback script executed 'GRANT ALL ... TO anon, authenticated', which re-opened a critical
-- security vulnerability (allowing unauthenticated public actors to dump password hashes and wipe user data via PostgREST).
-- The Spring Boot application exclusively connects as the 'postgres' database role. It does NOT use 'anon' or 'authenticated'.
-- Therefore, this corrected rollback safely disables RLS while maintaining the least-privilege principle (keeping untrusted roles blocked).
--
-- Target Tables: auth_user, error_logs_errorlog, generated_models_generatedmodel, print_jobs_auditlog, print_jobs_printjob
-- Database: PostgreSQL 14+ (Supabase)

BEGIN;

-- ==============================================================================
-- 1. DROP V2 RLS POLICIES
-- ==============================================================================

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


-- ==============================================================================
-- 2. DISABLE ROW LEVEL SECURITY (RLS) ON ALL 5 TARGET TABLES
-- ==============================================================================

ALTER TABLE public.auth_user DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.error_logs_errorlog DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.generated_models_generatedmodel DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.print_jobs_auditlog DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.print_jobs_printjob DISABLE ROW LEVEL SECURITY;


-- ==============================================================================
-- 3. ENSURE TRUSTED OPERATIONAL ROLES RETAIN FULL PERMISSIONS
-- ==============================================================================

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
-- 4. UNTRUSTED ROLES ('anon', 'authenticated') REMAIN DENIED (LEAST PRIVILEGE)
-- ==============================================================================
-- Note: We intentionally do NOT grant permissions to 'anon' or 'authenticated'.
-- Spring Boot connects as 'postgres' and does not need public role grants.
-- Keeping 'anon' and 'authenticated' denied protects auth_user password hashes from PostgREST scraping.
--
-- (If full legacy unrestricted access is strictly required for historical testing, uncomment below):
-- GRANT ALL ON TABLE public.auth_user TO anon, authenticated;
-- GRANT ALL ON TABLE public.error_logs_errorlog TO anon, authenticated;
-- GRANT ALL ON TABLE public.generated_models_generatedmodel TO anon, authenticated;
-- GRANT ALL ON TABLE public.print_jobs_auditlog TO anon, authenticated;
-- GRANT ALL ON TABLE public.print_jobs_printjob TO anon, authenticated;

COMMIT;
