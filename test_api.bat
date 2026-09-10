@echo off
chcp 65001 >nul
echo ========================================
echo 豆包 AI API 连通性测试
echo ========================================
echo.

curl.exe -s https://ark.cn-beijing.volces.com/api/v3/chat/completions ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer YOUR_ARK_API_KEY" ^
  -d "{\"model\":\"doubao-seed-character-260628\",\"messages\":[{\"role\":\"system\",\"content\":\"你是展厅讲解机器人。\"},{\"role\":\"user\",\"content\":\"你好，介绍一下自己\"}]}"

echo.
echo.
pause
