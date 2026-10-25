@echo off
title PrintGuard AI - One-Click Showcase Launcher
cd /d "%~dp0"
echo ========================================================================
echo   Launching PrintGuard AI Showcase Environment...
echo ========================================================================
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\development\run_showcase.ps1"
