import subprocess, time, sys

adb = r"C:\Users\Sampath Sriramoju\AppData\Local\Android\Sdk\platform-tools\adb.exe"
apk = r"D:\BolKisan-final-release-debug.apk"
package_name = "com.bolkisan.app"
activity_name = "com.bolkisan.app/.MainActivity"

print("Waiting for emulator to be authorized by user (Allow USB debugging)...", flush=True)

for i in range(120): # wait up to 4 minutes
    res = subprocess.run([adb, "devices"], capture_output=True, text=True)
    if "emulator-5554\tdevice" in res.stdout:
        print("\n>>> EMULATOR AUTHORIZED! INSTALLING BOL KISAN...", flush=True)
        # Install
        inst = subprocess.run([adb, "install", "-r", "-d", apk], capture_output=True, text=True)
        print("Install Output:\n", inst.stdout, inst.stderr, flush=True)
        
        # Grant permissions
        print("Granting permissions...", flush=True)
        subprocess.run([adb, "shell", "pm", "grant", package_name, "android.permission.RECORD_AUDIO"])
        subprocess.run([adb, "shell", "pm", "grant", package_name, "android.permission.CAMERA"])
        
        # Launch MainActivity
        print("Launching Bol Kisan MainActivity...", flush=True)
        launch = subprocess.run([adb, "shell", "am", "start", "-n", activity_name], capture_output=True, text=True)
        print("Launch Output:\n", launch.stdout, launch.stderr, flush=True)
        
        print("\n>>> BOL KISAN IS NOW RUNNING ON THE EMULATOR! <<<", flush=True)
        sys.exit(0)
    time.sleep(2)

print("Timed out waiting for authorization.", flush=True)
