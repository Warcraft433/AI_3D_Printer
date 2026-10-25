# Startup Script for AI 3D Printer Dashboard
# This script opens all 6 required service windows for you.

$workingDir = Get-Location

Write-Host "--- AI 3D Printer Dashboard: One-Click Startup ---" -ForegroundColor Cyan

# 1. Start Redis (Memurai)
Write-Host "[1/6] Checking for Redis (Memurai)..." -ForegroundColor Yellow
$redisCheck = netstat -ano | findstr ":6379.*LISTENING"
if (-not $redisCheck) {
    Write-Host "Redis (LISTENING) not found on 6379. Starting Memurai..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "& 'C:\Program Files\Memurai\memurai.exe' --port 6379" -WindowStyle Normal
    Start-Sleep -Seconds 2 # Give it a moment to bind
}
else {
    Write-Host "Redis is already running and listening." -ForegroundColor Green
}

# 2. Start Django Backend
Write-Host "[2/6] Starting Django Backend..." -ForegroundColor Yellow
$backendDir = "$workingDir\backend"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$backendDir'; .\venv\Scripts\activate; python manage.py runserver" -WindowStyle Normal

# 3. Start Celery Worker
Write-Host "[3/6] Starting Celery Worker..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$backendDir'; .\venv\Scripts\activate; python -m celery -A core worker --loglevel=info -P solo" -WindowStyle Normal

# 4. Start Celery Beat
Write-Host "[4/6] Starting Celery Beat..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$backendDir'; .\venv\Scripts\activate; python -m celery -A core beat --loglevel=info" -WindowStyle Normal

# 5. Start OctoPrint Server (Local)
Write-Host "[5/6] Starting Local OctoPrint Server..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$workingDir'; .\OctoPrint_UV\Scripts\python.exe -m octoprint serve" -WindowStyle Normal

# 6. Start Vite Frontend
Write-Host "[6/6] Starting Vite Frontend..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$workingDir\frontend'; npm run dev" -WindowStyle Normal

Write-Host "All services triggered! Check the new terminal windows for status." -ForegroundColor Green
Write-Host "Frontend: http://localhost:5173" -ForegroundColor Green
Write-Host "Backend: http://127.0.0.1:8000" -ForegroundColor Green
Write-Host "OctoPrint: http://localhost:5000" -ForegroundColor Green
