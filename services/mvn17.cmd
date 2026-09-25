@echo off
setlocal
cd /d "%~dp0"
call "%~dp0..\scripts\find-jdk17.cmd"
if errorlevel 1 exit /b 1
call mvn %*
