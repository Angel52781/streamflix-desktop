@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

where javac >nul 2>nul || (echo ERROR: JDK 21+ javac not found on PATH.& exit /b 1)
where jar >nul 2>nul || (echo ERROR: jar not found on PATH.& exit /b 1)

if exist build rmdir /s /q build
mkdir build\classes
mkdir build\test-classes

set "SOURCES=build\sources.txt"
set "TESTS=build\tests.txt"
dir /s /b src\main\java\*.java > "%SOURCES%"
dir /s /b src\test\java\*.java > "%TESTS%"

javac --release 21 -encoding UTF-8 -d build\classes @"%SOURCES%"
if errorlevel 1 exit /b %errorlevel%
jar --create --file build\streamflix-desktop.jar --main-class dev.streamflix.desktop.App -C build\classes .
if errorlevel 1 exit /b %errorlevel%

javac --release 21 -encoding UTF-8 -cp build\classes -d build\test-classes @"%TESTS%"
if errorlevel 1 exit /b %errorlevel%
java -cp "build\classes;build\test-classes" dev.streamflix.desktop.JsonTest
if errorlevel 1 exit /b %errorlevel%
java -cp "build\classes;build\test-classes" dev.streamflix.desktop.ProviderFixtureTest
if errorlevel 1 exit /b %errorlevel%
java -cp "build\classes;build\test-classes" dev.streamflix.desktop.ExtractorFixtureTest
if errorlevel 1 exit /b %errorlevel%

echo JAR built: build\streamflix-desktop.jar
if /I "%~1"=="--jar-only" exit /b 0

where jpackage >nul 2>nul || (echo ERROR: jpackage not found. Install a full JDK 21+ distribution.& exit /b 1)
if exist dist rmdir /s /q dist
jpackage --type app-image --name StreamflixDesktop --input build --main-jar streamflix-desktop.jar --main-class dev.streamflix.desktop.App --dest dist --description "Streamflix desktop port" --vendor "Streamflix Desktop Community Port"
if errorlevel 1 exit /b %errorlevel%
echo.
echo Windows app image: dist\StreamflixDesktop\StreamflixDesktop.exe
