# =============================================================================
# PrintGuard AI - Local Stop Script
# Shuts down Spring Boot, AI Service, and Vite dev server
# =============================================================================

$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host ""
Write-Host "Stopping PrintGuard AI local services..." -ForegroundColor Yellow

# Kill recorded PIDs if present
$pidFile = "$ROOT\.local_pids.json"
if (Test-Path $pidFile) {
    try {
        $pids = Get-Content $pidFile -Raw | ConvertFrom-Json
        if ($pids.BackendPid)  { Stop-Process -Id $pids.BackendPid -Force -ErrorAction SilentlyContinue }
        if ($pids.AiPid)       { Stop-Process -Id $pids.AiPid -Force -ErrorAction SilentlyContinue }
        if ($pids.FrontendPid) { Stop-Process -Id $pids.FrontendPid -Force -ErrorAction SilentlyContinue }
    } catch {}
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

# Free target ports if anything lingering
$ports = @(8080, 8001, 5173)
foreach ($p in $ports) {
    $conns = Get-NetTCPConnection -LocalPort $p -ErrorAction SilentlyContinue
    if ($conns) {
        $conns | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object {
            Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
        }
    }
}

Write-Host "All PrintGuard AI services stopped cleanly." -ForegroundColor Green
