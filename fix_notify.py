
c = open('/var/www/nexus/app/index.html').read()
# Убираем проверку которая блокирует вызов на всех путях
c = c.replace("if (!(\"Notification\" in window)) return;", "// notification check removed for Android")
# Убираем проверку permission - AndroidNotify не нужна permission
c = c.replace("if (Notification.permission === \"granted\") {", "if (true) {  // Android: always show")
open('/var/www/nexus/app/index.html', 'w').write(c)
print("done, AndroidNotify:", c.count('AndroidNotify'), "Notification check:", c.count('Notification in window'))
