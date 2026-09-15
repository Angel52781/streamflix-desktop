@echo off
setlocal
cd /d "%~dp0"
call build-windows.bat --jar-only
if errorlevel 1 exit /b %errorlevel%
java -jar build\streamflix-desktop.jar
