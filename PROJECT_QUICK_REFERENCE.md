# PrintGuard AI - Project Quick Reference Guide

> **Permanent Knowledge Base & Rapid Onboarding Cheat Sheet**
> **Document Version:** 2.4.0 (Production Verified)
> **Target Audience:** AI Coding Agents, Software Architects, and Core Maintainers

---

## 1. System Architecture at a Glance

PrintGuard AI unifies CAD modeling, slicing, hardware control, and defect monitoring into a single web application:

```
[ Browser / React 18 SPA (Port 5173) ]
       │                │
       │ (Web Serial)   │ (HTTPS / WSS)
       ▼                ▼
[ 3D Printer HW ]   [ Spring Boot 3 Backend (Port 8080) ]
                            │                  │
               (Internal)   │                  │ (JDBC / RLS)
                            ▼                  ▼
             [ FastAPI AI Service (8001) ]   [ PostgreSQL / Supabase ]
             (YOLOv8 + CAD Generator)        (Flyway V1, V2 Migrations)
```

---

## 2. Ports & Service Directory

| Service | Technology | Port | Entrypoint | Key Configuration |
| :--- | :--- | :--- | :--- | :--- |
| **Backend API** | Spring Boot 3.3 / Java 21 | `8080` | `PrintGuardApplication.java` | `application.yml` |
| **AI Inference** | FastAPI / Python 3.11 | `8001` | `ai-service/app.py` | `requirements.txt` |
| **Frontend UI** | React 18 / Vite / Three.js | `5173` | `frontend/src/main.jsx` | `vite.config.js` |
| **PostgreSQL** | Postgres 15+ / Supabase | `5432` | `db/migration/V1__init_schema.sql` | `application.yml` |

---

## 3. Quick Startup Commands

```bash
# 1. Start PostgreSQL (if running Docker)
docker run --name printguard-pg -e POSTGRES_DB=printguard -e POSTGRES_USER=printguard_app -e POSTGRES_PASSWORD=change-this-password -p 5432:5432 -d postgres:16

# 2. Start Spring Boot Backend (Port 8080)
cd backend-java
./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

# 3. Start AI Microservice (Port 8001)
python -m uvicorn ai-service.app:app --host 127.0.0.1 --port 8001 --reload

# 4. Start Frontend (Port 5173)
cd frontend
npm run dev
```

---

## 4. Key REST Endpoints

- `POST /api/auth/login`: Authenticate user and receive JWT bearer token.
- `POST /api/generate/text`: Generate procedural 3D model OBJ mesh from text prompt.
- `GET /api/models`: List all generated 3D models for authenticated user.
- `POST /api/jobs/create`: Submit model to headless PrusaSlicer to create G-code print job.
- `POST /api/inference/detect-frame`: Upload camera JPEG to FastAPI YOLOv8 for defect classification.
- `POST /api/jobs/{id}/pause`: Emergency auto-pause printer when defect is confirmed.
- `GET /actuator/health`: Spring Boot health probe.

---

## 5. Database Tables & Entity Relationships

- `auth_user`: User credentials (Argon2 hashes), email, superuser status.
- `generated_models_generatedmodel`: 3D meshes (OBJ/STL), prompts, generation status.
- `print_jobs_printjob`: Sliced jobs, G-code paths, layer progress, auto-pause flag.
- `error_logs_errorlog`: Defect detection events, YOLO confidence, action taken.
- `print_jobs_auditlog`: User action history and hardware operation log.

---

## 6. Critical Invariants for Developers & AI Agents

1. **Database Schema:** Never run manual DDL in production. Always write Flyway migrations (`V3__...sql`).
2. **Watertight Meshes:** Models must be manifold before slicing. Non-manifold geometry causes PrusaSlicer CLI errors.
3. **Hardware Safety:** When issuing G-code via Web Serial API, ensure coordinate limits (`X0-220, Y0-220, Z0-250`) are respected to prevent physical frame collisions.
4. **Secrets:** Never commit JWT keys or database passwords. Use `.env` file variables.
