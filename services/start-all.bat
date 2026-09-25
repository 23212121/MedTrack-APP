@echo off
REM Alias for CMD: start-all.bat  (same as start-all.cmd)
cd /d "%~dp0"
call "%~dp0start-all.cmd" %*
