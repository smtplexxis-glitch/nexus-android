
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

def _get_fcm_access_token():
    \'\'\'Получаем access token через service account JSON или возвращаем None\'\'\'
    sa_file = '/opt/nexus/firebase-service-account.json'
    if not _os.path.exists(sa_file):
        return None
    try:
        from google.oauth2 import service_account
        import google.auth.transport.requests as _gat
        creds = service_account.Credentials.from_service_account_file(
            sa_file,
            scopes=['https://www.googleapis.com/auth/firebase.messaging']
        )
        req = _gat.Request()
        creds.refresh(req)
        return creds.token
    except Exception as e:
        print(f"[FCM] Auth error: {e}")
        return None

def _send_fcm(user_id, title, body):
    token = _fcm_tokens.get(user_id)
    if not token:
        print(f"[FCM] No token for user {user_id}")
        return
    try:
        import requests as _req
        
        # Пробуем FCM V1 API через service account
        access_token = _get_fcm_access_token()
        if access_token:
            project_id = 'nexus-crm-cffd1'
            url = f'https://fcm.googleapis.com/v1/projects/{project_id}/messages:send'
            payload = {
                "message": {
                    "token": token,
                    "data": {"title": title, "body": body},
                    "android": {
                        "priority": "HIGH",
                        "data": {"title": title, "body": body}
                    }
                }
            }
            resp = _req.post(url,
                json=payload,
                headers={"Authorization": f"Bearer {access_token}", "Content-Type": "application/json"},
                timeout=10)
            print(f"[FCM V1] user={user_id} status={resp.status_code} resp={resp.text[:200]}")
            return
        
        # Fallback: Legacy API если есть ключ
        key_file = '/opt/nexus/fcm_server_key.txt'
        if _os.path.exists(key_file):
            server_key = open(key_file).read().strip()
            if server_key:
                resp = _req.post("https://fcm.googleapis.com/fcm/send",
                    json={"to": token, "data": {"title": title, "body": body}, "priority": "high"},
                    headers={"Authorization": "key=" + server_key, "Content-Type": "application/json"},
                    timeout=10)
                print(f"[FCM Legacy] user={user_id} status={resp.status_code}")
                return
        
        print(f"[FCM] No auth method available for user {user_id}")
    except Exception as e:
        print(f"[FCM] error: {e}")

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
