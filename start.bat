@echo off
echo --- AI-Integrated 3D Printer: Professional Startup ---

:: 1. Start Redis (Check if port 6379 is open)
netstat -ano | findstr ":6379" > nul
if %errorlevel% neq 0 (
    echo [1/4] Redis not detected on 6379. Please ensure Redis/Memurai is running.
) else (
    echo [1/4] Redis is running.
)

:: 2. Start Backend
echo [2/4] Starting Django Backend...
start "Backend" cmd /k "cd backend && venv\Scripts\activate && python manage.py runserver"

:: 3. Start Celery (Worker + Beat)
echo [3/4] Starting Celery Worker...
start "Celery" cmd /k "cd backend && venv\Scripts\activate && python -m celery -A core worker --loglevel=info -P solo"

:: 4. Start Frontend
echo [4/4] Starting React Frontend...
start "Frontend" cmd /k "cd frontend && npm run dev"

echo.
echo All services triggered! 
echo Frontend: http://localhost:3000
echo Backend: http://localhost:8000
pause
