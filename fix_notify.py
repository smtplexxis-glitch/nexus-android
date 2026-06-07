
c = open('/var/www/nexus/app/index.html').read()
# FCM JS использует 'token' но NEXUS хранит под 'nx_token'
c = c.replace("getItem('token')", "getItem('nx_token')", 1)
open('/var/www/nexus/app/index.html', 'w').write(c)
print('ok, nx_token count:', c.count('nx_token'))
