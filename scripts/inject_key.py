import socket
import time

s = socket.socket()
s.connect(('127.0.0.1', 4444))
time.sleep(1.0)
# Flush kernel dmesg
s.setblocking(False)
try:
    while True:
        data = s.recv(4096)
        if not data: break
except:
    pass

s.setblocking(True)

pub_key = open(r'C:\Users\Sampath Sriramoju\.android\adbkey.pub', 'r', encoding='utf-8').read().strip()
print("Injecting key:", pub_key[:50], "...")

cmds = [
    "\n",
    "mkdir -p /data/misc/adb\n",
    f"echo '{pub_key}' >> /data/misc/adb/adb_keys\n",
    "chmod 640 /data/misc/adb/adb_keys\n",
    "chown system:shell /data/misc/adb/adb_keys 2>/dev/null || true\n",
    "restorecon /data/misc/adb/adb_keys 2>/dev/null || true\n",
    "cat /data/misc/adb/adb_keys\n",
    "stop adbd\n",
    "start adbd\n",
    "\n"
]

for cmd in cmds:
    s.send(cmd.encode('utf-8'))
    time.sleep(0.3)

time.sleep(1.0)
s.setblocking(False)
output = ""
try:
    while True:
        chunk = s.recv(4096).decode('utf-8', errors='replace')
        if not chunk: break
        output += chunk
except:
    pass

print("SHELL OUTPUT:\n", output)
s.close()
