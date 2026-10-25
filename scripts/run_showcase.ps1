# Forwarding shim for run_showcase.ps1
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& "$scriptDir\development\run_showcase.ps1"
