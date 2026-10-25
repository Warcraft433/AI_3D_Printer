-- PrintGuard AI — Rollback Script for V2 RLS Migration (ORIGINAL PRE-REVIEW VERSION)
-- WARNING: This original version restores broad 'GRANT ALL' privileges to untrusted roles ('anon', 'authenticated').
-- It is preserved here strictly for historical audit reference. 
-- For production rollback, use the corrected 'scripts/rollback_v2_rls.sql' instead.
-- Target Tables: auth_user, error_logs_errorlog, generated_models_generatedmodel, print_jobs_auditlog, print_jobs_printjob
-- Database: PostgreSQL 14+ (Supabase)

BEGIN;

-- ==============================================================================
-- 1. DROP RLS POLICIES
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
-- 3. RESTORE ORIGINAL GRANTS TO PUBLIC / POSTGREST ROLES (SECURITY WARNING: INSECURE)
-- ==============================================================================

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


-- ==============================================================================
-- 4. RESTORE DEFAULT PRIVILEGES
-- ==============================================================================

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon, authenticated;

COMMIT;
