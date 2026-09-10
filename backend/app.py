# -*- coding: utf-8 -*-
"""
展厅机器人后台服务
提供 REST API + 网页管理界面
运行: python app.py  (默认 http://0.0.0.0:5000)
"""
import os
import sqlite3
import json
import time
from flask import Flask, request, jsonify, send_from_directory, g
from flask_cors import CORS

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "robot.db")
STATIC_DIR = os.path.join(BASE_DIR, "static")

app = Flask(__name__, static_folder=STATIC_DIR, static_url_path="/static")
CORS(app)  # 允许网页和Android跨域访问

# ====================== 数据库 ======================

def get_db():
    if not hasattr(g, "db"):
        g.db = sqlite3.connect(DB_PATH)
        g.db.row_factory = sqlite3.Row
        g.db.execute("PRAGMA journal_mode=WAL")
    return g.db

@app.teardown_appcontext
def close_db(exc):
    db = getattr(g, "db", None)
    if db is not None:
        db.close()

def init_db():
    db = sqlite3.connect(DB_PATH)
    c = db.cursor()

    # 问答库
    c.execute("""
        CREATE TABLE IF NOT EXISTS qa_items (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            question TEXT NOT NULL,
            answer TEXT NOT NULL,
            keywords TEXT DEFAULT '[]',
            media_refs TEXT DEFAULT '[]',
            created_at INTEGER,
            updated_at INTEGER
        )
    """)

    # 机器人配置
    c.execute("""
        CREATE TABLE IF NOT EXISTS robot_config (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
    """)

    # 初始化默认配置
    defaults = {
        "robot_name": "小胖",
        "robot_avatar": "default",
        "language": "zh-CN",              # zh-CN 普通话  yue-HK 粤语
        "greeting": "您好！我是{robot_name}，有什么可以帮到您的吗？",
        "auto_wake_words": "你好,您好,在吗",
        "api_key": "YOUR_ARK_API_KEY",
        "model_id": "doubao-seed-character-260628",
        "system_prompt": "你是展厅讲解机器人，性格活泼友善，回答简洁不超过三句话。",
        "match_threshold": "60",
        "use_ai": "true",
        "person_detection": "true",       # 开启人体检测自动唤醒
        "idle_timeout": "5",              # 无操作N分钟后回到待机
    }
    for k, v in defaults.items():
        c.execute("INSERT OR IGNORE INTO robot_config(key,value) VALUES(?,?)", (k, v))

    # 预置几条示例问答
    sample_qa = [
        ("展厅有什么展品？", "我们展厅目前展示的是智能机器人系列产品，欢迎参观体验！", ["展厅", "展品", "介绍"], []),
        ("你好", "您好！我是{robot_name}，很高兴见到您！有什么可以帮到您的吗？", ["你好", "您好", "hi"], []),
        ("你是谁", "我是展厅讲解机器人，可以回答关于展厅和展品的各种问题~", ["谁", "身份"], []),
    ]
    for q, a, kw, mr in sample_qa:
        c.execute("SELECT COUNT(*) FROM qa_items WHERE question=?", (q,))
        if c.fetchone()[0] == 0:
            c.execute(
                "INSERT INTO qa_items(question,answer,keywords,media_refs,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                (q, a, json.dumps(kw, ensure_ascii=False), json.dumps(mr, ensure_ascii=False),
                 int(time.time()), int(time.time()))
            )

    db.commit()
    db.close()
    print(f"[INFO] 数据库初始化完成: {DB_PATH}")

# ====================== 工具函数 ======================

def row_to_dict(row):
    d = dict(row)
    for k in ("keywords", "media_refs"):
        if k in d and isinstance(d[k], str):
            try:
                d[k] = json.loads(d[k])
            except:
                d[k] = []
    return d

# ====================== 网页入口 ======================

@app.route("/")
def admin_page():
    return send_from_directory(BASE_DIR, "admin.html")

@app.route("/api")
def api_index():
    return jsonify({
        "status": "ok",
        "version": "1.0",
        "endpoints": {
            "qa": "/api/qa",
            "config": "/api/config",
            "health": "/api/health",
        }
    })

# ====================== 健康检查 ======================

@app.route("/api/health")
def health():
    db = get_db()
    qa_count = db.execute("SELECT COUNT(*) FROM qa_items").fetchone()[0]
    cfg_count = db.execute("SELECT COUNT(*) FROM robot_config").fetchone()[0]
    return jsonify({
        "status": "ok",
        "qa_count": qa_count,
        "config_count": cfg_count,
        "timestamp": int(time.time())
    })

# ====================== 问答库 API ======================

@app.route("/api/qa", methods=["GET"])
def qa_list():
    db = get_db()
    rows = db.execute("SELECT * FROM qa_items ORDER BY updated_at DESC").fetchall()
    return jsonify([row_to_dict(r) for r in rows])

@app.route("/api/qa/<int:qa_id>", methods=["GET"])
def qa_get(qa_id):
    db = get_db()
    row = db.execute("SELECT * FROM qa_items WHERE id=?", (qa_id,)).fetchone()
    if not row:
        return jsonify({"error": "not found"}), 404
    return jsonify(row_to_dict(row))

@app.route("/api/qa", methods=["POST"])
def qa_create():
    data = request.get_json(force=True)
    question = (data.get("question") or "").strip()
    answer = (data.get("answer") or "").strip()
    if not question or not answer:
        return jsonify({"error": "question and answer required"}), 400
    keywords = json.dumps(data.get("keywords") or [], ensure_ascii=False)
    media_refs = json.dumps(data.get("media_refs") or [], ensure_ascii=False)
    ts = int(time.time())
    db = get_db()
    cur = db.execute(
        "INSERT INTO qa_items(question,answer,keywords,media_refs,created_at,updated_at) VALUES(?,?,?,?,?,?)",
        (question, answer, keywords, media_refs, ts, ts)
    )
    db.commit()
    return jsonify({"id": cur.lastrowid})

@app.route("/api/qa/<int:qa_id>", methods=["PUT"])
def qa_update(qa_id):
    data = request.get_json(force=True)
    db = get_db()
    exists = db.execute("SELECT id FROM qa_items WHERE id=?", (qa_id,)).fetchone()
    if not exists:
        return jsonify({"error": "not found"}), 404
    question = (data.get("question") or "").strip()
    answer = (data.get("answer") or "").strip()
    keywords = json.dumps(data.get("keywords") or [], ensure_ascii=False)
    media_refs = json.dumps(data.get("media_refs") or [], ensure_ascii=False)
    db.execute(
        "UPDATE qa_items SET question=?,answer=?,keywords=?,media_refs=?,updated_at=? WHERE id=?",
        (question, answer, keywords, media_refs, int(time.time()), qa_id)
    )
    db.commit()
    return jsonify({"ok": True})

@app.route("/api/qa/<int:qa_id>", methods=["DELETE"])
def qa_delete(qa_id):
    db = get_db()
    db.execute("DELETE FROM qa_items WHERE id=?", (qa_id,))
    db.commit()
    return jsonify({"ok": True})

@app.route("/api/qa/clear", methods=["POST"])
def qa_clear():
    db = get_db()
    db.execute("DELETE FROM qa_items")
    db.commit()
    return jsonify({"ok": True})

# ====================== 配置 API ======================

@app.route("/api/config", methods=["GET"])
def config_get():
    db = get_db()
    rows = db.execute("SELECT key,value FROM robot_config").fetchall()
    cfg = {r["key"]: r["value"] for r in rows}
    return jsonify(cfg)

@app.route("/api/config", methods=["POST"])
def config_update():
    data = request.get_json(force=True)
    db = get_db()
    ts = int(time.time())
    for k, v in data.items():
        db.execute(
            "INSERT INTO robot_config(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
            (k, str(v))
        )
    db.commit()
    return jsonify({"ok": True})

@app.route("/api/config/<key>", methods=["GET"])
def config_get_one(key):
    db = get_db()
    row = db.execute("SELECT value FROM robot_config WHERE key=?", (key,)).fetchone()
    if not row:
        return jsonify({"error": "not found"}), 404
    return jsonify({"key": key, "value": row["value"]})

# ====================== 批量同步 ======================

@app.route("/api/sync", methods=["GET"])
def sync_all():
    """Android机器人拉取全量数据"""
    db = get_db()
    qa_rows = db.execute("SELECT * FROM qa_items").fetchall()
    cfg_rows = db.execute("SELECT key,value FROM robot_config").fetchall()
    return jsonify({
        "version": int(time.time()),
        "config": {r["key"]: r["value"] for r in cfg_rows},
        "qa_items": [row_to_dict(r) for r in qa_rows]
    })

# ====================== 启动 ======================

if __name__ == "__main__":
    init_db()
    # 监听所有网卡，局域网内其他设备可以通过电脑IP访问
    print()
    print("=" * 50)
    print("  展厅机器人后台服务启动中...")
    print("  网页管理界面: http://localhost:5000")
    print("  局域网访问:    http://你的电脑IP:5000")
    print("=" * 50)
    print()
    app.run(host="0.0.0.0", port=5000, debug=True)
