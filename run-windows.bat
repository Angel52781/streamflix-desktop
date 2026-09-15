@echo off
setlocal
cd /d "%~dp0"
if not exist "dist\StreamflixDesktop\StreamflixDesktop.exe" call build-windows.bat
if errorlevel 1 exit /b %errorlevel%
start "" "dist\StreamflixDesktop\StreamflixDesktop.exe"
