from typing import Optional, Dict, Any

class SmartClarificationEngine:
    """
    Identifies missing agricultural context (crop, crop stage, affected leaf location, onset)
    and prompts the farmer for precise clarification before retrieval.
    Prevents premature diagnosis and unsafe chemical prescriptions.
    """

    SYMPTOM_INDICATORS = {
        "yellowing": ["yellow", "yellowing", "పసుపు", "పచ్చబడ్డాయి", "పీలే", "पीले", "sukhi", "ఎండి", "पीली", "पीलापन"],
        "spots": ["spots", "మచ్చలు", "దాగ్", "धब्बे", "మచ్చ"],
        "wilting": ["wilt", "wilting", "వాడిపోవడం", "వాడిపోతోంది", "వాడి", "డ్రై", "సూఖ్", "सूख", "ముడుచుకు"],
        "dropping": ["రాలిపోతోంది", "రాలడం", "dropping", "drop", "झड़ना", "गिरना"],
        "rotting": ["కుళ్ళిపోవడం", "కుళ్ళిపోతున్నాయి", "కుళ్ళు", "सड़न", "गलना", "rot"],
        "pests": ["insect", "pest", "worm", "పురుగు", "దోమ", "కీటకం", "కీటకాలు", "कीट", "इल्ली", "कीड़ा"]
    }

    CROPS = ["cotton", "పత్తి", "కపాస్", "paddy", "వరి", "ధాన్యం", "chilli", "మిరప", "మిర్చి", "groundnut", "వేరుశనగ", "మూంగఫలీ", "red gram", "కంది", "maize", "మొక్కజొన్న", "మక్క", "soybean", "సోయాబీన్", "turmeric", "పసుపు", "tomato", "టమాట", "brinjal", "వంగ", "bhendi", "బెండ", "vegetables", "కూరగాయలు"]

    @classmethod
    def evaluate_query(cls, text: str, language: str = "te", crop: Optional[str] = None) -> Optional[Dict[str, Any]]:
        text_low = text.lower()

        # General pesticide safety / disposal / procedural questions do not need symptom clarification
        safety_keywords = [
            "జాగ్రత్తలు", "సావధానత", "सावधानियां", "मास्क", "మాస్క్", "పారవేయాలి", "disposal", "నిరీక్షణ", "waiting period"
        ]
        if any(sk in text_low for sk in safety_keywords):
            return None

        # Clean generic pesticide terms so 'పురుగుమందు' / 'कीटनाशक' is not mistaken for a pest symptom report
        clean_text = (
            text_low.replace("పురుగుమందులు", "")
                    .replace("పురుగుమందుల", "")
                    .replace("పురుగుమందు", "")
                    .replace("కీటకనాశని", "")
                    .replace("कीटनाशक", "")
        )

        has_symptom = any(any(s in clean_text for s in words) for words in cls.SYMPTOM_INDICATORS.values())
        has_crop = bool(crop) or any(c in text_low for c in cls.CROPS)

        # If symptom is mentioned but crop is missing -> Scenario B Clarification
        if has_symptom and not has_crop:
            clarify_te = (
                "మీరు పంటలో లక్షణాలను పేర్కొన్నారు, కానీ ఏ పంటో స్పష్టంగా లేదు. "
                "దయచేసి తెలియజేయండి:\n"
                "1. మీరు ఏ పంట (పత్తి, వరి, మిరప, లేదా వేరుశనగ) సాగు చేస్తున్నారు?\n"
                "2. మీ పంట ఏ దశలో ఉంది (మొలక, పూత, లేదా కాయ దశ)?\n"
                "3. ముదిరిన ఆకులా లేక లేత చిగురుటాకులు ప్రభావితమయ్యాయా?"
            )
            clarify_hi = (
                "आपने फसल में लक्षण बताए हैं, लेकिन फसल का नाम स्पष्ट नहीं है। "
                "कृपया स्पष्ट करें:\n"
                "1. आपकी कौन सी फसल (कपास, धान, मिर्च, या मूंगफली) है?\n"
                "2. फसल किस अवस्था में है (अंकुरण, फूल, या फल अवस्था)?\n"
                "3. क्या पुरानी पत्तियां प्रभावित हैं या नई कोपलें?"
            )

            return {
                "needs_clarification": True,
                "missing_fields": ["crop", "growth_stage", "affected_part"],
                "clarification_prompt": clarify_te if language == "te" else clarify_hi
            }

        return None
