import os
import sys
import json
import time
import subprocess

# Ensure UTF-8 output
if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')

ADB = r"C:\Users\Sampath Sriramoju\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DATASET_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "vision_dataset_200")
MANIFEST_PATH = os.path.join(DATASET_DIR, "vision_test_dataset_200.json")

def run_adb(args):
    cmd = [ADB] + args
    res = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    return res.stdout.strip() if res.stdout else ""

print("=== COMMENCING 200-IMAGE APPLICATION EVALUATION CAMPAIGN ===", flush=True)

if not os.path.exists(MANIFEST_PATH):
    print(f"Error: Manifest not found at {MANIFEST_PATH}", flush=True)
    sys.exit(1)

with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
    images = json.load(f)

print(f"Loaded {len(images)} images from manifest.", flush=True)

# 1. Push Images to Emulator
print("[1/3] Pushing 200 test images to emulator /data/local/tmp/BolKisanVision200/ ...", flush=True)
run_adb(["shell", "mkdir", "-p", "/data/local/tmp/BolKisanVision200"])
images_dir = os.path.join(DATASET_DIR, "images")

push_res = run_adb(["push", images_dir + "/.", "/data/local/tmp/BolKisanVision200/"])
print(f"Push completed: {push_res}", flush=True)
run_adb(["shell", "chmod", "-R", "777", "/data/local/tmp/BolKisanVision200"])

# 2. Run Evaluation
print(f"\n[2/3] Executing 200 test cases through live Android app pipeline...", flush=True)
results = []
latencies = []

for idx, img in enumerate(images):
    img_id = img["image_id"]
    filename = img["filename"]
    crop = img["crop"]
    category = img["category"]
    expected_label = img["ground_truth_label"]
    device_img_path = f"/data/local/tmp/BolKisanVision200/{filename}"

    # Multimodal Modality Allocation
    if idx < 30:
        modality = "Image + Typed Telugu"
        lang = "te"
        query = f"ఈ {crop} ఆకుపై ఏ తెగులు లేదా పురుగు లక్షణాలు కనిపిస్తున్నాయి?" if crop != "None" else "ఈ చిత్రంలో ఏముందో వివరించండి."
        wav_path = None
    elif idx < 60:
        modality = "Image + Typed Hindi"
        lang = "hi"
        query = f"इस {crop} की पत्ती पर कौन सा रोग या कीट दिखाई दे रहा है?" if crop != "None" else "इस चित्र में क्या दिखाई दे रहा है?"
        wav_path = None
    elif idx < 90:
        modality = "Image + Typed English"
        lang = "en"
        query = f"What disease or pest is affecting this {crop} leaf? Explain symptoms." if crop != "None" else "What is visible in this photograph?"
        wav_path = None
    elif idx < 120:
        modality = "Image + Telugu Voice"
        lang = "te"
        query = "వరి పంటలో అగ్గి తెగులు నివారణ ఎలా?"
        wav_path = "/data/local/tmp/sample_te.wav"
    elif idx < 140:
        modality = "Image + Hindi Voice"
        lang = "hi"
        query = "कपास में गुलाबी सुंडी का नियंत्रण कैसे करें?"
        wav_path = "/data/local/tmp/sample_hi.wav"
    elif idx < 160:
        modality = "Image + English Voice"
        lang = "en"
        query = "How to control rice blast disease in paddy?"
        wav_path = "/data/local/tmp/sample_en.wav"
    elif idx < 180:
        modality = "Image Only (Visual Diagnosis)"
        lang = "te" if idx % 2 == 0 else "en"
        query = ""
        wav_path = None
    else:
        modality = "Quality & OOD Inquiry"
        lang = "en"
        query = "Is this image clear enough for diagnosis? Explain visible condition."
        wav_path = None

    run_adb(["logcat", "-c"])
    start_time = time.time()

    intent_args = [
        "shell", "am", "start", "-n", "com.bolkisan.app.debug/com.bolkisan.app.MainActivity",
        "-a", "com.bolkisan.app.TEST_VOICE_QUERY",
        "--es", "image_path", device_img_path,
        "--es", "language", lang
    ]
    if query:
        intent_args.extend(["--es", "query", f"'{query}'"])
    if wav_path:
        intent_args.extend(["--es", "wav_path", wav_path])

    run_adb(intent_args)

    # Poll logcat for response
    scenario = "UNKNOWN"
    quality_status = "OK"
    domain = "UNCERTAIN"
    top1 = None
    top2 = None
    top3 = None
    citation_doc = None
    citation_page = 0
    confidence = 0.0
    completed = False

    poll_start = time.time()
    while time.time() - poll_start < 1.8:
        time.sleep(0.08)
        logs = run_adb(["logcat", "-d", "-s", "BolKisanVision", "BolKisanMain", "BolKisanRAG"])
        if any(marker in logs for marker in ["RESPONSE_DISPLAYED", "QUALITY_REJECTED", "VISION_REJECTED", "OOD_DETECTED"]):
            completed = True
            break

    latency_ms = int((time.time() - start_time) * 1000)
    latencies.append(latency_ms)

    for line in logs.splitlines():
        if "QUALITY_REJECTED:" in line:
            if "Too Dark" in line or "Underexposed" in line:
                quality_status = "TOO_DARK"
            elif "Overexposed" in line:
                quality_status = "OVEREXPOSED"
            elif "Blurry" in line:
                quality_status = "TOO_BLURRY"
            scenario = "QUALITY_REJECTED"
        elif "QUALITY_PASSED:" in line:
            quality_status = "OK"

        if "OOD_DETECTED:" in line:
            if "AGRICULTURAL_MACHINERY" in line:
                domain = "AGRICULTURAL_MACHINERY"
                scenario = "NON_PLANT_OBJECT"
            elif "NON_AGRICULTURAL_OBJECT" in line:
                domain = "NON_AGRICULTURAL_OBJECT"
                scenario = "NON_PLANT_OBJECT"

        if "INFERENCE_OUTPUT:" in line:
            # Top1=CLASS (conf%), Top2=CLASS (conf%)
            try:
                parts = line.split("Top1=")[1].split(",")
                top1 = parts[0].split()[0].strip()
                if len(parts) > 1 and "Top2=" in parts[1]:
                    top2 = parts[1].split("Top2=")[1].split()[0].strip()
            except Exception:
                pass

        if "DIFFERENTIAL_DIAGNOSIS:" in line:
            try:
                primary_part = line.split("Primary=")[1].split()[0].strip()
                top1 = primary_part
            except Exception:
                pass

        if "Scenario=" in line:
            for p in line.split(","):
                if "Scenario=" in p:
                    scenario = p.split("=")[-1].strip()
                if "Doc=" in p:
                    citation_doc = p.split("=")[-1].strip().strip("'")
                if "Page=" in p:
                    try:
                        citation_page = int(p.split("=")[-1].strip())
                    except Exception:
                        pass

    # Accuracy Matching
    top1_match = (top1 == expected_label)
    top3_match = (expected_label in [top1, top2, top3]) if (top1 or top2) else False

    # Pipeline Verdict
    if category == "quality_degraded":
        verdict = "PASS" if (scenario == "QUALITY_REJECTED" or quality_status != "OK") else "FAIL"
    elif category == "machinery_nonagri":
        verdict = "PASS" if (scenario == "NON_PLANT_OBJECT" or domain in ["AGRICULTURAL_MACHINERY", "NON_AGRICULTURAL_OBJECT"]) else "FAIL"
    elif category == "unknown_unsupported":
        verdict = "PASS" if scenario in ["LOW_CONFIDENCE_ESCALATION", "NON_PLANT_OBJECT"] else "FAIL"
    else: # disease_pest or healthy
        verdict = "PASS" if scenario in ["EVIDENCE_AVAILABLE", "LOW_CONFIDENCE_ESCALATION", "NON_PLANT_OBJECT"] else "FAIL"

    print(f"[{idx+1}/200] {img_id} [{category.upper()}] {crop} -> Expected: {expected_label} | Top1: {top1} (Top1={top1_match}, Top3={top3_match}) | Scen={scenario} | Qual={quality_status} | {latency_ms}ms", flush=True)

    results.append({
        "image_id": img_id,
        "filename": filename,
        "crop": crop,
        "category": category,
        "ground_truth_label": expected_label,
        "dimensions": img["dimensions"],
        "license": img["license"],
        "sha256": img["sha256"],
        "modality": modality,
        "language": lang,
        "query": query,
        "quality_status": quality_status,
        "domain": domain,
        "scenario": scenario,
        "predicted_top1": top1,
        "predicted_top2": top2,
        "top1_match": top1_match,
        "top3_match": top3_match,
        "citation_doc": citation_doc,
        "citation_page": citation_page,
        "latency_ms": latency_ms,
        "verdict": verdict
    })

# Write Results JSON
results_path = os.path.join(DATASET_DIR, "vision_test_results_200.json")
with open(results_path, "w", encoding="utf-8") as f:
    json.dump(results, f, indent=2, ensure_ascii=False)

print(f"\n[3/3] 200-Image Evaluation Complete! Results stored at: {results_path}", flush=True)
