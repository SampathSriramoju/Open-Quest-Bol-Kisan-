import os
import sys
import json
import time
import subprocess

ADB = r"C:\Users\Sampath Sriramoju\AppData\Local\Android\Sdk\platform-tools\adb.exe"

def run_adb(args):
    cmd = [ADB] + args
    res = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    return res.stdout.strip() if res.stdout else ""

print("=== COMMENCING PHASE 9 & 10: OFFLINE AIRPLANE MODE & PERFORMANCE AUDIT ===")

# 1. Enable Airplane Mode
print("[1/5] Enabling Airplane Mode...")
run_adb(["shell", "cmd", "connectivity", "airplane-mode", "enable"])
time.sleep(2)

airplane_state = run_adb(["shell", "settings", "get", "global", "airplane_mode_on"])
print(f"Airplane Mode Status: {airplane_state} (1=Active)")

# 2. Test Offline Image & RAG Query
print("[2/5] Testing Multimodal Inference in Airplane Mode...")
run_adb(["logcat", "-c"])

run_adb(["shell", "am", "start", "-n", "com.bolkisan.app.debug/com.bolkisan.app.MainActivity", "-a", "com.bolkisan.app.TEST_VOICE_QUERY", "--es", "image_path", "/data/local/tmp/BolKisanTests/cotton_pink_bollworm.jpg", "--es", "query", "'Is pink bollworm affecting this cotton?'", "--es", "language", "te"])
time.sleep(4)

logs = run_adb(["logcat", "-d", "-s", "BolKisanVision", "BolKisanMain", "BolKisanRAG", "BolKisanTTS"])
has_crash = "FATAL" in logs or "Exception" in logs
scenario = "UNKNOWN"
for line in logs.splitlines():
    if "Scenario=" in line:
        for part in line.split(","):
            if "Scenario=" in part:
                scenario = part.split("=")[-1].strip()

print(f"Offline Image Diagnosis Scenario: {scenario} (Crash: {has_crash})")

# 3. Disable Airplane Mode
print("[3/5] Disabling Airplane Mode...")
run_adb(["shell", "cmd", "connectivity", "airplane-mode", "disable"])
time.sleep(2)

# 4. Measure Cold Startup Time
print("[4/5] Measuring App Cold Startup Time...")
run_adb(["shell", "am", "force-stop", "com.bolkisan.app.debug"])
time.sleep(1)

start_perf = run_adb(["shell", "am", "start", "-W", "-n", "com.bolkisan.app.debug/com.bolkisan.app.MainActivity"])
print(f"Startup metrics:\n{start_perf}")

total_time_ms = None
for line in start_perf.splitlines():
    if "TotalTime:" in line:
        total_time_ms = int(line.split(":")[-1].strip())

# 5. Measure Memory Footprint
print("[5/5] Measuring Memory Footprint...")
meminfo = run_adb(["shell", "dumpsys", "meminfo", "com.bolkisan.app.debug"])
pss_total = None
for line in meminfo.splitlines():
    if "TOTAL PSS:" in line:
        pss_total = line.strip()
    elif "TOTAL" in line and pss_total is None:
        parts = line.split()
        if len(parts) >= 2 and parts[0] == "TOTAL":
            pss_total = f"{parts[1]} KB"

print(f"Total PSS: {pss_total}")

results = {
    "airplane_mode_tested": True,
    "offline_query_scenario": scenario,
    "offline_has_crash": has_crash,
    "startup_total_time_ms": total_time_ms,
    "memory_pss_total": pss_total,
    "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
}

out_file = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "test_dataset", "offline_and_perf_results.json")
with open(out_file, 'w', encoding='utf-8') as f:
    json.dump(results, f, indent=2, ensure_ascii=False)

print(f"\nOffline & Performance Audit Complete! Stored at: {out_file}")
