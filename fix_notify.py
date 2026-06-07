
import shutil

shutil.copy('/var/www/nexus/app/index.html', '/var/www/nexus/app/index.html.bak_fcm')
c = open('/var/www/nexus/app/index.html').read()

# Добавляем функцию регистрации FCM токена после инициализации
fcm_js = """
// FCM Token Registration
async function registerFcmToken() {
  try {
    var fcmToken = null;
    // Пробуем получить из AndroidBridge
    if (window.AndroidBridge && AndroidBridge.getFcmToken) {
      fcmToken = AndroidBridge.getFcmToken();
    }
    if (!fcmToken) return;
    var authToken = localStorage.getItem('token');
    if (!authToken || authToken === 'null') return;
    await fetch('/api/fcm-token', {
      method: 'POST',
      headers: {'Content-Type': 'application/json', 'Authorization': 'Bearer ' + authToken},
      body: JSON.stringify({token: fcmToken})
    });
    console.log('[FCM] Token registered');
  } catch(e) {}
}
// Регистрируем токен через 3 сек после загрузки
setTimeout(registerFcmToken, 3000);
// И каждые 5 минут
setInterval(registerFcmToken, 300000);
"""

# Добавляем перед закрывающим </script> или перед window.onload
if '</body>' in c:
    c = c.replace('</body>', '<script>' + fcm_js + '</script></body>', 1)
    print("Injected before </body>")
else:
    print("ERROR: no </body> tag")

open('/var/www/nexus/app/index.html', 'w').write(c)
print("Done, FCM JS injected")
