
import shutil
shutil.copy('/var/www/nexus/app/index.html', '/var/www/nexus/app/index.html.bak2')
c = open('/var/www/nexus/app/index.html').read()

old = 'function notify(title, body) {'
new = (
    'function notify(title, body) {\n'
    '  try { if(window.AndroidNotify) AndroidNotify.show(String(title||""),String(body||"")); } catch(e) {}\n'
    '  try { if(window.AndroidBridge && AndroidBridge.show) AndroidBridge.show(String(title||""),String(body||"")); } catch(e) {}'
)

if old in c:
    c = c.replace(old, new, 1)
    open('/var/www/nexus/app/index.html', 'w').write(c)
    print("PATCHED OK, AndroidNotify count:", c.count('AndroidNotify'))
else:
    print("ERROR: notify() not found")
    print("Searching...")
    idx = c.find('notify(')
    print("notify( at:", idx)
    if idx > 0:
        print("Context:", c[idx-20:idx+60])
