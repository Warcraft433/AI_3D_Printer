# Start Spring Boot Backend with Supabase Profile
$ErrorActionPreference = "Stop"

$envFile = "C:\Projects01\AI_3D_Printer\.env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line.Split("=", 2)
            if ($parts.Length -eq 2) {
                $key = $parts[0].Trim()
                $val = $parts[1].Trim().Trim('"').Trim("'")
                [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
            }
        }
    }
}

Set-Location "C:\Projects01\AI_3D_Printer\printguard-migration-starter\backend-java"
java -jar target\printguard-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=supabase
