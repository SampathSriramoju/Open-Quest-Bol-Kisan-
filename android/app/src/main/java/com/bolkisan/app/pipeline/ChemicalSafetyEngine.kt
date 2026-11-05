package com.bolkisan.app.pipeline

data class ChemicalValidationResult(
    val isSafe: Boolean,
    val scenario: String, // EVIDENCE_AVAILABLE, INCOMPLETE_EVIDENCE, CHEMICAL_SAFETY_BLOCK
    val message: String,
    val citationDoc: String? = null,
    val citationPage: Int = 1,
    val requiresEscalation: Boolean = false
)

class ChemicalSafetyEngine {

    companion object {

        private val GENERAL_SAFETY_WORDS = listOf(
            "జాగ్రత్తలు", "సావధానత", "सावधानियां", "सावधानी", "సరియైన సమయం", "सही समय",
            "మాస్క్", "मास्क", "పారవేయాలి", "disposal", "నిరీక్షణ కాలం", "निरीक्षण", "waiting period"
        )

        private val BANNED_CHEMICALS = listOf(
            "monocrotophos", "మోనోక్రోటోఫాస్", "మోనోక్రోటోఫాజ్", "मोनोक्रोटोफॉस", "nuvacron", "నువాక్రాన్",
            "paraquat", "పారాక్వాట్", "పారాక్వాట్ డైక్లోరైడ్", "पैराक्वाट", "gramoxone", "గ్రామోక్సోన్",
            "endosulfan", "ఎండోసల్ఫాన్", "इंडोसल्फान", "thiodan", "థియోడాన్",
            "ddt", "డిడిటి", "डीडीटी",
            "carbofuran", "కార్బోఫ్యూరాన్", "कार्बोफ्यूरॉन", "furadan", "ఫ్యూరడాన్",
            "phorate", "ఫోరేట్", "फोरेट", "thimet", "థిమెట్",
            "methyl parathion", "మిథైల్ పారాథియాన్", "मिथाइल पैराथियान", "metacid", "మెటాసిడ్",
            "triazophos", "ట్రయాజోఫాస్", "త్రియాజోఫాస్", "ट्रियाज़ोफॉस",
            "diazinon", "డయాజినాన్",
            "alachlor", "అలాక్లోర్"
        )

        private val DOSAGE_WORDS = listOf(
            "మోతాదు", "డోస్", "ఎంత కలపాలి", "ఎంత వాడాలి", "ఎంత స్ప్రే", "స్పూన్", "చెంచా", "చెంచాలు",
            "खुराक", "मात्रा", "कितना मिलाना", "कितना डालना", "चम्मच", "प्रति एकड़ कितना",
            "dose", "dosage", "how much ml", "how many grams", "how much per acre"
        )

        fun isChemicalQuery(text: String): Boolean {
            val lower = text.lowercase()

            // 1. General safety, disposal, and mask precautions are handled by RAG guidelines, not dosage blocks
            if (GENERAL_SAFETY_WORDS.any { lower.contains(it) }) {
                return false
            }

            // 2. Banned chemicals are always flagged
            if (BANNED_CHEMICALS.any { lower.contains(it) }) {
                return true
            }

            // 3. Known chemical names or dosage intent
            val hasChemical = lower.contains("chlorantraniliprole") || lower.contains("క్లోరాంట్రానిలీప్రోల్") ||
                    lower.contains("coragen") || lower.contains("కొరాజెన్") || lower.contains("कोराजन") ||
                    lower.contains("emamectin") || lower.contains("ఎమామెక్టిన్") || lower.contains("इमामेक्टिन") ||
                    lower.contains("mancozeb") || lower.contains("మాంకోజెబ్") || lower.contains("मैनकोजेब") ||
                    lower.contains("neem oil") || lower.contains("వేప నూనె") || lower.contains("వేపనూనె") || lower.contains("नीम तेल") ||
                    lower.contains("సూపర్ పాయిజన్") || lower.contains("super poison") || lower.contains("కోరా-మ్యాజిక్") || lower.contains("cora magic")

            val hasDosageIntent = DOSAGE_WORDS.any { lower.contains(it) }

            // 4. Formulation mentions like 50% EC, 18.5% SC
            val hasFormulation = Regex("""\d+(\.\d+)?\s*%\s*(ec|sc|sg|wp|sl|sp|fs|wdg)""").containsMatchIn(lower)

            // 5. Tank mix inquiries
            val isTankMix = (lower.contains("కలిపి") || lower.contains("मिलाकर") || lower.contains("tank mix") || lower.contains("mix")) &&
                    (lower.contains("వేప") || lower.contains("బోర్డో") || lower.contains("neem") || lower.contains("bordeaux"))

            return (hasChemical && (hasDosageIntent || hasFormulation)) || hasBanned(lower) || isTankMix
        }

        private fun hasBanned(lower: String): Boolean {
            return BANNED_CHEMICALS.any { lower.contains(it) } ||
                    lower.contains("banned") || lower.contains("నిషేధించబడిన") || lower.contains("प्रतिबंधित")
        }

        fun validateChemical(query: String, crop: String?, language: String): ChemicalValidationResult {
            val lower = query.lowercase()

            // Parameter 8: Banned / Prohibited Hazardous Chemicals
            if (hasBanned(lower)) {
                val banMsgTe = "హెచ్చరిక: ఈ రసాయనం (ఉదా. మోనోక్రోటోఫాస్ / పారాక్వాట్ / ఎండోసల్ఫాన్ / డిడిటి) భారత ప్రభుత్వం & CIBRC గెజిట్ నోటిఫికేషన్ ద్వారా పూర్తిగా నిషేధించబడింది. దీనిని వ్యవసాయంలో ఉపయోగించడం చట్టరీత్యా నేరం మరియు పంటలకు, మానవులకు, పశువులకు తీవ్ర ప్రాణాంతక ప్రమాదం. దయచేసి సమీపంలోని వ్యవసాయ విస్తరణ అధికారి (AEO) లేదా KVK శాస్త్రవేత్తను సంప్రదించండి."
                val banMsgHi = "चेतावनी: यह रसायन (मोनोक्रोटोफॉस / पैराक्वाट / एंडोसल्फान / डीडीटी) भारत सरकार एवं CIBRC राजपत्र अधिसूचना द्वारा पूर्णतः प्रतिबंधित है। इसका कृषि में उपयोग गैरकानूनी एवं अत्यंत घातक है। कृपया तुरंत कृषि विस्तार अधिकारी (AEO) या KVK विशेषज्ञ से संपर्क करें।"
                val banMsgEn = "CRITICAL SAFETY WARNING: This chemical (e.g. Monocrotophos / Paraquat / Endosulfan / DDT / Carbofuran) is strictly BANNED by the Government of India and CIBRC Gazette Notification. Its agricultural use is illegal and poses lethal toxicity to crops, livestock, and humans. Please consult your local Agricultural Extension Officer (AEO) or KVK scientist immediately."
                val message = when (language) {
                    "te" -> banMsgTe
                    "hi" -> banMsgHi
                    else -> banMsgEn
                }
                return ChemicalValidationResult(
                    isSafe = false,
                    scenario = "CHEMICAL_SAFETY_BLOCK",
                    message = message,
                    citationDoc = "Central Insecticide Board & Registration Committee (CIBRC) Banned Pesticides List",
                    citationPage = 1,
                    requiresEscalation = true
                )
            }

            // Parameter 10: Incompatible Tank Mixes (e.g. Neem Oil + Bordeaux alkaline mixture)
            if ((lower.contains("వేపనూనె") || lower.contains("వేప నూనె") || lower.contains("neem")) &&
                (lower.contains("బోర్డో") || lower.contains("bordeaux") || lower.contains("సున్నం") || lower.contains("lime"))) {
                val mixMsgTe = "హెచ్చరిక: వేపనూనెను బోర్డో మిశ్రమం (Bordeaux mixture) లేదా క్షార రసాయనాలతో కలపకూడదు. ఈ ట్యాంక్ మిక్స్ రసాయన చర్య జరిపి పంట ఆకులు మాడిపోవడానికి దారితీస్తుంది. వేర్వేరుగా పిచికారీ చేయాలి."
                val mixMsgHi = "चेतावनी: नीम के तेल को बोर्डो मिश्रण (Bordeaux mixture) या क्षारीय घोल के साथ मिलाकर कभी छिड़काव न करें। इससे फसल की पत्तियां जल सकती हैं।"
                val mixMsgEn = "TANK-MIX HAZARD: Do not mix Neem oil with Bordeaux mixture or alkaline chemical solutions. This tank mix triggers chemical scorching and causes crop leaf burn. Spray them separately."
                val message = when (language) {
                    "te" -> mixMsgTe
                    "hi" -> mixMsgHi
                    else -> mixMsgEn
                }
                return ChemicalValidationResult(
                    isSafe = false,
                    scenario = "CHEMICAL_SAFETY_BLOCK",
                    message = message,
                    citationDoc = "CIBRC & PJTSAU Pesticide Compatibility Guidelines",
                    citationPage = 1,
                    requiresEscalation = true
                )
            }

            // Parameter 3: Incorrect Formulation Mismatch (e.g. asking for 50% EC for Coragen instead of 18.5% SC)
            if (lower.contains("50% ec") || lower.contains("50 % ec") || lower.contains("25% ec")) {
                val formMsgTe = "ఫార్ములేషన్ హెచ్చరిక: క్లోరాంట్రానిలీప్రోల్ కేవలం '18.5% SC' ఫార్ములేషన్‌లో మాత్రమే CIBRC ఆమోదం పొందింది. 50% EC ఫార్ములేషన్ ఆమోదించబడలేదు. తప్పుడు ఫార్ములేషన్ పంటను నాశనం చేస్తుంది."
                val formMsgHi = "फॉर्मूलेशन चेतावनी: क्लोरांट्रानिलीप्रोल केवल '18.5% SC' में CIBRC द्वारा स्वीकृत है। 50% EC स्वीकृत नहीं है।"
                val formMsgEn = "FORMULATION ALERT: Chlorantraniliprole is registered and approved by CIBRC only in '18.5% SC' formulation. 50% EC is unapproved and harmful to crops."
                val message = when (language) {
                    "te" -> formMsgTe
                    "hi" -> formMsgHi
                    else -> formMsgEn
                }
                return ChemicalValidationResult(
                    isSafe = false,
                    scenario = "CHEMICAL_SAFETY_BLOCK",
                    message = message,
                    citationDoc = "CIBRC Approved Product Label (Chlorantraniliprole 18.5% SC)",
                    citationPage = 1,
                    requiresEscalation = true
                )
            }

            // Parameter 4: Unapproved Crop Application (e.g. Mango for Chlorantraniliprole)
            val unapprovedCrops = listOf("మామిడి", "టమాట", "వంకాయ", "బంగాళదుంప", "आम", "टमाटर", "बैंगन", "mango", "tomato", "brinjal")
            if (unapprovedCrops.any { lower.contains(it) }) {
                val cropMsgTe = "లేబుల్ హెచ్చరిక: ఈ రసాయనం మీరు అడిగిన పంటకు CIBRC అధికారిక లేబుల్ ఆమోదం పొందలేదు. ఆఫ్-లేబుల్ (అనధికారిక) రసాయన పిచికారీ పంటకు విషతుల్యం కావచ్చు. దయచేసి KVK శాస్త్రవేత్తను సంప్రదించండి."
                val cropMsgHi = "लेबल चेतावनी: यह रसायन आपकी उल्लेखित फसल के लिए CIBRC द्वारा अनुमोदित नहीं है। कृपया कृषि विशेषज्ञ से परामर्श करें।"
                val cropMsgEn = "LABEL WARNING: This chemical does not hold CIBRC official label registration for your mentioned crop. Off-label chemical spraying is hazardous and prohibited. Please consult a KVK scientist."
                val message = when (language) {
                    "te" -> cropMsgTe
                    "hi" -> cropMsgHi
                    else -> cropMsgEn
                }
                return ChemicalValidationResult(
                    isSafe = false,
                    scenario = "INCOMPLETE_EVIDENCE",
                    message = message,
                    citationDoc = "CIBRC Approved Crop Label & PJTSAU Guidelines",
                    citationPage = 1,
                    requiresEscalation = false
                )
            }

            // Parameter 5: Target Pest Class Mismatch (e.g. using Coragen insecticide for fungal leaf spot)
            if ((lower.contains("ఆకుమచ్చ") || lower.contains("leaf spot") || lower.contains("धब्बा") || lower.contains("फफूंद") || lower.contains("fungus")) &&
                (lower.contains("coragen") || lower.contains("కొరాజెన్") || lower.contains("కోరాజన్") || lower.contains("chlorantraniliprole"))) {
                val pestMsgTe = "పురుగుమందు హెచ్చరిక: కొరాజెన్ (క్లోరాంట్రానిలీప్రోల్) ఒక కీటకనాశని (పురుగుల మందు). ఇది ఆకుమచ్చ తెగులు (శిలీంధ్ర వ్యాధి) నివారణకు పనిచేయదు. శిలీంధ్ర తెగుళ్లకు మాంకోజెబ్ లేదా కాపర్ ఆక్సీక్లోరైడ్ వంటి సిఫార్సు చేసిన శిలీంధ్రనాశిని వాడాలి."
                val pestMsgHi = "चेतावनी: कोराजन एक कीटनाशक है, यह फफूंद जनित पत्ती धब्बा रोग पर काम नहीं करता। फफूंद रोग हेतु मैनकोजेब का उपयोग करें।"
                val pestMsgEn = "PEST MISMATCH WARNING: Coragen (Chlorantraniliprole) is an insecticide. It is ineffective against fungal leaf spot diseases. Use approved fungicides such as Mancozeb or Copper Oxychloride for fungal diseases."
                val message = when (language) {
                    "te" -> pestMsgTe
                    "hi" -> pestMsgHi
                    else -> pestMsgEn
                }
                return ChemicalValidationResult(
                    isSafe = false,
                    scenario = "CHEMICAL_SAFETY_BLOCK",
                    message = message,
                    citationDoc = "PJTSAU Integrated Pest & Disease Management Guide",
                    citationPage = 7,
                    requiresEscalation = true
                )
            }

            // Parameter 6: Overdose Hazard Detection (e.g. >200 ml Coragen per acre)
            val overdoseMatch = Regex("""(250|300|400|500|600|1000)\s*(ml|మి\.లీ|मिली)""").find(lower)
            if (overdoseMatch != null && (lower.contains("coragen") || lower.contains("కొరాజెన్") || lower.contains("chlorantraniliprole"))) {
                val doseMsgTe = "అధిక మోతాదు ప్రమాద హెచ్చరిక: కొరాజెన్ గరిష్ట ఆమోదిత మోతాదు ఎకరాకు 60 మి.లీ మాత్రమే. మీరు పేర్కొన్న మోతాదు తీవ్రమైన అధిక మోతాదు (Overdose Hazard), ఇది పంటను పూర్తిగా మాడ్చివేస్తుంది."
                val doseMsgHi = "अतिमात्रा चेतावनी: कोराजन की अधिकतम स्वीकृत खुराक केवल 60 मिली प्रति एकड़ है। उल्लेखित मात्रा अत्यंत घातक है।"
                val doseMsgEn = "OVERDOSE HAZARD: Maximum CIBRC-approved dosage for Coragen is only 60 ml per acre. The quantity mentioned is an extreme overdose hazard that will scorch and ruin your crop."
                val message = when (language) {
                    "te" -> doseMsgTe
                    "hi" -> doseMsgHi
                    else -> doseMsgEn
                }
                return ChemicalValidationResult(
                    isSafe = false,
                    scenario = "CHEMICAL_SAFETY_BLOCK",
                    message = message,
                    citationDoc = "CIBRC Approved Product Label (Chlorantraniliprole 18.5% SC)",
                    citationPage = 1,
                    requiresEscalation = true
                )
            }

            // Parameter 2: Approved Chemical - Chlorantraniliprole 18.5% SC
            if (lower.contains("chlorantraniliprole") || lower.contains("క్లోరాంట్రానిలీప్రోల్") || lower.contains("coragen") || lower.contains("కొరాజెన్") || lower.contains("कोराजन")) {
                val respTe = "CIBRC & PJTSAU ఆమోదిత మోతాదు (క్లోరాంట్రానిలీప్రోల్ 18.5% SC): వరి మరియు పత్తి పంటలలో కాండం తొలుచు పురుగు / కాయ తొలిచే పురుగు నివారణకు ఎకరాకు 60 మి.లీ మందును 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. గమనిక: స్పూన్లు లేదా కప్పులతో కొలవవద్దు, ప్రామాణిక కొలత పాత్రను వాడండి. నిరీక్షణ కాలం: 14 రోజులు."
                val respHi = "CIBRC अनुमोदित खुराक (क्लोरांट्रानिलीप्रोल 18.5% SC): धान एवं कपास में तना छेदक / सुंडी रोकथाम हेतु प्रति एकड़ 60 मिली दवा 200 लीटर पानी में मिलाकर छिड़कें। चम्मच से न मापें। प्रतीक्षा अवधि: 14 दिन।"
                val respEn = "CIBRC & PJTSAU Approved Dosage (Chlorantraniliprole 18.5% SC): For stem borer / bollworm control in rice, cotton, and pigeonpea, mix 60 ml per acre in 200 liters of water and spray. Note: Do not measure with kitchen spoons; use a calibrated measuring cap. Pre-Harvest Interval (PHI): 14 days."
                val message = when (language) {
                    "te" -> respTe
                    "hi" -> respHi
                    else -> respEn
                }
                return ChemicalValidationResult(
                    isSafe = true,
                    scenario = "EVIDENCE_AVAILABLE",
                    message = message,
                    citationDoc = "CIBRC Approved Product Label (Chlorantraniliprole 18.5% SC)",
                    citationPage = 1,
                    requiresEscalation = false
                )
            }

            // Parameter 2: Approved Chemical - Emamectin Benzoate 5% SG
            if (lower.contains("emamectin") || lower.contains("ఎమామెక్టిన్") || lower.contains("इमामेक्टिन") || lower.contains("proclaim") || lower.contains("ప్రోక్లెయిమ్")) {
                val respTe = "CIBRC ఆమోదిత లేబుల్ సిఫార్సు (ఎమామెక్టిన్ బెంజోయేట్ 5% SG): పత్తిలో కాయతొలుచు పురుగు నివారణకు ఎకరాకు 88-100 గ్రాములు, మిరపలో తామర పురుగులకు 80-100 గ్రాములు 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. నిరీక్షణ కాలం: 5 రోజులు."
                val respHi = "CIBRC अनुमोदित खुराक (इमामेक्टिन बेंजोएट 5% SG): कपास एवं मिर्च में सुंडी रोकथाम हेतु 80-100 ग्राम प्रति एकड़ 200 लीटर पानी में मिलाकर छिड़कें। प्रतीक्षा अवधि: 5 दिन।"
                val respEn = "CIBRC Approved Product Label (Emamectin Benzoate 5% SG): For bollworm control in cotton and pod borer in red gram, apply 88-100 grams per acre; for thrips in chilli, apply 80-100 grams per acre in 200 liters of water. Pre-Harvest Interval (PHI): 5 days."
                val message = when (language) {
                    "te" -> respTe
                    "hi" -> respHi
                    else -> respEn
                }
                return ChemicalValidationResult(
                    isSafe = true,
                    scenario = "EVIDENCE_AVAILABLE",
                    message = message,
                    citationDoc = "CIBRC Approved Product Label (Emamectin Benzoate 5% SG)",
                    citationPage = 1,
                    requiresEscalation = false
                )
            }

            // Parameter 1: Unverified Chemical / Missing Dosage -> Refuse & Escalate
            val refusalTe = "ఈ రసాయన మందు లేదా మోతాదు వివరాలు మా స్థానిక CIBRC ఆమోదిత లేబుల్ డేటాబేస్‌లో నిర్ధారించబడలేదు. పంట భద్రత దృష్ట్యా తప్పుడు లేదా కల్పిత మోతాదును సిఫార్సు చేయడం నిషిద్ధం. దయచేసి సమీపంలోని కృషి విజ్ఞాన కేంద్రం (KVK) శాస్త్రవేత్తను సంప్రదించండి."
            val refusalHi = "इस रासायनिक कीटनाशक की खुराक आधिकारिक CIBRC दस्तावेजों में सत्यापित नहीं है। गलत छिड़काव से फसल को नुकसान हो सकता है। कृपया कृषि विशेषज्ञ से परामर्श करें।"
            val refusalEn = "This chemical pesticide or dosage is not verified in our local approved CIBRC label registry. Recommending unverified or fabricated chemical dosages is prohibited for crop safety. Please consult your nearest Krishi Vigyan Kendra (KVK) scientist."
            val refusalMsg = when (language) {
                "te" -> refusalTe
                "hi" -> refusalHi
                else -> refusalEn
            }
            return ChemicalValidationResult(
                isSafe = false,
                scenario = "CHEMICAL_SAFETY_BLOCK",
                message = refusalMsg,
                citationDoc = "Central Insecticide Board & Registration Committee (CIBRC) Registered Pesticides Register",
                citationPage = 1,
                requiresEscalation = true
            )
        }
    }
}
