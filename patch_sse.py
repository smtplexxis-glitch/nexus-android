
import shutil, py_compile, os

src = '/opt/nexus/main.py.bak3'
dst = '/opt/nexus/main.py'
shutil.copy(src, dst)
c = open(dst).read()

fcm_block = """import json as _json
import asyncio as _asyncio
import os as _os

_fcm_tokens = {}

def _load_fcm_tokens():
    try:
        conn = get_db()
        try:
            rows = conn.execute("SELECT user_id, fcm_token FROM user_fcm_tokens").fetchall()
            for row in rows: _fcm_tokens[row["user_id"]] = row["fcm_token"]
        except: pass
        conn.close()
    except: pass

def _send_fcm(user_id, title, body):
    token = _fcm_tokens.get(user_id)
    if not token: return
    key_file = '/opt/nexus/fcm_server_key.txt'
    if not _os.path.exists(key_file): return
    server_key = open(key_file).read().strip()
    if not server_key: return
    try:
        import requests as _req
        # DATA-ONLY push — onMessageReceived вызывается даже в фоне
        _req.post("https://fcm.googleapis.com/fcm/send",
            json={
                "to": token,
                "data": {"title": title, "body": body},
                "priority": "high",
                "android": {"priority": "high", "direct_boot_ok": True}
            },
            headers={"Authorization": "key=" + server_key,
                     "Content-Type": "application/json"},
            timeout=10)
    except Exception as e:
        print("[FCM] error:", e)

"""

c = c.replace("from fastapi import", fcm_block + "from fastapi import", 1)

lines = c.split("\n")
new_lines = []
in_avito = False
avito_done = False
for line in lines:
    new_lines.append(line)
    if "async def sync_avito(" in line:
        in_avito = True; avito_done = False
    if in_avito and not avito_done and "conn.commit()" in line:
        indent = len(line) - len(line.lstrip())
        new_lines.append(" " * indent + '_send_fcm(project_id, "Авито: "+name, last_text[:100] if last_text else "[фото]")')
        avito_done = True; in_avito = False
    if "[telegram webhook] New message" in line and "print(" in line:
        indent = len(line) - len(line.lstrip())
        new_lines.append(" " * indent + '_send_fcm(pid, "Telegram: "+name, text[:100] if text else "[media]")')

c = "\n".join(new_lines)

# Endpoint для регистрации FCM токена + загрузка токенов при старте + вызов _load_fcm_tokens
# Добавляем вызов _load_fcm_tokens после создания FastAPI app
c = c.replace("app = FastAPI()", "app = FastAPI()\n_load_fcm_tokens()", 1)

fcm_endpoint = """

@app.post("/api/fcm-token")
async def register_fcm_token(req: dict, sess=Depends(auth_user)):
    token = req.get("token", "")
    if not token: return {"ok": False}
    uid = sess["user_id"]
    _fcm_tokens[uid] = token
    try:
        conn = get_db()
        conn.execute("CREATE TABLE IF NOT EXISTS user_fcm_tokens (user_id INTEGER PRIMARY KEY, fcm_token TEXT)")
        conn.execute("INSERT OR REPLACE INTO user_fcm_tokens (user_id, fcm_token) VALUES (?,?)", (uid, token))
        conn.commit(); conn.close()
        print(f"[FCM] Token registered for user {uid}")
    except Exception as e:
        print("[FCM] DB:", e)
    return {"ok": True}
"""

c += fcm_endpoint
open(dst, "w").write(c)
print("Lines:", c.count("\n"))
try:
    py_compile.compile(dst, doraise=True)
    print("SYNTAX OK")
except Exception as e:
    print("ERROR:", e)
