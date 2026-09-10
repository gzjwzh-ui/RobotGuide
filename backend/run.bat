@echo off
title RobotGuide Backend
echo ========================================
echo   RobotGuide Backend Server
echo ========================================
echo.

echo [1] Checking Python...
where python >nul 2>&1
if %errorlevel%==0 (
    echo     Python found:
    python --version
) else (
    echo.
    echo     !!! Python NOT FOUND !!!
    echo     Please install Python first:
    echo     https://www.python.org/downloads/
    echo     Make sure to check "Add Python to PATH"
    echo.
    pause
    exit /b 1
)

echo.
echo [2] Checking Flask...
python -c "import flask" 2>nul
if %errorlevel%==0 (
    echo     Flask OK
) else (
    echo     Installing Flask...
    python -m pip install flask flask-cors
)

echo.
echo [3] Checking port 5000...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5000" ^| findstr "LISTENING" 2^>nul') do (
    echo     Port 5000 busy, killing PID %%a...
    taskkill /PID %%a /F >nul 2>&1
)
echo     Port 5000 ready

echo.
echo [4] Starting server...
echo.
echo ========================================
echo   Open browser: http://localhost:5000
echo ========================================
echo.

cd /d "%~dp0"
python start.py

pause
