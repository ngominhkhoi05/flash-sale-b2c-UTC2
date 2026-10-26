param(
    [string]$Profile = "loadtest-lua",
    [string]$ExtraArgs = ""
)

Write-Host "Killing existing Java processes..."
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

Write-Host "Starting Spring Boot with profile: $Profile $ExtraArgs"
$cmd = ".\gradlew.bat bootRun --args='--spring.profiles.active=$Profile $ExtraArgs'"
$proc = Start-Process powershell -ArgumentList "-NoProfile -Command $cmd" -PassThru

Write-Host "Waiting for backend on http://localhost:8080/actuator/health..."
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$ready = $false
while ($sw.ElapsedMilliseconds -lt 60000) {
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 2 -ErrorAction Stop
        if ($resp.status -eq "UP") {
            $ready = $true
            break
        }
    } catch {
        Start-Sleep -Milliseconds 1000
    }
}

if (-not $ready) {
    Write-Error "Spring Boot failed to start within 60s"
    exit 1
}

Write-Host "Backend is UP and READY in $($sw.Elapsed.TotalSeconds)s!"
