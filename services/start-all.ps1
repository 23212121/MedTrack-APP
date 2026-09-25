$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Force JDK 17 for MedTrack (keeps Java 8 available for other projects)
function Resolve-Jdk17 {
  $homes = @()
  if ($env:JAVA_HOME) { $homes += $env:JAVA_HOME }
  foreach ($root in @("C:\Program Files\Microsoft", "C:\Program Files\Eclipse Adoptium", "C:\Program Files\Java")) {
    if (Test-Path $root) {
      $homes += Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "jdk-17*" } |
        ForEach-Object { $_.FullName }
    }
  }
  foreach ($jdkHome in $homes) {
    if ($jdkHome -and (Test-Path (Join-Path $jdkHome "bin\java.exe"))) {
      $ver = & (Join-Path $jdkHome "bin\java.exe") -version 2>&1 | Out-String
      if ($ver -match 'version "17') { return $jdkHome }
    }
  }
  return $null
}
$Jdk17 = Resolve-Jdk17
if (-not $Jdk17) {
  throw "JDK 17 not found. Java 8 cannot build MedTrack. Install with: winget install Microsoft.OpenJDK.17"
}
$env:JAVA_HOME = $Jdk17
$env:Path = "$Jdk17\bin;" + $env:Path
Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
& "$Jdk17\bin\java.exe" -version

Write-Host "Building medtrack-app..."
mvn -q -pl medtrack-app -am package -DskipTests

$jar = Join-Path $PSScriptRoot "medtrack-app\target\medtrack-app-1.0.0-SNAPSHOT.jar"
if (-not (Test-Path $jar)) {
  throw "Build did not produce $jar"
}

Write-Host "Starting medtrack-app on http://localhost:8090 ..."
& "$Jdk17\bin\java.exe" -jar $jar
