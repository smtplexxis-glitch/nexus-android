
c = open('/opt/nexus/main.py').read()

# Убираем неправильный фикс — conn2 читает после commit
old = """        # Проверяем было ли это сообщение уже в БД
        conn2 = get_db()
        row = conn2.execute("SELECT last_message_at FROM chats WHERE integration_id=? AND external_id=?", (int_id, chat_id)).fetchone()
        old_at = row["last_message_at"] if row else None
        conn2.close()
        conn.commit(); conn.close()
        # Шлём FCM только если сообщение новее того что было
        if last_at and last_at != old_at:
            _send_fcm(project_id, "Авито: "+name, last_text[:100] if last_text else "[фото]")"""

new = """        conn.commit(); conn.close()
        _send_fcm(project_id, "Авито: "+name, last_text[:100] if last_text else "[фото]")"""

if old in c:
    c = c.replace(old, new, 1)
    open('/opt/nexus/main.py', 'w').write(c)
    print("REVERTED OK - FCM sends every sync again")
else:
    print("Not found, checking...")
    if "conn2 = get_db()" in c:
        print("conn2 found but different context")
    else:
        print("conn2 not found")
