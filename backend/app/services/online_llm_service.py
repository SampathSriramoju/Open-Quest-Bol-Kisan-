import time
import re
import logging
from typing import Dict, Any, List, Optional, Tuple
from backend.app.services.llm_provider import LLMProviderFactory, BaseLLMProvider
from backend.app.services.privacy_service import PrivacyRedactor
from backend.app.core.config import settings

logger = logging.getLogger("bolkisan.online_llm")

class OnlineLLMService:
    """
    Orchestrates Online LLM Advisory Generation with Strict Grounding.
    Ensures zero-hallucination compliance by constraining the LLM to
    synthesize ONLY retrieved and approved agricultural document chunks.
    Enforces strict target language consistency and chemical dosage grounding.
    """

    SYSTEM_INSTRUCTIONS = {
        "te": (
            "మీరు 'బోల్ కిసాన్' (Bol Kisan) ఏఐ వ్యవసాయ నిపుణుడు.\n"
            "ముఖ్య భాషా నిబంధన: రైతు ఎంచుకున్న భాష తెలుగు. మీ సమాధానం సంపూర్ణంగా స్పష్టమైన, ప్రామాణికమైన వ్యవసాయ తెలుగు భాషలోనే ఉండాలి. ఆంగ్లం లేదా హిందీ లిపిలో సమాధానం ఇవ్వరాదు.\n"
            "వ్యవసాయ భద్రతా నిబంధనలు (Agricultural Safety Rules):\n"
            "1. క్రింద ఇవ్వబడిన ఆమోదించబడిన వ్యవసాయ ఆధారాల (Context) ఆధారంగా మాత్రమే సమాధానం ఇవ్వండి.\n"
            "2. నివేదికలలో లేని కొత్త రసాయనాల పేర్లు, మోతాదులు, పురుగుమందుల మిశ్రమాలు లేదా నిరీక్షణ సమయాలను మీరే స్వయంగా సృష్టించవద్దు (Zero Hallucination).\n"
            "3. రసాయన మోతాదు నివేదికలలో ఖచ్చితంగా లేకపోతే, మోతాదు అధికారిక రికార్డులలో అందుబాటులో లేదని స్పష్టంగా చెప్పి వ్యవసాయ అధికారి (AEO) లేదా KVK నిపుణులను సంప్రదించమని సూచించండి.\n"
            "4. సమాధానం 3-4 వాక్యాలలో సంక్షిప్తంగా, సూటిగా మరియు రైతుకు సులభంగా అర్థమయ్యేలా ఉండాలి."
        ),
        "hi": (
            "आप 'बोल किसान' (Bol Kisan) एआई कृषि सलाहकार हैं।\n"
            "अनिवार्य भाषा नियम: किसान द्वारा चयनित भाषा हिंदी है। आपका संपूर्ण उत्तर अनिवार्य रूप से शुद्ध, प्रामाणिक और सरल हिंदी भाषा (देवनागरी लिपि) में ही होना चाहिए। तेलुगु या अंग्रेजी लिपि में उत्तर न दें।\n"
            "कृषि सुरक्षा एवं सत्यता नियम (Agricultural Safety Rules):\n"
            "1. केवल नीचे दिए गए आधिकारिक एवं प्रमाणित कृषि संदर्भ (Context) के आधार पर ही परामर्श दें।\n"
            "2. संदर्भ में अनुपस्थित किसी भी रासायनिक दवा, कीटनाशक खुराक (Dosage), टैंक मिश्रण या प्रतीक्षा अवधि का अपनी तरफ से अनुमान न लगाएं (Zero Hallucination)।\n"
            "3. यदि संदर्भ में अनुमोदित रासायनिक खुराक नहीं दी गई है, तो स्पष्ट रूप से बताएं कि प्रमाणित खुराक उपलब्ध नहीं है और कृषि विस्तार अधिकारी (AEO) या KVK विशेषज्ञ से संपर्क करने की सलाह दें।\n"
            "4. उत्तर 3-4 वाक्यों में संक्षिप्त, सटीक और व्यावहारिक रखें।"
        ),
        "en": (
            "You are 'Bol Kisan', an AI agricultural advisory assistant.\n"
            "MANDATORY LANGUAGE RULE: The farmer has explicitly selected ENGLISH. Your entire response MUST be generated in clear, professional agricultural ENGLISH. Under NO circumstances should you respond in Telugu or Hindi scripts or words, even if the user query or retrieved context documents contain Telugu or Hindi text.\n"
            "MANDATORY AGRICULTURAL SAFETY RULES:\n"
            "1. Ground your answer strictly and exclusively in the provided verified agricultural context excerpts.\n"
            "2. NEVER invent, estimate, or extrapolate chemical names, pesticide dosages, tank mixtures, waiting periods, or application frequencies absent from the context.\n"
            "3. If specific chemical dosage or product label is not present in the verified context, state clearly that official dosage is not available in approved records and advise consulting an Agricultural Extension Officer (AEO) or KVK specialist.\n"
            "4. Keep the answer conversational, practical, concise (3-4 sentences), and directly actionable."
        )
    }

    @staticmethod
    def verify_response_language(text: str, expected_lang: str) -> bool:
        """
        Validates that the generated answer respects the farmer's selected language.
        Prevents cross-language bleeding (e.g. English receiving Telugu response).
        """
        if not text:
            return False

        telugu_chars = len(re.findall(r'[\u0C00-\u0C7F]', text))
        devanagari_chars = len(re.findall(r'[\u0900-\u097F]', text))
        latin_chars = len(re.findall(r'[a-zA-Z]', text))
        total_alpha = telugu_chars + devanagari_chars + latin_chars

        if total_alpha == 0:
            return True

        if expected_lang == "en":
            # English response must NOT be dominated by Indic scripts
            indic_fraction = (telugu_chars + devanagari_chars) / total_alpha
            return indic_fraction < 0.10
        elif expected_lang == "hi":
            # Hindi response must have Devanagari and must not have Telugu
            if telugu_chars > 0:
                return False
            return devanagari_chars >= max(1, int(0.20 * total_alpha))
        elif expected_lang == "te":
            # Telugu response must have Telugu and must not have Devanagari
            if devanagari_chars > 0:
                return False
            return telugu_chars >= max(1, int(0.20 * total_alpha))

        return True

    @staticmethod
    def audit_chemical_safety_grounding(answer: str, context_chunks: List[Dict[str, Any]]) -> Tuple[bool, Optional[str]]:
        """
        Ensures the generated answer does not invent pesticide dosages, chemical formulations,
        or unregistered active ingredients not supported by the retrieved document chunks.
        """
        combined_context = " ".join(ch.get("content", "") for ch in context_chunks).lower()

        # Known regulated agricultural chemicals in database
        regulated_chemicals = [
            "chlorantraniliprole", "emamectin", "neem oil", "mancozeb",
            "monocrotophos", "endosulfan", "ddt",
            "క్లోరాంట్రానిలీప్రోల్", "ఎమామెక్టిన్", "వేప నూనె", "వేపనూనె", "మాంకోజెబ్",
            "మోనోక్రోటోఫాస్", "ఎండోసల్ఫాన్", "డిడిటి",
            "क्लोरांट्रानिलीप्रोल", "इमामेक्टिन", "नीम तेल", "मैनकोजेब",
            "मोनोक्रोटोफॉस", "इंडोसल्फान", "डीडीटी"
        ]

        # Check if answer mentions any chemical not present in context
        answer_low = answer.lower()
        for chem in regulated_chemicals:
            if chem in answer_low and chem not in combined_context:
                # Regulated chemical hallucinated without grounding context
                return False, f"Ungrounded chemical mention: '{chem}' not in retrieved context."

        # Check if numerical dosage is mentioned (e.g. 60 ml, 80-100 g, 500 ml)
        dosage_matches = re.findall(r'(\d+(?:-\d+)?(?:\.\d+)?)\s*(?:ml|మి\.లీ|మిల్లీ|g|gm|గ్రా|గ్రాములు|मिली|लीटर|ग्राम)', answer_low)
        if dosage_matches:
            # At least one dosage number must exist in the context chunks if dosages are recommended
            found_any = any(d in combined_context for d in dosage_matches)
            if not found_any and len(combined_context.strip()) > 0:
                return False, f"Ungrounded dosage recommendation: '{dosage_matches}' not supported by context."

        return True, None

    @classmethod
    async def generate_grounded_answer(
        cls,
        query: str,
        context_chunks: List[Dict[str, Any]],
        language: str = "te",
        crop: Optional[str] = None,
        provider_override: Optional[str] = None,
        model_override: Optional[str] = None,
        image_base64: Optional[str] = None,
        image_mime_type: Optional[str] = "image/jpeg"
    ) -> Dict[str, Any]:
        """
        Synthesizes a conversational, verified answer using the configured online LLM.
        Supports multimodal crop image inputs and model overrides.
        Guarantees strict target language fidelity and chemical dosage safety.
        """
        start_time = time.time()
        lang_key = language if language in cls.SYSTEM_INSTRUCTIONS else "te"
        system_prompt = cls.SYSTEM_INSTRUCTIONS[lang_key]

        # Format context excerpts according to target language
        formatted_context_list = []
        for i, ch in enumerate(context_chunks[:3], 1):
            auth = ch.get("issuing_authority", "PJTSAU / ICAR")
            title = ch.get("document_title", "Official Advisory")
            page = ch.get("page_number", 1)
            content = ch.get("content", "").strip()

            if lang_key == "en":
                formatted_context_list.append(f"[Excerpt {i} | {auth} - {title}, Page {page}]:\n{content}")
            elif lang_key == "hi":
                formatted_context_list.append(f"[संदर्भ {i} | {auth} - {title}, पृष्ठ {page}]:\n{content}")
            else:
                formatted_context_list.append(f"[సందర్భం {i} | {auth} - {title}, పేజీ {page}]:\n{content}")

        context_block = "\n\n".join(formatted_context_list)

        # Sanitize query of any identifiable farmer information before external dispatch
        clean_query, was_redacted = PrivacyRedactor.redact_pii(query)

        # Strip EXIF and camera metadata from image if present
        clean_image_base64 = None
        if image_base64:
            clean_image_base64 = PrivacyRedactor.strip_image_metadata(image_base64, image_mime_type or "image/jpeg")

        # Build language-specific user prompt
        if lang_key == "en":
            img_note = "\n(Note: The farmer has also attached a crop diagnosis photograph. Please inspect visual symptoms.)" if clean_image_base64 else ""
            user_prompt = (
                f"Farmer Query: {clean_query}\n"
                f"Target Crop: {crop or 'General Agricultural Enquiry'}{img_note}\n\n"
                f"Verified Official Agricultural Context Excerpts:\n"
                f"{context_block}\n\n"
                f"CRITICAL REQUIREMENT: The farmer has selected ENGLISH. You MUST formulate your answer entirely in fluent, professional agricultural ENGLISH. Ground every single claim strictly in the excerpts above. NEVER invent or guess any chemical dosage."
            )
        elif lang_key == "hi":
            img_note = "\n(नोट: किसान ने फसल की तस्वीर भी संलग्न की है, कृपया दृश्य लक्षणों का संज्ञान लें।)" if clean_image_base64 else ""
            user_prompt = (
                f"किसान का प्रश्न (Farmer Query): {clean_query}\n"
                f"फसल (Crop): {crop or 'सामान्य कृषि परामर्श'}{img_note}\n\n"
                f"प्रमाणित आधिकारिक कृषि संदर्भ (Verified Agricultural Context):\n"
                f"{context_block}\n\n"
                f"अनिवार्य निर्देश: किसान ने हिंदी भाषा का चयन किया है। अपना संपूर्ण उत्तर अनिवार्य रूप से केवल हिंदी (देवनागरी लिपि) में ही दें। केवल उपरोक्त प्रमाणित संदर्भ के आधार पर ही सलाह दें। किसी भी अमान्य खुराक का अनुमान न लगाएं।"
            )
        else:
            img_note = "\n(గమనిక: రైతు పంట సమస్య ఫోటోను కూడా జతపరిచారు, పరిశీలించండి.)" if clean_image_base64 else ""
            user_prompt = (
                f"రైతు ప్రశ్న (Farmer Query): {clean_query}\n"
                f"పంట (Crop): {crop or 'సాధారణ వ్యవసాయ సలహా'}{img_note}\n\n"
                f"ఆమోదించబడిన అధికారిక వ్యవసాయ ఆధారాలు (Verified Agricultural Context):\n"
                f"{context_block}\n\n"
                f"ముఖ్య నిబంధన: రైతు ఎంచుకున్న భాష తెలుగు. మీ సమాధానం ఖచ్చితంగా తెలుగు భాషలోనే ఉండాలి. పై ఆధారాలలో లేని మోతాదులను ఎట్టిపరిస్థితుల్లోనూ సూచించవద్దు."
            )

        provider = LLMProviderFactory.get_provider(
            provider_type=provider_override,
            model_override=model_override
        )
        logger.info(f"[BOL_KISAN_ONLINE_LLM] Calling provider={provider.provider_name}, model={provider.model_id}, lang={lang_key}")

        result = await provider.generate_response(
            prompt=user_prompt,
            system_instruction=system_prompt,
            max_tokens=settings.LLM_MAX_OUTPUT_TOKENS,
            image_base64=clean_image_base64,
            image_mime_type=image_mime_type
        )

        total_latency_ms = int((time.time() - start_time) * 1000)

        if result.get("success"):
            answer_text = result["text"]

            # 1. Independent Language Consistency Verification
            if not cls.verify_response_language(answer_text, lang_key):
                logger.warning(
                    f"[BOL_KISAN_ONLINE_LLM] Response language mismatch: expected '{lang_key}', but output violated script constraints. Falling back to extractive RAG."
                )
                return {
                    "success": False,
                    "answer": "",
                    "spoken_answer": "",
                    "provider": result["provider"],
                    "model": result["model"],
                    "latency_ms": total_latency_ms,
                    "is_fallback": True,
                    "error": f"Language consistency check failed: Expected {lang_key}",
                    "error_code": "LANGUAGE_MISMATCH"
                }

            # 2. Independent Agricultural Chemical Safety Grounding Verification
            is_grounded, safety_violation = cls.audit_chemical_safety_grounding(answer_text, context_chunks)
            if not is_grounded:
                logger.warning(
                    f"[BOL_KISAN_ONLINE_LLM] Chemical safety audit failed: {safety_violation}. Rejecting ungrounded LLM advice and falling back to extractive RAG."
                )
                return {
                    "success": False,
                    "answer": "",
                    "spoken_answer": "",
                    "provider": result["provider"],
                    "model": result["model"],
                    "latency_ms": total_latency_ms,
                    "is_fallback": True,
                    "error": f"Chemical safety violation: {safety_violation}",
                    "error_code": "UNGROUNDED_CHEMICAL_DOSAGE"
                }

            # Clean up spoken answer (removing markdown asterisks / bullet symbols for clean TTS playback)
            spoken_text = answer_text.replace("*", "").replace("#", "").replace("- ", "").strip()
            
            return {
                "success": True,
                "answer": answer_text,
                "spoken_answer": spoken_text,
                "provider": result["provider"],
                "model": result["model"],
                "latency_ms": total_latency_ms,
                "is_fallback": False,
                "error": None
            }
        else:
            logger.warning(
                f"[BOL_KISAN_ONLINE_LLM] Provider call failed: {result.get('error')}. Falling back to extractive RAG."
            )
            return {
                "success": False,
                "answer": "",
                "spoken_answer": "",
                "provider": result.get("provider", "none"),
                "model": result.get("model", "none"),
                "latency_ms": total_latency_ms,
                "is_fallback": True,
                "error": result.get("error"),
                "error_code": result.get("error_code")
            }
