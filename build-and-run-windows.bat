@echo off
rem ===========================================================================
rem  Smart Grievance - build the JAR and run it on Windows (local profile)
rem ===========================================================================
rem  Needs ONLY Java 25. Maven does NOT have to be installed: the Maven Wrapper
rem  (app\mvnw.cmd) downloads Maven 3.9.16 by itself on the first build (needs
rem  internet once; it is kept in %USERPROFILE%\.m2\wrapper).
rem
rem  Double-click this file, or run it from a Command Prompt in this folder:
rem    build-and-run-windows.bat            build the JAR (tests skipped) and start the app
rem    build-and-run-windows.bat --test     build with all tests first (slower, a few minutes)
rem    build-and-run-windows.bat --no-run   only build; do not start the app
rem    build-and-run-windows.bat --reset    start with fresh demo data (deletes dist\local-data)
rem
rem  Result: dist\smart-grievance.jar and dist\run-local.bat (copy the dist folder anywhere).
rem  The JAR is the same for every profile; "local" (embedded H2 database, demo data,
rem  H2 console) is chosen when it starts:  java -jar smart-grievance.jar --spring.profiles.active=local
rem ===========================================================================
setlocal EnableExtensions
cd /d "%~dp0"

set "GOAL=package -DskipTests"
set "RUN=1"
set "RESET=0"
set "PAUSE_AT_END=1"
:args
if "%~1"=="" goto :args_done
if /i "%~1"=="--test" set "GOAL=verify"
if /i "%~1"=="--no-run" set "RUN=0"
if /i "%~1"=="--reset" set "RESET=1"
if /i "%~1"=="--no-pause" set "PAUSE_AT_END=0"
shift
goto :args
:args_done

echo.
echo [1/4] Checking Java ...
where java >nul 2>&1
if errorlevel 1 goto :no_java
set "JMAJOR="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i /c:" version "') do set "JVER=%%~v"
for /f "delims=." %%m in ("%JVER%") do set "JMAJOR=%%m"
if not defined JMAJOR goto :no_java
if %JMAJOR% LSS 25 goto :old_java
echo       Java %JVER% found:
java -version 2>&1 | findstr /i /c:" version "

rem A JAVA_HOME that points at an older JDK would make Maven compile with it: use the Java on PATH instead.
if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" (
    "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /r /c:"version .2[5-9]" /c:"version .[3-9][0-9]" >nul || (
      echo       JAVA_HOME=%JAVA_HOME% is not Java 25 - ignoring it for this build.
      set "JAVA_HOME="
    )
  ) else (
    echo       JAVA_HOME=%JAVA_HOME% does not contain bin\java.exe - ignoring it for this build.
    set "JAVA_HOME="
  )
)

echo.
echo [2/4] Checking Maven (via the Maven Wrapper - no installation needed) ...
pushd app
call mvnw.cmd -v
if errorlevel 1 (
  popd
  echo.
  echo  The Maven Wrapper could not download or start Maven.
  echo  Check the internet connection ^(and proxy settings^), then run this file again.
  goto :fail
)

echo.
if "%GOAL%"=="verify" (echo [3/4] Building the JAR with all tests ^(mvnw.cmd clean verify^) ...) else (echo [3/4] Building the JAR ^(mvnw.cmd clean package, tests skipped^) ...)
call mvnw.cmd -B clean %GOAL%
if errorlevel 1 (
  popd
  echo.
  echo  The build failed - see the messages above.
  goto :fail
)
popd
if not exist "app\target\smart-grievance.jar" (
  echo  app\target\smart-grievance.jar was not created.
  goto :fail
)
if not exist dist mkdir dist
copy /y "app\target\smart-grievance.jar" "dist\smart-grievance.jar" >nul
(
  echo @echo off
  echo rem Start Smart Grievance with the local profile ^(embedded H2 database, demo data^). Needs Java 25.
  echo cd /d "%%~dp0"
  echo echo App: http://localhost:8080   H2 console: http://localhost:8080/h2-console  ^(admin / admin^)
  echo java -Xmx512m -jar smart-grievance.jar --spring.profiles.active=local
  echo pause
) > "dist\run-local.bat"
echo       Created dist\smart-grievance.jar and dist\run-local.bat

if "%RUN%"=="0" goto :done

echo.
echo [4/4] Starting the application (local profile) ...
cd dist
if "%RESET%"=="1" if exist local-data (rmdir /s /q local-data & echo       Deleted dist\local-data - fresh demo data will be loaded.)
echo.
echo   Application : http://localhost:8080
echo   Logins      : admin@example.com / Admin@1234   ^|  mahesh@example.com / Demo@1234
echo   H2 console  : http://localhost:8080/h2-console
echo                 JDBC URL jdbc:h2:file:./local-data/db/smart-grievance   user admin   password admin
echo   Data folder : %CD%\local-data
echo   Stop        : press Ctrl+C in this window
echo.
java -Xmx512m -jar smart-grievance.jar --spring.profiles.active=local
goto :done

:no_java
echo.
echo  Java was not found. This project needs Java 25 (JDK).
goto :install_java
:old_java
echo.
echo  Java %JVER% was found, but this project needs Java 25 or newer.
:install_java
where winget >nul 2>&1
if errorlevel 1 (
  echo  Install Temurin 25 from https://adoptium.net and run this file again.
  goto :fail
)
echo  It can be installed now with:  winget install --id EclipseAdoptium.Temurin.25.JDK -e
choice /c YN /m "Install Java 25 now"
if errorlevel 2 goto :fail
winget install --id EclipseAdoptium.Temurin.25.JDK -e --accept-source-agreements --accept-package-agreements
echo.
echo  Close this window, open a NEW Command Prompt (so PATH is refreshed) and run this file again.
goto :fail

:done
endlocal
exit /b 0

:fail
if "%PAUSE_AT_END%"=="1" pause
endlocal
exit /b 1
