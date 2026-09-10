# -*- coding: utf-8 -*-
"""
展厅机器人后台 - 单文件版（一键启动）
运行: python start.py
依赖: flask, flask-cors  （首次自动安装）
"""
import os, sys, subprocess

# ===== 自动安装依赖 =====
def ensure_deps():
    try:
        import flask
        import flask_cors
        return True
    except ImportError:
        print("[首次运行] 正在安装依赖...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "flask", "flask-cors"])
        return True

ensure_deps()

import sqlite3, json, time
from flask import Flask, request, jsonify, send_from_directory, g
from flask_cors import CORS

BASE = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE, "robot.db")

app = Flask(__name__, static_folder=BASE, static_url_path="")
CORS(app)

# ===== 数据库 =====
def get_db():
    if not hasattr(g, "db"):
        g.db = sqlite3.connect(DB_PATH)
        g.db.row_factory = sqlite3.Row
        g.db.execute("PRAGMA journal_mode=WAL")
    return g.db

@app.teardown_appcontext
def close_db(exc):
    getattr(g, "db", None)?.close()

def init_db():
    db = sqlite3.connect(DB_PATH)
    c = db.cursor()

    c.execute("""CREATE TABLE IF NOT EXISTS qa_items (
        id INTEGER PRIMARY KEY AUTOINCREMENT, question TEXT NOT NULL, answer TEXT NOT NULL,
        keywords TEXT DEFAULT '[]', media_refs TEXT DEFAULT '[]',
        created_at INTEGER, updated_at INTEGER)""")

    c.execute("""CREATE TABLE IF NOT EXISTS robot_config (
        key TEXT PRIMARY KEY, value TEXT NOT NULL)""")

    defaults = {
        "robot_name": "小胖",
        "language": "zh-CN",
        "greeting": "您好！我是{robot_name}，有什么可以帮您的吗？",
        "auto_wake_words": "你好,您好,在吗",
        "api_key": "YOUR_ARK_API_KEY",
        "model_id": "doubao-seed-character-260628",
        "system_prompt": "你是展厅讲解机器人，性格活泼友善，回答简洁不超过三句话。",
        "match_threshold": "60",
        "use_ai": "true",
        "person_detection": "true",
        "idle_timeout": "5",
    }
    for k, v in defaults.items():
        c.execute("INSERT OR IGNORE INTO robot_config(key,value) VALUES(?,?)", (k, v))

    # 预置问答
    samples = [
        ("展厅有什么展品？", "我们展厅目前展示的是智能机器人系列产品，欢迎参观体验！", ["展厅","展品","介绍"]),
        ("你好", "您好！我是{robot_name}，很高兴见到您！", ["你好","您好"]),
        ("你是谁", "我是展厅讲解机器人，可以回答关于展厅的各种问题~", ["谁","身份"]),
        ("你叫什么名字", "我叫{robot_name}！很高兴认识您~", ["名字","叫什么"]),
        ("谢谢", "不客气！还有什么想了解的随时问我~", ["谢谢","多谢"]),
    ]
    for q, a, kw in samples:
        c.execute("SELECT COUNT(*) FROM qa_items WHERE question=?", (q,))
        if c.fetchone()[0] == 0:
            c.execute("INSERT INTO qa_items(question,answer,keywords,media_refs,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                (q, a, json.dumps(kw, ensure_ascii=False), "[]", int(time.time()), int(time.time())))

    db.commit()
    db.close()

def row_to_dict(row):
    d = dict(row)
    for k in ("keywords","media_refs"):
        if k in d and isinstance(d[k], str):
            try: d[k] = json.loads(d[k])
            except: d[k] = []
    return d

# ===== 网页入口 =====
@app.route("/")
def admin():
    return send_from_directory(BASE, "admin.html")

@app.route("/api/health")
def health():
    db = get_db()
    return jsonify(status="ok", qa_count=db.execute("SELECT COUNT(*) FROM qa_items").fetchone()[0])

# ===== QA API =====
@app.route("/api/qa", methods=["GET"])
def qa_list():
    db = get_db()
    rows = db.execute("SELECT * FROM qa_items ORDER BY updated_at DESC").fetchall()
    return jsonify([row_to_dict(r) for r in rows])

@app.route("/api/qa", methods=["POST"])
def qa_create():
    d = request.get_json(force=True)
    q = (d.get("question") or "").strip()
    a = (d.get("answer") or "").strip()
    if not q or not a: return jsonify(error="question and answer required"), 400
    db = get_db()
    cur = db.execute("INSERT INTO qa_items(question,answer,keywords,media_refs,created_at,updated_at) VALUES(?,?,?,?,?,?)",
        (q, a, json.dumps(d.get("keywords") or [], ensure_ascii=False),
         json.dumps(d.get("media_refs") or [], ensure_ascii=False), int(time.time()), int(time.time())))
    db.commit()
    return jsonify(id=cur.lastrowid)

@app.route("/api/qa/<int:qa_id>", methods=["PUT"])
def qa_update(qa_id):
    d = request.get_json(force=True)
    db = get_db()
    db.execute("UPDATE qa_items SET question=?,answer=?,keywords=?,media_refs=?,updated_at=? WHERE id=?",
        ((d.get("question") or "").strip(), (d.get("answer") or "").strip(),
         json.dumps(d.get("keywords") or [], ensure_ascii=False),
         json.dumps(d.get("media_refs") or [], ensure_ascii=False), int(time.time()), qa_id))
    db.commit()
    return jsonify(ok=True)

@app.route("/api/qa/<int:qa_id>", methods=["DELETE"])
def qa_delete(qa_id):
    db = get_db()
    db.execute("DELETE FROM qa_items WHERE id=?", (qa_id,))
    db.commit()
    return jsonify(ok=True)

# ===== Config API =====
@app.route("/api/config", methods=["GET"])
def cfg_get():
    db = get_db()
    return jsonify({r["key"]: r["value"] for r in db.execute("SELECT key,value FROM robot_config").fetchall()})

@app.route("/api/config", methods=["POST"])
def cfg_save():
    d = request.get_json(force=True)
    db = get_db()
    for k, v in d.items():
        db.execute("INSERT INTO robot_config(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (k, str(v)))
    db.commit()
    return jsonify(ok=True)

# ===== Sync API =====
@app.route("/api/sync")
def sync():
    db = get_db()
    return jsonify(
        version=int(time.time()),
        config={r["key"]: r["value"] for r in db.execute("SELECT key,value FROM robot_config").fetchall()},
        qa_items=[row_to_dict(r) for r in db.execute("SELECT * FROM qa_items").fetchall()]
    )

# ===== 启动 =====
if __name__ == "__main__":
    init_db()
    print()
    print("=" * 50)
    print("  🤖 展厅机器人后台服务")
    print("=" * 50)
    print("  网页管理: http://localhost:5000")
    print("  机器人APP填IP后即可同步")
    print("=" * 50)
    print()
    app.run(host="0.0.0.0", port=5000, debug=False)
