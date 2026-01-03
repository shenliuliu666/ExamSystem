@echo off
setlocal

set BASE_DIR=%~dp0
if "%BASE_DIR:~-1%"=="\" set BASE_DIR=%BASE_DIR:~0,-1%
set WRAPPER_DIR=%BASE_DIR%\.mvn\wrapper
set WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar
set WRAPPER_PROPERTIES=%WRAPPER_DIR%\maven-wrapper.properties

if not exist "%WRAPPER_PROPERTIES%" (
  echo Missing "%WRAPPER_PROPERTIES%"
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in ("%WRAPPER_PROPERTIES%") do (
  if "%%A"=="distributionUrl" set DISTRIBUTION_URL=%%B
  if "%%A"=="wrapperUrl" set WRAPPER_URL=%%B
)

if not exist "%WRAPPER_JAR%" (
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ProgressPreference='SilentlyContinue';" ^
    "Invoke-WebRequest -UseBasicParsing -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'" || exit /b 1
)

java "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" -cp "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
endlocal
