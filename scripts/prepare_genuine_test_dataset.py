import os
import sys
import json
import hashlib
import ssl
import urllib.request
import urllib.parse
from PIL import Image, ImageFilter, ImageEnhance

DATASET_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "test_dataset")
os.makedirs(DATASET_DIR, exist_ok=True)

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

HEADERS = {'User-Agent': 'BolKisanBot/2.0 (agri-qa@bolkisan.org)'}

SEARCH_TARGETS = [
    # Cotton
    {"id": "cotton_leaf_roller", "crop": "Cotton", "label": "Cotton Leaf Roller Damage", "query": "Cotton leaf roller", "expected": "COTTON_PINK_BOLLWORM"},
    {"id": "cotton_pink_bollworm", "crop": "Cotton", "label": "Pink Bollworm (Pectinophora)", "query": "Pectinophora gossypiella", "expected": "COTTON_PINK_BOLLWORM"},
    {"id": "cotton_whitefly", "crop": "Cotton", "label": "Whitefly (Bemisia tabaci)", "query": "Bemisia tabaci", "expected": "COTTON_WHITEFLY_LEAF_CURL"},
    {"id": "cotton_alternaria", "crop": "Cotton", "label": "Alternaria Leaf Blight", "query": "Alternaria brassicicola", "expected": "COTTON_ALTERNARIA_LEAF_SPOT"},

    # Paddy
    {"id": "paddy_blast", "crop": "Paddy", "label": "Rice Blast (Magnaporthe grisea)", "query": "Magnaporthe grisea", "expected": "PADDY_BLAST_PYRICULARIA"},
    {"id": "paddy_stem_borer", "crop": "Paddy", "label": "Stem Borer (Scirpophaga)", "query": "Scirpophaga incertulas", "expected": "PADDY_STEM_BORER_DEADHEART"},
    {"id": "paddy_bacterial_blight", "crop": "Paddy", "label": "Bacterial Blight (Xanthomonas)", "query": "Xanthomonas campestris", "expected": "PADDY_BACTERIAL_BLIGHT"},
    {"id": "paddy_healthy_field", "crop": "Paddy", "label": "Healthy Paddy Canopy", "query": "Rice paddy terrace Bali", "expected": "PADDY_BLAST_PYRICULARIA"},

    # Chilli
    {"id": "chilli_plant", "crop": "Chilli", "label": "Capsicum Chilli Foliage", "query": "Capsicum annuum flower", "expected": "CHILLI_THRIPS_MITES_CURL"},
    {"id": "chilli_anthracnose", "crop": "Chilli", "label": "Colletotrichum Fruit Rot", "query": "Colletotrichum gloeosporioides", "expected": "CHILLI_ANTHRACNOSE_FRUIT_ROT"},

    # Groundnut
    {"id": "groundnut_plant", "crop": "Groundnut", "label": "Groundnut Peanut Plant", "query": "Arachis hypogaea flower", "expected": "GROUNDNUT_TIKKA_LEAF_SPOT"},
    {"id": "groundnut_rust", "crop": "Groundnut", "label": "Puccinia Rust Pustules", "query": "Puccinia sorghi rust", "expected": "GROUNDNUT_RUST"},

    # Red gram
    {"id": "redgram_plant", "crop": "Red gram", "label": "Cajanus cajan Pigeonpea", "query": "Cajanus cajan", "expected": "REDGRAM_POD_BORER"},
    {"id": "redgram_pod_borer", "crop": "Red gram", "label": "Helicoverpa armigera Pod Borer", "query": "Helicoverpa armigera", "expected": "REDGRAM_POD_BORER"},

    # Out of domain & Machinery
    {"id": "machinery_tractor", "crop": "None", "label": "Agricultural Tractor", "query": "Tractor plowing", "expected": "MACHINERY_EQUIPMENT"},
    {"id": "non_plant_animal", "crop": "None", "label": "Farm Zebu Cattle", "query": "Bos indicus bullock", "expected": "NON_AGRICULTURAL_OBJECT"},
    {"id": "non_plant_object", "crop": "None", "label": "Mechanical Clock/Watch", "query": "Pocket watch movement", "expected": "NON_AGRICULTURAL_OBJECT"}
]

def search_wikimedia(query):
    url = f"https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrsearch={urllib.parse.quote(query)}&gsrlimit=3&prop=imageinfo&iiprop=url|mime|extmetadata&format=json"
    req = urllib.request.Request(url, headers=HEADERS)
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=12) as r:
            data = json.loads(r.read())
            pages = data.get('query', {}).get('pages', {})
            for pid, page in pages.items():
                infos = page.get('imageinfo', [])
                if infos:
                    info = infos[0]
                    mime = info.get('mime', '')
                    orig_url = info.get('url', '')
                    meta = info.get('extmetadata', {})
                    lic = meta.get('LicenseShortName', {}).get('value', 'CC / Public Domain')
                    path = urllib.parse.urlparse(orig_url).path
                    if 'image' in mime and path.lower().endswith(('.jpg', '.jpeg', '.png')):
                        return {
                            "title": page.get('title'),
                            "url": orig_url,
                            "license": lic
                        }
    except Exception as e:
        print(f"Error querying '{query}': {e}")
    return None

def download_file(url, target_path):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, context=ctx, timeout=25) as r:
        content = r.read()
        with open(target_path, 'wb') as f:
            f.write(content)
        return len(content)

manifest = []

print("=== FETCHING AUTHENTIC AGRICULTURAL BENCHMARK SAMPLES ===")
for target in SEARCH_TARGETS:
    tid = target['id']
    target_path = os.path.join(DATASET_DIR, f"{tid}.jpg")
    print(f"Acquiring [{target['crop']} - {target['label']}] (query: {target['query']})...")
    
    result = search_wikimedia(target['query'])
    if result:
        try:
            size = download_file(result['url'], target_path)
            with Image.open(target_path) as im:
                im = im.convert('RGB')
                im.thumbnail((800, 800))
                im.save(target_path, "JPEG", quality=90)
            
            with open(target_path, 'rb') as f:
                sha256 = hashlib.sha256(f.read()).hexdigest()

            entry = {
                "id": tid,
                "crop": target['crop'],
                "label": target['label'],
                "expected_class": target['expected'],
                "filename": f"{tid}.jpg",
                "source_title": result['title'],
                "source_url": result['url'],
                "license": result['license'],
                "sha256": sha256,
                "status": "ACQUIRED"
            }
            manifest.append(entry)
            print(f"  -> OK: {result['title'][:50]}... ({sha256[:12]})")
        except Exception as e:
            print(f"  -> Error: {e}")
            manifest.append({
                "id": tid, "crop": target['crop'], "label": target['label'],
                "expected_class": target['expected'], "status": "PENDING", "reason": str(e)
            })
    else:
        print("  -> PENDING (no unencumbered match)")
        manifest.append({
            "id": tid, "crop": target['crop'], "label": target['label'],
            "expected_class": target['expected'], "status": "PENDING", "reason": "No match"
        })

# Controlled distortions for quality tests
print("\n=== GENERATING CONTROLLED QUALITY EDGE CASES ===")
base_sample = None
for m in manifest:
    if m.get('status') == 'ACQUIRED':
        base_sample = os.path.join(DATASET_DIR, m['filename'])
        break

if base_sample and os.path.exists(base_sample):
    with Image.open(base_sample) as img:
        img = img.convert('RGB')
        
        # 1. Blurry
        b_path = os.path.join(DATASET_DIR, "quality_too_blurry.jpg")
        img.filter(ImageFilter.GaussianBlur(radius=12)).save(b_path, "JPEG")
        with open(b_path, 'rb') as f:
            b_hash = hashlib.sha256(f.read()).hexdigest()
        manifest.append({
            "id": "quality_too_blurry", "crop": "Cotton", "label": "Severe Motion Blur Test",
            "expected_class": "QUALITY_REJECTED", "filename": "quality_too_blurry.jpg",
            "source_title": "Gaussian Blur Kernel radius=12 of " + os.path.basename(base_sample),
            "source_url": "Local Transformation", "license": "CC-BY", "sha256": b_hash, "status": "ACQUIRED"
        })
        print(f"  -> Generated quality_too_blurry.jpg ({b_hash[:12]})")

        # 2. Dark
        d_path = os.path.join(DATASET_DIR, "quality_too_dark.jpg")
        ImageEnhance.Brightness(img).enhance(0.10).save(d_path, "JPEG")
        with open(d_path, 'rb') as f:
            d_hash = hashlib.sha256(f.read()).hexdigest()
        manifest.append({
            "id": "quality_too_dark", "crop": "Cotton", "label": "Severe Underexposure Test",
            "expected_class": "QUALITY_REJECTED", "filename": "quality_too_dark.jpg",
            "source_title": "Brightness factor=0.10 of " + os.path.basename(base_sample),
            "source_url": "Local Transformation", "license": "CC-BY", "sha256": d_hash, "status": "ACQUIRED"
        })
        print(f"  -> Generated quality_too_dark.jpg ({d_hash[:12]})")

        # 3. Overexposed
        o_path = os.path.join(DATASET_DIR, "quality_overexposed.jpg")
        bright = ImageEnhance.Brightness(img).enhance(3.8)
        ImageEnhance.Contrast(bright).enhance(1.9).save(o_path, "JPEG")
        with open(o_path, 'rb') as f:
            o_hash = hashlib.sha256(f.read()).hexdigest()
        manifest.append({
            "id": "quality_overexposed", "crop": "Cotton", "label": "Severe Sun Glare Overexposure Test",
            "expected_class": "QUALITY_REJECTED", "filename": "quality_overexposed.jpg",
            "source_title": "Brightness 3.8 + Contrast 1.9 of " + os.path.basename(base_sample),
            "source_url": "Local Transformation", "license": "CC-BY", "sha256": o_hash, "status": "ACQUIRED"
        })
        print(f"  -> Generated quality_overexposed.jpg ({o_hash[:12]})")

manifest_path = os.path.join(DATASET_DIR, "test_dataset_manifest.json")
with open(manifest_path, 'w', encoding='utf-8') as f:
    json.dump(manifest, f, indent=2, ensure_ascii=False)

acquired_count = sum(1 for m in manifest if m.get('status') == 'ACQUIRED')
print(f"\nManifest ready: {acquired_count}/{len(manifest)} acquired.")
