@echo off
setlocal
cd /d "%~dp0"

call "%~dp0..\scripts\find-jdk17.cmd"
if errorlevel 1 exit /b 1

echo Stopping anything on port 8090 so Maven can rewrite the JAR...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8090" ^| findstr "LISTENING"') do (
  echo   taskkill PID %%a
  taskkill /F /PID %%a >nul 2>&1
)
timeout /t 2 /nobreak >nul

echo Building medtrack-app with JDK 17...
call mvn -pl medtrack-app -am package -DskipTests
if errorlevel 1 exit /b 1

set "JAR=%~dp0medtrack-app\target\medtrack-app-1.0.0-SNAPSHOT.jar"
set "RUN=%~dp0medtrack-app\target\medtrack-app-running.jar"
if not exist "%JAR%" (
  echo Build did not produce %JAR%
  exit /b 1
)

REM Run a copy so the next `mvn package` can overwrite SNAPSHOT.jar on Windows.
copy /Y "%JAR%" "%RUN%" >nul
echo Starting medtrack-app on http://localhost:8090 ...
"%JAVA_HOME%\bin\java.exe" -jar "%RUN%"
