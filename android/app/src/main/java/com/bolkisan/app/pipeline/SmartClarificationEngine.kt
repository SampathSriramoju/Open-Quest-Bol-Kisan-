package com.bolkisan.app.pipeline

data class ClarificationResult(
    val needsClarification: Boolean,
    val prompt: String
)

class SmartClarificationEngine {

    companion object {
        private val GENERAL_SAFETY_WORDS = listOf(
            "జాగ్రత్తలు", "సావధానత", "सावधानियां", "మాస్క్", "मास्क", "mask", "పారవేయాలి", "disposal", "నిరీక్షణ", "waiting period", "safety"
        )

        private val SYMPTOM_WORDS = listOf(
            "yellow", "yellowing", "పసుపు", "పచ్చబడ్డాయి", "పీలే", "पीले", "sukhi", "ఎండి", "पीली", "पीलापन",
            "spots", "మచ్చలు", "దాగ్", "धब्बे", "wilt", "వాడిపోవడం", "వాడిపోతోంది", "వాడి", "డ్రై", "సూఖ్", "सूख",
            "dropping", "రాలిపోతోంది", "రాలడం", "झड़ना", "rotting", "కుళ్ళిపోవడం", "కుళ్ళిపోతున్నాయి",
            "పురుగు", "కీటకం", "కీటకాలు", "దోమ", "कीट", "इल्ली", "कीड़ा", "borer", "caterpillar", "thrips", "mites", "tikka"
        )

        private val CROPS = listOf(
            "cotton", "పత్తి", "कपास",
            "paddy", "వరి", "धान", "rice",
            "chilli", "మిరప", "మిర్చి", "मिर्च", "chili",
            "groundnut", "వేరుశనగ", "వేరుశెనగ", "మూంగఫలీ", "मूंगफली", "peanut",
            "red gram", "redgram", "pigeonpea", "కంది", "अरहर", "तूर",
            "maize", "మొక్కజొన్న", "మక్క", "मक्का", "corn",
            "soybean", "సోయాబీన్", "సోయా", "सोयाबीन",
            "turmeric", "పసుపు", "हल्दी",
            "tomato", "టమాట", "टमाटर",
            "brinjal", "వంగ", "వంకాయ", "बैंगन",
            "bhendi", "బెండ", "బెండకాయ", "भिंडी", "okra",
            "vegetables", "కూరగాయలు", "सब्जियां"
        )

        private val AMBIGUOUS_PATTERNS = listOf(
            "ఏదో పురుగు", "ఏ మందు కొట్టాలి", "పూత రాలిపోతోంది", "ఎరువుల బస్తా ఎంత", "పురుగు మందు పిచికారీ చేయవచ్చా",
            "విత్తనాలు మొలకెత్తలేదు", "కీడ లగా హే", "कीड़ा लगा है क्या दवा", "पौधे पीले पड़ रहे", "दवा का छिड़काव कब", "फूल बहुत गिर रहे",
            "खाद कितनी मात्रा", "बीज अंकुरित नहीं", "पत्तियां सिकुड़ रही", "some insect is attacking", "leaves are turning yellow, what is the remedy",
            "how much fertilizer should i apply", "flowers are dropping heavily", "can i mix pesticides", "seed germination is poor",
            "spots are appearing on leaves, need spray", "plants are wilting suddenly", "how often should i irrigate my field"
        )

        fun evaluate(query: String, crop: String?, language: String): ClarificationResult {
            val lower = query.lowercase()

            if (GENERAL_SAFETY_WORDS.any { lower.contains(it) }) {
                return ClarificationResult(needsClarification = false, prompt = "")
            }

            // Remove generic pesticide terms so 'పురుగుమందు' / 'कीटनाशक' is not mistaken for a pest symptom report
            val cleanLower = lower.replace("పురుగుమందులు", "")
                .replace("పురుగుమందుల", "")
                .replace("పురుగుమందు", "")
                .replace("కీటకనాశని", "")
                .replace("कीटनाशक", "")

            val hasSymptom = SYMPTOM_WORDS.any { cleanLower.contains(it) }
            val hasCropInQuery = CROPS.any { lower.contains(it) }
            val isAmbiguous = AMBIGUOUS_PATTERNS.any { lower.contains(it) }

            // If farmer mentioned symptoms without crop in query, or asked inherently ambiguous question
            if ((hasSymptom && !hasCropInQuery) || isAmbiguous) {
                val clarifyTe = "మీరు పంటలో లక్షణాలను పేర్కొన్నారు, కానీ ఏ పంటో స్పష్టంగా లేదు. దయచేసి తెలియజేయండి:\n1. మీరు ఏ పంట (పత్తి, వరి, మిరప, వేరుశనగ లేదా కంది) సాగు చేస్తున్నారు?\n2. మీ పంట ఏ దశలో ఉంది (మొలక, పూత, లేదా కాయ దశ)?\n3. ముదిరిన ఆకులా లేక లేత చిగురుటాకులు ప్రభావితమయ్యాయా?"
                val clarifyHi = "आपने फसल में लक्षण बताए हैं, लेकिन फसल का नाम स्पष्ट नहीं है। कृपया स्पष्ट करें:\n1. आपकी कौन सी फसल (कपास, धान, मिर्च, मूंगफली या अरहर) है?\n2. फसल किस अवस्था में है (अंकुरण, फूल, या फल अवस्था)?\n3. क्या पुरानी पत्तियां प्रभावित हैं या नई कोपलें?"
                val clarifyEn = "You mentioned crop symptoms, but the crop name is unclear. Please specify:\n1. Which crop (Cotton, Paddy, Chilli, Groundnut, or Red gram) are you cultivating?\n2. What growth stage is the crop in (seedling, flowering, or pod/boll formation)?\n3. Are older leaves or young new flushes affected?"
                val prompt = when (language) {
                    "te" -> clarifyTe
                    "hi" -> clarifyHi
                    else -> clarifyEn
                }
                return ClarificationResult(
                    needsClarification = true,
                    prompt = prompt
                )
            }

            return ClarificationResult(needsClarification = false, prompt = "")
        }
    }
}
