# -*- coding: utf-8 -*-
"""
展厅机器人后台 v3.1
新增: 车辆管理 CRUD + 图片上传 + 机器动作/状态 API
"""
import os, json, sqlite3, time, asyncio, tempfile, uuid, mimetypes, re, threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

# ===== edge-tts =====
try:
    import edge_tts
    HAS_EDGE_TTS = True
except ImportError:
    HAS_EDGE_TTS = False

VOICE_LIBRARY = {
    "zh-CN": [
        {"name": "晓晓 (女)", "voice": "zh-CN-XiaoxiaoNeural"},
        {"name": "云希 (男)", "voice": "zh-CN-YunxiNeural"},
        {"name": "晓伊 (温柔女)", "voice": "zh-CN-XiaoyiNeural"},
        {"name": "云健 (新闻男)", "voice": "zh-CN-YunjianNeural"},
    ],
    "zh-HK": [
        {"name": "晓佳 (女)", "voice": "zh-HK-HiuGaaiNeural"},
        {"name": "晓欣 (女)", "voice": "zh-HK-HiuMaanNeural"},
        {"name": "龙坤 (男)", "voice": "zh-HK-WanLungNeural"},
    ],
    "en-US": [
        {"name": "Jenny (女)", "voice": "en-US-JennyNeural"},
        {"name": "Guy (男)", "voice": "en-US-GuyNeural"},
        {"name": "Aria (自然女)", "voice": "en-US-AriaNeural"},
    ],
}

BASE = os.path.dirname(os.path.abspath(__file__))
# 支持 Docker 环境变量覆盖，本地运行用默认路径
DB_PATH = os.environ.get("ROBOT_DB") or os.path.join(BASE, "robot.db")
UPLOAD_DIR = os.environ.get("ROBOT_UPLOAD") or os.path.join(BASE, "uploads")
os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
os.makedirs(UPLOAD_DIR, exist_ok=True)

# ===== DB =====
def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    db = sqlite3.connect(DB_PATH)
    c = db.cursor()

    c.execute("""CREATE TABLE IF NOT EXISTS qa_libraries (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        description TEXT DEFAULT '',
        is_default INTEGER DEFAULT 0,
        language TEXT DEFAULT 'all',
        created_at INTEGER)""")

    c.execute("""CREATE TABLE IF NOT EXISTS qa_items (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        library_id INTEGER DEFAULT 1,
        question TEXT NOT NULL,
        question_en TEXT DEFAULT '',
        question_yue TEXT DEFAULT '',
        answer TEXT NOT NULL,
        answer_en TEXT DEFAULT '',
        answer_yue TEXT DEFAULT '',
        keywords TEXT DEFAULT '[]',
        media_refs TEXT DEFAULT '[]',
        created_at INTEGER,
        updated_at INTEGER)""")

    c.execute("""CREATE TABLE IF NOT EXISTS robot_config (
        key TEXT PRIMARY KEY, value TEXT NOT NULL)""")

    # ===== 新增: 车辆表 =====
    c.execute("""CREATE TABLE IF NOT EXISTS vehicles (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,           -- 高尔夫, 速腾L, 迈腾
        brand TEXT DEFAULT '',        -- 大众
        year TEXT DEFAULT '',         -- 2024
        category TEXT DEFAULT '',     -- 轿车/SUV/新能源
        description TEXT DEFAULT '',   -- 中文简介
        description_en TEXT DEFAULT '',
        description_yue TEXT DEFAULT '',
        thumbnail TEXT DEFAULT '',    -- 封面图路径 /uploads/xxx.jpg
        sort_order INTEGER DEFAULT 0,
        created_at INTEGER,
        updated_at INTEGER)""")

    c.execute("""CREATE TABLE IF NOT EXISTS vehicle_images (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        vehicle_id INTEGER NOT NULL,
        filename TEXT NOT NULL,       -- 原始文件名
        stored_path TEXT NOT NULL,    -- /uploads/uuid.jpg
        caption TEXT DEFAULT '',      -- 图片说明
        sort_order INTEGER DEFAULT 0,
        created_at INTEGER)""")

    # ===== 新增: 视频表 =====
    c.execute("""CREATE TABLE IF NOT EXISTS videos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,           -- 视频名称
        filename TEXT NOT NULL,       -- 原始文件名
        stored_path TEXT NOT NULL,    -- /uploads/uuid.mp4
        description TEXT DEFAULT '',  -- 视频简介
        sort_order INTEGER DEFAULT 0,
        created_at INTEGER)""")

    # 默认数据
    if c.execute("SELECT COUNT(*) FROM qa_libraries").fetchone()[0] == 0:
        c.execute("INSERT INTO qa_libraries(name,description,is_default,language,created_at) VALUES(?,?,?,?,?)",
            ("通用问候", "打招呼和礼貌用语", 1, "all", int(time.time())))
        c.execute("INSERT INTO qa_libraries(name,description,is_default,language,created_at) VALUES(?,?,?,?,?)",
            ("健稳汽车历史", "关于健稳汽车品牌和发展历程", 0, "all", int(time.time())))

    # 默认车辆 (name, brand, year, category, desc, desc_en, desc_yue, sort_order)
    if c.execute("SELECT COUNT(*) FROM vehicles").fetchone()[0] == 0:
        vehicles = [
            ("高尔夫", "大众", "2024", "轿车", "经典两厢车，动感时尚，适合年轻人。",
             "Classic hatchback, sporty and stylish.",
             "經典兩廂車，動感時尚，適合年輕人。", 1),
            ("速腾L", "大众", "2024", "轿车", "加长轴距，后排空间宽敞，家用首选。",
             "Extended wheelbase, spacious rear row.",
             "加長軸距，後排空間寬敞，家用首選。", 2),
            ("迈腾", "大众", "2024", "轿车", "商务中型轿车，低调稳重，动力充沛。",
             "Business mid-size sedan, powerful and elegant.",
             "商務中型轎車，低調穩重，動力充沛。", 3),
            ("途观L", "大众", "2024", "SUV", "中型SUV，通过性好，适合家庭出游。",
             "Mid-size SUV, great for family trips.",
             "中型SUV，通過性好，適合家庭出遊。", 4),
            ("ID.4", "大众", "2024", "新能源", "纯电动SUV，续航500km+，智能驾驶。",
             "Pure electric SUV, 500km+ range, smart driving.",
             "純電動SUV，續航500km+，智能駕駛。", 5),
        ]
        now = int(time.time())
        for v in vehicles:
            # 列顺序: name,brand,year,category,description,description_en,description_yue,thumbnail,sort_order,created_at,updated_at
            c.execute("""INSERT INTO vehicles(name,brand,year,category,description,description_en,description_yue,thumbnail,sort_order,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)""",
                (v[0], v[1], v[2], v[3], v[4], v[5], v[6], "", v[7], now, now))

    defaults = {
        "robot_name": "小胖", "language": "zh-CN",
        "greeting": "您好！我是{robot_name}，有什么可以帮您的吗？",
        "auto_wake_words": "你好,您好,在吗",
        "api_key": "YOUR_ARK_API_KEY",
        "model_id": "doubao-seed-character-260628",
        "system_prompt": "你是展厅讲解机器人，性格活泼友善，回答简洁不超过三句话。",
        "match_threshold": "60", "use_ai": "true",
        "person_detection": "true", "idle_timeout": "5",
    }
    for k, v in defaults.items():
        c.execute("INSERT OR IGNORE INTO robot_config(key,value) VALUES(?,?)", (k, v))

    # 修复旧库中可能存在的非普通话语言值 → "zh-CN"
    c.execute("UPDATE robot_config SET value='zh-CN' WHERE key='language' AND value IN ('auto','yue-HK','en-US')")

    db.commit()
    db.close()

def row_to_dict(row):
    d = dict(row)
    for k in ("keywords","media_refs"):
        if k in d and isinstance(d[k], str):
            try: d[k] = json.loads(d[k])
            except: d[k] = []
    return d


# ===== 机器人动作注册表（对齐实体机器控制面板） =====
ROBOT_ACTIONS = [
    # 底座移动
    {"code":"base_turn_left_90", "group":"底座移动", "name":"左转90°", "duration_ms":2500, "hardware":"base"},
    {"code":"base_turn_right_90","group":"底座移动", "name":"右转90°", "duration_ms":2500, "hardware":"base"},
    {"code":"base_forward_1m",    "group":"底座移动", "name":"前进1米", "duration_ms":3500, "hardware":"base"},
    {"code":"base_backward_1m",   "group":"底座移动", "name":"后退1米", "duration_ms":3500, "hardware":"base"},
    {"code":"base_stop",          "group":"底座移动", "name":"底座停止", "duration_ms":500,  "hardware":"base"},
    # 头部 / 手臂
    {"code":"head_left",          "group":"头部",    "name":"头部左",   "duration_ms":800,  "hardware":"head"},
    {"code":"head_right",         "group":"头部",    "name":"头部右",   "duration_ms":800,  "hardware":"head"},
    {"code":"head_up",            "group":"头部",    "name":"头部上",   "duration_ms":800,  "hardware":"head"},
    {"code":"head_down",          "group":"头部",    "name":"头部下",   "duration_ms":800,  "hardware":"head"},
    {"code":"head_reset_all",     "group":"头部",    "name":"头部手臂复位","duration_ms":1200,"hardware":"head"},
    {"code":"head_reset",         "group":"头部",    "name":"头部复位", "duration_ms":1000, "hardware":"head"},
    # 灯光
    {"code":"ear_led_on",         "group":"灯光",    "name":"耳朵灯开", "duration_ms":300,  "hardware":"led"},
    {"code":"ear_led_off",        "group":"灯光",    "name":"耳朵灯关", "duration_ms":300,  "hardware":"led"},
    {"code":"eye_led_on",         "group":"灯光",    "name":"眼睛灯开", "duration_ms":300,  "hardware":"led"},
    {"code":"eye_led_off",        "group":"灯光",    "name":"眼睛灯关", "duration_ms":300,  "hardware":"led"},
    # 组合动作
    {"code":"combo_stretch",      "group":"组合动作","name":"活动筋骨", "duration_ms":5000, "hardware":"combo"},
]
ACTION_MAP = {a["code"]: a for a in ROBOT_ACTIONS}

# 内部状态（模拟硬件反馈，未来接真实串口/网络时替换 setter）
robot_state_lock = threading.Lock()
ROBOT_STATE = {
    "head":        {"angle":0, "status":"idle"},   # angle: -30~30 deg, status: idle/moving/resetting
    "base":        {"moving":False, "direction":"idle"},
    "led":         {"ear":True, "eye":True},        # 默认都亮
    "sensors": {
        "infrared": {
            "right": 0,
            "left":  0,
            "fcc":   0,
            "top":   0,
        },
        "ultrasonic": {
            "rear":  255,
            "front": 255,
            "left_center":  255,
            "mid_left":     255,
            "mid_center":   255,
            "right_center": 255,
            "right_side":   255,
        },
        "laser":   "ok",
        "human_detected": False,
        "position": {"x":0, "y":0, "theta":0},
    },
    "last_action": None,
    "last_action_at": 0,
    "hardware_connected": False,  # True = 已连真实硬件
}


def _apply_action_state(action_code: str):
    """根据动作码更新内部状态（模拟执行效果）"""
    with robot_state_lock:
        s = ROBOT_STATE
        now = int(time.time() * 1000)
        s["last_action"] = action_code
        s["last_action_at"] = now
        act = ACTION_MAP.get(action_code)
        if not act:
            return
        hw = act["hardware"]
        if hw == "base":
            s["base"]["moving"] = action_code != "base_stop"
            dir_map = {
                "base_forward_1m":"forward", "base_backward_1m":"backward",
                "base_turn_left_90":"turn_left", "base_turn_right_90":"turn_right",
                "base_stop":"idle",
            }
            s["base"]["direction"] = dir_map.get(action_code, "idle")
        elif hw == "head":
            s["head"]["status"] = "moving" if "reset" not in action_code else "resetting"
        elif hw == "led":
            if action_code == "ear_led_on":  s["led"]["ear"] = True
            if action_code == "ear_led_off": s["led"]["ear"] = False
            if action_code == "eye_led_on":  s["led"]["eye"] = True
            if action_code == "eye_led_off": s["led"]["eye"] = False
        elif hw == "combo":
            s["base"]["moving"] = True
            s["head"]["status"] = "moving"

# ===== HTTP =====
class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args): pass

    def send_json(self, data, code=200):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_html(self, path):
        try:
            with open(path, "rb") as f: body = f.read()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except FileNotFoundError:
            self.send_json({"error":"not found"}, 404)

    def send_file(self, fpath, content_type=None):
        try:
            with open(fpath, "rb") as f: body = f.read()
            ct = content_type or mimetypes.guess_type(fpath)[0] or "application/octet-stream"
            self.send_response(200)
            self.send_header("Content-Type", ct)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except FileNotFoundError:
            self.send_json({"error":"not found"}, 404)

    def read_body(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length).decode("utf-8") if length else "{}"
        try: return json.loads(raw)
        except: return {}

    # ===== GET =====
    def do_GET(self):
        path = urlparse(self.path).path.rstrip("/") or "/"
        qs = parse_qs(urlparse(self.path).query)

        if path == "/": self.send_html(os.path.join(BASE, "admin.html")); return
        if path == "/app-preview": self.send_html(os.path.join(BASE, "app_preview.html")); return

        # 静态文件 - 图片
        if path.startswith("/uploads/"):
            fname = path.split("/uploads/")[1]
            fpath = os.path.join(UPLOAD_DIR, fname)
            if os.path.isfile(fpath): self.send_file(fpath); return
            self.send_json({"error":"not found"}, 404); return

        if path == "/api/health":
            db = get_db()
            self.send_json({"status":"ok",
                "qa_count": db.execute("SELECT COUNT(*) FROM qa_items").fetchone()[0],
                "lib_count": db.execute("SELECT COUNT(*) FROM qa_libraries").fetchone()[0],
                "vehicle_count": db.execute("SELECT COUNT(*) FROM vehicles").fetchone()[0]})
            db.close(); return

        # === 车辆 API ===
        if path == "/api/vehicles":
            db = get_db()
            rows = db.execute("SELECT * FROM vehicles ORDER BY sort_order ASC, id ASC").fetchall()
            result = []
            for r in rows:
                imgs = db.execute("SELECT * FROM vehicle_images WHERE vehicle_id=? ORDER BY sort_order ASC", (r["id"],)).fetchall()
                vd = dict(r)
                vd["images"] = [dict(i) for i in imgs]
                result.append(vd)
            db.close()
            self.send_json(result); return

        if path.startswith("/api/vehicles/"):
            vid = path.split("/")[-1]
            if vid.isdigit():
                db = get_db()
                row = db.execute("SELECT * FROM vehicles WHERE id=?", (int(vid),)).fetchone()
                if not row: db.close(); self.send_json({"error":"not found"}, 404); return
                imgs = db.execute("SELECT * FROM vehicle_images WHERE vehicle_id=? ORDER BY sort_order ASC", (int(vid),)).fetchall()
                vd = dict(row)
                vd["images"] = [dict(i) for i in imgs]
                db.close()
                self.send_json(vd); return

        # 图片列表
        if path.startswith("/api/vehicle-images/"):
            vid = path.split("/")[-1]
            if vid.isdigit():
                db = get_db()
                rows = db.execute("SELECT * FROM vehicle_images WHERE vehicle_id=? ORDER BY sort_order ASC", (int(vid),)).fetchall()
                db.close()
                self.send_json([dict(r) for r in rows]); return

        # === 视频 API ===
        if path == "/api/videos":
            db = get_db()
            rows = db.execute("SELECT * FROM videos ORDER BY sort_order ASC, id ASC").fetchall()
            result = []
            for r in rows:
                vd = dict(r)
                vd["url"] = vd["stored_path"]
                result.append(vd)
            db.close()
            self.send_json(result); return

        # === QA API ===
        if path == "/api/libraries":
            db = get_db()
            rows = db.execute("SELECT * FROM qa_libraries ORDER BY is_default DESC, id ASC").fetchall()
            result = []
            for r in rows:
                cnt = db.execute("SELECT COUNT(*) FROM qa_items WHERE library_id=?", (r["id"],)).fetchone()[0]
                result.append({**dict(r), "qa_count": cnt})
            db.close()
            self.send_json(result); return

        if path == "/api/qa":
            db = get_db()
            lib_id = qs.get("library_id", [""])[0]
            sql = "SELECT * FROM qa_items"
            params = []
            if lib_id: sql += " WHERE library_id=?"; params.append(int(lib_id))
            sql += " ORDER BY updated_at DESC"
            rows = db.execute(sql, params).fetchall()
            db.close()
            self.send_json([row_to_dict(r) for r in rows]); return

        if path.startswith("/api/qa/"):
            qid = path.split("/")[-1]
            if qid.isdigit():
                db = get_db()
                row = db.execute("SELECT * FROM qa_items WHERE id=?", (int(qid),)).fetchone()
                db.close()
                self.send_json(row_to_dict(row) if row else {"error":"not found"}, 200 if row else 404); return

        if path == "/api/config":
            db = get_db()
            rows = db.execute("SELECT key,value FROM robot_config").fetchall()
            db.close()
            self.send_json({r["key"]: r["value"] for r in rows}); return

        if path == "/api/sync":
            db = get_db()
            cfg = {r["key"]: r["value"] for r in db.execute("SELECT key,value FROM robot_config").fetchall()}
            libs = [dict(r) for r in db.execute("SELECT * FROM qa_libraries").fetchall()]
            qa = [row_to_dict(r) for r in db.execute("SELECT * FROM qa_items").fetchall()]
            vhs = []
            for v in db.execute("SELECT * FROM vehicles ORDER BY sort_order ASC").fetchall():
                imgs = [dict(i) for i in db.execute("SELECT * FROM vehicle_images WHERE vehicle_id=?", (v["id"],)).fetchall()]
                vd = dict(v); vd["images"] = imgs; vhs.append(vd)
            db.close()
            self.send_json({"version":int(time.time()), "config":cfg, "libraries":libs, "qa_items":qa, "vehicles":vhs}); return

        if path == "/api/voices":
            self.send_json({"available": HAS_EDGE_TTS, "voices": VOICE_LIBRARY}); return

        # === 机器人：列出所有支持的动作 ===
        if path == "/api/robot/actions":
            self.send_json({
                "version": 1,
                "hardware_connected": ROBOT_STATE["hardware_connected"],
                "actions": ROBOT_ACTIONS,
                "groups": sorted({a["group"] for a in ROBOT_ACTIONS}),
            }); return

        # === 机器人：查询当前状态（硬件反馈） ===
        if path == "/api/robot/status":
            with robot_state_lock:
                snapshot = json.loads(json.dumps(ROBOT_STATE))  # 深拷贝
            # 如果没真实硬件，模拟轻微传感器抖动让 UI 有动态感
            import random as _r
            snapshot["sensors"]["ultrasonic"]["front"]  = max(0, min(255, 255 - int(_r.uniform(0, 6))))
            snapshot["sensors"]["ultrasonic"]["mid_center"] = max(0, min(255, 255 - int(_r.uniform(0, 6))))
            self.send_json(snapshot); return

        self.send_json({"error":"not found"}, 404)

    # ===== POST =====
    def do_POST(self):
        path = urlparse(self.path).path.rstrip("/") or "/"

        # 图片上传 (multipart/form-data)
        if path == "/api/upload":
            ct = self.headers.get("Content-Type", "")
            if "multipart/form-data" not in ct:
                self.send_json({"error":"need multipart"}, 400); return
            boundary = ct.split("boundary=")[-1].encode()
            length = int(self.headers.get("Content-Length", 0))
            raw = self.rfile.read(length)
            parts = raw.split(b"--" + boundary)
            uploaded = []
            for part in parts:
                if not part.strip() or b"--" in part[:20]: continue
                header_end = part.find(b"\r\n\r\n")
                if header_end < 0: continue
                headers = part[:header_end].decode(errors="ignore")
                # 文件名
                m = re.search(r'filename="([^"]*)"', headers)
                if not m: continue
                orig_name = m.group(1)
                # 找到 form field (vehicle_id)
                fm = re.search(r'name="([^"]*)"', headers)
                field_name = fm.group(1) if fm else ""
                body = part[header_end+4:]
                if body.endswith(b"\r\n"): body = body[:-2]
                if field_name == "vehicle_id":
                    vid = body.decode().strip()
                    continue
                if field_name == "file" or field_name == "files":
                    ext = os.path.splitext(orig_name)[1] or ".jpg"
                    new_name = f"{uuid.uuid4().hex[:16]}{ext}"
                    fpath = os.path.join(UPLOAD_DIR, new_name)
                    with open(fpath, "wb") as f: f.write(body)
                    uploaded.append({"original": orig_name, "stored": f"/uploads/{new_name}", "size": len(body)})
            self.send_json({"files": uploaded, "vehicle_id": vid if 'vid' in dir() else ""}); return

        # 车辆图片单独 POST
        if path.startswith("/api/vehicles/") and path.endswith("/images"):
            vid = path.split("/")[3]
            if not vid.isdigit(): self.send_json({"error":"bad id"}, 400); return
            ct = self.headers.get("Content-Type", "")
            if "multipart/form-data" not in ct: self.send_json({"error":"need multipart"}, 400); return
            boundary = ct.split("boundary=")[-1].encode()
            length = int(self.headers.get("Content-Length", 0))
            raw = self.rfile.read(length)
            parts = raw.split(b"--" + boundary)
            db = get_db()
            for part in parts:
                if not part.strip() or b"--" in part[:20]: continue
                header_end = part.find(b"\r\n\r\n")
                if header_end < 0: continue
                headers = part[:header_end].decode(errors="ignore")
                m = re.search(r'filename="([^"]*)"', headers)
                if not m: continue
                orig_name = m.group(1)
                fm = re.search(r'name="([^"]*)"', headers)
                field_name = fm.group(1) if fm else ""
                body = part[header_end+4:]
                if body.endswith(b"\r\n"): body = body[:-2]
                if field_name == "file":
                    ext = os.path.splitext(orig_name)[1] or ".jpg"
                    new_name = f"{uuid.uuid4().hex[:16]}{ext}"
                    fpath = os.path.join(UPLOAD_DIR, new_name)
                    with open(fpath, "wb") as f: f.write(body)
                    cur = db.execute("INSERT INTO vehicle_images(vehicle_id,filename,stored_path,caption,sort_order,created_at) VALUES(?,?,?,?,?,?)",
                        (int(vid), orig_name, f"/uploads/{new_name}", "", 0, int(time.time())))
                    # 如果还没有缩略图，自动设为 thumbnail
                    row = db.execute("SELECT thumbnail FROM vehicles WHERE id=?", (int(vid),)).fetchone()
                    if row and not row["thumbnail"]:
                        db.execute("UPDATE vehicles SET thumbnail=? WHERE id=?", (f"/uploads/{new_name}", int(vid)))
            db.commit(); db.close()
            self.send_json({"ok": True}); return

        # === 视频上传 (multipart/form-data) ===
        if path == "/api/videos/upload":
            ct = self.headers.get("Content-Type", "")
            if "multipart/form-data" not in ct:
                self.send_json({"error":"need multipart"}, 400); return
            boundary = ct.split("boundary=")[-1].encode()
            length = int(self.headers.get("Content-Length", 0))
            raw = self.rfile.read(length)
            parts = raw.split(b"--" + boundary)
            video_name = ""
            description = ""
            uploaded_file = None
            for part in parts:
                if not part.strip() or b"--" in part[:20]: continue
                header_end = part.find(b"\r\n\r\n")
                if header_end < 0: continue
                headers = part[:header_end].decode(errors="ignore")
                fm = re.search(r'name="([^"]*)"', headers)
                field_name = fm.group(1) if fm else ""
                body = part[header_end+4:]
                if body.endswith(b"\r\n"): body = body[:-2]
                if field_name == "name":
                    video_name = body.decode().strip()
                elif field_name == "description":
                    description = body.decode().strip()
                elif field_name == "file":
                    m = re.search(r'filename="([^"]*)"', headers)
                    orig_name = m.group(1) if m else "video.mp4"
                    ext = os.path.splitext(orig_name)[1] or ".mp4"
                    new_name = f"{uuid.uuid4().hex[:16]}{ext}"
                    fpath = os.path.join(UPLOAD_DIR, new_name)
                    with open(fpath, "wb") as f: f.write(body)
                    uploaded_file = {"original": orig_name, "stored": f"/uploads/{new_name}"}
            if not uploaded_file:
                self.send_json({"error":"no file"}, 400); return
            if not video_name: video_name = uploaded_file["original"]
            db = get_db()
            cur = db.execute("INSERT INTO videos(name,filename,stored_path,description,sort_order,created_at) VALUES(?,?,?,?,?,?)",
                (video_name, uploaded_file["original"], uploaded_file["stored"], description, 0, int(time.time())))
            db.commit(); db.close()
            self.send_json({"id": cur.lastrowid, "ok": True}); return

        data = self.read_body()

        # 新建车辆
        if path == "/api/vehicles":
            name = (data.get("name") or "").strip()
            if not name: self.send_json({"error":"name required"}, 400); return
            db = get_db()
            cur = db.execute("""INSERT INTO vehicles(name,brand,year,category,description,description_en,description_yue,thumbnail,sort_order,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)""",
                (name, data.get("brand",""), data.get("year",""), data.get("category",""),
                 data.get("description",""), data.get("description_en",""), data.get("description_yue",""),
                 data.get("thumbnail",""), int(data.get("sort_order",0)), int(time.time()), int(time.time())))
            db.commit(); db.close()
            self.send_json({"id": cur.lastrowid}); return

        # 新建题库
        if path == "/api/libraries":
            name = (data.get("name") or "").strip()
            if not name: self.send_json({"error":"name required"}, 400); return
            db = get_db()
            cur = db.execute("INSERT INTO qa_libraries(name,description,is_default,language,created_at) VALUES(?,?,?,?,?)",
                (name, data.get("description",""), 0, data.get("language","all"), int(time.time())))
            db.commit(); db.close()
            self.send_json({"id": cur.lastrowid}); return

        # 新建问答
        if path == "/api/qa":
            q = (data.get("question") or "").strip()
            a = (data.get("answer") or "").strip()
            if not q or not a: self.send_json({"error":"question and answer required"}, 400); return
            lib_id = data.get("library_id") or 1
            db = get_db()
            cur = db.execute("""INSERT INTO qa_items(library_id,question,question_en,question_yue,
                answer,answer_en,answer_yue,keywords,media_refs,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)""",
                (lib_id, q, data.get("question_en",""), data.get("question_yue",""),
                 a, data.get("answer_en",""), data.get("answer_yue",""),
                 json.dumps(data.get("keywords") or [], ensure_ascii=False),
                 json.dumps(data.get("media_refs") or [], ensure_ascii=False),
                 int(time.time()), int(time.time())))
            db.commit(); db.close()
            self.send_json({"id": cur.lastrowid}); return

        if path == "/api/config":
            db = get_db()
            for k, v in data.items():
                db.execute("INSERT INTO robot_config(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (k, str(v)))
            db.commit(); db.close()
            self.send_json({"ok": True}); return

        # TTS
        if path == "/api/tts":
            if not HAS_EDGE_TTS: self.send_json({"error":"edge-tts not installed: pip install edge-tts"}, 503); return
            text = (data.get("text") or "").strip()
            voice = data.get("voice") or "zh-CN-XiaoxiaoNeural"
            rate = data.get("rate") or "+0%"
            if not text: self.send_json({"error":"text required"}, 400); return
            try:
                audio_data = asyncio.run(_tts_generate(text, voice, rate))
                self.send_response(200)
                self.send_header("Content-Type", "audio/mpeg")
                self.send_header("Content-Length", str(len(audio_data)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(audio_data)
            except Exception as e:
                self.send_json({"error": str(e)}, 500)
            return

        # === 机器人：执行动作 ===
        if path == "/api/robot/action":
            code = (data.get("action") or "").strip()
            duration = int(data.get("duration") or (ACTION_MAP.get(code) or {}).get("duration_ms", 0))
            if not code: self.send_json({"error":"action required"}, 400); return
            act = ACTION_MAP.get(code)
            if not act:
                self.send_json({
                    "ok": False,
                    "error": "unknown action",
                    "available_codes": list(ACTION_MAP.keys()),
                }, 400); return
            _apply_action_state(code)
            # TODO: 真实硬件接入点 —— 在此处通过串口/网络发送指令给实体机器
            # serial_port.write(ACTION_TO_HW_CMD[code])
            self.send_json({
                "ok": True,
                "action": code,
                "name": act["name"],
                "group": act["group"],
                "duration_ms": duration,
                "hardware_connected": ROBOT_STATE["hardware_connected"],
                "hint": "动作指令已接收（本地模拟），接入真实硬件后此处会发送到机器。",
                "ack_at": int(time.time() * 1000),
            }); return

        self.send_json({"error":"not found"}, 404)

    # ===== PUT =====
    def do_PUT(self):
        path = urlparse(self.path).path.rstrip("/") or "/"
        data = self.read_body()

        # 更新车辆
        if path.startswith("/api/vehicles/"):
            vid = path.split("/")[-1]
            if vid.isdigit():
                db = get_db()
                db.execute("""UPDATE vehicles SET name=?,brand=?,year=?,category=?,
                    description=?,description_en=?,description_yue=?,thumbnail=?,sort_order=?,updated_at=?
                    WHERE id=?""",
                    ((data.get("name") or "").strip(), data.get("brand",""), data.get("year",""), data.get("category",""),
                     data.get("description",""), data.get("description_en",""), data.get("description_yue",""),
                     data.get("thumbnail",""), int(data.get("sort_order",0)), int(time.time()), int(vid)))
                db.commit(); db.close()
                self.send_json({"ok": True}); return

        # 更新题库
        if path.startswith("/api/libraries/"):
            lid = path.split("/")[-1]
            if lid.isdigit():
                db = get_db()
                db.execute("UPDATE qa_libraries SET name=?,description=?,language=? WHERE id=?",
                    ((data.get("name") or "").strip(), data.get("description",""), data.get("language","all"), int(lid)))
                db.commit(); db.close()
                self.send_json({"ok": True}); return

        # 更新问答
        if path.startswith("/api/qa/"):
            qid = path.split("/")[-1]
            if qid.isdigit():
                db = get_db()
                db.execute("""UPDATE qa_items SET library_id=?,question=?,question_en=?,question_yue=?,
                    answer=?,answer_en=?,answer_yue=?,keywords=?,media_refs=?,updated_at=? WHERE id=?""",
                    (data.get("library_id",1),
                     (data.get("question") or "").strip(), data.get("question_en",""), data.get("question_yue",""),
                     (data.get("answer") or "").strip(), data.get("answer_en",""), data.get("answer_yue",""),
                     json.dumps(data.get("keywords") or [], ensure_ascii=False),
                     json.dumps(data.get("media_refs") or [], ensure_ascii=False),
                     int(time.time()), int(qid)))
                db.commit(); db.close()
                self.send_json({"ok": True}); return

        self.send_json({"error":"not found"}, 404)

    # ===== DELETE =====
    def do_DELETE(self):
        path = urlparse(self.path).path.rstrip("/") or "/"

        # 删除车辆
        if path.startswith("/api/vehicles/"):
            vid = path.split("/")[-1]
            if vid.isdigit():
                db = get_db()
                # 清理关联图片文件
                imgs = db.execute("SELECT stored_path FROM vehicle_images WHERE vehicle_id=?", (int(vid),)).fetchall()
                for img in imgs:
                    fname = img["stored_path"].split("/uploads/")[-1]
                    fpath = os.path.join(UPLOAD_DIR, fname)
                    if os.path.isfile(fpath): os.unlink(fpath)
                db.execute("DELETE FROM vehicle_images WHERE vehicle_id=?", (int(vid),))
                db.execute("DELETE FROM vehicles WHERE id=?", (int(vid),))
                db.commit(); db.close()
                self.send_json({"ok": True}); return

        # 删除单张图片
        if path.startswith("/api/vehicle-images/"):
            iid = path.split("/")[-1]
            if iid.isdigit():
                db = get_db()
                row = db.execute("SELECT stored_path FROM vehicle_images WHERE id=?", (int(iid),)).fetchone()
                if row:
                    fname = row["stored_path"].split("/uploads/")[-1]
                    fpath = os.path.join(UPLOAD_DIR, fname)
                    if os.path.isfile(fpath): os.unlink(fpath)
                db.execute("DELETE FROM vehicle_images WHERE id=?", (int(iid),))
                db.commit(); db.close()
                self.send_json({"ok": True}); return

        # 删除视频
        if path.startswith("/api/videos/"):
            vid = path.split("/")[-1]
            if vid.isdigit():
                db = get_db()
                row = db.execute("SELECT stored_path FROM videos WHERE id=?", (int(vid),)).fetchone()
                if row:
                    fname = row["stored_path"].split("/uploads/")[-1]
                    fpath = os.path.join(UPLOAD_DIR, fname)
                    if os.path.isfile(fpath): os.unlink(fpath)
                db.execute("DELETE FROM videos WHERE id=?", (int(vid),))
                db.commit(); db.close()
                self.send_json({"ok": True}); return

        if path.startswith("/api/libraries/"):
            lid = path.split("/")[-1]
            if lid.isdigit():
                db = get_db()
                db.execute("DELETE FROM qa_items WHERE library_id=?", (int(lid),))
                db.execute("DELETE FROM qa_libraries WHERE id=?", (int(lid),))
                db.commit(); db.close()
                self.send_json({"ok": True}); return

        if path.startswith("/api/qa/"):
            qid = path.split("/")[-1]
            if qid.isdigit():
                db = get_db()
                db.execute("DELETE FROM qa_items WHERE id=?", (int(qid),))
                db.commit(); db.close()
                self.send_json({"ok": True}); return

        self.send_json({"error":"not found"}, 404)

async def _tts_generate(text, voice, rate="+0%"):
    communicate = edge_tts.Communicate(text=text, voice=voice, rate=rate)
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".mp3")
    tmp.close()
    await communicate.save(tmp.name)
    with open(tmp.name, "rb") as f: data = f.read()
    os.unlink(tmp.name)
    return data

if __name__ == "__main__":
    init_db()
    port = 5000
    server = HTTPServer(("0.0.0.0", port), Handler)
    print()
    print("=" * 50)
    print("  RobotGuide Backend v3.0")
    print("  (+ Vehicle Management + Upload)")
    print("=" * 50)
    print(f"  Open:  http://localhost:{port}")
    print(f"  APP:   http://localhost:{port}/app-preview")
    print(f"  API:   http://localhost:{port}/api/health")
    print("=" * 50)
    print()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.server_close()
