@echo off
setlocal
chcp 65001 >nul
set "ROOT=%~dp0"
if not exist "%ROOT%build\ApiKey_Scan.jar" (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%build.ps1"
  if errorlevel 1 exit /b %ERRORLEVEL%
)
java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -jar "%ROOT%build\ApiKey_Scan.jar" --root "%ROOT%" %*
exit /b %ERRORLEVEL%

