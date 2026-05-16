# API Documentation

The backend provides a RESTful API built with Django REST Framework.

## Authentication

We use JWT (JSON Web Tokens) for authentication.

- **Login:** `POST /api/accounts/login/`
- **Refresh Token:** `POST /api/accounts/token/refresh/`

Include the token in the header for protected routes:
`Authorization: Bearer <your_token>`

## Key Endpoints

### Print Jobs
- `GET /api/print-jobs/`: List all print jobs.
- `POST /api/print-jobs/`: Create a new print job.
- `GET /api/print-jobs/{id}/`: Get details of a specific job.

### AI Monitoring
- `GET /api/dashboard/status/`: Get real-time printer and AI status.
- `GET /api/dashboard/defects/`: List detected defects.
- `POST /api/dashboard/toggle-ai/`: Enable/Disable AI monitoring.

### 3D Generation
- `POST /api/generated-models/generate/`: Generate a 3D model from a text prompt.
- `GET /api/generated-models/history/`: View generation history.

### Printer Control (OctoPrint Proxy)
- `POST /api/printer/command/`: Send G-code commands (e.g., Pause, Resume, Cancel).
- `GET /api/printer/temps/`: Get current bed and tool temperatures.

## Real-Time Updates

We use WebSockets (Django Channels) for real-time data:
- `ws://localhost:8000/ws/dashboard/`: Printer status and AI detection events.
