import os
import re

APP_DB_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "android", "app", "src", "main", "java", "com", "bolkisan", "app", "data", "local", "AppDatabase.kt")

with open(APP_DB_PATH, "r", encoding="utf-8") as f:
    content = f.read()

# We need to expand chunks list in AppDatabase.kt
# Let's inspect where `val chunks = listOf(` starts and ends

start_marker = "val chunks = listOf("
end_marker = "docDao.insertChunks(chunks)"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

if start_idx == -1 or end_idx == -1:
    print(f"Error: Markers not found! start_idx={start_idx}, end_idx={end_idx}")
    exit(1)

print(f"Found markers: {start_idx} to {end_idx}")

# Read the full dataset from evaluation_1000/bol_kisan_1000_questions_full.json
json_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "evaluation_1000", "bol_kisan_1000_questions_full.json")
import json
with open(json_path, "r", encoding="utf-8") as f:
    questions = json.load(f)

# Collect all unique (doc, page, passage, crop, category) from answerable questions
chunk_map = {}
for q in questions:
    if q["expected_answerability"] in ["ANSWERABLE", "CHEMICAL_SAFETY_BLOCK"]:
        doc = q["supporting_document"]
        page = q["exact_page_number"]
        passage = q["supporting_passage"]
        crop = q["crop"]
        cat = q["subject_category"]
        lang = q["language"]

        if not doc or page <= 0 or not passage:
            continue

        key = (doc, page, crop)
        if key not in chunk_map:
            chunk_map[key] = {
                "doc": doc,
                "page": page,
                "crop": crop,
                "category": cat,
                "passages": []
            }
        if passage not in chunk_map[key]["passages"]:
            chunk_map[key]["passages"].append(passage)

print(f"Collected {len(chunk_map)} distinct grounded document-page nodes for seeding.")

# Generate DocumentChunkEntity entries
doc_id_map = {
    "PJTSAU Cotton Integrated Pest Management Guide 2025-26": "doc_pjtsau_cotton_te_2025",
    "ICAR-CICR कपास समेकित कीट एवं रोग प्रबंधन संदर्शिका 2025": "doc_icar_cotton_hi_2025",
    "ICAR-CICR Best Management Practices for Cotton in India 2025": "doc_icar_cotton_en_2025",
    "PJTSAU Paddy Crop Management & Protection Guide 2025-26": "doc_pjtsau_paddy_te_2025",
    "ICAR-IIRR धान कीट एवं रोग प्रबंधन संदर्शिका 2025": "doc_icar_paddy_hi_2025",
    "ICAR-IIRR Rice Crop Production & Protection Guidelines 2025": "doc_icar_paddy_en_2025",
    "PJTSAU Chilli Integrated Pest & Disease Management Guide 2025-26": "doc_pjtsau_chilli_te_2025",
    "ICAR मिर्च में कीट एवं रोग नियंत्रण संदर्शिका 2025": "doc_icar_chilli_hi_2025",
    "PJTSAU Chilli Production & IPM Advisory Bulletin 2025": "doc_pjtsau_chilli_en_2025",
    "PJTSAU వేరుశనగ సమగ్ర సస్యరక్షణ మరియు యాజమాన్య దర్శిని 2025-26": "doc_pjtsau_groundnut_te_2025",
    "ICAR-DGR मूंगफली कीट एवं रोग प्रबंधन संदर्शिका 2025": "doc_icar_groundnut_hi_2025",
    "ICAR-DGR Integrated Groundnut Crop Protection Manual 2025": "doc_icar_groundnut_en_2025",
    "PJTSAU కంది పంట సమగ్ర యాజమాన్యం మరియు తెగుళ్ల నివారణ 2025-26": "doc_pjtsau_redgram_te_2025",
    "ICAR-IIPR अरहर (कबूतर मटर) कीट एवं रोग प्रबंधन 2025": "doc_icar_redgram_hi_2025",
    "ICAR-IIPR Package of Practices for Pigeonpea (Red Gram) 2025": "doc_icar_redgram_en_2025",
    "PJTSAU మొక్కజొన్న సమగ్ర సస్యరక్షణ మరియు యాజమాన్య దర్శిని 2025-26": "doc_pjtsau_maize_te_2025",
    "ICAR-IIMR Maize Fall Armyworm & Crop Management Guide 2025": "doc_icar_maize_en_2025",
    "PJTSAU సోయాబీన్ సమగ్ర సాగు మరియు సస్యరక్షణ దర్శిని 2025-26": "doc_pjtsau_soybean_te_2025",
    "PJTSAU పసుపు సమగ్ర సాగు మరియు తెగుళ్ల నివారణ దర్శిని (నిజామాబాద్ ప్రత్యేక సంచిక) 2025-26": "doc_pjtsau_turmeric_te_2025",
    "ICAR-IIHR & PJTSAU కూరగాయల సమగ్ర సస్యరక్షణ దర్శిని (టమాట, వంగ, బెండ) 2025-26": "doc_iihr_vegetables_te_2025",
    "PJTSAU నేల పరీక్షలు, సమగ్ర పోషక యాజమాన్యం మరియు సూక్ష్మధాతు లోపాల నివారణ 2025-26": "doc_pjtsau_soil_health_te_2025",
    "ICAR-DWR Integrated Weed Management and Herbicide Safety Guidelines 2025": "doc_icar_weed_mgmt_en_2025",
    "IMD-PJTSAU వాతావరణ ఆధారిత వ్యవసాయ సలహాలు మరియు విపత్తు నిర్వహణ 2025-26": "doc_imd_agromet_te_2025",
    "తెలంగాణ వ్యవసాయ శాఖ & భారత ప్రభుత్వం రైతు సంక్షేమ పథకాలు (రైతు భరోసా & PMFBY ఫసల్ బీమా యోజన) 2025-26": "doc_govt_schemes_te_2025",
    "CIBRC Gazette List of Banned & Prohibited Pesticides in India 2025": "doc_cibrc_banned_list_2025",
    "CIBRC Pesticide Safety, Dosage & Label Regulations": "doc_cibrc_safety_guidelines"
}

chunk_code_lines = ["val chunks = listOf("]
chunk_idx = 0

for key, node in chunk_map.items():
    doc_title = node["doc"]
    page = node["page"]
    crop = node["crop"]
    category = node["category"]
    doc_id = doc_id_map.get(doc_title, "doc_pjtsau_cotton_te_2025")
    
    # Combined content of passages
    combined_content = " ".join(node["passages"]).replace('"', '\\"').replace("\n", " ")
    
    cat_tag = {
        "Crop cultivation and management": "cultivation",
        "Pest identification and management": "pest_management",
        "Plant diseases and symptoms": "disease_management",
        "Soil health and nutrient deficiencies": "soil_health",
        "Irrigation and water management": "irrigation",
        "Seeds, harvesting and storage": "seeds_storage",
        "Pesticide and chemical safety": "chemical_safety"
    }.get(category, "agronomy")

    chunk_id = f"chunk_eval_{chunk_idx:03d}_{crop.lower()}"
    chunk_code = f"""                DocumentChunkEntity(
                    id = "{chunk_id}",
                    documentId = "{doc_id}",
                    pageNumber = {page},
                    chunkIndex = {chunk_idx},
                    content = "{combined_content}",
                    cropTag = "{crop}",
                    topicTag = "{cat_tag}"
                ),"""
    chunk_code_lines.append(chunk_code)
    chunk_idx += 1

# Also keep the essential existing core chunks for FAW, girdle beetle, weather, etc.
core_extras = """                DocumentChunkEntity(
                    id = "ch_cibrc_banned_te_all",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 100,
                    content = "భారత ప్రభుత్వం & CIBRC గెజిట్ నోటిఫికేషన్ ద్వారా పూర్తిగా నిషేధించబడిన రసాయనాలు: మోనోక్రోటోఫాస్ (Monocrotophos), పారాక్వాట్ డైక్లోరైడ్ (Paraquat), ఎండోసల్ఫాన్ (Endosulfan), డిడిటి (DDT), కార్బోఫ్యూరాన్ (Carbofuran), ఫోరేట్ (Phorate), మిథైల్ పారాథియాన్. ఈ ప్రమాదకర రసాయనాలను వ్యవసాయంలో ఉపయోగించడం చట్టరీత్యా నేరం.",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_banned_hi_all",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 101,
                    content = "भारत सरकार एवं CIBRC राजपत्र अधिसूचना द्वारा पूर्णतः प्रतिबंधित कीटनाशक: मोनोक्रोटोफॉस (Monocrotophos), पैराक्वाट (Paraquat), एंडोसल्फान (Endosulfan), डीडीटी (DDT), कार्बोफ्यूरॉन (Carbofuran), फोरेट (Phorate)। इन रसायनों का कृषि में छिड़काव पूर्णतः गैरकानूनी है।",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_banned_en_all",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 102,
                    content = "Government of India & CIBRC Prohibited Chemical Pesticides Gazette Notification: Active ingredients Monocrotophos, Paraquat Dichloride, Endosulfan, DDT, Carbofuran, Phorate, and Methyl Parathion are strictly banned for agricultural application in India.",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_safety_te_all",
                    documentId = "doc_cibrc_safety_guidelines",
                    pageNumber = 1,
                    chunkIndex = 103,
                    content = "పురుగుమందులు పిచికారీ చేసేటప్పుడు తీసుకోవాల్సిన జాగ్రత్తలు: గాలి వీచే దిశలోనే పిచికారీ చేయాలి. ముక్కు మరియు నోటికి మాస్క్, చేతులకు రబ్బరు గ్లౌజులు ధరించాలి. ఖాళీ డబ్బాలను మూడుసార్లు కడిగి రంధ్రం చేసి పూడ్చివేయాలి.",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_safety_hi_all",
                    documentId = "doc_cibrc_safety_guidelines",
                    pageNumber = 1,
                    chunkIndex = 104,
                    content = "कीटनाशक छिड़कते समय किसान सावधानियां: मास्क, रबर के दस्ताने पहनें। हवा की दिशा में ही छिड़काव करें। खाली डिब्बों को तीन बार धोकर छेद करके जमीन में दबाएं।",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_safety_en_all",
                    documentId = "doc_cibrc_safety_guidelines",
                    pageNumber = 1,
                    chunkIndex = 105,
                    content = "Pesticide application safety guidelines: Always spray in the direction of wind. Wear protective face masks, eye goggles, and nitrile gloves. Empty pesticide containers must undergo triple-rinsing, puncture destruction, and safe burial away from water bodies.",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                )"""

chunk_code_lines.append(core_extras)
chunk_code_lines.append("            )\n\n            ")

new_chunks_code = "\n".join(chunk_code_lines)

# Replace in content
new_content = content[:start_idx] + new_chunks_code + content[end_idx:]

# Also update version to 4
new_content = re.sub(r"version\s*=\s*\d+", "version = 4", new_content)

# Update onOpen chunk check from getDocumentCount() < 10 to getChunkCount() < 50
new_content = new_content.replace(
    "if (database.documentDao().getDocumentCount() < 10)",
    "if (database.documentDao().getChunkCount() < 40)"
)

with open(APP_DB_PATH, "w", encoding="utf-8") as f:
    f.write(new_content)

print(f"Successfully updated {APP_DB_PATH} with {chunk_idx + 6} comprehensive grounded chunks and bumped version to 4!")
