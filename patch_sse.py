
import shutil, py_compile, os

src = '/opt/nexus/main.py.bak3'
dst = '/opt/nexus/main.py'
shutil.copy(src, dst)
c = open(dst).read()

fcm_block = """import json as _json
import os as _os

_fcm_tokens = {}

def _load_fcm_tokens():
    try:
        conn = get_db()
        try:
            rows = conn.execute("SELECT user_id, fcm_token FROM user_fcm_tokens").fetchall()
            for row in rows: _fcm_tokens[row["user_id"]] = row["fcm_token"]
            print(f"[FCM] Loaded {len(_fcm_tokens)} tokens")
        except: pass
        conn.close()
    except: pass

def _get_access_token():
    sa_file = '/opt/nexus/firebase-service-account.json'
    if not _os.path.exists(sa_file): return None
    try:
        from google.oauth2 import service_account
        import google.auth.transport.requests as _gat
        creds = service_account.Credentials.from_service_account_file(
            sa_file, scopes=['https://www.googleapis.com/auth/firebase.messaging'])
        creds.refresh(_gat.Request())
        return creds.token
    except Exception as e:
        print(f"[FCM] auth error: {e}")
        return None

def _send_fcm(user_id, title, body):
    token = _fcm_tokens.get(user_id)
    if not token:
        print(f"[FCM] No token for user {user_id}")
        return
    access_token = _get_access_token()
    if not access_token:
        print("[FCM] No access token")
        return
    try:
        import requests as _req
        # FCM V1 — notification блок показывает уведомление даже когда приложение убито
        payload = {
            "message": {
                "token": token,
                "notification": {
                    "title": title,
                    "body": body
                },
                "android": {
                    "priority": "HIGH",
                    "notification": {
                        "channel_id": "nexus_messages",
                        "notification_priority": "PRIORITY_MAX",
                        "default_vibrate_timings": True,
                        "default_sound": True
                    }
                },
                "data": {
                    "title": title,
                    "body": body
                }
            }
        }
        resp = _req.post(
            f'https://fcm.googleapis.com/v1/projects/nexus-crm-cffd1/messages:send',
            json=payload,
            headers={"Authorization": f"Bearer {access_token}", "Content-Type": "application/json"},
            timeout=10
        )
        print(f"[FCM] sent to user {user_id}: {resp.status_code} {resp.text[:100]}")
    except Exception as e:
        print(f"[FCM] error: {e}")

"""

c = c.replace("from fastapi import", fcm_block + "from fastapi import", 1)
c = c.replace("app = FastAPI()", "app = FastAPI()\n_load_fcm_tokens()", 1)

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
        print(f"[FCM] Token registered for user {uid}: {token[:20]}...")
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
