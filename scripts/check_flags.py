import subprocess

res = subprocess.run([r'C:\Users\Sampath Sriramoju\AppData\Local\Android\Sdk\emulator\emulator.exe', '-help-all'], capture_output=True)
out = res.stdout.decode('utf-8', errors='replace')
for l in out.splitlines():
    if any(k in l.lower() for k in ['adb', 'key', 'auth', 'shell', 'prop', 'writable']):
        print(l)
