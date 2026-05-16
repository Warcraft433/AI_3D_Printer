# AI-Integrated 3D Printer Dashboard - Startup Script
Write-Host "--- AI 3D Printer Dashboard: Startup ---" -ForegroundColor Cyan

$workingDir = Get-Location

# 1. Start Redis
Write-Host "[1/5] Checking Redis..." -ForegroundColor Yellow
$redisCheck = netstat -ano | findstr ":6379"
if (-not $redisCheck) {
    Write-Host "Redis not found on 6379. Please start it manually or ensure Memurai is installed." -ForegroundColor Red
}

# 2. Start Backend
Write-Host "[2/5] Starting Backend..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k", "cd backend && venv\Scripts\activate && python manage.py runserver"

# 3. Start Celery
Write-Host "[3/5] Starting Celery..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k", "cd backend && venv\Scripts\activate && python -m celery -A core worker --loglevel=info -P solo"

# 4. Start OctoPrint (Local if exists)
if (Test-Path "OctoPrint_UV") {
    Write-Host "[4/5] Starting Local OctoPrint..." -ForegroundColor Yellow
    Start-Process cmd -ArgumentList "/k", ".\OctoPrint_UV\Scripts\python.exe -m octoprint serve"
}

# 5. Start Frontend
Write-Host "[5/5] Starting Frontend..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k", "cd frontend && npm run dev"

Write-Host "All services triggered!" -ForegroundColor Green
