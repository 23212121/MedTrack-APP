@echo off
REM Stop the API on 8090 so Maven can rewrite medtrack-app-1.0.0-SNAPSHOT.jar
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8090" ^| findstr "LISTENING"') do (
  echo Stopping PID %%a on port 8090
  taskkill /F /PID %%a >nul 2>&1
)
echo Port 8090 is free.
