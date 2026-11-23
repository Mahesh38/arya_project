@echo off
rem Start Smart Grievance from the built JAR (doc 14 section 7.3). Windows 10/11.
rem   run.bat          dev profile (local MySQL grievance_dev, demo data)
rem   run.bat prod     prod profile; set DB_URL, DB_USERNAME, DB_PASSWORD, ADMIN_PASSWORD first
rem Run it from the app\ folder (logs\ and uploads\ are created there).
setlocal
set PROFILE=%1
if "%PROFILE%"=="" set PROFILE=dev
if not exist target\smart-grievance.jar (
  echo No target\smart-grievance.jar - run mvnw.cmd clean verify first
  exit /b 1
)
java -version 2>&1 | findstr /c:"version \"25" >nul || (
  echo JDK 25 is required - check JAVA_HOME and PATH
  exit /b 1
)
java -Xms128m -Xmx512m -XX:+ExitOnOutOfMemoryError -jar target\smart-grievance.jar --spring.profiles.active=%PROFILE%
endlocal
