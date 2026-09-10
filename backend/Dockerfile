# RobotGuide Backend - 展厅机器人后端服务
# 从开发机 192.168.0.143:8888 自动下载代码
FROM python:3.12-slim

LABEL maintainer="RobotGuide"
LABEL description="展厅机器人后台管理服务 v3.0"

WORKDIR /app

# 安装 edge-tts（TTS 语音合成必需）
RUN pip install --no-cache-dir edge-tts -i https://mirrors.aliyun.com/pypi/simple/

# 从你的开发机下载代码（必须先在 192.168.0.143 开 python -m http.server 8888）
# 如果下载失败说明你电脑上的 8888 端口服务器没开或者被防火墙挡了
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/*

ARG DEV_HOST=192.168.0.143
ARG DEV_PORT=8888

RUN echo ">>> 从 ${DEV_HOST}:${DEV_PORT} 下载代码..." && \
    curl -fsSL "http://${DEV_HOST}:${DEV_PORT}/server.py" -o server.py && \
    curl -fsSL "http://${DEV_HOST}:${DEV_PORT}/admin.html" -o admin.html && \
    curl -fsSL "http://${DEV_HOST}:${DEV_PORT}/app_preview.html" -o app_preview.html && \
    echo ">>> 下载完成 ✓"

# 测试（构建时会打印这个）
RUN echo ">>> 检查文件..." && ls -la /app/ && python -c "import ast; ast.parse(open('server.py').read()); print('server.py 语法 OK')"

# 创建持久化目录
RUN mkdir -p /app/data/uploads

# 数据卷
ENV ROBOT_DB=/app/data/robot.db
ENV ROBOT_UPLOAD=/app/data/uploads
ENV TZ=Asia/Shanghai

EXPOSE 5000

CMD ["python", "server.py"]
