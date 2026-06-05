import shutil, py_compile

src = '/opt/nexus/main.py.bak3'
dst = '/opt/nexus/main.py'

# Восстанавливаем чистый файл
shutil.copy(src, dst)
lines = open(dst).readlines()
print(f"Restored: {len(lines)} lines")

# 1. Добавляем SSE импорты после строки "from fastapi import"
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

new_lines = []
inserted_header = False
for line in lines:
    new_lines.append(line)
    if not inserted_header and line.startswith("from fastapi import"):
        new_lines.insert(len(new_lines)-1, sse_header)
        inserted_header = True

# 2. Вставляем _push_notify после print("[telegram webhook] New message")
final_lines = []
for i, line in enumerate(new_lines):
    final_lines.append(line)
    if '[telegram webhook] New message' in line and 'print(' in line:
        indent = len(line) - len(line.lstrip())
        push = ' ' * indent + '_push_notify(pid, "Telegram: "+name, text[:100] if text else "[media]")\n'
        final_lines.append(push)
        print(f"Inserted _push_notify after: {line.strip()[:60]}")

# 3. Добавляем SSE endpoint в конец
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
final_lines.append(sse_endpoint)

open(dst, 'w').writelines(final_lines)
print(f"Final: {len(final_lines)} lines")

# Проверка синтаксиса
try:
    py_compile.compile(dst, doraise=True)
    print("SYNTAX OK")
except Exception as e:
    print(f"SYNTAX ERROR: {e}")
