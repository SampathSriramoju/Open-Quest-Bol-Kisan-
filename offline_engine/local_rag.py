import re
import uuid
from typing import Dict, Any, Optional
from offline_engine.local_db import LocalOfflineDatabase
from offline_engine.chemical_safety_validator import OfflineChemicalSafetyValidator
from offline_engine.clarification_engine import SmartClarificationEngine
from offline_engine.language_detector import LanguageDetector

class LocalOfflineRagEngine:
    """
    On-device Offline RAG Engine using SQLite FTS5.
    Zero network dependencies, Zero hallucination.
    """

    TELUGU_REFUSAL = "క్షమించండి, మీ ప్రశ్నపై మా స్థానిక ఆమోదిత వ్యవసాయ పత్రాలలో ఎటువంటి సమాచారం లభించలేదు. దయచేసి స్థానిక వ్యవసాయ అధికారి లేదా KVK శాస్త్రవేత్తను సంప్రదించండి."
    HINDI_REFUSAL = "क्षमा करें, आपके प्रश्न पर स्थानीय अनुमोदित कृषि दस्तावेजों में कोई जानकारी नहीं मिली। कृपया स्थानीय कृषि अधिकारी या KVK वैज्ञानिक से संपर्क करें।"

    STOPWORDS = {
        "ఎలా", "చేయాలి", "ఉంది", "వలన", "మరియు", "దీని", "ఏమిటి", "ఎంత", "ఎప్పుడు", "ఎక్కడ", "కావాలి", "వాడాలి",
        "ఎందుకు", "లేదా", "చేశారు", "చేస్తే", "వరకు", "నన్ను", "నా", "మీ", "ఆ", "ఈ", "ఒక", "గురించి",
        "कैसे", "करें", "क्या", "होता", "है", "हैं", "का", "की", "के", "में", "से", "पर", "लिए", "और", "या", "बताएं", "चाहिए",
        "how", "what", "why", "when", "where", "can", "should", "the", "and", "or", "in", "on", "to", "for"
    }

    def __init__(self, db: Optional[LocalOfflineDatabase] = None):
        self.db = db or LocalOfflineDatabase()

    NON_AGRI_TRAPS = [
        "క్యాన్సర్", "కరోనా", "వ్యాక్సిన్", "వ్యాక్సిన్లు", "హైడ్రోజన్ బాంబు", "బంగారం", "షేర్ మార్కెట్", "యూట్యూబ్", "రేషన్ కార్డు", "రిజిస్ట్రేషన్", "ముఖ్యమంత్రి",
        "कैंसर", "कोरोना", "वैक्सीन", "शेयर मार्केट", "सोना", "मुख्यमंत्री", "गाड़ी",
        "cancer", "vaccine", "corona", "covid", "gold", "share market", "borewell", "minister", "gear box", "tractor gear"
    ]

    def process_query(self, query: str, user_language: Optional[str] = None) -> Dict[str, Any]:
        inter_id = f"inter_{uuid.uuid4().hex[:10]}"
        
        # 1. Detect Language if not explicitly supplied
        detected_lang, conf = LanguageDetector.detect_language(
            query, 
            default_lang=user_language or self.db.get_preferred_language()
        )

        query_low = query.lower()

        # Check Non-Agricultural / Medical / Trap Questions
        if any(trap in query_low for trap in self.NON_AGRI_TRAPS):
            refusal = self.TELUGU_REFUSAL if detected_lang == "te" else self.HINDI_REFUSAL
            self.db.queue_offline_interaction(inter_id, query, refusal, "EVIDENCE_UNAVAILABLE_REFUSAL", "N/A", 0, detected_lang)
            return {
                "interaction_id": inter_id,
                "detected_language": detected_lang,
                "confidence": conf,
                "scenario": "EVIDENCE_UNAVAILABLE_REFUSAL",
                "answer": refusal,
                "citation_document": None,
                "citation_page": None,
                "is_offline": True,
                "escalation_needed": True
            }

        # 2. Chemical Safety Inspection
        if OfflineChemicalSafetyValidator.is_chemical_query(query):
            chem_res = OfflineChemicalSafetyValidator.validate(query, language=detected_lang)
            scenario = chem_res["scenario"]
            response_text = chem_res["response"]
            doc_title = chem_res.get("citation_doc", "CIBRC Label")
            page_num = chem_res.get("citation_page", 1)

            self.db.queue_offline_interaction(
                inter_id=inter_id,
                transcript=query,
                response=response_text,
                scenario=scenario,
                doc=doc_title,
                page=page_num,
                lang=detected_lang
            )

            return {
                "interaction_id": inter_id,
                "detected_language": detected_lang,
                "confidence": conf,
                "scenario": scenario,
                "answer": response_text,
                "citation_document": doc_title,
                "citation_page": page_num,
                "is_offline": True,
                "escalation_needed": chem_res.get("escalate", False)
            }

        # 3. Smart Clarification Inspection
        clarify_eval = SmartClarificationEngine.evaluate_query(query, language=detected_lang)
        if clarify_eval and clarify_eval.get("needs_clarification"):
            scenario = "INCOMPLETE_EVIDENCE"
            response_text = clarify_eval["clarification_prompt"]

            self.db.queue_offline_interaction(
                inter_id=inter_id,
                transcript=query,
                response=response_text,
                scenario=scenario,
                doc="N/A",
                page=0,
                lang=detected_lang
            )

            return {
                "interaction_id": inter_id,
                "detected_language": detected_lang,
                "confidence": conf,
                "scenario": scenario,
                "answer": response_text,
                "citation_document": None,
                "citation_page": None,
                "is_offline": True,
                "escalation_needed": False
            }

        # 4. Extract Substantive Query Terms
        all_terms = re.findall(r'[\w\u0C00-\u0C7F\u0900-\u097F]+', query_low)
        query_terms = [t for t in all_terms if len(t) > 2 and t not in self.STOPWORDS]

        if not query_terms:
            refusal = self.TELUGU_REFUSAL if detected_lang == "te" else self.HINDI_REFUSAL
            self.db.queue_offline_interaction(inter_id, query, refusal, "EVIDENCE_UNAVAILABLE_REFUSAL", "N/A", 0, detected_lang)
            return {
                "interaction_id": inter_id,
                "detected_language": detected_lang,
                "confidence": conf,
                "scenario": "EVIDENCE_UNAVAILABLE_REFUSAL",
                "answer": refusal,
                "citation_document": None,
                "citation_page": None,
                "is_offline": True,
                "escalation_needed": True
            }

        # 5. Execute SQLite FTS5 Lexical Query
        conn = self.db.get_connection()
        cur = conn.cursor()

        # Detect Crop Intent
        crop_scores = {
            "Paddy": any(w in query_low for w in ["వరి", "ధాన్యం", "धान", "paddy", "rice"]),
            "Cotton": any(w in query_low for w in ["పత్తి", "कपास", "cotton"]),
            "Chilli": any(w in query_low for w in ["మిరప", "మిర్చి", "मिर्च", "chilli"])
        }

        # Build FTS5 MATCH query with OR syntax for robust matching
        fts_query = " OR ".join(f'"{t}"' for t in query_terms)
        cur.execute("""
            SELECT f.chunk_id, f.document_id, f.page_number, f.crop, f.content,
                   d.title as doc_title, d.issuing_authority, d.language
            FROM chunks_fts f
            JOIN local_documents d ON f.document_id = d.id
            WHERE chunks_fts MATCH ?
            ORDER BY rank
            LIMIT 10
        """, (fts_query,))

        rows = cur.fetchall()
        conn.close()

        # Score & validate matches against substantive terms and crop alignment
        valid_candidates = []
        for r in rows:
            content_low = r["content"].lower()
            matches = [t for t in query_terms if t in content_low]
            # Require at least 2 substantive matches to prevent false positives
            if len(matches) >= 2 or (len(query_terms) == 1 and len(matches) == 1):
                score = len(matches) * 2
                # Boost for crop match
                r_crop = r["crop"]
                if crop_scores.get(r_crop):
                    score += 10
                # Boost for language match
                if r["language"] == detected_lang:
                    score += 5
                valid_candidates.append((r, score))

        if not valid_candidates:
            refusal = self.TELUGU_REFUSAL if detected_lang == "te" else self.HINDI_REFUSAL
            self.db.queue_offline_interaction(inter_id, query, refusal, "EVIDENCE_UNAVAILABLE_REFUSAL", "N/A", 0, detected_lang)
            return {
                "interaction_id": inter_id,
                "detected_language": detected_lang,
                "confidence": conf,
                "scenario": "EVIDENCE_UNAVAILABLE_REFUSAL",
                "answer": refusal,
                "citation_document": None,
                "citation_page": None,
                "is_offline": True,
                "escalation_needed": True
            }

        # Pick top matched chunk
        valid_candidates.sort(key=lambda x: x[1], reverse=True)
        best_row, match_count = valid_candidates[0]

        # Extract authoritative grounded response
        raw_content = best_row["content"]
        sentences = [s.strip() for s in raw_content.split('.') if len(s.strip()) > 8]
        selected_text = ". ".join(sentences[:3]) + "." if sentences else raw_content[:280]

        doc_title = best_row["doc_title"]
        page_num = best_row["page_number"]
        authority = best_row["issuing_authority"]

        if detected_lang == "te":
            answer = f"{authority} సిఫార్సు ప్రకారం ({doc_title}, పేజీ {page_num}):\n{selected_text}"
        else:
            answer = f"{authority} के अनुसार ({doc_title}, पृष्ठ {page_num}):\n{selected_text}"

        self.db.queue_offline_interaction(inter_id, query, answer, "EVIDENCE_AVAILABLE", doc_title, page_num, detected_lang)

        return {
            "interaction_id": inter_id,
            "detected_language": detected_lang,
            "confidence": conf,
            "scenario": "EVIDENCE_AVAILABLE",
            "answer": answer,
            "citation_document": doc_title,
            "citation_page": page_num,
            "is_offline": True,
            "escalation_needed": False
        }
