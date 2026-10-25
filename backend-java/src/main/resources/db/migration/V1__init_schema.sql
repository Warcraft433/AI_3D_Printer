-- Flyway V1: Initial PostgreSQL Schema for PrintGuard AI
-- Compatible with PostgreSQL 14, 15, 16, 17

CREATE TABLE IF NOT EXISTS auth_user (
    id BIGSERIAL PRIMARY KEY,
    password VARCHAR(128) NOT NULL,
    last_login TIMESTAMP WITH TIME ZONE,
    is_superuser BOOLEAN NOT NULL DEFAULT FALSE,
    username VARCHAR(150) NOT NULL UNIQUE,
    first_name VARCHAR(150) NOT NULL DEFAULT '',
    last_name VARCHAR(150) NOT NULL DEFAULT '',
    email VARCHAR(254) NOT NULL DEFAULT '',
    is_staff BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_joined TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS generated_models_generatedmodel (
    id BIGSERIAL PRIMARY KEY,
    prompt TEXT NOT NULL,
    file_path VARCHAR(500) NOT NULL DEFAULT '',
    thumbnail_path VARCHAR(500) NOT NULL DEFAULT '',
    generation_time DOUBLE PRECISION,
    status VARCHAR(20) NOT NULL DEFAULT 'processing',
    is_repaired BOOLEAN NOT NULL DEFAULT FALSE,
    source_type VARCHAR(10) NOT NULL DEFAULT 'text',
    steps INTEGER NOT NULL DEFAULT 32,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NOT NULL REFERENCES auth_user(id) ON DELETE CASCADE,
    error_detail TEXT NOT NULL DEFAULT '',
    celery_task_id VARCHAR(255) NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS print_jobs_printjob (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'queued',
    gcode_path VARCHAR(500) NOT NULL DEFAULT '',
    estimated_time INTEGER,
    progress INTEGER NOT NULL DEFAULT 0,
    start_time TIMESTAMP WITH TIME ZONE,
    end_time TIMESTAMP WITH TIME ZONE,
    printer_ip VARCHAR(39),
    auto_pause_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    error_detail TEXT NOT NULL DEFAULT '',
    celery_task_id VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    model_id BIGINT NOT NULL REFERENCES generated_models_generatedmodel(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS error_logs_errorlog (
    id BIGSERIAL PRIMARY KEY,
    error_type VARCHAR(30) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    action_taken VARCHAR(20) NOT NULL DEFAULT 'alerted',
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    print_job_id BIGINT NOT NULL REFERENCES print_jobs_printjob(id) ON DELETE CASCADE,
    frame_image VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS print_jobs_auditlog (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(255) NOT NULL,
    job_id INTEGER,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NOT NULL REFERENCES auth_user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_generated_models_user_id ON generated_models_generatedmodel(user_id);
CREATE INDEX IF NOT EXISTS idx_print_jobs_model_id ON print_jobs_printjob(model_id);
CREATE INDEX IF NOT EXISTS idx_print_jobs_status ON print_jobs_printjob(status);
CREATE INDEX IF NOT EXISTS idx_error_logs_job_id ON error_logs_errorlog(print_job_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON print_jobs_auditlog(user_id);
