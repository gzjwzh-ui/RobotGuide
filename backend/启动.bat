@echo off
title RobotGuide Backend Server
echo.
echo ========================================
echo   Starting RobotGuide Backend...
echo ========================================
echo.
cd /d "%~dp0"
python server.py
pause
