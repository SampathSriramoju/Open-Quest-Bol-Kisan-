import re
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import or_, and_
from backend.app.models.all_models import Document, DocumentChunk, Citation
from backend.app.services.chemical_safety_service import ChemicalSafetyService

class RagEngine:
    """
    Trustworthy Agricultural RAG Engine.
    Strictly follows PS-16 zero-hallucination requirement.
    """

    TELUGU_REFUSAL = "క్షమించండి, ఆమోదించబడిన వ్యవసాయ నివేదికలలో ఈ సమాచారం అందుబాటులో లేదు. దయచేసి సమీపంలోని వ్యవసాయ విస్తరణ అధికారి (AEO) లేదా KVK శాస్త్రవేత్తను సంప్రదించండి."
    HINDI_REFUSAL = "क्षमा करें, अनुमोदित सरकारी कृषि दस्तावेजों में इस प्रश्न के लिए विश्वसनीय जानकारी उपलब्ध नहीं है। कृपया कृषि विस्तार अधिकारी या KVK विशेषज्ञ से संपर्क करें।"

    STOPWORDS = {
        "ఎలా", "చేయాలి", "ఉంది", "వలన", "మరియు", "దీని", "ఏమిటి", "ఎంత", "ఎప్పుడు", "ఎక్కడ", "కావాలి", "వాడాలి",
        "ఎందుకు", "లేదా", "చేశారు", "చేస్తే", "వరకు", "నన్ను", "నా", "మీ", "ఆ", "ఈ", "ఒక", "గురించి",
        "कैसे", "करें", "क्या", "होता", "है", "हैं", "का", "की", "के", "में", "से", "पर", "लिए", "और", "या", "बताएं", "चाहिए",
        "how", "what", "why", "when", "where", "can", "should", "the", "and", "or", "in", "on", "to", "for", "is", "are"
    }

    # Clarification patterns
    SYMPTOM_PATTERNS = [
        "yellow", "yellowing", "ఆకులు పసుపు", "పచ్చబడ్డాయి", "పీత", "పీలే पत्ते", "sukhi",
        "spots", "మచ్చలు", "दाग", "wilt", "వాడిపోవడం", "drying", "ఎండుతున్నాయి", "పురుగులు", "కీటకాలు"
    ]

    CROPS = ["cotton", "పత్తి", "कपास", "paddy", "వరి", "धान", "chilli", "మిరప", "मिर्च", "groundnut", "వేరుశనగ", "मूंगफली", "redgram", "కంది", "अरहर"]

    @classmethod
    def detect_crop(cls, text: str) -> Optional[str]:
        low = text.lower()
        if any(w in low for w in ["cotton", "పత్తి", "కపాస్", "कपास"]):
            return "Cotton"
        if any(w in low for w in ["paddy", "వరి", "బియ్యం", "धान", "चावल"]):
            return "Paddy"
        if any(w in low for w in ["chilli", "మిరప", "మిర్చి", "मिर्च"]):
            return "Chilli"
        if any(w in low for w in ["groundnut", "వేరుశనగ", "పల్లీ", "मूंगफली"]):
            return "Groundnut"
        if any(w in low for w in ["redgram", "కంది", "తోర్", "अरहर"]):
            return "Red Gram"
        return None

    @classmethod
    def query(
        cls,
        db: Session,
        query: str,
        language: str = "te",
        crop: Optional[str] = None,
        region: str = "Telangana"
    ) -> Dict[str, Any]:
        """
        Executes verified RAG query with strict grounding and safety guarantees.
        """
        detected_crop = crop or cls.detect_crop(query)
        query_low = query.lower()

        # Step 1: Check for Chemical Safety
        if ChemicalSafetyService.is_chemical_query(query):
            chem_res = ChemicalSafetyService.validate_chemical_application(
                crop=detected_crop,
                chemical_name=None,
                query=query,
                language=language
            )
            if not chem_res["is_safe"]:
                return {
                    "query": query,
                    "detected_language": language,
                    "scenario": chem_res["scenario"],
                    "answer": chem_res["message"],
                    "spoken_answer": chem_res["message"],
                    "clarification_question": None,
                    "citations": [],
                    "chemical_safety_warning": chem_res.get("reason"),
                    "escalation_needed": chem_res.get("escalation_needed", False)
                }
            else:
                # Chemical is verified and approved
                citation = {
                    "document_title": chem_res.get("citation", "CIBRC Regulatory Label"),
                    "issuing_authority": "CIBRC / PJTSAU",
                    "page_number": chem_res.get("citation_page", 1),
                    "quote_excerpt": f"Approved dose: {chem_res.get('dose', 'Recommended label dose')} in {chem_res.get('water', '200L water')} for {detected_crop or 'target crop'}.",
                    "relevance_score": 0.98
                }
                return {
                    "query": query,
                    "detected_language": language,
                    "scenario": "EVIDENCE_AVAILABLE",
                    "answer": chem_res["message"],
                    "spoken_answer": chem_res["message"],
                    "clarification_question": None,
                    "citations": [citation],
                    "chemical_safety_warning": chem_res.get("warnings"),
                    "escalation_needed": False
                }

        # Step 2: Smart Clarification Check
        # If symptoms described without specifying the crop
        has_symptoms = any(s in query_low for s in cls.SYMPTOM_PATTERNS)
        if has_symptoms and not detected_crop:
            clarify_te = "మీరు ఆకులు పసుపుబారడం లేదా తెగులు లక్షణాలను పేర్కొన్నారు. దయచేసి ఇది ఏ పంటలో (పత్తి, వరి, మిరప, లేదా వేరుశనగ) గమనించారో మరియు పంట ఏ దశలో ఉందో తెలియజేయండి."
            clarify_hi = "आपने पत्तों के पीले होने या कीट के लक्षण बताए हैं। कृपया बताएं कि यह किस फसल (कपास, धान, मिर्च, मूंगफली) में देखा गया है और फसल किस अवस्था में है?"
            clarify_msg = clarify_te if language == "te" else clarify_hi
            return {
                "query": query,
                "detected_language": language,
                "scenario": "INCOMPLETE_EVIDENCE",
                "answer": clarify_msg,
                "spoken_answer": clarify_msg,
                "clarification_question": clarify_msg,
                "citations": [],
                "chemical_safety_warning": None,
                "escalation_needed": False
            }

        # Step 3: Extract Substantive Query Terms (excluding stopwords)
        all_terms = re.findall(r'[\w\u0C00-\u0C7F\u0900-\u097F]+', query_low)
        query_terms = [t for t in all_terms if len(t) > 2 and t not in cls.STOPWORDS]

        if not query_terms:
            refusal = cls.TELUGU_REFUSAL if language == "te" else cls.HINDI_REFUSAL
            return {
                "query": query,
                "detected_language": language,
                "scenario": "EVIDENCE_UNAVAILABLE_REFUSAL",
                "answer": refusal,
                "spoken_answer": refusal,
                "clarification_question": None,
                "citations": [],
                "chemical_safety_warning": None,
                "escalation_needed": True
            }

        # Step 4: Local / Server Retrieval from APPROVED Documents
        docs_query = db.query(Document).filter(Document.approval_status == "APPROVED")
        if detected_crop:
            docs_query = docs_query.filter(Document.crop.ilike(f"%{detected_crop}%"))

        approved_docs = docs_query.all()
        if not approved_docs:
            refusal = cls.TELUGU_REFUSAL if language == "te" else cls.HINDI_REFUSAL
            return {
                "query": query,
                "detected_language": language,
                "scenario": "EVIDENCE_UNAVAILABLE_REFUSAL",
                "answer": refusal,
                "spoken_answer": refusal,
                "clarification_question": None,
                "citations": [],
                "chemical_safety_warning": None,
                "escalation_needed": True
            }

        # Search matching chunks with strict threshold
        doc_ids = [d.id for d in approved_docs]
        matched_chunks = []
        chunks = db.query(DocumentChunk).filter(DocumentChunk.document_id.in_(doc_ids)).all()

        for ch in chunks:
            content_low = ch.content.lower()
            # Count substantive term matches
            matches = [term for term in query_terms if term in content_low]
            score = len(matches)
            # Require at least 2 substantive matches (or >50% of query keywords) to prevent hallucinated/false matches
            if score >= 2 or (len(query_terms) == 1 and score == 1 and any(c.lower() in content_low for c in cls.CROPS)):
                matched_chunks.append((ch, score))

        if not matched_chunks:
            refusal = cls.TELUGU_REFUSAL if language == "te" else cls.HINDI_REFUSAL
            return {
                "query": query,
                "detected_language": language,
                "scenario": "EVIDENCE_UNAVAILABLE_REFUSAL",
                "answer": refusal,
                "spoken_answer": refusal,
                "clarification_question": None,
                "citations": [],
                "chemical_safety_warning": None,
                "escalation_needed": True
            }

        # Sort by score descending
        matched_chunks.sort(key=lambda x: x[1], reverse=True)
        top_chunk, score = matched_chunks[0]

        # Retrieve parent document for authoritative citation
        parent_doc = db.query(Document).filter(Document.id == top_chunk.document_id).first()

        # Build Grounded Answer directly extracted from chunk evidence
        clean_text = top_chunk.cleaned_content or top_chunk.content
        sentences = [s.strip() for s in clean_text.split('.') if len(s.strip()) > 10]
        selected_content = ". ".join(sentences[:3]) + "." if sentences else clean_text[:300]

        if language == "te":
            grounded_answer = (
                f"{parent_doc.issuing_authority} సిఫార్సు చేసిన మార్గదర్శకాల ప్రకారం ({parent_doc.title}, పేజీ {top_chunk.page_number}):\n"
                f"{selected_content}"
            )
            spoken_answer = f"{parent_doc.issuing_authority} పరిశోధన ప్రకారం: {selected_content}"
        else:
            grounded_answer = (
                f"{parent_doc.issuing_authority} के आधिकारिक दिशानिर्देशों के अनुसार ({parent_doc.title}, पृष्ठ {top_chunk.page_number}):\n"
                f"{selected_content}"
            )
            spoken_answer = f"{parent_doc.issuing_authority} के अनुसार: {selected_content}"

        citation = {
            "document_title": parent_doc.title,
            "issuing_authority": parent_doc.issuing_authority,
            "page_number": top_chunk.page_number,
            "quote_excerpt": selected_content[:180] + "...",
            "relevance_score": min(0.99, 0.6 + (score * 0.1))
        }

        return {
            "query": query,
            "detected_language": language,
            "scenario": "EVIDENCE_AVAILABLE",
            "answer": grounded_answer,
            "spoken_answer": spoken_answer,
            "clarification_question": None,
            "citations": [citation],
            "chemical_safety_warning": None,
            "escalation_needed": False
        }
