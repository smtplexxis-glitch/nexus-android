
c = open('/var/www/nexus/app/index.html').read()

old_js = '''// FCM Token Registration
async function registerFcmToken() {
  try {
    var fcmToken = null;
    // Пробуем получить из AndroidBridge
    if (window.AndroidBridge && AndroidBridge.getFcmToken) {
      fcmToken = AndroidBridge.getFcmToken();
    }
    if (!fcmToken) return;
    var authToken = localStorage.getItem('nx_token');
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
setInterval(registerFcmToken, 300000);'''

new_js = '''// FCM Token Registration
async function registerFcmToken() {
  try {
    var fcmToken = null;
    if (window.AndroidBridge && typeof AndroidBridge.getFcmToken === 'function') {
      fcmToken = AndroidBridge.getFcmToken();
    }
    if (!fcmToken || fcmToken === '') return false;
    var authToken = localStorage.getItem('nx_token');
    if (!authToken || authToken === 'null' || authToken === 'undefined') return false;
    var resp = await fetch('/api/fcm-token', {
      method: 'POST',
      headers: {'Content-Type': 'application/json', 'Authorization': 'Bearer ' + authToken},
      body: JSON.stringify({token: fcmToken})
    });
    console.log('[FCM] Token registered, status:', resp.status);
    return true;
  } catch(e) { console.log('[FCM] Error:', e); return false; }
}
// Пробуем несколько раз с нарастающей задержкой
var fcmAttempts = 0;
async function tryRegisterFcm() {
  fcmAttempts++;
  var ok = await registerFcmToken();
  if (!ok && fcmAttempts < 10) {
    setTimeout(tryRegisterFcm, fcmAttempts * 3000);
  }
}
setTimeout(tryRegisterFcm, 2000);'''

if old_js in c:
    c = c.replace(old_js, new_js, 1)
    open('/var/www/nexus/app/index.html', 'w').write(c)
    print("PATCHED OK")
else:
    print("Not found exact match, trying partial...")
    if 'registerFcmToken' in c:
        print("registerFcmToken exists, count:", c.count('registerFcmToken'))
        # Покажем что там
        idx = c.find('registerFcmToken')
        print(repr(c[idx-50:idx+200]))
    else:
        print("registerFcmToken NOT in file!")
