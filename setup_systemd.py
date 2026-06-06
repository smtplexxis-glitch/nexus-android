import subprocess, os

# Создаём systemd service файл
service = """[Unit]
Description=NEXUS API Server
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/nexus
Environment=PATH=/opt/nexus/venv/bin:/usr/bin:/bin
ExecStart=/opt/nexus/venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000
Restart=always
RestartSec=5
StandardOutput=append:/var/log/nexus.log
StandardError=append:/var/log/nexus.log

[Install]
WantedBy=multi-user.target
"""

with open('/etc/systemd/system/nexus.service', 'w') as f:
    f.write(service)
print("Service file written")

subprocess.run(['pkill', '-9', '-f', 'uvicorn'])
import time; time.sleep(2)

for cmd in [
    ['systemctl', 'daemon-reload'],
    ['systemctl', 'enable', 'nexus'],
    ['systemctl', 'start', 'nexus'],
]:
    r = subprocess.run(cmd, capture_output=True, text=True)
    print(f"{' '.join(cmd)}: {r.returncode}")

time.sleep(3)
r = subprocess.run(['systemctl', 'is-active', 'nexus'], capture_output=True, text=True)
print(f"Status: {r.stdout.strip()}")
