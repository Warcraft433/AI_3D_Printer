# Forwarding shim for stop_showcase.ps1
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& "$scriptDir\development\stop_showcase.ps1"
