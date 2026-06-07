
c = open('/var/www/nexus/app/index.html').read()

old = "TOKEN=d.token; localStorage.setItem('nx_token',TOKEN);"
new = "TOKEN=d.token; localStorage.setItem('nx_token',TOKEN); try{if(window.AndroidBridge)AndroidBridge.setToken(TOKEN);}catch(e){}"

if old in c:
    c = c.replace(old, new, 1)
    open('/var/www/nexus/app/index.html','w').write(c)
    print('PATCHED OK - AndroidBridge.setToken called on login')
else:
    # Попробуем найти вариант с пробелами
    import re
    m = re.search(r"TOKEN=d\.token;\s*localStorage\.setItem\('nx_token',TOKEN\);", c)
    if m:
        old2 = m.group(0)
        new2 = old2 + " try{if(window.AndroidBridge)AndroidBridge.setToken(TOKEN);}catch(e){}"
        c = c.replace(old2, new2, 1)
        open('/var/www/nexus/app/index.html','w').write(c)
        print('PATCHED OK v2')
    else:
        print('NOT FOUND, searching...')
        idx = c.find('nx_token')
        print(repr(c[idx-50:idx+100]))
