@echo off
REM Find JDK 17 and set JAVA_HOME for this CMD session only.
REM Java 8 can stay as the system default; MedTrack must use 17.
REM Usage:  call "...\scripts\find-jdk17.cmd"

set "MEDTRACK_JDK17="

echo %JAVA_HOME% | findstr /I "jdk-17 jdk17" >nul
if not errorlevel 1 if exist "%JAVA_HOME%\bin\java.exe" set "MEDTRACK_JDK17=%JAVA_HOME%"

if not defined MEDTRACK_JDK17 if exist "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot\bin\java.exe" (
  set "MEDTRACK_JDK17=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
)

if not defined MEDTRACK_JDK17 (
  for /d %%D in ("C:\Program Files\Microsoft\jdk-17*") do (
    if exist "%%D\bin\java.exe" set "MEDTRACK_JDK17=%%D"
  )
)

if not defined MEDTRACK_JDK17 (
  for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do (
    if exist "%%D\bin\java.exe" set "MEDTRACK_JDK17=%%D"
  )
)

if not defined MEDTRACK_JDK17 (
  for /d %%D in ("C:\Program Files\Java\jdk-17*") do (
    if exist "%%D\bin\java.exe" set "MEDTRACK_JDK17=%%D"
  )
)

if not defined MEDTRACK_JDK17 (
  echo.
  echo MedTrack cannot build with Java 8.
  echo This project is Spring Boot 3 and uses Java records, which need JDK 17.
  echo Keep Java 8 installed for other apps; this CMD will switch to JDK 17 automatically.
  echo.
  echo Install JDK 17:  winget install Microsoft.OpenJDK.17
  echo.
  exit /b 1
)

set "JAVA_HOME=%MEDTRACK_JDK17%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JAVA_HOME=%JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version
exit /b 0
