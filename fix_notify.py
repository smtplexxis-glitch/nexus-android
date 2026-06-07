
import re

c = open('/opt/nexus/main.py').read()

# Ищем место ДО conn.execute INSERT INTO chats
# Вставляем чтение old_last_at перед INSERT
old_insert = '            conn.execute(\"\"\"\n                INSERT INTO chats'
new_insert = '''            # Читаем текущее last_message_at ДО обновления
            _old_row = conn.execute("SELECT last_message_at FROM chats WHERE integration_id=? AND external_id=?", (int_id, chat_id)).fetchone()
            _old_last_at = _old_row["last_message_at"] if _old_row else None
            conn.execute(\"\"\"
                INSERT INTO chats'''

if old_insert in c:
    c = c.replace(old_insert, new_insert, 1)
    print("Step 1 OK: added _old_last_at reading")
else:
    print("Step 1 FAIL")

# Заменяем вызов _send_fcm — шлём только если last_at изменился И unread > 0
old_send = '        conn.commit(); conn.close()\n        _send_fcm(project_id, "\u0410\u0432\u0438\u0442\u043e: "+name, last_text[:100] if last_text else "[\u0444\u043e\u0442\u043e]")'
new_send = '        conn.commit(); conn.close()\n        # Шлём FCM только если: новое сообщение (last_at изменился) И непрочитанных > 0\n        if last_at and last_at != _old_last_at and unread > 0:\n            _send_fcm(project_id, "\u0410\u0432\u0438\u0442\u043e: "+name, last_text[:100] if last_text else "[\u0444\u043e\u0442\u043e]")'

if old_send in c:
    c = c.replace(old_send, new_send, 1)
    print("Step 2 OK: FCM only on new unread message")
else:
    print("Step 2 FAIL - trying to find...")
    idx = c.find('_send_fcm(project_id')
    if idx > 0:
        print(repr(c[idx-80:idx+60]))

open('/opt/nexus/main.py', 'w').write(c)

import py_compile
try:
    py_compile.compile('/opt/nexus/main.py', doraise=True)
    print("SYNTAX OK")
except Exception as e:
    print("SYNTAX ERROR:", e)
    # Восстанавливаем бэкап
    import shutil
    shutil.copy('/opt/nexus/main.py.bak3', '/opt/nexus/main.py')
    print("RESTORED from backup")
