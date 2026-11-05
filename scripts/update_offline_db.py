import os
import sqlite3
import json

db_file = os.path.join(os.path.dirname(__file__), "..", "offline_engine", "bol_kisan_offline.db")

# Read documents from verified catalog
catalog_path = os.path.join(os.path.dirname(__file__), "..", "docs", "VERIFIED_AGRICULTURAL_DOCUMENTS.json")
with open(catalog_path, "r", encoding="utf-8") as f:
    catalog_data = json.load(f)

docs = catalog_data["documents"]

# Multilingual chunks covering all 5 crops & CIBRC
chunks = [
    # ------------------ COTTON ------------------
    ("ch_cotton_pbw_te", "doc_pjtsau_cotton_te_2025", 4, 0,
     "పత్తిలో గులాబీ రంగు పురుగు నివారణ మరియు యాజమాన్యం (Cotton Pink Bollworm Control): పూత మరియు కాయ దశలో ఎకరానికి 8 లింగాకర్షక బుట్టలను అమర్చాలి. నివారణకు: వేప నూనె 10,000 PPM ఎకరానికి 400-500 మి.లీ 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. పురుగు ఉధృతిని బట్టి పిచికారీ చేయాలి. పత్తి పంటలో గులాబీ రంగు పురుగు సమస్యలు సమర్థవంతంగా అదుపులోకి వస్తాయి.",
     "Cotton", "pest_management"),

    ("ch_cotton_yellow_te", "doc_pjtsau_cotton_te_2025", 7, 1,
     "పత్తిలో ఆకులు పసుపు రంగులోకి మారుతున్నాయి (ఆకులు పసుపుబారడం మరియు రసం పీల్చే పురుగుల నివారణ): తెల్లదోమ మరియు జెస్సిడ్స్ ఆకుల రసాన్ని పీల్చడం వల్ల ఆకులు, ఆకుల అంచులు పసుపు రంగులోకి మారుతాయి / మారుతున్నాయి. నివారణకు పసుపు మరియు నీలి రంగు జిగురు అట్టలను ఎకరానికి 10 చొప్పున అమర్చాలి. వేపనూనె 5 మి.లీ లీటరు నీటికి కలిపి పిచికారీ చేయాలి. నత్రజని అధికంగా వేయవద్దు.",
     "Cotton", "symptom_yellowing"),

    ("ch_cotton_pbw_hi", "doc_icar_cotton_hi_2025", 5, 0,
     "कपास की फसल में मुख्य समस्याएं एवं गुलाबी सुंडी (Pink Bollworm) का नियंत्रण: फूल और गूलर बनने की अवस्था में प्रति एकड़ 8 फेरोमोन ट्रैप लगाएं। नियंत्रण के लिए नीम का तेल (Neem oil 10000 PPM) 400-500 मिली प्रति एकड़ 200 लीटर पानी में मिलाकर छिड़काव करें। लगातार 3 दिन तक 8 पतंगे मिलने पर तुरंत उपचार करें।",
     "Cotton", "pest_management"),

    ("ch_cotton_whitefly_hi", "doc_icar_cotton_hi_2025", 8, 1,
     "कपास में पत्तियों का पीलापन और सफेद मक्खी की रोकथाम: सफेद मक्खी पत्तियों से रस चूसती है जिससे पत्तियां पीली पड़ जाती हैं। रोकथाम के लिए प्रति एकड़ 10-15 पीले चिपचिपे कार्ड लगाएं और नीम तेल 5 मिली प्रति लीटर पानी में मिलाकर छिड़कें। यूरिया का अत्यधिक उपयोग न करें।",
     "Cotton", "symptom_yellowing"),

    ("ch_cotton_pbw_en", "doc_icar_cotton_en_2025", 6, 0,
     "Cotton Pink Bollworm management and prevention: Install 8 pheromone traps per acre during flowering and boll formation stage. Economic Threshold Level (ETL) is 8 moths per trap per day for 3 consecutive days. For control, spray Neem oil 10,000 PPM at 400-500 ml per acre in 200 liters of water or Chlorantraniliprole 18.5% SC at 60 ml per acre.",
     "Cotton", "pest_management"),

    ("ch_cotton_yellow_en", "doc_icar_cotton_en_2025", 10, 1,
     "Cotton leaves turning yellow and sucking pest control: Whiteflies and jassids suck cell sap, causing cotton leaf edges to curl and turn yellow. Control with 10 yellow and blue sticky traps per acre. Spray 5% neem seed kernel extract (NSKE) or neem oil at 5 ml per liter of water. Avoid excessive nitrogenous fertilizers.",
     "Cotton", "symptom_yellowing"),

    # ------------------ PADDY ------------------
    ("ch_paddy_stem_te", "doc_pjtsau_paddy_te_2025", 14, 0,
     "వరిలో కాండం తొలుచు పురుగు (Stem Borer) మరియు చనిపోయిన మొవ్వులు (Dead hearts) నివారణ: సిఫార్సు చేసిన క్లోరాంట్రానిలీప్రోల్ (Chlorantraniliprole 18.5% SC) 60 మి.లీ ఎకరాకు 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. ఎకరానికి 5 లింగాకర్షక బుట్టలను అమర్చాలి.",
     "Paddy", "pest_management"),

    ("ch_paddy_yellow_te", "doc_pjtsau_paddy_te_2025", 16, 1,
     "వరిలో ఆకులు పసుపు రంగులోకి మారుతున్నాయి (వరి ఆకులు పసుపుబారడం మరియు జింక్ లోపం నివారణ): వరి పైరులో జింక్ లేదా ఇనుము లోపం వల్ల లేదా కాండం తొలుచు పురుగు ఉధృతి వల్ల ఆకులు పసుపు రంగులోకి మారుతాయి. నివారణకు జింక్ సల్ఫేట్ 2 గ్రాములు లీటరు నీటికి కలిపి పిచికారీ చేయాలి.",
     "Paddy", "symptom_yellowing"),

    ("ch_paddy_stem_hi", "doc_icar_paddy_hi_2025", 12, 0,
     "धान में तना छेदक (Stem Borer) एवं मृत गोभ (Dead heart) की रोकथाम: फसल में लक्षण दिखने पर क्लोरांट्रानिलीप्रोल (Chlorantraniliprole 18.5% SC) 60 मिली प्रति एकड़ 200 लीटर पानी में मिलाकर छिड़काव करें। 5 फेरोमोन ट्रैप प्रति एकड़ लगाएं।",
     "Paddy", "pest_management"),

    ("ch_paddy_yellow_hi", "doc_icar_paddy_hi_2025", 15, 1,
     "धान में पत्तियों का पीलापन एवं जिंक की कमी का नियंत्रण: जिंक या लौह तत्व की कमी से धान की पत्तियां पीली पड़ जाती हैं। रोकथाम के लिए जिंक सल्फेट (Zinc Sulphate 21%) 2 ग्राम प्रति लीटर पानी में मिलाकर छिड़कें।",
     "Paddy", "symptom_yellowing"),

    ("ch_paddy_stem_en", "doc_icar_paddy_en_2025", 14, 0,
     "Paddy rice stem borer and dead heart control: Upon noticing dead hearts during vegetative stage or white ears at heading, apply Chlorantraniliprole 18.5% SC at 60 ml per acre mixed in 200 liters of water. Install 5 pheromone traps per acre to monitor yellow stem borer adult activity.",
     "Paddy", "pest_management"),

    ("ch_paddy_yellow_en", "doc_icar_paddy_en_2025", 18, 1,
     "Paddy rice leaves turning yellow due to zinc deficiency or iron chlorosis: Yellowing appears on younger leaves and spreads down the blades. Spray Zinc Sulphate (ZnSO4) at 2 grams per liter of water (400-500 grams per acre in 200 liters of water) twice at 10-day intervals.",
     "Paddy", "symptom_yellowing"),

    # ------------------ CHILLI ------------------
    ("ch_chilli_thrips_te", "doc_pjtsau_chilli_te_2025", 9, 0,
     "మిరపలో తామర పురుగులు, నల్లి మరియు ఆకుముడుత నివారణ (Chilli Thrips & Mite Control): తామర పురుగుల వల్ల ఆకులు పైకి, నల్లి వల్ల ఆకులు క్రిందికి ముడుచుకుంటాయి. నివారణకు వేపనూనె 10,000 PPM ఎకరానికి 400-500 మి.లీ లేదా ఎకరాకు 80-100 గ్రాముల ఎమామెక్టిన్ బెంజోయేట్ 5% SG 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. ఎకరానికి 15-20 నీలి మరియు పసుపు జిగురు అట్టలను అమర్చాలి.",
     "Chilli", "pest_management"),

    ("ch_chilli_yellow_te", "doc_pjtsau_chilli_te_2025", 12, 1,
     "మిరపలో ఆకులు పసుపు రంగులోకి మారడం మరియు కొమ్మ ఎండు తెగులు నివారణ: పోషక లోపాలు లేదా ఫంగస్ వల్ల మిరప ఆకులు పసుపుబారతాయి. నివారణకు కాపర్ ఆక్సీక్లోరైడ్ 3 గ్రాములు లేదా మాంకోజెబ్ 2.5 గ్రాములు లీటరు నీటికి కలిపి పిచికారీ చేయాలి.",
     "Chilli", "symptom_yellowing"),

    ("ch_chilli_thrips_hi", "doc_icar_chilli_hi_2025", 10, 0,
     "मिर्च में थ्रिप्स एवं माइट्स (मुरडिया रोग) की रोकथाम: थ्रिप्स से पत्तियां ऊपर की ओर नाव की तरह और माइट्स से नीचे की ओर मुड़ती हैं। नियंत्रण के लिए नीम तेल 10000 PPM 2-3 मिली प्रति लीटर या इमामेक्टिन बेंजोएट 5% SG 80-100 ग्राम प्रति एकड़ 200 लीटर पानी में छिड़कें। 15 नीले व पीले ट्रैप लगाएं।",
     "Chilli", "pest_management"),

    ("ch_chilli_thrips_en", "doc_pjtsau_chilli_en_2025", 11, 0,
     "Chilli thrips, yellow mites, and leaf curl management: Upward curling is caused by thrips, while downward curling indicates yellow mites. Spray Neem oil 10,000 PPM at 2-3 ml per liter of water, or Emamectin Benzoate 5% SG at 80-100 grams per acre in 200 liters of water. Install 15-20 blue and yellow sticky traps per acre.",
     "Chilli", "pest_management"),

    ("ch_chilli_yellow_en", "doc_pjtsau_chilli_en_2025", 15, 1,
     "Chilli leaves turning yellow and die-back anthracnose prevention: Yellowing of chilli foliage occurs from nutrient imbalance or early fungal infection. Spray Copper Oxychloride 50% WP at 3 g/L or Mancozeb 75% WP at 2.5 g/L of water. Ensure proper field drainage to avoid root rot.",
     "Chilli", "symptom_yellowing"),

    # ------------------ GROUNDNUT ------------------
    ("ch_groundnut_tikka_te", "doc_pjtsau_groundnut_te_2025", 8, 0,
     "వేరుశనగలో టిక్కా ఆకుమచ్చ తెగులు నివారణ (Groundnut Tikka Leaf Spot Control): ఆకులపై గోధుమ రంగు గుండ్రటి మచ్చలు ఏర్పడి ఆకులు రాలిపోతాయి. నివారణకు మాంకోజెబ్ (Mancozeb 75% WP) 2-2.5 గ్రాములు లేదా టెబుకొనజోల్ 1 మి.లీ లీటరు నీటికి కలిపి 10-12 రోజుల వ్యవధిలో రెండుసార్లు పిచికారీ చేయాలి. విత్తన శుద్ధి తప్పనిసరి.",
     "Groundnut", "pest_management"),

    ("ch_groundnut_yellow_te", "doc_pjtsau_groundnut_te_2025", 11, 1,
     "వేరుశనగలో ఆకులు పసుపు రంగులోకి మారడం మరియు ఇనుము లోపం నివారణ (Groundnut Iron Chlorosis): సున్నపు నేలల్లో లేత ఆకులు పసుపు రంగులోకి మారి ఈనెలు మాత్రమే పచ్చగా ఉంటాయి. నివారణకు అన్నభేది (ఫెర్రస్ సల్ఫేట్) 5 గ్రాములు మరియు నిమ్మ ఉప్పు (సిట్రిక్ యాసిడ్) 1 గ్రాము లీటరు నీటికి కలిపి వారం వ్యవధిలో రెండుసార్లు పిచికారీ చేయాలి.",
     "Groundnut", "symptom_yellowing"),

    ("ch_groundnut_tikka_hi", "doc_icar_groundnut_hi_2025", 9, 0,
     "मूंगफली में टिक्का पत्ती धब्बा रोग (Tikka Disease) का नियंत्रण: पत्तियों पर गहरे भूरे रंग के गोल धब्बे बनते हैं। रोकथाम हेतु मैनकोजेब 2-2.5 ग्राम प्रति लीटर पानी या टेबुकोनाजोल 1 मिली प्रति लीटर पानी में मिलाकर 12-15 दिन के अंतराल पर छिड़कें। बीजोपचार अवश्य करें।",
     "Groundnut", "pest_management"),

    ("ch_groundnut_tikka_en", "doc_icar_groundnut_en_2025", 10, 0,
     "Groundnut Tikka leaf spot (Cercospora leaf spot) and collar rot control: Early and late leaf spots cause circular dark brown necrotic spots leading to severe defoliation. Spray Mancozeb 75% WP at 2.5 g/L or Tebuconazole 25.9% EC at 1 ml/L of water at 12-14 day intervals.",
     "Groundnut", "pest_management"),

    ("ch_groundnut_yellow_en", "doc_icar_groundnut_en_2025", 14, 1,
     "Groundnut leaves turning yellow due to iron chlorosis: Interveinal chlorosis appears on young emerging leaves, turning them completely pale yellow in calcareous soils. Apply foliar spray of Ferrous Sulphate (FeSO4) at 5 g/L along with Citric Acid at 1 g/L twice at 7-day intervals.",
     "Groundnut", "symptom_yellowing"),

    # ------------------ RED GRAM (PIGEONPEA) ------------------
    ("ch_redgram_podborer_te", "doc_pjtsau_redgram_te_2025", 7, 0,
     "కందిలో కాయతొలుచు పురుగు / శనగపచ్చ పురుగు నివారణ (Red gram / Pigeonpea Pod Borer Control): పూత మరియు పిందె దశలో హెలికోవెర్పా లార్వా పూతను, కాయలను తొలచి తింటుంది. నివారణకు ఎకరాకు 4 లింగాకర్షక బుట్టలు, 10-15 పక్షి స్థావరాలను అమర్చాలి. పిచికారీకి: ఎమామెక్టిన్ బెంజోయేట్ 5% SG ఎకరాకు 88-100 గ్రాములు లేదా క్లోరాంట్రానిలీప్రోల్ 18.5% SC 60 మి.లీ 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి.",
     "Red gram", "pest_management"),

    ("ch_redgram_wilt_te", "doc_pjtsau_redgram_te_2025", 12, 1,
     "కందిలో ఎండు తెగులు మరియు ఆకులు పసుపుబారడం నివారణ (Pigeonpea Fusarium Wilt): మొక్కల ఆకులు పసుపు రంగులోకి మారి వాడిపోయి మొక్కలు ఎండిపోతాయి. నివారణకు ట్రైకోడెర్మా విరిడి 10 గ్రాములు కిలో విత్తనానికి కలిపి విత్తన శుద్ధి చేయాలి. తెగులు లక్షణాలు కనిపించినప్పుడు కాపర్ ఆక్సీక్లోరైడ్ 3 గ్రాములు లీటరు నీటికి కలిపి మొదళ్ల వద్ద తడపాలి.",
     "Red gram", "symptom_yellowing"),

    ("ch_redgram_podborer_hi", "doc_icar_redgram_hi_2025", 8, 0,
     "अरहर (कबूतर मटर) में फली छेदक (Pod Borer - Helicoverpa) का नियंत्रण: फूल और फली बनने के समय सुंडी कलियों और फलियों में छेद करती है। रोकथाम के लिए प्रति एकड़ 4 फेरोमोन ट्रैप और पक्षी बसेरे लगाएं। रासायनिक नियंत्रण हेतु इमामेक्टिन बेंजोएट 5% SG 88-100 ग्राम या क्लोरांट्रानिलीप्रोल 18.5% SC 60 मिली प्रति एकड़ 200 लीटर पानी में छिड़कें।",
     "Red gram", "pest_management"),

    ("ch_redgram_podborer_en", "doc_icar_redgram_en_2025", 9, 0,
     "Red gram (Pigeonpea) pod borer Helicoverpa armigera management: Caterpillars feed on flowers, buds, and bore circular holes into developing pods. Install 4 pheromone traps and 10-15 bird perches per acre. Spray Emamectin Benzoate 5% SG at 88-100 g/acre or Chlorantraniliprole 18.5% SC at 60 ml per acre in 200 liters of water at flower bud initiation.",
     "Red gram", "pest_management"),

    ("ch_redgram_wilt_en", "doc_icar_redgram_en_2025", 15, 1,
     "Red gram pigeonpea Fusarium wilt and leaf yellowing prevention: Symptoms start with leaf yellowing, dropping, and sudden wilting of plant branches due to vascular browning. Practice seed treatment with Trichoderma viride at 10 g/kg seed. In standing crops showing early symptoms, drench plant base with Copper Oxychloride at 3 g/L.",
     "Red gram", "symptom_yellowing"),

    # ------------------ CHEMICAL SAFETY & BANNED LIST ------------------
    ("ch_cibrc_banned_te", "doc_cibrc_banned_list_2025", 1, 0,
     "భారత ప్రభుత్వం & CIBRC గెజిట్ నోటిఫికేషన్ ద్వారా పూర్తిగా నిషేధించబడిన రసాయనాలు: మోనోక్రోటోఫాస్ (Monocrotophos), పారాక్వాట్ డైక్లోరైడ్ (Paraquat), ఎండోసల్ఫాన్ (Endosulfan), డిడిటి (DDT), కార్బోఫ్యూరాన్ (Carbofuran), ఫోరేట్ (Phorate), మిథైల్ పారాథియాన్. ఈ ప్రమాదకర రసాయనాలను వ్యవసాయంలో ఉపయోగించడం చట్టరీత్యా నేరం మరియు పంటలకు, ప్రజలకు ప్రాణాంతక ప్రమాదం.",
     "Multi", "chemical_safety"),

    ("ch_cibrc_banned_hi", "doc_cibrc_banned_list_2025", 1, 1,
     "भारत सरकार एवं CIBRC राजपत्र अधिसूचना द्वारा पूर्णतः प्रतिबंधित कीटनाशक: मोनोक्रोटोफॉस (Monocrotophos), पैराक्वाट (Paraquat), एंडोसल्फान (Endosulfan), डीडीटी (DDT), कार्बोफ्यूरॉन (Carbofuran), फोरेट (Phorate)। इन रसायनों का कृषि में छिड़काव पूर्णतः गैरकानूनी एवं विषैला है।",
     "Multi", "chemical_safety"),

    ("ch_cibrc_banned_en", "doc_cibrc_banned_list_2025", 1, 2,
     "Government of India & CIBRC Prohibited Chemical Pesticides Gazette Notification: Active ingredients Monocrotophos, Paraquat Dichloride, Endosulfan, DDT, Carbofuran, Phorate, and Methyl Parathion are strictly banned for manufacture, import, sale, and agricultural application in India. Unauthorized usage is a cognizable legal offense under the Insecticides Act.",
     "Multi", "chemical_safety"),

    ("ch_cibrc_safe_te", "doc_cibrc_safety_guidelines", 1, 0,
     "పురుగుమందులు పిచికారీ చేసేటప్పుడు తీసుకోవాల్సిన జాగ్రత్తలు: గాలి వీచే దిశలోనే పిచికారీ చేయాలి. ముక్కు మరియు నోటికి మాస్క్, చేతులకు రబ్బరు గ్లౌజులు ధరించాలి. ఖాళీ డబ్బాలను మూడుసార్లు కడిగి రంధ్రం చేసి పూడ్చివేయాలి. నిరీక్షణ కాలాన్ని ఖచ్చితంగా పాటించాలి.",
     "Multi", "chemical_safety"),

    ("ch_cibrc_safe_en", "doc_cibrc_safety_guidelines", 1, 1,
     "Pesticide application safety guidelines and mandatory precautions: Always spray in the direction of wind. Wear protective face masks, eye goggles, and nitrile rubber gloves. Empty pesticide containers must undergo triple-rinsing, puncture destruction, and safe burial away from water bodies. Adhere strictly to the recommended Pre-Harvest Interval (PHI).",
     "Multi", "chemical_safety")
]

conn = sqlite3.connect(db_file)
cur = conn.cursor()

cur.execute("DROP TABLE IF EXISTS chunks_fts")
cur.execute("DROP TABLE IF EXISTS local_chunks")
cur.execute("DROP TABLE IF EXISTS local_documents")

# Ensure updated table schema
cur.execute("""
    CREATE TABLE IF NOT EXISTS local_documents (
        id TEXT PRIMARY KEY,
        title TEXT,
        crop TEXT,
        region TEXT,
        language TEXT,
        issuing_authority TEXT,
        publication_date TEXT,
        version TEXT,
        source_url TEXT DEFAULT '',
        verification_status TEXT DEFAULT 'VERIFIED_OFFICIAL',
        approval_status TEXT DEFAULT 'APPROVED'
    )
""")

cur.execute("""
    CREATE TABLE IF NOT EXISTS local_chunks (
        id TEXT PRIMARY KEY,
        document_id TEXT,
        page_number INTEGER,
        chunk_index INTEGER,
        content TEXT,
        crop_tag TEXT,
        topic_tag TEXT,
        FOREIGN KEY (document_id) REFERENCES local_documents(id)
    )
""")

cur.execute("""
    CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
        chunk_id UNINDEXED,
        document_id UNINDEXED,
        page_number UNINDEXED,
        crop UNINDEXED,
        content
    )
""")

# Insert documents
for d in docs:
    cur.execute("""
        INSERT OR REPLACE INTO local_documents 
        (id, title, crop, region, language, issuing_authority, publication_date, version, source_url, verification_status, approval_status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        d["id"], d["document_title"], d["crop"], d["region"], d["language"],
        d["publisher"], d["publication_date"], "2026.2", d["official_source_url"],
        d["verification_status"], d["administrator_approval_status"]
    ))

# Insert chunks
for ch_id, doc_id, page, c_idx, content, crop, topic in chunks:
    cur.execute("""
        INSERT OR REPLACE INTO local_chunks 
        (id, document_id, page_number, chunk_index, content, crop_tag, topic_tag)
        VALUES (?, ?, ?, ?, ?, ?, ?)
    """, (ch_id, doc_id, page, c_idx, content, crop, topic))

    cur.execute("""
        INSERT INTO chunks_fts (chunk_id, document_id, page_number, crop, content)
        VALUES (?, ?, ?, ?, ?)
    """, (ch_id, doc_id, page, crop, content))

# Insert Mandi prices for all 5 crops
cur.execute("""
    CREATE TABLE IF NOT EXISTS local_mandi_cache (
        id TEXT PRIMARY KEY,
        state TEXT,
        district TEXT,
        market_name TEXT,
        commodity TEXT,
        variety TEXT,
        min_price REAL,
        max_price REAL,
        modal_price REAL,
        observation_date TEXT,
        last_synced TIMESTAMP
    )
""")

mandi_prices = [
    ("mandi_warangal_cotton", "Telangana", "Warangal", "Warangal (Enamamula) Mandi", "Cotton (పత్తి / कपास)", "Medium Staple", 7250.0, 7820.0, 7550.0, "2026-11-04"),
    ("mandi_warangal_chilli", "Telangana", "Warangal", "Warangal (Enamamula) Mandi", "Chilli (మిర్చి / मिर्च)", "Teja", 14500.0, 18200.0, 16400.0, "2026-11-04"),
    ("mandi_suryapet_paddy", "Telangana", "Suryapet", "Suryapet Mandi", "Paddy (వరి / धान)", "BPT 5204", 2320.0, 2550.0, 2420.0, "2026-11-04"),
    ("mandi_gadwal_groundnut", "Telangana", "Jogulamba Gadwal", "Gadwal Mandi", "Groundnut (వేరుశనగ / मूंगफली)", "TAG 24", 5800.0, 6450.0, 6200.0, "2026-11-04"),
    ("mandi_tandur_redgram", "Telangana", "Vikarabad", "Tandur Mandi", "Red gram (కంది / अरहर)", "Tandur White", 9600.0, 10850.0, 10400.0, "2026-11-04")
]

cur.execute("DELETE FROM local_mandi_cache")
for mp in mandi_prices:
    cur.execute("""
        INSERT OR REPLACE INTO local_mandi_cache 
        (id, state, district, market_name, commodity, variety, min_price, max_price, modal_price, observation_date, last_synced)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
    """, mp)

conn.commit()

cur.execute("SELECT COUNT(*) FROM local_documents")
doc_cnt = cur.fetchone()[0]

cur.execute("SELECT COUNT(*) FROM local_chunks")
chk_cnt = cur.fetchone()[0]

cur.execute("SELECT COUNT(*) FROM local_mandi_cache")
mnd_cnt = cur.fetchone()[0]

conn.close()

print(f"Successfully updated offline database {db_file}:")
print(f"  Documents: {doc_cnt}")
print(f"  Chunks: {chk_cnt}")
print(f"  Mandi Prices: {mnd_cnt}")
