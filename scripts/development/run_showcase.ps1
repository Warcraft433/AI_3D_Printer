# PrintGuard AI - One-Click Showcase Launcher
# Starts Python AI Service (8001), Java Spring Boot Backend (8080), and React Frontend (5173)

$Host.UI.RawUI.WindowTitle = "PrintGuard AI - Showcase Hub"
Clear-Host

Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host "         PRINTGUARD AI - ONE-CLICK SHOWCASE LAUNCHER                    " -ForegroundColor Cyan
Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host ""

$baseDir = $PSScriptRoot
while ($baseDir -and -not (Test-Path "$baseDir\frontend")) {
    $parent = Split-Path -Parent $baseDir
    if ($parent -eq $baseDir) { break }
    $baseDir = $parent
}
if (-not $baseDir -or -not (Test-Path "$baseDir\frontend")) {
    $baseDir = (Get-Location).Path
}

# 1. Clean existing lingering ports
Write-Host "[1/5] Checking and clearing ports (8001, 8080, 5173)..." -ForegroundColor Yellow
$stopScript = Join-Path $baseDir "scripts\development\stop_showcase.ps1"
if (-not (Test-Path $stopScript)) {
    $stopScript = Join-Path $baseDir "scripts\stop_showcase.ps1"
}
if (Test-Path $stopScript) {
    & $stopScript | Out-Null
}
Write-Host "      Ports are ready." -ForegroundColor Green

# 2. Load .env configuration
Write-Host "[2/5] Loading environment variables..." -ForegroundColor Yellow
$envFile = Join-Path $baseDir ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line.Split("=", 2)
            if ($parts.Length -eq 2) {
                $key = $parts[0].Trim()
                $val = $parts[1].Trim().Trim('"').Trim("'")
                [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
            }
        }
    }
    Write-Host "      Loaded settings from .env successfully." -ForegroundColor Green
} else {
    Write-Host "      No .env found; proceeding with local defaults." -ForegroundColor DarkGray
}

# 3. Check Python venv
$pythonExe = $null
$pyCandidates = @(
    (Join-Path $baseDir "venv\Scripts\python.exe"),
    (Join-Path $baseDir ".venv\Scripts\python.exe"),
    (Join-Path $baseDir "legacy\backend-django\venv\Scripts\python.exe")
)
foreach ($c in $pyCandidates) {
    if (Test-Path $c) { $pythonExe = $c; break }
}
if (-not $pythonExe) {
    $sysPy = (Get-Command python -ErrorAction SilentlyContinue).Source
    if ($sysPy) { $pythonExe = $sysPy }
}

if (-not $pythonExe) {
    Write-Host "[ERROR] Python executable not found." -ForegroundColor Red
    Write-Host "Please ensure a Python virtualenv exists at venv\." -ForegroundColor Red
    pause
    exit 1
}

# 4. Launch Services
Write-Host "[3/5] Starting backend and microservice processes..." -ForegroundColor Yellow

# A. Python AI Microservice (:8001)
Write-Host "      Starting [1/3] Python AI Microservice (Port 8001)..." -ForegroundColor Cyan
$aiPath = Join-Path $baseDir "ai-service"
$aiCmd = "cd `"$aiPath`"; & `"$pythonExe`" -m uvicorn app:app --host 127.0.0.1 --port 8001"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle='PrintGuard AI Inference (:8001)'; $aiCmd" -WindowStyle Normal

# B. Java Spring Boot Backend (:8080)
$profile = "local"
if ($env:SPRING_PROFILES_ACTIVE) {
    $profile = $env:SPRING_PROFILES_ACTIVE
} elseif ($env:SPRING_DATASOURCE_PASSWORD -and $env:SPRING_DATASOURCE_PASSWORD -ne "your_local_password" -and $env:SPRING_DATASOURCE_PASSWORD -ne "") {
    $profile = "supabase"
}

if ($profile -eq "supabase") {
    Write-Host "      Starting [2/3] Java Spring Boot Backend (:8080) [Profile: Supabase PostgreSQL]..." -ForegroundColor Cyan
} else {
    Write-Host "      Starting [2/3] Java Spring Boot Backend (:8080) [Profile: Local H2 Database]..." -ForegroundColor Cyan
}

$javaPath = Join-Path $baseDir "backend-java"
$jarFile = Join-Path $javaPath "target\printguard-backend-0.1.0-SNAPSHOT.jar"
if (Test-Path $jarFile) {
    $bootCmd = "cd `"$javaPath`"; java `"-Dspring.profiles.active=$profile`" -jar `"$jarFile`""
} else {
    $bootCmd = "cd `"$javaPath`"; mvn spring-boot:run `"-Dspring-boot.run.profiles=$profile`""
}
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle='PrintGuard Java Backend (:8080)'; $bootCmd" -WindowStyle Normal

# C. React Vite Frontend (:5173)
Write-Host "      Starting [3/3] React Vite Frontend (Port 5173)..." -ForegroundColor Cyan
$fePath = Join-Path $baseDir "frontend"
$feCmd = "cd `"$fePath`"; npm run dev"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle='PrintGuard React UI (:5173)'; $feCmd" -WindowStyle Normal

# 5. Wait for Backend AND Frontend and Launch Browser
Write-Host "[4/5] Synchronizing and waiting for all services to be fully ready..." -ForegroundColor Yellow
$feUrl = "http://localhost:5173"
$beHealthUrl = "http://localhost:8080/api/health"

$beReady = $false
$feReady = $false
$maxRetries = 40
$retryCount = 0

while ((-not $beReady -or -not $feReady) -and $retryCount -lt $maxRetries) {
    Start-Sleep -Seconds 1
    $retryCount++

    if (-not $feReady) {
        try {
            $tcpFe = Test-NetConnection -ComputerName localhost -Port 5173 -WarningAction SilentlyContinue
            if ($tcpFe.TcpTestSucceeded) {
                $feReady = $true
            }
        } catch {}
    }

    if (-not $beReady) {
        try {
            $resp = Invoke-RestMethod -Uri $beHealthUrl -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($resp -and ($resp.status -eq "UP" -or $resp.service)) {
                $beReady = $true
            }
        } catch {
            try {
                $tcpBe = Test-NetConnection -ComputerName localhost -Port 8080 -WarningAction SilentlyContinue
                if ($tcpBe.TcpTestSucceeded) {
                    $beReady = $true
                }
            } catch {}
        }
    }

    Write-Host -NoNewline "."
}
Write-Host ""

if ($beReady -and $feReady) {
    Write-Host "[5/5] Backend and Frontend are BOTH UP and synchronized! Launching browser..." -ForegroundColor Green
    Start-Process $feUrl
} else {
    Write-Host "[5/5] Launching browser (services are finishing startup)..." -ForegroundColor Yellow
    Start-Process $feUrl
}

# Control Panel Display
Write-Host ""
Write-Host "========================================================================" -ForegroundColor Green
Write-Host "               PRINTGUARD AI SHOWCASE IS LIVE!                          " -ForegroundColor Green
Write-Host "========================================================================" -ForegroundColor Green
Write-Host "  >> Frontend Dashboard:   http://localhost:5173" -ForegroundColor White
Write-Host "  >> Spring Boot API:      http://localhost:8080/api/health" -ForegroundColor White
Write-Host "  >> AI Inference Health:  http://127.0.0.1:8001/api/inference/health" -ForegroundColor White
Write-Host "------------------------------------------------------------------------" -ForegroundColor DarkGray
Write-Host "  Showcase Login Credentials:" -ForegroundColor Yellow
Write-Host "     * Username: admin" -ForegroundColor White
Write-Host "     * Password: admin123" -ForegroundColor White
Write-Host "     (Or use the Register tab to create any new demo account)" -ForegroundColor DarkGray
Write-Host "------------------------------------------------------------------------" -ForegroundColor DarkGray
Write-Host "  Demo features ready for showcase:" -ForegroundColor Yellow
Write-Host "     * Real-time 3D Model Rendering (Three.js / React Three Fiber)" -ForegroundColor DarkGray
Write-Host "     * AI Spaghetti Defect Detection (YOLOv8 + Webcam)" -ForegroundColor DarkGray
Write-Host "     * 3D Model Generator (Prompt to 3D)" -ForegroundColor DarkGray
Write-Host "     * Telemetry, OctoPrint and Job Monitoring" -ForegroundColor DarkGray
Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host "  Commands:" -ForegroundColor White
Write-Host "    [B] - Reopen Web Dashboard in Browser" -ForegroundColor Gray
Write-Host "    [Q] - Stop all services and Exit cleanly" -ForegroundColor Gray
Write-Host "========================================================================" -ForegroundColor Cyan

# Interactive loop
while ($true) {
    Write-Host -NoNewline "Enter command [B/Q]: "
    $key = Read-Host
    if ($key -eq "B" -or $key -eq "b") {
        Write-Host "Reopening $feUrl..." -ForegroundColor Cyan
        Start-Process $feUrl
    } elseif ($key -eq "Q" -or $key -eq "q") {
        Write-Host ""
        Write-Host "Stopping all PrintGuard AI services..." -ForegroundColor Red
        if (Test-Path $stopScript) {
            & $stopScript
        }
        Write-Host "All showcase services stopped. Goodbye!" -ForegroundColor Green
        Start-Sleep -Seconds 2
        break
    }
}
