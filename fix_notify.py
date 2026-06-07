
import subprocess, json

# Логинимся локально
r = subprocess.run(['curl','-s','-X','POST','http://127.0.0.1:8000/api/auth/login',
    '-H','Content-Type: application/json',
    '-d','{"username":"exclauto","password":"123456"}'],
    capture_output=True, text=True)
print("Login response:", r.stdout[:200])

try:
    d = json.loads(r.stdout)
    auth_token = d.get('token') or d.get('access_token')
    print("Auth token:", auth_token[:30] if auth_token else "NONE")
    
    if auth_token:
        # Регистрируем тестовый FCM токен
        r2 = subprocess.run(['curl','-s','-X','POST','http://127.0.0.1:8000/api/fcm-token',
            '-H','Content-Type: application/json',
            '-H',f'Authorization: Bearer {auth_token}',
            '-d','{"token":"test_token_12345"}'],
            capture_output=True, text=True)
        print("FCM register:", r2.stdout)
        
        # Проверяем БД
        r3 = subprocess.run(['sqlite3','/opt/nexus/nexus.db',
            'SELECT user_id, fcm_token FROM user_fcm_tokens;'],
            capture_output=True, text=True)
        print("DB:", r3.stdout)
except Exception as e:
    print("Error:", e)
