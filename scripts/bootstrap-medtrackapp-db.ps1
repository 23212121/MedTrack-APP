# Bootstrap database "MedTrackApp" using temporary local trust auth (no admin password needed).
# Restores pg_hba.conf afterward. Run elevated if pg_hba write is denied.

$ErrorActionPreference = "Stop"
$PgData = "C:\Program Files\PostgreSQL\18\data"
$PgBin = "C:\Program Files\PostgreSQL\18\bin"
$Psql = Join-Path $PgBin "psql.exe"
$PgHba = Join-Path $PgData "pg_hba.conf"
$Backup = Join-Path $PgData "pg_hba.conf.medtrack.bak"
$SetupScript = Join-Path $PSScriptRoot "setup-postgres.ps1"

if (-not (Test-Path $Psql)) { throw "psql not found: $Psql" }
if (-not (Test-Path $PgHba)) { throw "pg_hba.conf not found: $PgHba" }

Write-Host "Backing up pg_hba.conf..."
Copy-Item $PgHba $Backup -Force

try {
  Write-Host "Enabling temporary local trust for bootstrap..."
  $lines = Get-Content $PgHba
  $out = foreach ($line in $lines) {
    if ($line -match '^\s*host\s+all\s+all\s+127\.0\.0\.1/32\s+') {
      "host    all             all             127.0.0.1/32            trust"
    } elseif ($line -match '^\s*host\s+all\s+all\s+::1/128\s+') {
      "host    all             all             ::1/128                 trust"
    } else {
      $line
    }
  }
  Set-Content -Path $PgHba -Value $out -Encoding ascii

  & (Join-Path $PgBin "pg_ctl.exe") reload -D $PgData | Out-Null
  Start-Sleep -Seconds 2

  # Empty password works under trust; setup script still requires the param
  & $SetupScript -PostgresAdminPassword "unused-under-trust" -DbName "MedTrackApp"
}
finally {
  Write-Host "Restoring pg_hba.conf..."
  if (Test-Path $Backup) {
    Copy-Item $Backup $PgHba -Force
    & (Join-Path $PgBin "pg_ctl.exe") reload -D $PgData | Out-Null
  }
}

Write-Host "Done. Database MedTrackApp is ready."
