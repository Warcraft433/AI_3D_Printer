@echo off
title PrintGuard AI - 1-Click Launch
cd /d "%~dp0"
echo Starting PrintGuard AI local full-stack environment...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start_local.ps1"
