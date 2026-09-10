@echo off
chcp 65001 >nul
title 展厅机器人后台服务
echo ========================================
echo   展厅机器人后台服务启动中...
echo ========================================
echo.

cd /d "%~dp0"

:: 检查 Python
where python >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到 Python，请先安装 Python 3.8+
    echo 下载: https://www.python.org/downloads/
    pause
    exit /b 1
)

:: 安装依赖（首次运行）
if not exist "installed.flag" (
    echo [首次运行] 正在安装依赖...
    python -m pip install -r requirements.txt
    if errorlevel 1 (
        echo [错误] 依赖安装失败
        pause
        exit /b 1
    )
    echo. > installed.flag
)

:: 获取本机IP给用户看
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4" ^| findstr /v "127.0.0.1"') do (
    set IP=%%a
    goto :found_ip
)
:found_ip

echo.
echo   本机IP: %IP%
echo.
echo   👉 网页管理后台: http://localhost:5000
echo   👉 局域网设备访问: http://%IP%:5000
echo   👉 机器人APP后端地址填: http://%IP%:5000
echo.
echo ========================================
echo.

python app.py

pause
