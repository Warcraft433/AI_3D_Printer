# =============================================================================
# PrintGuard AI - Local 1-Click Startup Script
# Starts: Spring Boot (port 8080) + AI Service (port 8001) + Vite (port 5173)
# =============================================================================

$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path
$JAR  = "$ROOT\backend-java\target\printguard-backend-0.1.0-SNAPSHOT.jar"
$VENV_PYTHON = "$ROOT\venv\Scripts\python.exe"

Write-Host ""
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "   PrintGuard AI - 1-Click Local Dev Launch      " -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

# 0. Clean up any stale processes holding ports 8080, 8001, 5173
$targetPorts = @(8080, 8001, 5173)
foreach ($p in $targetPorts) {
    $conns = Get-NetTCPConnection -LocalPort $p -ErrorAction SilentlyContinue
    if ($conns) {
        $conns | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object {
            Write-Host "  Freeing port $p (Stopping stale PID: $_)..." -ForegroundColor DarkYellow
            Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
        }
    }
}

# Load environment from .env
$envFile = "$ROOT\.env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*[^#]' -and $_ -match '=' } | ForEach-Object {
        $parts = $_ -split '=', 2
        $key = $parts[0].Trim()
        $val = $parts[1].Trim().Trim('"').Trim("'")
        [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
    }
    Write-Host "  Loaded environment configuration from .env" -ForegroundColor DarkGray
}

# 1. Start Spring Boot Backend (Embedded H2 DB on port 8080)
if (-not (Test-Path $JAR)) {
    Write-Host "  Building Spring Boot backend jar..." -ForegroundColor Yellow
    & mvn package -DskipTests -f "$ROOT\backend-java\pom.xml"
}

Write-Host "[1/3] Starting Spring Boot Backend (http://localhost:8080) ..." -ForegroundColor Yellow
$env:SPRING_PROFILES_ACTIVE = "local"
[System.Environment]::SetEnvironmentVariable("SPRING_PROFILES_ACTIVE", "local", "Process")
$backendProcess = Start-Process -FilePath "java" `
    -ArgumentList @("-jar", $JAR) `
    -WorkingDirectory "$ROOT" `
    -PassThru -WindowStyle Minimized
Write-Host "  Spring Boot running with PID: $($backendProcess.Id)" -ForegroundColor DarkGray

# 2. Start FastAPI AI Service (PyTorch + CUDA fp16 on port 8001)
Write-Host "[2/3] Starting AI Service (http://localhost:8001) ..." -ForegroundColor Yellow
$aiProcess = Start-Process -FilePath $VENV_PYTHON `
    -ArgumentList @("-m", "uvicorn", "app:app", "--host", "127.0.0.1", "--port", "8001") `
    -WorkingDirectory "$ROOT\ai-service" `
    -PassThru -WindowStyle Minimized
Write-Host "  FastAPI AI Service running with PID: $($aiProcess.Id)" -ForegroundColor DarkGray

# 3. Start React Vite Frontend (port 5173)
Write-Host "[3/3] Starting Vite Frontend (http://localhost:5173) ..." -ForegroundColor Yellow
$npmProcess = Start-Process -FilePath "cmd" `
    -ArgumentList @("/c", "npm run dev") `
    -WorkingDirectory "$ROOT\frontend" `
    -PassThru -WindowStyle Minimized
Write-Host "  Frontend dev server running with PID: $($npmProcess.Id)" -ForegroundColor DarkGray

# Save active PIDs for clean shutdown
@{
    BackendPid  = $backendProcess.Id
    AiPid       = $aiProcess.Id
    FrontendPid = $npmProcess.Id
} | ConvertTo-Json | Set-Content "$ROOT\.local_pids.json"

Write-Host ""
Write-Host "=================================================" -ForegroundColor Green
Write-Host "   PrintGuard AI Platform is now ONLINE!        " -ForegroundColor Green
Write-Host "   Frontend  : http://localhost:5173             " -ForegroundColor Green
Write-Host "   Backend   : http://localhost:8080/api/health " -ForegroundColor Green
Write-Host "   AI Micro  : http://localhost:8001/health     " -ForegroundColor Green
Write-Host "   Demo User : admin / admin123                  " -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green

# Wait a moment for services to bind, then launch browser
Start-Sleep -Seconds 3
Start-Process "http://localhost:5173"
