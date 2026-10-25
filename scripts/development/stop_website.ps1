# Stop Script for AI 3D Printer Dashboard
# This script kills Python, Node, and Celery processes.

Write-Host "Stopping AI 3D Printer Dashboard services..." -ForegroundColor Cyan

# Stop Celery
Write-Host "Stopping Celery..." -ForegroundColor Yellow
taskkill /F /IM celery.exe /T 2>$null

# Stop Django (Python)
Write-Host "Stopping Python (Django/Celery)..." -ForegroundColor Yellow
taskkill /F /IM python.exe /T 2>$null

# Stop Vite (Node)
Write-Host "Stopping Node (Vite)..." -ForegroundColor Yellow
taskkill /F /IM node.exe /T 2>$null
# Stop Redis (Memurai)
Write-Host "Stopping Memurai..." -ForegroundColor Yellow
taskkill /F /IM memurai.exe /T 2>$null

Write-Host "All services stopped." -ForegroundColor Green
