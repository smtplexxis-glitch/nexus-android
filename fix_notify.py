
c = open('/opt/nexus/main.py').read()

# Старый вызов (Авито)
old = '''conn.commit(); conn.close()
        _send_fcm(project_id, "Авито: "+name, last_text[:100] if last_text else "[фото]")'''

# Новый — проверяем что сообщение реально новое
new = '''# Проверяем было ли это сообщение уже в БД
        conn2 = get_db()
        row = conn2.execute("SELECT last_message_at FROM chats WHERE integration_id=? AND external_id=?", (int_id, chat_id)).fetchone()
        old_at = row["last_message_at"] if row else None
        conn2.close()
        conn.commit(); conn.close()
        # Шлём FCM только если сообщение новее того что было
        if last_at and last_at != old_at:
            _send_fcm(project_id, "Авито: "+name, last_text[:100] if last_text else "[фото]")'''

if old in c:
    c = c.replace(old, new, 1)
    open('/opt/nexus/main.py', 'w').write(c)
    print("PATCHED OK")
else:
    # Попробуем другой вариант
    old2 = 'conn.commit(); conn.close()\n        _send_fcm(project_id, "Авито: "+name'
    print("NOT FOUND, searching...")
    idx = c.find('_send_fcm(project_id')
    if idx > 0:
        print(repr(c[idx-100:idx+50]))
    else:
        print("_send_fcm not found at all")
