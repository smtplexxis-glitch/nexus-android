import shutil, py_compile

src = '/opt/nexus/main.py.bak3'
dst = '/opt/nexus/main.py'
shutil.copy(src, dst)
lines = open(dst).readlines()
print(f"Restored: {len(lines)} lines")

sse_header = """from sse_starlette.sse import EventSourceResponse
import asyncio as _asyncio
import json as _json

_sse_clients = {}

def _push_notify(user_id, title, body):
    q = _sse_clients.get(user_id)
    if q:
        try: q.put_nowait(_json.dumps({"title": title, "body": body}))
        except: pass

"""

# Фоновый поллинг — добавляем в конец файла как lifespan или startup
bg_poller = """

# ── BACKGROUND POLLER ────────────────────────────────────────────────────────
import threading as _threading
import time as _time

def _bg_poll_loop():
    \"\"\"Каждые 30 сек синхронизирует все активные интеграции и пушит уведомления\"\"\"
    import asyncio
    _time.sleep(15)  # первый запуск через 15 сек после старта
    while True:
        try:
            conn = get_db()
            # Берём все активные интеграции
            rows = conn.execute(
                "SELECT id, project_id, platform, credentials FROM integrations WHERE is_active=1"
            ).fetchall()
            conn.close()
            for row in rows:
                int_id = row["id"]
                pid    = row["project_id"]
                p      = row["platform"]
                creds  = creds_dict(row["credentials"])
                try:
                    loop = asyncio.new_event_loop()
                    if p == "avito":
                        loop.run_until_complete(sync_avito(int_id, pid, creds))
                    elif p == "drom":
                        loop.run_until_complete(sync_drom(int_id, pid, creds))
                    elif p == "yula":
                        loop.run_until_complete(sync_yula(int_id, pid, creds))
                    loop.close()
                except Exception as ex:
                    pass
        except Exception as ex:
            pass
        _time.sleep(30)

_poll_thread = _threading.Thread(target=_bg_poll_loop, daemon=True)
_poll_thread.start()
"""

# SSE endpoint
sse_endpoint = """
@app.get("/api/sse")
async def sse_stream(sess=Depends(auth_user)):
    uid = sess["user_id"]
    q = _asyncio.Queue(maxsize=20)
    _sse_clients[uid] = q
    async def gen():
        try:
            yield {"data": "connected"}
            while True:
                try:
                    data = await _asyncio.wait_for(q.get(), timeout=25)
                    yield {"data": data}
                except _asyncio.TimeoutError:
                    yield {"data": "ping"}
        finally:
            _sse_clients.pop(uid, None)
    return EventSourceResponse(gen())
"""

# 1. Вставляем SSE header после "from fastapi import"
new_lines = []
inserted_header = False
for line in lines:
    new_lines.append(line)
    if not inserted_header and line.startswith("from fastapi import"):
        new_lines.insert(len(new_lines)-1, sse_header)
        inserted_header = True

# 2. Вставляем _push_notify в sync_avito — после conn.commit() внутри неё
#    и в telegram webhook
final_lines = []
in_avito_func = False
avito_push_done = False

for i, line in enumerate(new_lines):
    final_lines.append(line)

    if 'async def sync_avito(' in line:
        in_avito_func = True
        avito_push_done = False

    if in_avito_func and not avito_push_done and 'conn.commit()' in line:
        indent = len(line) - len(line.lstrip())
        final_lines.append(' ' * indent + '_push_notify(project_id, "Авито: "+name, last_text[:100] if last_text else "[фото]")\n')
        avito_push_done = True
        in_avito_func = False
        print(f"Added Avito push at line ~{i}")

    if '[telegram webhook] New message' in line and 'print(' in line:
        indent = len(line) - len(line.lstrip())
        final_lines.append(' ' * indent + '_push_notify(pid, "Telegram: "+name, text[:100] if text else "[media]")\n')
        print(f"Added Telegram push at line ~{i}")

# 3. Добавляем фоновый поллер и SSE endpoint в конец
final_lines.append(bg_poller)
final_lines.append(sse_endpoint)

open(dst, 'w').writelines(final_lines)
print(f"Final: {len(final_lines)} lines")

try:
    py_compile.compile(dst, doraise=True)
    print("SYNTAX OK")
except Exception as e:
    print(f"SYNTAX ERROR: {e}")
