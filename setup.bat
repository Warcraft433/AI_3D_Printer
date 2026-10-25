@echo off
echo ===================================================
echo   AI 3D Printer Dashboard - One-Click Setup
echo ===================================================
echo.

:: 1. Backend Setup
echo [1/3] Setting up Python Virtual Environment...
cd backend
if not exist "venv\Scripts\python.exe" (
    python -m venv venv
)
call venv\Scripts\activate
echo [2/3] Installing Python dependencies...
python -m pip install -r requirements.txt
echo Running database migrations & seeding demo job...
python manage.py migrate
python manage.py seed_demo_job
cd ..

:: 2. Frontend Setup
echo.
echo [3/3] Installing Frontend dependencies...
cd frontend
call npm install
cd ..

echo.
echo ===================================================
echo   Setup Complete! Run start.bat to launch everything.
echo ===================================================
pause
