@echo off
rem Run Smart Grievance on this computer with the embedded H2 database (local profile, D130).
rem No MySQL needed. Data, uploads and logs are kept in .\local-data next to this script.
rem   run-local.bat            start the built JAR (builds it first if it is missing)
rem   run-local.bat --build    clean build with all tests (mvnw clean verify), then start
rem   run-local.bat --reset    delete .\local-data (fresh demo data on the next start), then start
rem Open http://localhost:8080 - demo logins are in README.md
setlocal
cd /d "%~dp0"
java -version 2>&1 | findstr /c:"version \"25" >nul || (echo JDK 25 is required ^(java -version^) & exit /b 1)
if "%~1"=="--build" call mvnw.cmd -B clean verify || exit /b 1
if "%~1"=="--reset" if exist local-data rmdir /s /q local-data
if not exist target\smart-grievance.jar call mvnw.cmd -B clean package -DskipTests || exit /b 1
if "%LOCAL_DATA_DIR%"=="" set LOCAL_DATA_DIR=.\local-data
echo Starting on http://localhost:8080 (data in %LOCAL_DATA_DIR%, Ctrl+C to stop)
java -Xms128m -Xmx512m -XX:+ExitOnOutOfMemoryError -jar target\smart-grievance.jar --spring.profiles.active=local
