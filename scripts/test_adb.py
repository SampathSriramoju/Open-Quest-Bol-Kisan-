import os
import subprocess
import time

adb = r"C:\Users\Sampath Sriramoju\AppData\Local\Android\Sdk\platform-tools\adb.exe"
env = os.environ.copy()
env['ADB_VENDOR_KEYS'] = r"C:\Users\Sampath Sriramoju\.android\adbkey"

res = subprocess.run([adb, "devices"], env=env, capture_output=True, text=True)
print("STDOUT:\n" + res.stdout)
print("STDERR:\n" + res.stderr)
