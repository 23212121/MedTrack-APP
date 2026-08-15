# Creates MedTrackApp PostgreSQL role + database, UI tables, seed data, and wires .env
# Usage:
#   .\scripts\setup-postgres.ps1 -PostgresAdminPassword 'YOUR_POSTGRES_PASSWORD'

param(
  [Parameter(Mandatory = $true)]
  [string]$PostgresAdminPassword,

  [string]$PostgresAdminUser = "postgres",
  [string]$HostName = "localhost",
  [int]$Port = 5432,
  # Logical app DB name. Unquoted Postgres identifiers fold to lowercase (medtrackapp).
  # Pass -DbName 'MedTrackApp' only if you also create it quoted as "MedTrackApp".
  [string]$DbName = "medtrackapp",
  [string]$AppUser = "medtrack",
  [string]$AppPassword = "medtrack",
  [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $PsqlPath)) {
  throw "psql not found at $PsqlPath. Install PostgreSQL or pass -PsqlPath."
}

# Preserve mixed-case database name
$DbLiteral = '"' + ($DbName -replace '"', '""') + '"'

function Invoke-PsqlAdmin {
  param([string]$Sql)
  $env:PGPASSWORD = $PostgresAdminPassword
  try {
    & $PsqlPath -v ON_ERROR_STOP=1 -U $PostgresAdminUser -h $HostName -p $Port -d postgres -c $Sql
    if ($LASTEXITCODE -ne 0) { throw "psql failed (exit $LASTEXITCODE)" }
  }
  finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
  }
}

function Invoke-PsqlDb {
  param([string]$Sql)
  $env:PGPASSWORD = $PostgresAdminPassword
  try {
    & $PsqlPath -v ON_ERROR_STOP=1 -U $PostgresAdminUser -h $HostName -p $Port -d $DbName -c $Sql
    if ($LASTEXITCODE -ne 0) { throw "psql failed (exit $LASTEXITCODE)" }
  }
  finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
  }
}

function Invoke-PsqlDbFile {
  param([string]$FilePath)
  $env:PGPASSWORD = $PostgresAdminPassword
  try {
    & $PsqlPath -v ON_ERROR_STOP=1 -U $PostgresAdminUser -h $HostName -p $Port -d $DbName -f $FilePath
    if ($LASTEXITCODE -ne 0) { throw "psql -f failed for $FilePath (exit $LASTEXITCODE)" }
  }
  finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
  }
}

Write-Host "Checking PostgreSQL admin login..."
Invoke-PsqlAdmin "SELECT version();"

Write-Host "Ensuring role '$AppUser'..."
Invoke-PsqlAdmin @"
DO `$`$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$AppUser') THEN
    CREATE ROLE $AppUser LOGIN PASSWORD '$AppPassword';
  ELSE
    ALTER ROLE $AppUser WITH LOGIN PASSWORD '$AppPassword';
  END IF;
END
`$`$;
"@

Write-Host "Ensuring database $DbLiteral..."
$env:PGPASSWORD = $PostgresAdminPassword
$dbExists = & $PsqlPath -U $PostgresAdminUser -h $HostName -p $Port -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$DbName'"
Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
if (-not ($dbExists -and $dbExists.Trim())) {
  Invoke-PsqlAdmin "CREATE DATABASE $DbLiteral OWNER $AppUser;"
} else {
  Write-Host "Database already exists."
}

Invoke-PsqlDb "CREATE EXTENSION IF NOT EXISTS pgcrypto;"
Invoke-PsqlDb "GRANT ALL ON SCHEMA public TO $AppUser;"
Invoke-PsqlDb "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO $AppUser;"
Invoke-PsqlDb "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO $AppUser;"

$sqlDir = Join-Path $PSScriptRoot "sql"
$tablesSql = Join-Path $sqlDir "jpa-ui-tables.sql"
$seedSql = Join-Path $sqlDir "jpa-ui-seed.sql"

if (Test-Path $tablesSql) {
  Write-Host "Creating UI tables (schema svc)..."
  Invoke-PsqlDbFile $tablesSql
}
if (Test-Path $seedSql) {
  Write-Host "Seeding UI sample data..."
  Invoke-PsqlDbFile $seedSql
}

$root = Split-Path $PSScriptRoot -Parent
$databaseUrl = "postgresql://${AppUser}:${AppPassword}@${HostName}:${Port}/${DbName}?schema=public&sslmode=disable"
$jdbcUrl = "jdbc:postgresql://${HostName}:${Port}/${DbName}?currentSchema=svc"

$envPath = Join-Path $root ".env"
$envExamplePath = Join-Path $root ".env.example"
$jwt = "replace-with-a-long-random-secret"
if (Test-Path $envPath) {
  $existingJwt = (Select-String -Path $envPath -Pattern '^JWT_SECRET=' | Select-Object -First 1)
  if ($existingJwt) {
    $jwt = ($existingJwt.Line -replace '^JWT_SECRET=', '').Trim('"')
  }
}

@"
DATABASE_URL="$databaseUrl"
JWT_SECRET="$jwt"
NEXT_PUBLIC_APP_NAME="MedTrack Clinic"

# Java microservices (Spring) — UI tables live in schema svc
SPRING_DATASOURCE_URL=$jdbcUrl
SPRING_DATASOURCE_USERNAME=$AppUser
SPRING_DATASOURCE_PASSWORD=$AppPassword
SPRING_DATASOURCE_DRIVER=org.postgresql.Driver
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
"@ | Set-Content -Path $envPath -Encoding UTF8

@"
DATABASE_URL="postgresql://medtrack:medtrack@localhost:5432/MedTrackApp?schema=public&sslmode=disable"
JWT_SECRET="replace-with-a-long-random-secret"
NEXT_PUBLIC_APP_NAME="MedTrack Clinic"

# Java microservices (Spring) — UI tables live in schema svc
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/MedTrackApp?currentSchema=svc
SPRING_DATASOURCE_USERNAME=medtrack
SPRING_DATASOURCE_PASSWORD=medtrack
SPRING_DATASOURCE_DRIVER=org.postgresql.Driver
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
"@ | Set-Content -Path $envExamplePath -Encoding UTF8

Write-Host ""
Write-Host "PostgreSQL ready for MedTrack."
Write-Host "  Database : $DbName"
Write-Host "  User     : $AppUser"
Write-Host "  JDBC     : $jdbcUrl"
Write-Host "  .env     : $envPath"
Write-Host ""
Write-Host "Next steps:"
Write-Host "  cd `"$root`""
Write-Host "  npx prisma db push"
Write-Host "  npm run db:seed"
