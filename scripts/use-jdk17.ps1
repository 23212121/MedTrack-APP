# Point this shell at JDK 17 for MedTrack (does not change system-wide Java 8).
$Jdk17 = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
if (-not (Test-Path "$Jdk17\bin\java.exe")) {
  throw "JDK 17 not found at $Jdk17. Install Microsoft OpenJDK 17 first."
}
$env:JAVA_HOME = $Jdk17
$env:Path = "$Jdk17\bin;" + (($env:Path -split ';' | Where-Object { $_ -and $_ -notlike "*\jdk*\bin" }) -join ';')
Write-Host "JAVA_HOME=$env:JAVA_HOME"
& "$Jdk17\bin\java.exe" -version
