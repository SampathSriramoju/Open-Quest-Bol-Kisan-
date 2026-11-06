import time
import logging
from typing import Dict, Any, List, Optional
from backend.app.services.llm_provider import LLMProviderFactory, BaseLLMProvider
from backend.app.services.privacy_service import PrivacyRedactor
from backend.app.core.config import settings

logger = logging.getLogger("bolkisan.online_llm")

class OnlineLLMService:
    """
    Orchestrates Online LLM Advisory Generation with Strict Grounding.
    Ensures zero-hallucination compliance by constraining the LLM to
    synthesize ONLY retrieved and approved agricultural document chunks.
    """

    SYSTEM_INSTRUCTIONS = {
        "te": (
            "మీరు 'బోల్ కిసాన్' (Bol Kisan) ఏఐ వ్యవసాయ నిపుణుడు. "
            "మీరు భారతీయ రైతులకు తెలుగులో ప్రామాణికమైన మరియు ఉపయోగకరమైన వ్యవసాయ సలహాలను అందిస్తారు.\n"
            "ముఖ్య నిబంధనలు:\n"
            "1. క్రింద ఇవ్వబడిన ఆమోదించబడిన వ్యవసాయ నివేదికల (Context) ఆధారంగా మాత్రమే సమాధానం ఇవ్వండి.\n"
            "2. నివేదికలలో లేని కొత్త రసాయనాల పేర్లు లేదా మోతాదులను మీరే స్వయంగా సృష్టించవద్దు (Zero Hallucination).\n"
            "3. సమాధానం స్పష్టంగా, సహజమైన వ్యవసాయ తెలుగు భాషలో మరియు రైతుకు సులభంగా అర్థమయ్యేలా ఉండాలి.\n"
            "4. రసాయన మందుల వాడకంలో జాగ్రత్తలు మరియు సిఫార్సు చేసిన మోతాదును మాత్రమే వివరించండి.\n"
            "5. సమాధానం 3-4 వాక్యాలలో సంక్షిప్తంగా మరియు సూటిగా ఉండాలి."
        ),
        "hi": (
            "आप 'बोल किसान' (Bol Kisan) एआई कृषि सलाहकार हैं। "
            "आप भारतीय किसानों को हिंदी में प्रमाणित और सटीक कृषि परामर्श देते हैं।\n"
            "अनिवार्य नियम:\n"
            "1. केवल नीचे दिए गए आधिकारिक कृषि संदर्भ (Context) के आधार पर ही उत्तर दें।\n"
            "2. संदर्भ में अनुपस्थित किसी भी रसायन का नाम या खुराक अपनी तरफ से न जोड़ें (Zero Hallucination)।\n"
            "3. उत्तर सरल, व्यावहारिक और स्पष्ट हिंदी भाषा में होना चाहिए।\n"
            "4. कीटनाशक या रासायनिक उपयोग में अनुशंसित मात्रा और सावधानियां बताएं।\n"
            "5. उत्तर 3-4 वाक्यों में संक्षिप्त और व्यावहारिक रखें।"
        ),
        "en": (
            "You are 'Bol Kisan', an AI agricultural advisory assistant. "
            "You provide certified, practical agricultural advice to Indian farmers.\n"
            "MANDATORY SAFETY RULES:\n"
            "1. Ground your answer strictly and exclusively in the provided verified agricultural context excerpts.\n"
            "2. NEVER invent unregistered chemical names, unverified dosages, or recommendations absent from the context.\n"
            "3. Keep the answer conversational, practical, concise (3-4 sentences), and directly actionable.\n"
            "4. State dosage and safety precautions clearly as documented in the context."
        )
    }

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
        """
        start_time = time.time()
        lang_key = language if language in cls.SYSTEM_INSTRUCTIONS else "te"
        system_prompt = cls.SYSTEM_INSTRUCTIONS[lang_key]

        # Format context excerpts (limiting to top 3 chunks to maintain low latency and free quota safety)
        formatted_context_list = []
        for i, ch in enumerate(context_chunks[:3], 1):
            auth = ch.get("issuing_authority", "PJTSAU / ICAR")
            title = ch.get("document_title", "Official Advisory")
            page = ch.get("page_number", 1)
            content = ch.get("content", "").strip()
            formatted_context_list.append(f"[సందర్భం {i} | {auth} - {title}, పేజీ {page}]:\n{content}")

        context_block = "\n\n".join(formatted_context_list)

        image_context_note = "\n(గమనిక: రైతు పంట సమస్య ఫోటోను కూడా జతపరిచారు, పరిశీలించండి.)" if image_base64 else ""

        # Sanitize query of any identifiable farmer information before external dispatch
        clean_query, was_redacted = PrivacyRedactor.redact_pii(query)

        user_prompt = (
            f"రైతు ప్రశ్న (Farmer Query): {clean_query}\n"
            f"పంట (Crop): {crop or 'గమనించిన పంట'}{image_context_note}\n\n"
            f"ఆమోదించబడిన వ్యవసాయ ఆధారాలు (Verified Agricultural Context):\n"
            f"{context_block}\n\n"
            f"దయచేసి పై ఆధారాల ఆధారంగా మాత్రమే రైతుకు స్పష్టమైన సమాధానాన్ని అందించండి."
        )

        provider = LLMProviderFactory.get_provider(
            provider_type=provider_override,
            model_override=model_override
        )
        logger.info(f"[BOL_KISAN_ONLINE_LLM] Calling provider={provider.provider_name}, model={provider.model_id}")

        result = await provider.generate_response(
            prompt=user_prompt,
            system_instruction=system_prompt,
            max_tokens=settings.LLM_MAX_OUTPUT_TOKENS,
            image_base64=image_base64,
            image_mime_type=image_mime_type
        )

        total_latency_ms = int((time.time() - start_time) * 1000)

        if result.get("success"):
            answer_text = result["text"]
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
