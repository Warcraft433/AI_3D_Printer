@echo off
title Stop PrintGuard AI Services
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\development\stop_showcase.ps1"
echo.
pause
