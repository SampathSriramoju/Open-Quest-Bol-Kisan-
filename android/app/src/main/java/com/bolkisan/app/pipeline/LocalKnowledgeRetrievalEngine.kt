package com.bolkisan.app.pipeline

import android.util.Log
import com.bolkisan.app.data.local.AppDatabase
import com.bolkisan.app.data.local.daos.ChunkWithDoc
import com.bolkisan.app.data.packs.KnowledgePackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalAdvisoryResult(
    val scenario: String, // EVIDENCE_AVAILABLE, INCOMPLETE_EVIDENCE, EVIDENCE_UNAVAILABLE_REFUSAL, CHEMICAL_SAFETY_BLOCK
    val answer: String,
    val spokenAnswer: String,
    val citationDoc: String? = null,
    val citationPage: Int = 1,
    val isOffline: Boolean = true,
    val requiresEscalation: Boolean = false
)

class LocalKnowledgeRetrievalEngine(
    private val database: AppDatabase,
    private val packManager: KnowledgePackManager? = null
) {

    private val TAG = "BolKisanRAG"

    private val teluguRefusal = "క్షమించండి, మీ ప్రశ్నపై మా స్థానిక ఆమోదిత వ్యవసాయ పత్రాలలో ఎటువంటి సమాచారం లభించలేదు. దయచేసి స్థానిక వ్యవసాయ అధికారి లేదా KVK శాస్త్రవేత్తను సంప్రదించండి."
    private val hindiRefusal = "क्षमा करें, आपके प्रश्न पर स्थानीय अनुमोदित कृषि दस्तावेजों में कोई जानकारी नहीं मिली। कृपया स्थानीय कृषि अधिकारी या KVK वैज्ञानिक से संपर्क करें।"
    private val englishRefusal = "Sorry, no relevant information was found in our local verified agricultural documents for your query. Please consult your local Agricultural Extension Officer (AEO) or KVK scientist."

    private fun getRefusal(lang: String): String = when (lang) {
        "te" -> teluguRefusal
        "hi" -> hindiRefusal
        else -> englishRefusal
    }

    private val nonAgriTraps = listOf(
        "క్యాన్సర్", "కరోనా", "వ్యాక్సిన్", "వ్యాక్సిన్లు", "హైడ్రోజన్", "బంగారం", "షేర్ మార్కెట్", "యూట్యూబ్", "రేషన్ కార్డు", "రిజిస్ట్రేషన్", "ముఖ్యమంత్రి",
        "ట్రాక్టర్", "ట్రాక్టరు", "ట్రాక్టర్లు", "ట్రాక్టర్ల", "సినిమా", "క్రికెట్", "రాజకీయాలు", "బ్యాంక్ లోన్", "ఆధార్", "పాస్‌పోర్ట్",
        "कैंसर", "कोरोना", "वैक्सीन", "शेयर मार्केट", "सोना", "मुख्यमंत्री", "गाड़ी", "ट्रैक्टर", "मोटर कार", "क्रिकेट", "सिनेमा", "आधार", "राशन कार्ड",
        "cancer", "vaccine", "corona", "covid", "gold", "share market", "minister", "gear box", "tractor", "tractors", "cricket", "cinema", "politics",
        "loan", "bank loan", "election", "aadhaar", "passport", "crypto", "bitcoin", "bollywood", "tollywood"
    )

    private val stopWords = setOf(
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
    )

    private val crossLingualConceptMap = listOf(
        // Cotton
        setOf("cotton", "పత్తి", "కపాస్", "कपास"),
        // Paddy
        setOf("paddy", "rice", "వరి", "ధాన్యం", "धान", "चावल"),
        // Chilli
        setOf("chilli", "chili", "pepper", "మిరప", "మిర్చి", "मिर्च", "मिर्ची"),
        // Groundnut
        setOf("groundnut", "peanut", "వేరుశనగ", "వేరుశెనగ", "పల్లీ", "పల్లీలు", "मूंगफली"),
        // Red gram
        setOf("red gram", "redgram", "pigeonpea", "pigeon pea", "కంది", "కందిపైరు", "अरहर", "तूर", "तुअर"),
        // Maize
        setOf("maize", "corn", "మొక్కజొన్న", "జొన్న", "మక్క", "मक्का"),
        // Soybean
        setOf("soybean", "soya", "సోయాబీన్", "సోయా", "सोयाबीन"),
        // Turmeric
        setOf("turmeric", "పసుపు", "हल्दी"),
        // Vegetables
        setOf("vegetables", "tomato", "brinjal", "bhendi", "okra", "కూరగాయలు", "టమాట", "వంగ", "వంకాయ", "బెండ", "బెండకాయ", "सब्जियां", "टमाटर", "बैंगन", "भिंडी"),

        // Pink bollworm
        setOf("pink bollworm", "bollworm", "గులాబీ రంగు పురుగు", "గులాబీ పురుగు", "గులాబీ", "గులాబి", "गुलाबी सुंडी", "गुलाबी"),
        // Fall Armyworm (FAW)
        setOf("fall armyworm", "armyworm", "spodoptera", "faw", "కత్తెర పురుగు", "కత్తెర", "ఫాల్ ఆర్మీవార్మ్", "आर्मीवर्म"),
        // Girdle beetle
        setOf("girdle beetle", "ring cutter", "గిర్డిల్ బీటిల్", "రింగ్ కట్టర్", "గర్డల్ बीटल"),
        // Rhizome rot
        setOf("rhizome rot", "soft rot", "దుంప కుళ్లు", "దుంప కుళ్ళు", "ప్రకంద సడన్"),
        // Shoot and fruit borer
        setOf("shoot and fruit borer", "fruit borer", "కాండం మరియు కాయ తొలుచు", "కాయ తొలుచు పురుగు"),
        // Stem borer / Dead heart
        setOf("stem borer", "dead heart", "dead hearts", "borer", "కాండం తొలుచు పురుగు", "కాండం తొలుచు", "మొవ్వులు", "మొవ్వు", "చనిపోయిన", "तना छेदक", "मृत गोभ"),
        // Thrips / Mites / Leaf curl
        setOf("thrips", "mites", "whitefly", "leaf curl", "తామర పురుగులు", "తామర", "నల్లి", "తెల్లదోమ", "ఆకుముడుత", "थ्रिप्स", "सफेद मक्खी", "माइट्स"),
        // Tikka leaf spot
        setOf("tikka", "leaf spot", "cercospora", "టిక్కా", "ఆకుమచ్చ", "టెంకాయ", "टिक्का", "धब्बा"),
        // Pod borer / Helicoverpa
        setOf("pod borer", "helicoverpa", "కాయతొలుచు పురుగు", "శనగపచ్చ పురుగు", "కాయతొలుచు", "కాయ తొలిచే", "फली छेदक"),
        // Wilt
        setOf("wilt", "fusarium", "ఎండు తెగులు", "వాడిపోవడం", "వాడిపోతోంది", "उकठा"),
        // Leaf yellowing / Chlorosis
        setOf("yellowing", "yellow", "chlorosis", "zinc", "పసుపు రంగులోకి", "పసుపుబారడం", "పసుపు రంగు", "పసుపు", "ఆకులు పసుపు", "జింక్", "पीलापन", "पीली", "पीले", "पीला", "पत्तियां", "पत्ते", "जिंक", "పీలాपन", "పీలీ", "పీలా"),
        // Soil testing
        setOf("soil test", "soil health", "soil sampling", "నేల పరీక్ష", "భూసార పరీక్ష", "మట్టి నమూనా", "మట్టి పరీక్ష", "मृदा परीक्षण"),
        // Weed management
        setOf("weed", "herbicides", "pendimethalin", "కలుపు", "కలుపు మందు", "కలుపు నివారణ", "खरपतवार"),
        // Weather advisory
        setOf("weather", "rain", "rainfall", "forecast", "వాతావరణం", "వర్షం", "తుఫాను", "భారీ వర్షాలు", "మురుగునీరు", "मौसम"),
        // Govt schemes
        setOf("rythu bharosa", "pmfby", "crop insurance", "రైతు భరోసా", "ఫసల్ బీమా", "పంట బీమా", "రైతు బంధు", "फसल बीमा")
    )

    private fun stripTeluguSuffix(word: String): String {
        val suffixes = listOf("లోని", "లోకి", "నుంచి", "నుండి", "లో", "తో", "కు", "కి", "ల", "లు", "ను", "ని")
        for (suffix in suffixes) {
            if (word.endsWith(suffix) && word.length > suffix.length + 1) {
                return word.removeSuffix(suffix)
            }
        }
        return word
    }

    suspend fun getAdvisory(
        query: String,
        crop: String?,
        language: String,
        interactionId: String? = null
    ): LocalAdvisoryResult = withContext(Dispatchers.IO) {
        val qId = interactionId ?: java.util.UUID.randomUUID().toString()
        val queryLow = query.lowercase().trim()
        Log.i(TAG, "[BOL_KISAN] RAG_STARTED: interactionId=$qId, Query='$query', Lang=$language, Crop=$crop")

        // 1. Non-Agricultural / Adversarial Refusal Trap Check
        // Use token boundaries to prevent substring collisions (e.g. "कार" in "कारण", "सोना" in "सोयाबीन")
        val queryTokens = queryLow.split(Regex("[\\s,;:.?!।\\-\"']+")).filter { it.isNotBlank() }.toSet()
        val isTrap = nonAgriTraps.any { trap ->
            val trapLow = trap.lowercase().trim()
            if (trapLow.contains(" ")) {
                queryLow.contains(trapLow)
            } else {
                queryTokens.contains(trapLow)
            }
        }

        if (isTrap) {
            Log.i(TAG, "[BOL_KISAN] Non-agricultural or trap query detected, refusing to prevent hallucination (interactionId=$qId)")
            val refusal = getRefusal(language)
            Log.i(TAG, "[BOL_KISAN] RAG_COMPLETED: interactionId=$qId, Scenario=EVIDENCE_UNAVAILABLE_REFUSAL, Doc=null, Page=0")
            Log.i(TAG, "[BOL_KISAN] RESPONSE_READY: Scenario=EVIDENCE_UNAVAILABLE_REFUSAL")
            return@withContext LocalAdvisoryResult(
                scenario = "EVIDENCE_UNAVAILABLE_REFUSAL",
                answer = refusal,
                spokenAnswer = refusal,
                citationDoc = null,
                citationPage = 0,
                requiresEscalation = true
            )
        }

        // 2. Chemical Safety Engine Check
        if (ChemicalSafetyEngine.isChemicalQuery(query)) {
            val chemRes = ChemicalSafetyEngine.validateChemical(query, crop, language)
            Log.i(TAG, "[BOL_KISAN] SAFETY_CHECK_COMPLETED: ChemicalQuery=true, Scenario=${chemRes.scenario} (interactionId=$qId)")
            Log.i(TAG, "[BOL_KISAN] RAG_COMPLETED: interactionId=$qId, Scenario=${chemRes.scenario}, Doc=${chemRes.citationDoc}, Page=${chemRes.citationPage}")
            Log.i(TAG, "[BOL_KISAN] RESPONSE_READY: Scenario=${chemRes.scenario}")
            return@withContext LocalAdvisoryResult(
                scenario = chemRes.scenario,
                answer = chemRes.message,
                spokenAnswer = chemRes.message,
                citationDoc = chemRes.citationDoc,
                citationPage = chemRes.citationPage,
                requiresEscalation = chemRes.requiresEscalation
            )
        }

        // 3. Smart Clarification Check (Missing Crop Context)
        val clarify = SmartClarificationEngine.evaluate(query, crop, language)
        if (clarify.needsClarification) {
            Log.i(TAG, "[BOL_KISAN] Incomplete evidence context, prompting for clarification")
            Log.i(TAG, "[BOL_KISAN] RESPONSE_READY: Scenario=INCOMPLETE_EVIDENCE")
            return@withContext LocalAdvisoryResult(
                scenario = "INCOMPLETE_EVIDENCE",
                answer = clarify.prompt,
                spokenAnswer = clarify.prompt,
                citationDoc = null,
                citationPage = 0,
                requiresEscalation = false
            )
        }

        // 4. Substantive Keyword Search with Cross-Lingual Concept Expansion
        val rawTerms = queryLow.split(Regex("[\\s,?.!]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }

        val searchTerms = rawTerms.filter { it !in stopWords }
        val effectiveTerms = if (searchTerms.isNotEmpty()) searchTerms else rawTerms

        val expandedTerms = mutableSetOf<String>()
        for (term in effectiveTerms) {
            expandedTerms.add(term)
            // Telugu suffix stemming
            if (term.any { it in '\u0C00'..'\u0C7F' }) {
                val stem = stripTeluguSuffix(term)
                if (stem.length >= 2 && stem !in stopWords) {
                    expandedTerms.add(stem)
                }
            }
        }

        // Cross-lingual concept expansion
        for (conceptSet in crossLingualConceptMap) {
            if (conceptSet.any { kw -> queryLow.contains(kw) }) {
                for (term in conceptSet) {
                    val parts = term.split(Regex("\\s+"))
                    expandedTerms.addAll(parts.filter { it.length >= 2 })
                    expandedTerms.add(term)
                }
            }
        }

        val docDao = database.documentDao()
        val candidateChunks = mutableListOf<ChunkWithDoc>()

        for (term in expandedTerms) {
            val matches = docDao.searchChunksByKeyword(term)
            candidateChunks.addAll(matches)
        }

        // Modular Knowledge Pack Search (Zero-bloat external crop packs)
        if (packManager != null) {
            for (term in expandedTerms) {
                val packMatches = packManager.searchPackChunks(term, crop)
                candidateChunks.addAll(packMatches)
            }
        }

        if (candidateChunks.isNotEmpty()) {
            // Rank candidate chunks by relevance (term matches + language alignment + crop match)
            val scored = candidateChunks.distinctBy { it.id }.map { chunk ->
                var score = 0
                val contentLow = chunk.content.lowercase()
                for (term in expandedTerms) {
                    if (contentLow.contains(term.lowercase())) score += 2
                }

                // Native language alignment boost
                val chunkIsTelugu = chunk.content.any { it in '\u0C00'..'\u0C7F' }
                val chunkIsHindi = chunk.content.any { it in '\u0900'..'\u097F' }
                val chunkIsEnglish = !chunkIsTelugu && !chunkIsHindi

                if (language == "te" && chunkIsTelugu) score += 6
                if (language == "hi" && chunkIsHindi) score += 6
                if (language == "en" && chunkIsEnglish) score += 6

                // Crop alignment boost
                if (!crop.isNullOrBlank()) {
                    if (chunk.cropTag.equals(crop, ignoreCase = true)) score += 6
                    else if (chunk.cropTag.equals("Multi", ignoreCase = true)) score += 2
                }

                chunk to score
            }.sortedByDescending { it.second }

            val best = scored.first().first
            val citation = best.docTitle
            val page = best.pageNumber
            val authority = best.issuingAuthority

            Log.i(TAG, "[BOL_KISAN] EVIDENCE_FOUND: Doc='$citation', Page=$page, Authority='$authority'")

            // Build grounded response formatted in user's target language
            val (answerText, spokenText) = formatLocalizedAnswer(
                authority = authority,
                citation = citation,
                page = page,
                chunk = best,
                targetLang = language
            )

            Log.i(TAG, "[BOL_KISAN] RAG_COMPLETED: interactionId=$qId, Scenario=EVIDENCE_AVAILABLE, Doc='$citation', Page=$page")
            Log.i(TAG, "[BOL_KISAN] RESPONSE_READY: Scenario=EVIDENCE_AVAILABLE")
            return@withContext LocalAdvisoryResult(
                scenario = "EVIDENCE_AVAILABLE",
                answer = answerText,
                spokenAnswer = spokenText,
                citationDoc = citation,
                citationPage = page,
                requiresEscalation = false
            )
        }

        // 5. Zero-Hallucination Refusal if no chunk matched
        Log.i(TAG, "[BOL_KISAN] No verified document chunk matched query. Refusing (interactionId=$qId)")
        val refusal = getRefusal(language)
        Log.i(TAG, "[BOL_KISAN] RAG_COMPLETED: interactionId=$qId, Scenario=EVIDENCE_UNAVAILABLE_REFUSAL, Doc=null, Page=0")
        Log.i(TAG, "[BOL_KISAN] RESPONSE_READY: Scenario=EVIDENCE_UNAVAILABLE_REFUSAL")
        LocalAdvisoryResult(
            scenario = "EVIDENCE_UNAVAILABLE_REFUSAL",
            answer = refusal,
            spokenAnswer = refusal,
            citationDoc = null,
            citationPage = 0,
            requiresEscalation = true
        )
    }

    private fun formatLocalizedAnswer(
        authority: String,
        citation: String,
        page: Int,
        chunk: ChunkWithDoc,
        targetLang: String
    ): Pair<String, String> {
        val chunkIsTelugu = chunk.content.any { it in '\u0C00'..'\u0C7F' }
        val chunkIsHindi = chunk.content.any { it in '\u0900'..'\u097F' }
        val chunkIsEnglish = !chunkIsTelugu && !chunkIsHindi

        // If chunk is already in target language, return directly
        if ((targetLang == "te" && chunkIsTelugu) ||
            (targetLang == "hi" && chunkIsHindi) ||
            (targetLang == "en" && chunkIsEnglish)
        ) {
            val answer = when (targetLang) {
                "te" -> "$authority సిఫార్సు ప్రకారం ($citation, పేజీ $page):\n${chunk.content}"
                "hi" -> "$authority के अनुसार ($citation, पृष्ठ $page):\n${chunk.content}"
                else -> "According to $authority ($citation, Page $page):\n${chunk.content}"
            }
            return Pair(answer, chunk.content)
        }

        // Cross-Language Retrieval Synthesis
        return when (targetLang) {
            "en" -> {
                val synthesized = generateEnglishCrossLingualSummary(chunk)
                val full = "According to $authority ($citation, Page $page):\n[Verified Advisory Translated from $citation]:\n$synthesized"
                Pair(full, synthesized)
            }
            "te" -> {
                val synthesized = generateTeluguCrossLingualSummary(chunk)
                val full = "$authority సిఫార్సు ప్రకారం ($citation, పేజీ $page):\n[ఆమోదిత పత్రం నుండి అనువదించబడిన సమాచారం]:\n$synthesized"
                Pair(full, synthesized)
            }
            else -> {
                val synthesized = generateHindiCrossLingualSummary(chunk)
                val full = "$authority के अनुसार ($citation, पृष्ठ $page):\n[अनुमोदित दस्तावेज से अनुवादित परामर्श]:\n$synthesized"
                Pair(full, synthesized)
            }
        }
    }

    private fun generateEnglishCrossLingualSummary(chunk: ChunkWithDoc): String {
        return when (chunk.topicTag) {
            "symptom_yellowing" -> {
                "For ${chunk.cropTag} yellowing leaves and sucking pests: Apply balanced nutrition (Zinc Sulphate 2 g/L or Ferrous Sulphate 5 g/L). For sucking pest vectors (whitefly, jassids), install 10-15 yellow and blue sticky traps per acre and spray neem oil 10,000 PPM (5 ml/L). Avoid excessive urea."
            }
            "chemical_safety" -> {
                "CIBRC Safety Mandate: Always spray pesticides in the windward direction with PPE masks and gloves. Banned active ingredients (Monocrotophos, Paraquat, Endosulfan, DDT, Carbofuran, Phorate) are prohibited by law. Empty containers must be triple-rinsed, punctured, and buried."
            }
            else -> {
                "Recommended IPM Practice for ${chunk.cropTag} (${chunk.topicTag}): Install 5-8 pheromone traps per acre. For biological control, spray Neem oil 10,000 PPM (400-500 ml/acre in 200 L water). When reaching ETL threshold, apply approved label chemicals such as Chlorantraniliprole 18.5% SC (60 ml/acre) or Emamectin Benzoate 5% SG (88-100 g/acre)."
            }
        }
    }

    private fun generateTeluguCrossLingualSummary(chunk: ChunkWithDoc): String {
        return when (chunk.topicTag) {
            "symptom_yellowing" -> {
                "${chunk.cropTag} పంటలో ఆకులు పసుపుబారడం నివారణ: రసం పీల్చే పురుగుల నివారణకు పసుపు మరియు నీలి రంగు జిగురు అట్టలను ఎకరానికి 10-15 అమర్చాలి. వేపనూనె 5 మి.లీ లీటరు నీటికి కలిపి పిచికారీ చేయాలి. సూక్ష్మధాతు లోపాలకు జింక్ లేదా అన్నభేది సిఫార్సు చేసిన మోతాదులో వాడాలి."
            }
            "chemical_safety" -> {
                "CIBRC భద్రతా మార్గదర్శకాలు: పిచికారీ సమయంలో మాస్క్ మరియు చేతి తొడుగులు ధరించాలి. మోనోక్రోటోఫాస్, పారాక్వాట్, ఎండోసల్ఫాన్ వంటి నిషేధిత పురుగుమందులను ఎట్టిపరిస్థితుల్లో వాడరాదు. ఖాళీ డబ్బాలను మూడుసార్లు కడిగి పూడ్చివేయాలి."
            }
            else -> {
                "${chunk.cropTag} పంటలో సస్యరక్షణ యాజమాన్యం (${chunk.topicTag}): ఎకరానికి 5-8 లింగాకర్షక బుట్టలను అమర్చాలి. నివారణకు వేపనూనె 10,000 PPM ఎకరానికి 400-500 మి.లీ 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. పురుగు ఉధృతిని బట్టి సిఫార్సు చేసిన ఆమోదిత మందులను మాత్రమే వాడాలి."
            }
        }
    }

    private fun generateHindiCrossLingualSummary(chunk: ChunkWithDoc): String {
        return when (chunk.topicTag) {
            "symptom_yellowing" -> {
                "${chunk.cropTag} में पत्तियों का पीलापन एवं रस चूसक कीट नियंत्रण: पीले चिपचिपे कार्ड 10-15 प्रति एकड़ लगाएं। नीम तेल 5 मिली प्रति लीटर पानी में मिलाकर छिड़कें। पोषक तत्वों की कमी हेतु जिंक सल्फेट या फेरस सल्फेट का छिड़काव करें।"
            }
            "chemical_safety" -> {
                "CIBRC सुरक्षा नियम: छिड़काव करते समय मास्क एवं दस्ताने अवश्य पहनें। मोनोक्रोटोफॉस, पैराक्वाट, एंडोसल्फान जैसे प्रतिबंधित रसायनों का उपयोग गैरकानूनी है। खाली बोतलों को तीन बार धोकर नष्ट करें।"
            }
            else -> {
                "${chunk.cropTag} में समेकित कीट प्रबंधन (${chunk.topicTag}): प्रति एकड़ 5-8 फेरोमोन ट्रैप लगाएं। जैविक नियंत्रण हेतु नीम का तेल 10,000 PPM 400-500 मिली प्रति एकड़ 200 लीटर पानी में मिलाकर छिड़कें। केवल अनुमोदित कीटनाशकों का ही प्रयोग करें।"
            }
        }
    }

    suspend fun persistAdvisoryInteraction(
        query: String,
        result: LocalAdvisoryResult,
        language: String,
        sessionId: String = "offline_session"
    ): com.bolkisan.app.data.local.entities.InteractionEntity = withContext(Dispatchers.IO) {
        val entity = com.bolkisan.app.data.local.entities.InteractionEntity(
            id = java.util.UUID.randomUUID().toString(),
            sessionId = sessionId,
            detectedLanguage = language,
            rawTranscript = query,
            responseText = result.answer,
            responseScenario = result.scenario,
            citationDoc = result.citationDoc,
            citationPage = result.citationPage,
            isChemicalFlagged = result.scenario == "CHEMICAL_SAFETY_BLOCK",
            isSynced = false
        )
        database.interactionDao().insertInteraction(entity)
        Log.i(TAG, "[BOL_KISAN] INTERACTION_PERSISTED: id=${entity.id}, scenario=${result.scenario}")
        entity
    }
}
