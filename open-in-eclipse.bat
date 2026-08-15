@echo off
set ECLIPSE=D:\Eclips\eclipse-jee-2025-12-R-win32-x86_64 (1)\eclipse\eclipse.exe
set WORKSPACE=D:\MedTracker -APP\medical-visit-tracker\eclipse-workspace
set PROJECT=D:\MedTracker -APP\medical-visit-tracker\services
if not exist "%WORKSPACE%" mkdir "%WORKSPACE%"
start "" "%ECLIPSE%" -data "%WORKSPACE%" -showlocation
echo.
echo Eclipse is opening with workspace:
echo   %WORKSPACE%
echo.
echo Import MedTrack (one time):
echo   1. File -^> Import... -^> Maven -^> Existing Maven Projects
echo   2. Root directory: %PROJECT%
echo   3. Select all modules -^> Finish
echo.
echo Run the app:
echo   Project Explorer -^> medtrack-app -^> MedtrackApplication.java
echo   Right-click -^> Run As -^> Java Application
echo   Or use MedtrackApplication.launch
echo   API: http://localhost:8090/api/health
echo.
pause
