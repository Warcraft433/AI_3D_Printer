# PrintGuard AI - Project Changelog & Revision History

All notable changes, architectural milestones, and version upgrades for the PrintGuard AI 3D Printer project are documented herein.

---

## [v2.4.0] - 2026-10-24 (Current Production State)
### Added
- Created complete Project Master Documentation suite (`PROJECT_MASTER_DOCUMENTATION.pdf`, `PROJECT_QUICK_REFERENCE.md`, `PROJECT_CHANGELOG.md`).
- Verified Spring Boot 3.3.0 backend health endpoints (`/actuator/health`, `/api/health`).
- Documented 10 backend integration test suites covering auth, storage, slicing, and security.

### Verified & Audited
- Verified W3C Web Serial API direct hardware controller in `PrinterControl.jsx`.
- Audited PostgreSQL Row Level Security (RLS) migrations (`V1__init_schema.sql`, `V2__enable_rls_and_least_privilege.sql`).
- Confirmed YOLOv8 spaghetti defect detector with confidence gating (`YOLO_CONFIDENCE=0.35`) and allow-list filtering.

---

## [v2.3.0] - 2026-09-15
### Added
- Sentry error logging and exception tracking in backend controllers.
- Multi-stage Dockerfile for Spring Boot backend utilizing Eclipse Temurin 21.
- Supabase Cloud Storage driver with local disk caching fallback.

### Changed
- Refactored `GenerationController.java` to support both FastAPI procedural CAD generation and direct Python fallback.

---

## [v2.2.0] - 2026-07-10
### Added
- Integrated PrusaSlicer CLI headless execution pipeline in `SlicerService.java`.
- Added interactive Three.js 3D G-code visualizer with layer slider scrubbing (`GCodePreview.jsx`).
- Added WebSocket real-time telemetry broadcaster (`WebSocketEventPublisher.java`).

---

## [v2.1.0] - 2026-04-05
### Added
- Complete migration from legacy Django backend to Spring Boot 3 (Java 21).
- Implemented Argon2 and jBCrypt password hashing in `PasswordService.java`.
- Implemented stateless HMAC-SHA256 JWT authentication filter chain.

---

## [v1.0.0] - 2025-11-20
### Initial Release
- Initial prototype featuring camera monitoring, YOLOv8 defect detection, and basic 3D model queue.
