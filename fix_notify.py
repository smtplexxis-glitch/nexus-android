c = open('/opt/nexus/main.py').read()
old = '        _key = (int_id, chat_id, last_at)\n        if _key not in _fcm_sent:\n            _fcm_sent.add(_key)\n            _send_fcm(project_id, "\u0410\u0432\u0438\u0442\u043e: "+name, last_text[:100] if last_text else "[\u0444\u043e\u0442\u043e]")'
new = '        _key = (int_id, chat_id, last_at)\n        if _key not in _fcm_sent and unread > 0:\n            _fcm_sent.add(_key)\n            _send_fcm(project_id, "\u0410\u0432\u0438\u0442\u043e: "+name, last_text[:100] if last_text else "[\u0444\u043e\u0442\u043e]")'
c = c.replace(old, new, 1)
open('/opt/nexus/main.py', 'w').write(c)
print('ok, unread>0 check:', 'unread > 0' in c)
