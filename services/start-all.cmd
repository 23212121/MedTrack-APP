@echo off
setlocal
cd /d "%~dp0"

call "%~dp0..\scripts\find-jdk17.cmd"
if errorlevel 1 exit /b 1

echo Building medtrack-app with JDK 17...
call mvn -pl medtrack-app -am package -DskipTests
if errorlevel 1 exit /b 1

set "JAR=%~dp0medtrack-app\target\medtrack-app-1.0.0-SNAPSHOT.jar"
if not exist "%JAR%" (
  echo Build did not produce %JAR%
  exit /b 1
)

echo Starting medtrack-app on http://localhost:8090 ...
"%JAVA_HOME%\bin\java.exe" -jar "%JAR%"
