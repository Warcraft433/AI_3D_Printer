# Deployment Script for Raspberry Pi Integration
# This script copies the local 'pi' directory to your Raspberry Pi using SCP.

$piUser = "raspberry"  # Your Pi username
$piIp = "192.168.31.77"  # Confirmed from your screenshot
$piPath = "/home/raspberry/Desktop/pi"

Write-Host "--- AI 3D Printer Dashboard: Pi Deployment ---" -ForegroundColor Cyan

# Check if the folder exists
if (-not (Test-Path ".\pi")) {
    Write-Host "[ERROR] Local 'pi' directory not found!" -ForegroundColor Red
    exit
}

Write-Host "Deploying to $piUser@$piIp : $piPath" -ForegroundColor Yellow

# Use SCP to copy the directory
# -r is for recursive copy
try {
    scp -r .\pi\* "${piUser}@${piIp}:${piPath}"
    Write-Host "Deployment successful!" -ForegroundColor Green
}
catch {
    Write-Host "[ERROR] Deployment failed. Check your IP and SSH connection." -ForegroundColor Red
}
