@echo off
setlocal

set GRADLE_VERSION=8.8
set DIST_NAME=gradle-%GRADLE_VERSION%-bin
set DIST_URL=https://services.gradle.org/distributions/%DIST_NAME%.zip
if "%GRADLE_USER_HOME%"=="" (
  set BASE_DIR=%USERPROFILE%\.gradle\wrapper\dists\%DIST_NAME%
) else (
  set BASE_DIR=%GRADLE_USER_HOME%\wrapper\dists\%DIST_NAME%
)
set GRADLE_HOME=%BASE_DIR%\gradle-%GRADLE_VERSION%
set ZIP_PATH=%BASE_DIR%\%DIST_NAME%.zip

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%BASE_DIR%" mkdir "%BASE_DIR%"
  if not exist "%ZIP_PATH%" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%ZIP_PATH%'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP_PATH%' -DestinationPath '%BASE_DIR%' -Force"
)

call "%GRADLE_HOME%\bin\gradle.bat" %*
