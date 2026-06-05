import re

p = '/opt/nexus/main.py'
c = open(p).read()

# Восстанавливаем из бекапа сначала
import shutil
shutil.copy('/opt/nexus/main.py.bak3', p)
c = open(p).read()

# 1. SSE импорты после "from fastapi import"
sse_block = '''from sse_starlette.sse import EventSourceResponse
import asyncio as _asyncio
import json as _json

_sse_clients = {}

def _push_notify(user_id, title, body):
    q = _sse_clients.get(user_id)
    if q:
        try: q.put_nowait(_json.dumps({"title": title, "body": body}))
        except: pass

'''
c = c.replace('from fastapi import', sse_block + 'from fastapi import', 1)

# 2. Вставляем _push_notify ПОСЛЕ print("[telegram webhook] New message")
# Находим точный контекст и правильный отступ
old = 'print(f"[telegram webhook] New message from {name}: {text[:50]}")\n'
new = 'print(f"[telegram webhook] New message from {name}: {text[:50]}")\n            _push_notify(pid, f"Telegram: {name}", text[:100] if text else "[медиа]")\n'
c = c.replace(old, new, 1)

# 3. SSE endpoint в конец файла
c += '''

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
'''

open(p, 'w').write(c)
print("DONE lines:", c.count('\n'))
# Проверка синтаксиса
import py_compile
try:
    py_compile.compile(p, doraise=True)
    print("SYNTAX OK")
except py_compile.PyCompileError as e:
    print("SYNTAX ERROR:", e)
