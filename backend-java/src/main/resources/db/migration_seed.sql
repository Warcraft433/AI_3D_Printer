-- PrintGuard AI: PostgreSQL Migration Seed Data
-- Generated from SQLite backend/db.sqlite3
-- Preserves IDs, Foreign Keys, and Timestamps

BEGIN;

INSERT INTO auth_user (id, password, last_login, is_superuser, username, last_name, email, is_staff, is_active, date_joined, first_name) VALUES (1, 'pbkdf2_sha256$1000000$HKtmNoYW8YYBe2AbgSgG5a$dMHNLbkHKeiy5S7UPagYV29yftb9Qz1byLSA4VP4V+c=', NULL, TRUE, 'admin', '', 'admin@ai3dprinter.local', TRUE, TRUE, '2026-08-22 13:01:27.099086', '') ON CONFLICT (id) DO NOTHING;
INSERT INTO generated_models_generatedmodel (id, prompt, file_path, thumbnail_path, generation_time, status, is_repaired, source_type, steps, created_at, user_id, error_detail, celery_task_id) VALUES (3, '3D Printer Simulation — Spaghetti Error Demo', '', '', 0.0, 'ready', FALSE, 'text', 32, '2026-09-10 05:03:57.765529', 1, '', '') ON CONFLICT (id) DO NOTHING;
INSERT INTO generated_models_generatedmodel (id, prompt, file_path, thumbnail_path, generation_time, status, is_repaired, source_type, steps, created_at, user_id, error_detail, celery_task_id) VALUES (4, 'cup', '', '', NULL, 'failed', FALSE, 'text', 32, '2026-09-10 05:12:35.036601', 1, 'Traceback (most recent call last):
  File "C:\Users\warca\AppData\Local\Temp\shap_e_gen_hijzr6fo.py", line 3, in <module>
    import torch
ModuleNotFoundError: No module named ''torch''', '') ON CONFLICT (id) DO NOTHING;
INSERT INTO print_jobs_printjob (id, status, gcode_path, estimated_time, progress, start_time, end_time, printer_ip, created_at, model_id, celery_task_id, auto_pause_enabled, error_detail) VALUES (2, 'ready_to_print', 'demo/demo_print.gcode', 600, 0, NULL, NULL, NULL, '2026-09-10 05:03:57.794196', 3, '', TRUE, '') ON CONFLICT (id) DO NOTHING;
INSERT INTO error_logs_errorlog (id, error_type, confidence, action_taken, timestamp, print_job_id, frame_image) VALUES (1, 'spaghetti', 0.927, 'alerted', '2026-09-10 05:17:37.740298', 2, 'detections/defect_1.jpg') ON CONFLICT (id) DO NOTHING;
INSERT INTO error_logs_errorlog (id, error_type, confidence, action_taken, timestamp, print_job_id, frame_image) VALUES (2, 'spaghetti', 0.952, 'alerted', '2026-09-10 05:17:38.950355', 2, 'detections/defect_2.jpg') ON CONFLICT (id) DO NOTHING;
INSERT INTO error_logs_errorlog (id, error_type, confidence, action_taken, timestamp, print_job_id, frame_image) VALUES (3, 'spaghetti', 0.848, 'alerted', '2026-09-10 05:18:00.586146', 2, 'detections/defect_3.jpg') ON CONFLICT (id) DO NOTHING;

-- Reset PostgreSQL Sequences
SELECT setval('auth_user_id_seq', COALESCE((SELECT MAX(id) FROM auth_user), 1));
SELECT setval('generated_models_generatedmodel_id_seq', COALESCE((SELECT MAX(id) FROM generated_models_generatedmodel), 1));
SELECT setval('print_jobs_printjob_id_seq', COALESCE((SELECT MAX(id) FROM print_jobs_printjob), 1));
SELECT setval('error_logs_errorlog_id_seq', COALESCE((SELECT MAX(id) FROM error_logs_errorlog), 1));
SELECT setval('print_jobs_auditlog_id_seq', COALESCE((SELECT MAX(id) FROM print_jobs_auditlog), 1));

COMMIT;
