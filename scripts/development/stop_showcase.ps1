# Stop Script for PrintGuard AI Services (Ports 8001, 8080, 5173)
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  Stopping PrintGuard AI Services..." -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

$targetPorts = @(8001, 8080, 5173)

function Stop-PortListener([int]$port) {
    Write-Host "Checking port $port..." -ForegroundColor Yellow
    $killed = $false
    try {
        $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        foreach ($conn in $conns) {
            $procId = $conn.OwningProcess
            if ($procId -gt 0) {
                $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
                if ($proc) {
                    Write-Host "  Stopping process on port $port (PID: $procId, Name: $($proc.ProcessName))..." -ForegroundColor Red
                    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                    $killed = $true
                }
            }
        }
    } catch {
        # Fallback to netstat
    }

    if (-not $killed) {
        $lines = netstat -ano | Select-String ":$port\s+.*LISTENING"
        foreach ($line in $lines) {
            $parts = ($line.ToString().Trim() -split "\s+")
            if ($parts.Length -gt 4) {
                $pidToKill = [int]$parts[-1]
                if ($pidToKill -gt 0) {
                    Write-Host "  Stopping process on port $port (PID: $pidToKill)..." -ForegroundColor Red
                    Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
                    $killed = $true
                }
            }
        }
    }

    if (-not $killed) {
        Write-Host "  Port $port is free." -ForegroundColor Green
    }
}

foreach ($p in $targetPorts) {
    Stop-PortListener -port $p
}

Write-Host ""
Write-Host "All PrintGuard AI services have been stopped." -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Cyan
