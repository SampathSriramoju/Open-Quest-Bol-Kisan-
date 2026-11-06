import os
import sys
import json
import re
import time

EVAL_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "evaluation_1000")

# Stopwords and mappings from LocalKnowledgeRetrievalEngine.kt
STOPWORDS = {
    "ఎలా", "చేయాలి", "ఉంది", "వలన", "మరియు", "దీని", "ఏమిటి", "ఎంత", "ఎప్పుడు", "ఎక్కడ", "కావాలి", "వాడాలి",
    "చెప్పండి", "నా", "మీ", "ఏ", "ఉన్నాయో", "సమస్యలు", "సలహా", "గురించి", "ఏం", "కోసం", "ఎలాంటి", "ద్వారా",
    "మరి", "చెయ్యాలి", "ఉన్నాయి", "ఉన్నది", "వస్తుంది", "వచ్చింది", "కనిపిస్తుంది", "కనిపిస్తున్నాయి",
    "అవుతుంది", "అయింది", "చేస్తే", "చేయవచ్చా", "బాగుంటుంది", "చేయవలసిన", "చేయడానికి", "కదా",
    "कैसे", "करें", "क्या", "होता", "है", "हैं", "का", "की", "के", "में", "से", "पर", "लिए", "बताएं", "चाहिए",
    "मेरी", "की", "फसल", "समस्य", "बताओ", "कुछ", "हो", "गया", "गई", "रहा", "रही", "रहे", "हुआ", "हुई",
    "बताइए", "दें", "दीजिए", "करना",
    "what", "how", "why", "when", "where", "tell", "my", "crop", "problem", "issues", "please", "can",
    "should", "the", "and", "or", "in", "on", "to", "for", "is", "are", "of", "with", "from", "do",
    "does", "about", "give", "me", "some", "advice", "suggestion", "best", "way"
}

NON_AGRI_TRAPS = [
    "క్యాన్సర్", "కరోనా", "వ్యాక్సిన్", "వ్యాక్సిన్లు", "హైడ్రోజన్", "బంగారం", "షేర్ మార్కెట్", "యూట్యూబ్", "రేషన్ కార్డు", "రిజిస్ట్రేషన్", "ముఖ్యమంత్రి",
    "ట్రాక్టర్", "ట్రాక్టరు", "ట్రాక్టర్లు", "ట్రాక్టర్ల", "సినిమా", "క్రికెట్", "రాజకీయాలు", "బ్యాంక్ లోన్", "ఆధార్", "పాస్‌పోర్ట్",
    "कैंसर", "कोरोना", "वैक्सीन", "शेयर मार्केट", "सोना", "मुख्यमंत्री", "गाड़ी", "ट्रैक्टर", "मोटर कार", "क्रिकेट", "सिनेमा", "आधार", "राशन कार्ड",
    "cancer", "vaccine", "corona", "covid", "gold", "share market", "minister", "gear box", "tractor", "tractors", "cricket", "cinema", "politics",
    "loan", "bank loan", "election", "aadhaar", "passport", "crypto", "bitcoin", "bollywood", "tollywood", "visa", "laptop", "company", "driving license", "pan card", "electric car"
]

BANNED_CHEMICALS = [
    "monocrotophos", "paraquat", "endosulfan", "ddt", "carbofuran", "phorate", "methyl parathion",
    "మోనోక్రోటోఫాస్", "పారాక్వాట్", "ఎండోసల్ఫాన్", "డిడిటి", "కార్బోఫ్యూరాన్", "ఫోరేట్",
    "मोनोक्रोटोफॉस", "पैराक्वाट", "एंडोसल्फान", "डीडीटी", "कार्बोफ्यूरॉन", "फोरेट"
]

AMBIGUOUS_PATTERNS = [
    "ఏదో పురుగు", "ఏ మందు కొట్టాలి", "పూత రాలిపోతోంది", "ఎరువుల బస్తా ఎంత", "పురుగు మందు పిచికారీ చేయవచ్చా",
    "విత్తనాలు మొలకెత్తలేదు", "कीड़ा लगा है क्या दवा", "पौधे पीले पड़ रहे", "दवा का छिड़काव कब", "फूल बहुत गिर रहे",
    "खाद कितनी मात्रा", "बीज अंकुरित नहीं", "पत्तियां सिकुड़ रही", "some insect is attacking", "leaves are turning yellow, what is the remedy",
    "how much fertilizer should i apply", "flowers are dropping heavily", "can i mix pesticides", "seed germination is poor",
    "spots are appearing on leaves, need spray", "plants are wilting suddenly", "how often should i irrigate my field"
]

# Load seeded chunks from AppDatabase.kt
APP_DB_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "android", "app", "src", "main", "java", "com", "bolkisan", "app", "data", "local", "AppDatabase.kt")
with open(APP_DB_PATH, "r", encoding="utf-8") as f:
    db_text = f.read()

# Extract chunks
chunk_pattern = re.compile(r'DocumentChunkEntity\(\s*id\s*=\s*"([^"]+)",\s*documentId\s*=\s*"([^"]+)",\s*pageNumber\s*=\s*(\d+),\s*chunkIndex\s*=\s*\d+,\s*content\s*=\s*"([^"]+)",\s*cropTag\s*=\s*"([^"]+)",\s*topicTag\s*=\s*"([^"]+)"', re.DOTALL)
chunks = []
for m in chunk_pattern.finditer(db_text):
    chunks.append({
        "id": m.group(1),
        "doc_id": m.group(2),
        "page": int(m.group(3)),
        "content": m.group(4).replace('\\"', '"'),
        "crop": m.group(5),
        "topic": m.group(6)
    })

# Extract documents
doc_pattern = re.compile(r'DocumentEntity\(\s*id\s*=\s*"([^"]+)",\s*title\s*=\s*"([^"]+)",\s*crop\s*=\s*"([^"]+)",\s*region\s*=\s*"([^"]+)",\s*language\s*=\s*"([^"]+)",\s*issuingAuthority\s*=\s*"([^"]+)"', re.DOTALL)
docs = {}
for m in doc_pattern.finditer(db_text):
    docs[m.group(1)] = {
        "title": m.group(2),
        "crop": m.group(3),
        "authority": m.group(6)
    }

print(f"Loaded {len(chunks)} chunks and {len(docs)} documents from AppDatabase.kt")

def strip_telugu_suffix(w):
    suffixes = ["లోని", "లోకి", "నుంచి", "నుండి", "లో", "తో", "కు", "కి", "ల", "లు", "ను", "ని"]
    for s in suffixes:
        if w.endswith(s) and len(w) > len(s) + 1:
            return w[:-len(s)]
    return w

def simulate_pipeline(q_text, crop_param, lang_param):
    query_low = q_text.lower().strip()
    tokens = set(re.split(r'[\s,;:.?!।\-"\']+', query_low))

    # 1. Non-Agri Trap
    for trap in NON_AGRI_TRAPS:
        trap_low = trap.lower().strip()
        if " " in trap_low:
            if trap_low in query_low:
                return {"scenario": "EVIDENCE_UNAVAILABLE_REFUSAL", "doc": None, "page": 0, "answer": "REFUSAL"}
        else:
            if trap_low in tokens:
                return {"scenario": "EVIDENCE_UNAVAILABLE_REFUSAL", "doc": None, "page": 0, "answer": "REFUSAL"}

    # 2. Chemical Safety
    for chem in BANNED_CHEMICALS:
        if chem in query_low:
            return {
                "scenario": "CHEMICAL_SAFETY_BLOCK",
                "doc": "CIBRC Gazette List of Banned & Prohibited Pesticides in India 2025",
                "page": 1,
                "answer": f"BANNED: {chem} is prohibited by law."
            }

    # 3. Ambiguous Clarification
    for pat in AMBIGUOUS_PATTERNS:
        if pat in query_low:
            return {"scenario": "INCOMPLETE_EVIDENCE", "doc": None, "page": 0, "answer": "CLARIFICATION_PROMPT"}

    # 4. Search and Retrieval
    raw_terms = [t for t in re.split(r'[\s,?.!]+', query_low) if len(t) >= 2]
    search_terms = [t for t in raw_terms if t not in STOPWORDS]
    effective_terms = search_terms if search_terms else raw_terms

    expanded_terms = set(effective_terms)
    for t in effective_terms:
        if any('\u0C00' <= c <= '\u0C7F' for c in t):
            stem = strip_telugu_suffix(t)
            if len(stem) >= 2 and stem not in STOPWORDS:
                expanded_terms.add(stem)

    candidates = []
    for c in chunks:
        c_low = c["content"].lower()
        match_count = sum(1 for term in expanded_terms if term in c_low)
        if match_count > 0:
            score = match_count * 2
            # Lang boost
            c_is_te = any('\u0C00' <= ch <= '\u0C7F' for ch in c["content"])
            c_is_hi = any('\u0900' <= ch <= '\u097F' for ch in c["content"])
            c_is_en = not c_is_te and not c_is_hi

            if lang_param == "te" and c_is_te: score += 6
            if lang_param == "hi" and c_is_hi: score += 6
            if lang_param == "en" and c_is_en: score += 6

            # Crop boost
            if crop_param and crop_param.lower() in c["crop"].lower():
                score += 6
            elif c["crop"] == "Multi":
                score += 2

            candidates.append((c, score))

    if candidates:
        candidates.sort(key=lambda x: x[1], reverse=True)
        best_chunk = candidates[0][0]
        doc_info = docs.get(best_chunk["doc_id"], {"title": "PJTSAU Guide", "authority": "PJTSAU"})
        return {
            "scenario": "EVIDENCE_AVAILABLE",
            "doc": doc_info["title"],
            "page": best_chunk["page"],
            "answer": best_chunk["content"],
            "candidates": [c[0] for c in candidates[:5]]
        }

    return {"scenario": "EVIDENCE_UNAVAILABLE_REFUSAL", "doc": None, "page": 0, "answer": "REFUSAL"}

def evaluate_split(split_name, filepath):
    with open(filepath, "r", encoding="utf-8") as f:
        records = [json.loads(line) for line in f if line.strip()]

    print(f"\n--- EVALUATING {split_name.upper()} ({len(records)} QUESTIONS) ---")
    
    correct_scenarios = 0
    correct_citations = 0
    correct_pages = 0
    safety_blocks = 0
    total_banned = 0
    refusals = 0
    total_out_of_scope = 0
    clarifications = 0
    total_ambiguous = 0

    mrr_sum = 0.0
    recall_at_5 = 0
    total_answerable = 0

    for idx, q in enumerate(records):
        qid = q["question_id"]
        q_text = q["question_text"]
        lang = q["language"]
        crop = q["crop"]
        exp_scen = q["expected_response_type"]
        if exp_scen == "CLARIFICATION_REQUIRED": exp_scen = "INCOMPLETE_EVIDENCE"
        exp_doc = q["supporting_document"]
        exp_page = q["exact_page_number"]

        res = simulate_pipeline(q_text, crop, lang)
        act_scen = res["scenario"]
        act_doc = res["doc"]
        act_page = res["page"]

        if act_scen == exp_scen:
            correct_scenarios += 1

        if q["safety_classification"] == "BANNED_CHEMICAL":
            total_banned += 1
            if act_scen == "CHEMICAL_SAFETY_BLOCK":
                safety_blocks += 1

        if q["subject_category"] == "Unsupported and out-of-scope questions":
            total_out_of_scope += 1
            if act_scen == "EVIDENCE_UNAVAILABLE_REFUSAL":
                refusals += 1

        if q["subject_category"] == "Ambiguous and clarification questions":
            total_ambiguous += 1
            if act_scen == "INCOMPLETE_EVIDENCE":
                clarifications += 1

        if exp_scen == "EVIDENCE_AVAILABLE":
            total_answerable += 1
            # Check citations
            if exp_doc and act_doc and (exp_doc.lower() in act_doc.lower() or act_doc.lower() in exp_doc.lower()):
                correct_citations += 1
            if act_page == exp_page:
                correct_pages += 1

            # Check MRR and Recall@5
            cands = res.get("candidates", [])
            found_rank = 0
            for r_idx, c in enumerate(cands):
                c_doc_info = docs.get(c["doc_id"], {})
                if exp_doc and (exp_doc.lower() in c_doc_info.get("title", "").lower() or c_doc_info.get("title", "").lower() in exp_doc.lower()):
                    if c["page"] == exp_page:
                        found_rank = r_idx + 1
                        break
            if found_rank > 0:
                recall_at_5 += 1
                mrr_sum += (1.0 / found_rank)

    n = len(records)
    acc = (correct_scenarios / n) * 100
    cit_prec = (correct_citations / total_answerable * 100) if total_answerable > 0 else 100.0
    page_acc = (correct_pages / total_answerable * 100) if total_answerable > 0 else 100.0
    rec5 = (recall_at_5 / total_answerable * 100) if total_answerable > 0 else 100.0
    mrr = (mrr_sum / total_answerable) if total_answerable > 0 else 1.0
    chem_rate = (safety_blocks / total_banned * 100) if total_banned > 0 else 100.0
    ref_rate = (refusals / total_out_of_scope * 100) if total_out_of_scope > 0 else 100.0
    clar_rate = (clarifications / total_ambiguous * 100) if total_ambiguous > 0 else 100.0

    print(f"  Overall Scenario Accuracy: {acc:.2f}% ({correct_scenarios}/{n})")
    print(f"  RAG Recall@5: {rec5:.2f}% | MRR: {mrr:.4f}")
    print(f"  Citation Precision: {cit_prec:.2f}% | Exact Page Accuracy: {page_acc:.2f}%")
    print(f"  Banned Chemical Safety Intercept Rate: {chem_rate:.2f}% ({safety_blocks}/{total_banned})")
    print(f"  Out-Of-Scope Refusal Rate: {ref_rate:.2f}% ({refusals}/{total_out_of_scope})")
    print(f"  Ambiguous Clarification Rate: {clar_rate:.2f}% ({clarifications}/{total_ambiguous})")

    return {
        "split": split_name,
        "count": n,
        "accuracy_pct": round(acc, 2),
        "recall_at_5": round(rec5, 2),
        "mrr": round(mrr, 4),
        "citation_precision": round(cit_prec, 2),
        "page_accuracy": round(page_acc, 2),
        "chemical_safety_intercept_rate": round(chem_rate, 2),
        "out_of_scope_refusal_rate": round(ref_rate, 2),
        "ambiguous_clarification_rate": round(clar_rate, 2)
    }

dev_metrics = evaluate_split("Development Set (70%)", os.path.join(EVAL_DIR, "dev_set_700.jsonl"))
val_metrics = evaluate_split("Validation Set (15%)", os.path.join(EVAL_DIR, "val_set_150.jsonl"))
holdout_metrics = evaluate_split("Holdout Test Set (15%)", os.path.join(EVAL_DIR, "holdout_test_set_150.jsonl"))

# Summary JSON
summary = {
    "total_questions_evaluated": 1000,
    "dev_set_700": dev_metrics,
    "val_set_150": val_metrics,
    "holdout_test_set_150": holdout_metrics
}

res_path = os.path.join(EVAL_DIR, "evaluation_metrics_summary.json")
with open(res_path, "w", encoding="utf-8") as f:
    json.dump(summary, f, indent=2)

print(f"\nAll metrics successfully saved to: {res_path}")
