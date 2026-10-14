# activate_and_run.ps1
#
# Convenience wrapper for generate_products.py.
# Creates a local virtual environment under .\.venv, installs requirements,
# then runs the generator. The venv is created once and reused on later runs.
#
# This script does NOT modify anything outside script_python\.

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$venvDir = Join-Path $scriptDir ".venv"

# Pick the first Python launcher that exists on PATH. Compatible with
# both Windows PowerShell 5.x (no '??' operator) and PowerShell 7+.
$pyLauncher = Get-Command python -ErrorAction SilentlyContinue
if (-not $pyLauncher) {
    $pyLauncher = Get-Command py -ErrorAction SilentlyContinue
}

if (-not $pyLauncher) {
    Write-Host "ERROR: Python is not on PATH. Install Python 3.10+ first." `
        -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $venvDir)) {
    Write-Host "[venv] Creating virtual environment at $venvDir..." `
        -ForegroundColor Cyan
    & $pyLauncher -m venv $venvDir
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: venv creation failed." -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

$venvPython = Join-Path $venvDir "Scripts\python.exe"
if (-not (Test-Path $venvPython)) {
    Write-Host "ERROR: venv python not found at $venvPython." -ForegroundColor Red
    exit 1
}

Write-Host "[venv] Upgrading pip..." -ForegroundColor Cyan
& $venvPython -m pip install --upgrade pip | Out-Null

Write-Host "[venv] Installing requirements..." -ForegroundColor Cyan
& $venvPython -m pip install -r (Join-Path $scriptDir "requirements.txt")
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: pip install failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "[venv] Activating venv and running generator..." -ForegroundColor Cyan
& $venvPython (Join-Path $scriptDir "generate_products.py") @args
exit $LASTEXITCODE
