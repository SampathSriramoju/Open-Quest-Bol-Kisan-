import re
import asyncio
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import or_, and_
from backend.app.models.all_models import Document, DocumentChunk, Citation
from backend.app.services.chemical_safety_service import ChemicalSafetyService
from backend.app.services.online_llm_service import OnlineLLMService

class RagEngine:
    """
    Trustworthy Agricultural RAG Engine.
    Strictly follows PS-16 zero-hallucination requirement.
    Supports both Online AI LLM generation and Offline Extractive Knowledge Retrieval.
    """

    TELUGU_REFUSAL = "క్షమించండి, ఆమోదించబడిన వ్యవసాయ నివేదికలలో ఈ సమాచారం అందుబాటులో లేదు. దయచేసి సమీపంలోని వ్యవసాయ విస్తరణ అధికారి (AEO) లేదా KVK శాస్త్రవేత్తను సంప్రదించండి."
    HINDI_REFUSAL = "क्षमा करें, अनुमोदित सरकारी कृषि दस्तावेजों में इस प्रश्न के लिए विश्वसनीय जानकारी उपलब्ध नहीं है। कृपया कृषि विस्तार अधिकारी या KVK विशेषज्ञ से संपर्क करें।"
    ENGLISH_REFUSAL = "Sorry, verified agricultural documents do not contain certified information for this query. Please consult your local Agricultural Extension Officer (AEO) or KVK specialist."

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
    def get_refusal(cls, language: str) -> str:
        if language == "te":
            return cls.TELUGU_REFUSAL
        elif language == "hi":
            return cls.HINDI_REFUSAL
        else:
            return cls.ENGLISH_REFUSAL

    @classmethod
    async def query_async(
        cls,
        db: Session,
        query: str,
        language: str = "te",
        crop: Optional[str] = None,
        region: str = "Telangana",
        mode: str = "online",
        provider: Optional[str] = None,
        model: Optional[str] = None,
        image_base64: Optional[str] = None,
        image_mime_type: Optional[str] = "image/jpeg"
    ) -> Dict[str, Any]:
        """
        Asynchronous RAG query execution supporting both Online AI LLM synthesis
        and Offline Extractive Evidence synthesis.
        """
        detected_crop = crop or cls.detect_crop(query)
        query_low = query.lower()

        # Step 1: Deterministic Chemical Safety Check (Always executed first)
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
                    "escalation_needed": chem_res.get("escalation_needed", False),
                    "mode": mode,
                    "model_provider": "chemical_safety_engine",
                    "model_name": "cibrc_regulatory_validator",
                    "is_fallback": False
                }
            else:
                # Chemical is verified and approved under CIBRC / PJTSAU
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
                    "escalation_needed": False,
                    "mode": mode,
                    "model_provider": "chemical_safety_engine",
                    "model_name": "cibrc_regulatory_validator",
                    "is_fallback": False
                }

        # Step 2: Smart Clarification Check
        has_symptoms = any(s in query_low for s in cls.SYMPTOM_PATTERNS)
        if has_symptoms and not detected_crop:
            clarify_te = "మీరు ఆకులు పసుపుబారడం లేదా తెగులు లక్షణాలను పేర్కొన్నారు. దయచేసి ఇది ఏ పంటలో (పత్తి, వరి, మిరప, లేదా వేరుశనగ) గమనించారో మరియు పంట ఏ దశలో ఉందో తెలియజేయండి."
            clarify_hi = "आपने पत्तों के पीले होने या कीट के लक्षण बताए हैं। कृपया बताएं कि यह किस फसल (कपास, धान, मिर्च, मूंगफली) में देखा गया है और फसल किस अवस्था में है?"
            clarify_en = "You described yellowing or pest symptoms. Please specify the crop (cotton, paddy, chilli, or groundnut) and growth stage so I can provide certified advice."
            clarify_msg = clarify_te if language == "te" else (clarify_hi if language == "hi" else clarify_en)
            return {
                "query": query,
                "detected_language": language,
                "scenario": "INCOMPLETE_EVIDENCE",
                "answer": clarify_msg,
                "spoken_answer": clarify_msg,
                "clarification_question": clarify_msg,
                "citations": [],
                "chemical_safety_warning": None,
                "escalation_needed": False,
                "mode": mode,
                "model_provider": "smart_clarification",
                "model_name": "symptom_clarification_rule",
                "is_fallback": False
            }

        # Step 3: Extract Substantive Query Terms
        all_terms = re.findall(r'[\w\u0C00-\u0C7F\u0900-\u097F]+', query_low)
        query_terms = [t for t in all_terms if len(t) > 2 and t not in cls.STOPWORDS]

        if not query_terms:
            refusal = cls.get_refusal(language)
            return {
                "query": query,
                "detected_language": language,
                "scenario": "EVIDENCE_UNAVAILABLE_REFUSAL",
                "answer": refusal,
                "spoken_answer": refusal,
                "clarification_question": None,
                "citations": [],
                "chemical_safety_warning": None,
                "escalation_needed": True,
                "mode": mode,
                "model_provider": "rag_refusal",
                "model_name": "zero_hallucination_gate",
                "is_fallback": False
            }

        # Step 4: Strict Retrieval from APPROVED Documents ONLY
        docs_query = db.query(Document).filter(Document.approval_status == "APPROVED")
        if detected_crop:
            docs_query = docs_query.filter(Document.crop.ilike(f"%{detected_crop}%"))

        approved_docs = docs_query.all()
        if not approved_docs:
            refusal = cls.get_refusal(language)
            return {
                "query": query,
                "detected_language": language,
                "scenario": "EVIDENCE_UNAVAILABLE_REFUSAL",
                "answer": refusal,
                "spoken_answer": refusal,
                "clarification_question": None,
                "citations": [],
                "chemical_safety_warning": None,
                "escalation_needed": True,
                "mode": mode,
                "model_provider": "rag_refusal",
                "model_name": "zero_hallucination_gate",
                "is_fallback": False
            }

        # Search matching chunks with strict threshold
        doc_ids = [d.id for d in approved_docs]
        matched_chunks = []
        chunks = db.query(DocumentChunk).filter(DocumentChunk.document_id.in_(doc_ids)).all()

        for ch in chunks:
            content_low = ch.content.lower()
            matches = [term for term in query_terms if term in content_low]
            score = len(matches)
            if score >= 2 or (len(query_terms) == 1 and score == 1 and any(c.lower() in content_low for c in cls.CROPS)):
                matched_chunks.append((ch, score))

        if not matched_chunks:
            refusal = cls.get_refusal(language)
            return {
                "query": query,
                "detected_language": language,
                "scenario": "EVIDENCE_UNAVAILABLE_REFUSAL",
                "answer": refusal,
                "spoken_answer": refusal,
                "clarification_question": None,
                "citations": [],
                "chemical_safety_warning": None,
                "escalation_needed": True,
                "mode": mode,
                "model_provider": "rag_refusal",
                "model_name": "zero_hallucination_gate",
                "is_fallback": False
            }

        # Sort by relevance score descending
        matched_chunks.sort(key=lambda x: x[1], reverse=True)
        top_chunk, top_score = matched_chunks[0]

        # Retrieve parent documents and build verified citations
        doc_cache = {}
        context_chunks_for_llm = []
        citations = []

        for ch, sc in matched_chunks[:3]:
            if ch.document_id not in doc_cache:
                doc_cache[ch.document_id] = db.query(Document).filter(Document.id == ch.document_id).first()
            p_doc = doc_cache[ch.document_id]

            authority = p_doc.issuing_authority if p_doc else "PJTSAU / ICAR"
            doc_title = p_doc.title if p_doc else "Official Agricultural Advisory"
            chunk_content = ch.cleaned_content or ch.content

            context_chunks_for_llm.append({
                "document_title": doc_title,
                "issuing_authority": authority,
                "page_number": ch.page_number,
                "content": chunk_content
            })

            if len(citations) < 2:
                citations.append({
                    "document_title": doc_title,
                    "issuing_authority": authority,
                    "page_number": ch.page_number,
                    "quote_excerpt": chunk_content[:180] + "...",
                    "relevance_score": min(0.99, 0.6 + (sc * 0.1))
                })

        parent_doc = doc_cache.get(top_chunk.document_id)
        authority_name = parent_doc.issuing_authority if parent_doc else "PJTSAU / ICAR"
        doc_title_name = parent_doc.title if parent_doc else "Official Advisory"

        # Build Extractive Grounded Fallback Answer
        clean_text = top_chunk.cleaned_content or top_chunk.content
        sentences = [s.strip() for s in clean_text.split('.') if len(s.strip()) > 10]
        selected_content = ". ".join(sentences[:3]) + "." if sentences else clean_text[:300]

        if language == "te":
            extractive_answer = (
                f"{authority_name} సిఫార్సు చేసిన మార్గదర్శకాల ప్రకారం ({doc_title_name}, పేజీ {top_chunk.page_number}):\n"
                f"{selected_content}"
            )
            extractive_spoken = f"{authority_name} పరిశోధన ప్రకారం: {selected_content}"
        elif language == "hi":
            extractive_answer = (
                f"{authority_name} के आधिकारिक दिशानिर्देशों के अनुसार ({doc_title_name}, पृष्ठ {top_chunk.page_number}):\n"
                f"{selected_content}"
            )
            extractive_spoken = f"{authority_name} के अनुसार: {selected_content}"
        else:
            extractive_answer = (
                f"According to certified {authority_name} guidelines ({doc_title_name}, page {top_chunk.page_number}):\n"
                f"{selected_content}"
            )
            extractive_spoken = f"According to {authority_name} research: {selected_content}"

        # Step 5: Execute based on Application Mode
        is_online_mode = (mode or "online").lower() == "online"

        if is_online_mode:
            # Mode A: ONLINE AI MODE with LLM Synthesis
            llm_res = await OnlineLLMService.generate_grounded_answer(
                query=query,
                context_chunks=context_chunks_for_llm,
                language=language,
                crop=detected_crop,
                provider_override=provider,
                model_override=model,
                image_base64=image_base64,
                image_mime_type=image_mime_type
            )

            if llm_res.get("success"):
                return {
                    "query": query,
                    "detected_language": language,
                    "scenario": "EVIDENCE_AVAILABLE",
                    "answer": llm_res["answer"],
                    "spoken_answer": llm_res["spoken_answer"],
                    "clarification_question": None,
                    "citations": citations,
                    "chemical_safety_warning": None,
                    "escalation_needed": False,
                    "mode": "online",
                    "model_provider": llm_res["provider"],
                    "model_name": llm_res["model"],
                    "is_fallback": False
                }
            else:
                # Graceful fallback to verified extractive RAG if LLM key/quota fails
                fallback_prefix = {
                    "te": "🌾 [గమనిక: ఆన్‌లైన్ ఏఐ సేవ అందుబాటులో లేనందున, ధృవీకరించబడిన స్థానిక నివేదికల నుండి సలహా అందించబడింది]\n",
                    "hi": "🌾 [सूचना: ऑनलाइन एआई अनुपलब्ध होने के कारण प्रमाणित स्थानीय दस्तावेजों से परामर्श दिया गया है]\n",
                    "en": "🌾 [Notice: Online AI service unavailable. Serving verified advisory directly from official records]\n"
                }.get(language, "")

                return {
                    "query": query,
                    "detected_language": language,
                    "scenario": "EVIDENCE_AVAILABLE",
                    "answer": fallback_prefix + extractive_answer,
                    "spoken_answer": extractive_spoken,
                    "clarification_question": None,
                    "citations": citations,
                    "chemical_safety_warning": None,
                    "escalation_needed": False,
                    "mode": "online",
                    "model_provider": llm_res.get("provider", "extractive_rag"),
                    "model_name": llm_res.get("model", "fallback_rag"),
                    "is_fallback": True
                }
        else:
            # Mode B: OFFLINE KNOWLEDGE MODE (Zero external LLM API calls)
            return {
                "query": query,
                "detected_language": language,
                "scenario": "EVIDENCE_AVAILABLE",
                "answer": extractive_answer,
                "spoken_answer": extractive_spoken,
                "clarification_question": None,
                "citations": citations,
                "chemical_safety_warning": None,
                "escalation_needed": False,
                "mode": "offline",
                "model_provider": "local_sqlite_fts",
                "model_name": "room_extractive_v4",
                "is_fallback": False
            }

    @classmethod
    def query(
        cls,
        db: Session,
        query: str,
        language: str = "te",
        crop: Optional[str] = None,
        region: str = "Telangana",
        mode: str = "online",
        provider: Optional[str] = None,
        model: Optional[str] = None,
        image_base64: Optional[str] = None,
        image_mime_type: Optional[str] = "image/jpeg"
    ) -> Dict[str, Any]:
        """
        Synchronous wrapper for query_async for backward compatibility.
        """
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                # If already in an event loop (e.g. some test runners), run in thread pool
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
                    future = executor.submit(
                        asyncio.run,
                        cls.query_async(db, query, language, crop, region, mode, provider, model, image_base64, image_mime_type)
                    )
                    return future.result(timeout=10.0)
            else:
                return loop.run_until_complete(
                    cls.query_async(db, query, language, crop, region, mode, provider, model, image_base64, image_mime_type)
                )
        except Exception:
            return asyncio.run(
                cls.query_async(db, query, language, crop, region, mode, provider, model, image_base64, image_mime_type)
            )
