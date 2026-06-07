
c = open('/opt/nexus/main.py').read()

# Добавляем in-memory set для отслеживания уже отправленных уведомлений
# Ключ: (integration_id, chat_id, last_message_at)
old_set = "_fcm_tokens = {}"
new_set = """_fcm_tokens = {}
_fcm_sent = set()  # (integration_id, chat_id, last_message_at) - уже отправленные
_fcm_initialized = False  # Флаг первой инициализации"""

c = c.replace(old_set, new_set, 1)

# Заменяем _load_fcm_tokens чтобы при загрузке заполнить _fcm_sent существующими чатами
old_load = """def _load_fcm_tokens():
    try:
        conn = get_db()
        try:
            rows = conn.execute("SELECT user_id, fcm_token FROM user_fcm_tokens").fetchall()
            for row in rows: _fcm_tokens[row["user_id"]] = row["fcm_token"]
            print(f"[FCM] Loaded {len(_fcm_tokens)} tokens")
        except: pass
        conn.close()
    except: pass"""

new_load = """def _load_fcm_tokens():
    global _fcm_initialized
    try:
        conn = get_db()
        try:
            rows = conn.execute("SELECT user_id, fcm_token FROM user_fcm_tokens").fetchall()
            for row in rows: _fcm_tokens[row["user_id"]] = row["fcm_token"]
            print(f"[FCM] Loaded {len(_fcm_tokens)} tokens")
        except: pass
        # Загружаем текущие last_message_at чтобы не слать дубли после рестарта
        try:
            chats = conn.execute("SELECT integration_id, external_id, last_message_at FROM chats WHERE last_message_at IS NOT NULL").fetchall()
            for ch in chats:
                _fcm_sent.add((ch["integration_id"], ch["external_id"], ch["last_message_at"]))
            print(f"[FCM] Pre-loaded {len(_fcm_sent)} chat states")
        except: pass
        conn.close()
        _fcm_initialized = True
    except: pass"""

if old_load in c:
    c = c.replace(old_load, new_load, 1)
    print("_load_fcm_tokens patched")
else:
    print("WARNING: _load_fcm_tokens not found")

# Заменяем вызов _send_fcm для Авито
old_send = """        conn.commit(); conn.close()
        _send_fcm(project_id, "Авито: "+name, last_text[:100] if last_text else "[фото]")"""

new_send = """        conn.commit(); conn.close()
        # Шлём FCM только если это новое сообщение (не было при запуске сервера)
        _key = (int_id, chat_id, last_at)
        if _key not in _fcm_sent:
            _fcm_sent.add(_key)
            _send_fcm(project_id, "Авито: "+name, last_text[:100] if last_text else "[фото]")"""

if old_send in c:
    c = c.replace(old_send, new_send, 1)
    print("Avito FCM patched")
else:
    print("WARNING: Avito send not found")

open('/opt/nexus/main.py', 'w').write(c)

import py_compile
try:
    py_compile.compile('/opt/nexus/main.py', doraise=True)
    print("SYNTAX OK")
except Exception as e:
    print("SYNTAX ERROR:", e)
