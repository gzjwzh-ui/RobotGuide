# -*- coding: utf-8 -*-
"""
豆包 API 连通性测试
运行方式: 双击运行 或 在终端执行  python test_api.py
"""
import urllib.request
import json

# ============ 你的配置 ============
API_KEY = "YOUR_ARK_API_KEY"
MODEL_ID = "doubao-seed-character-260628"
ENDPOINT = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
# ==================================

print("=" * 50)
print("豆包 AI API 连通性测试")
print("=" * 50)
print(f"模型: {MODEL_ID}")
print(f"Key:  {API_KEY[:10]}...{API_KEY[-6:]}")
print()

# 构造请求
payload = {
    "model": MODEL_ID,
    "messages": [
        {"role": "system", "content": "你是展厅讲解机器人。"},
        {"role": "user", "content": "你好，请用一句话介绍你自己"}
    ]
}

req = urllib.request.Request(
    ENDPOINT,
    data=json.dumps(payload).encode("utf-8"),
    headers={
        "Content-Type": "application/json",
        "Authorization": f"Bearer {API_KEY}"
    }
)

try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        body = json.loads(resp.read())
        content = body["choices"][0]["message"]["content"]
        usage = body.get("usage", {})
        
        print("✅ API 调用成功!")
        print()
        print("🤖 机器人回复:")
        print("-" * 50)
        print(content)
        print("-" * 50)
        print(f"📊 Token 消耗: {usage.get('total_tokens', 'N/A')} "
              f"(输入 {usage.get('prompt_tokens', '?')} + 输出 {usage.get('completion_tokens', '?')})")
        
except urllib.error.HTTPError as e:
    err_body = e.read().decode()
    print(f"❌ HTTP 错误 {e.code}: {err_body}")
except urllib.error.URLError as e:
    print(f"❌ 网络错误: {e.reason}")
except Exception as e:
    print(f"❌ 未知错误: {e}")

print()
input("按回车键退出...")
