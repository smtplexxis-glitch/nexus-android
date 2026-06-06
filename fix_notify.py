
import shutil, re
shutil.copy('/var/www/nexus/app/index.html', '/var/www/nexus/app/index.html.bak3')
c = open('/var/www/nexus/app/index.html').read()

# Заменяем всю функцию notify на версию которая сразу вызывает AndroidNotify
old = '''function notify(title, body) {
  try { if(window.AndroidNotify) AndroidNotify.show(String(title||""),String(body||"")); } catch(e) {}
  try { if(window.AndroidBridge && AndroidBridge.show) AndroidBridge.show(String(title||""),String(body||"")); } catch(e) {}
  if (!("Notification" in window)) return;
    if (Notification.permission === "granted") {
      try {
        new Notification("NEXUS: " + title, {body: body||'', icon:"/app/icon-192.png", tag: "nexus-msg"});
      } catch(e) {}
    }
  }'''

new = '''function notify(title, body) {
  try { if(window.AndroidNotify) { AndroidNotify.show(String(title||''), String(body||'')); return; } } catch(e) {}
  try { if(window.AndroidBridge && AndroidBridge.show) { AndroidBridge.show(String(title||''), String(body||'')); return; } } catch(e) {}
  if (!("Notification" in window)) return;
  if (Notification.permission === "granted") {
    try { new Notification("NEXUS: " + title, {body: body||'', icon:"/app/icon-192.png", tag: "nexus-msg"}); } catch(e) {}
  }
}'''

if old in c:
    c = c.replace(old, new, 1)
    open('/var/www/nexus/app/index.html', 'w').write(c)
    print("PATCHED v2")
else:
    # Попробуем найти что реально там
    idx = c.find('function notify(title, body)')
    if idx >= 0:
        print("Current notify():")
        print(repr(c[idx:idx+300]))
    else:
        print("notify not found!")
        # Ищем любое упоминание notify
        for i,line in enumerate(c.split('\n')):
            if 'notify' in line.lower() and 'function' in line.lower():
                print(f"Line {i}: {line}")
