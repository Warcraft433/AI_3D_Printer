@echo off
title PrintGuard AI - Stop Services
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop_local.ps1"
