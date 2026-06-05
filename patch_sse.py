import re

p = '/opt/nexus/main.py'
c = open(p).read()

# 1. Добавляем импорт SSE и asyncio queue в начало после "from fastapi import"
sse_import = """from sse_starlette.sse import EventSourceResponse
import asyncio as _asyncio
import json as _json

# SSE подключения: user_id -> Queue
_sse_clients = {}

def _push_notify(user_id: int, title: str, body: str):
    q = _sse_clients.get(user_id)
    if q:
        try:
            q.put_nowait(_json.dumps({"title": title, "body": body}))
        except Exception:
            pass

"""

# Вставляем после строки "from fastapi import"
c = re.sub(
    r'(from fastapi import[^\n]+\n)',
    r'\1' + sse_import,
    c, count=1
)

# 2. Добавляем SSE endpoint перед последней строкой файла
sse_endpoint = """

# ── SSE PUSH NOTIFICATIONS ────────────────────────────────────────────────────
@app.get("/api/sse")
async def sse_stream(sess=Depends(auth_user)):
    uid = sess["user_id"]
    q = _asyncio.Queue(maxsize=20)
    _sse_clients[uid] = q

    async def event_gen():
        try:
            yield {"data": "connected"}
            while True:
                try:
                    data = await _asyncio.wait_for(q.get(), timeout=30)
                    yield {"data": data}
                except _asyncio.TimeoutError:
                    yield {"data": "ping"}
        finally:
            _sse_clients.pop(uid, None)

    return EventSourceResponse(event_gen())
"""

c = c + sse_endpoint

# 3. Добавляем вызов _push_notify после каждого INSERT нового сообщения
# Telegram webhook — строка с print("[telegram webhook] New message"
c = c.replace(
    'print(f"[telegram webhook] New message from {name}: {text[:50]}")',
    'print(f"[telegram webhook] New message from {name}: {text[:50]}")\n            _push_notify(pid, f"Telegram: {name}", text[:100] if text else "[медиа]")'
)

# Avito/WhatsApp/другие — ищем другие print о новых сообщениях
c = c.replace(
    'print(f"[whatsapp] New message',
    '_push_notify(0, "WhatsApp", last_text)\n            print(f"[whatsapp] New message'
)

open(p, 'w').write(c)
print("DONE - lines:", c.count('\n'))
