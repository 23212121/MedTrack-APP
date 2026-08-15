$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Force JDK 17 for MedTrack (keeps Java 8 available for other projects)
$Jdk17 = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
if (-not (Test-Path "$Jdk17\bin\java.exe")) {
  throw "JDK 17 not found at $Jdk17. Install with: winget install Microsoft.OpenJDK.17"
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
