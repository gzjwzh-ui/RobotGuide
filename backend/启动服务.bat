@echo off
chcp 65001 >nul
title 环境诊断 - 展厅机器人后端
echo ========================================
echo   诊断环境...
echo ========================================
echo.

echo [1] 检测 Python...
where python >nul 2>&1
if %errorlevel%==0 (
    echo     ✅ Python 已安装
    python --version
) else (
    echo     ❌ 未检测到 Python！
    echo.
    echo 👉 请先安装 Python: https://www.python.org/downloads/
    echo    安装时务必勾选 "Add Python to PATH"
    echo.
    pause
    exit /b 1
)

echo.
echo [2] 检测 Flask 依赖...
python -c "import flask" 2>nul
if %errorlevel%==0 (
    echo     ✅ Flask 已安装
) else (
    echo     ❌ Flask 未安装
    echo     正在自动安装...
    python -m pip install flask flask-cors
)

echo.
echo [3] 检测 5000 端口...
netstat -ano | findstr ":5000" | findstr "LISTENING" >nul 2>&1
if %errorlevel%==0 (
    echo     ⚠️  5000 端口已被占用，尝试释放...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5000" ^| findstr "LISTENING"') do (
        taskkill /PID %%a /F >nul 2>&1
    )
) else (
    echo     ✅ 5000 端口空闲
)

echo.
echo [4] 启动后端...
echo.
echo ========================================
echo.

cd /d "%~dp0"
python start.py

pause
