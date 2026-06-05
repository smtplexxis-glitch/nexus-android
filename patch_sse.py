p = '/opt/nexus/main.py'
lines = open(p).readlines()

# Находим строку с print("[telegram webhook] New message"
for i, line in enumerate(lines):
    if '[telegram webhook] New message' in line and 'print(' in line:
        indent = len(line) - len(line.lstrip())
        push_line = ' ' * indent + '_push_notify(pid, "Telegram: "+name, text[:100] if text else "[media]")\n'
        lines.insert(i + 1, push_line)
        print(f"Inserted _push_notify after line {i+1}: {line.strip()[:60]}")
        break

open(p, 'w').writelines(lines)

import py_compile
try:
    py_compile.compile(p, doraise=True)
    print("SYNTAX OK")
except Exception as e:
    print("SYNTAX ERROR:", e)
