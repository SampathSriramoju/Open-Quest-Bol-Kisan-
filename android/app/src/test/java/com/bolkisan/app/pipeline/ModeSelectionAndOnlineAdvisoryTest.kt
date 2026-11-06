package com.bolkisan.app.pipeline

import com.bolkisan.app.data.AppMode
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ModeSelectionAndOnlineAdvisoryTest {

    @Test
    fun testAppModeEnumValues() {
        val online = AppMode.ONLINE_AI
        val offline = AppMode.OFFLINE_KNOWLEDGE

        assertEquals("ONLINE_AI", online.name)
        assertEquals("OFFLINE_KNOWLEDGE", offline.name)
        assertEquals(2, AppMode.values().size)
    }

    @Test
    fun testModeSwitchingLogic() {
        var currentMode = AppMode.ONLINE_AI
        // Toggle to offline
        currentMode = if (currentMode == AppMode.ONLINE_AI) AppMode.OFFLINE_KNOWLEDGE else AppMode.ONLINE_AI
        assertEquals(AppMode.OFFLINE_KNOWLEDGE, currentMode)

        // Toggle back to online
        currentMode = if (currentMode == AppMode.ONLINE_AI) AppMode.OFFLINE_KNOWLEDGE else AppMode.ONLINE_AI
        assertEquals(AppMode.ONLINE_AI, currentMode)
    }

    @Test
    fun testOnlineAdvisoryResultParsing() {
        val rawJson = """
            {
                "query": "పత్తిలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?",
                "detected_language": "te",
                "scenario": "EVIDENCE_AVAILABLE",
                "answer": "ప్రొఫెసర్ జయశంకర్ తెలంగాణ రాష్ట్ర వ్యవసాయ విశ్వవిద్యాలయం సిఫార్సు ప్రకారం లింగాకర్షక బుట్టలు ఏర్పాటు చేయండి.",
                "spoken_answer": "లింగాకర్షక బుట్టలు ఏర్పాటు చేయండి.",
                "model_provider": "gemini",
                "model_name": "gemini-2.5-flash",
                "is_fallback": false,
                "latency_ms": 320,
                "citations": [
                    {
                        "issuing_authority": "PJTSAU",
                        "document_title": "Cotton Comprehensive Pest Advisory 2025-26",
                        "page_number": 18,
                        "quote_excerpt": "ఎకరాకు 4-5 లింగాకర్షక బుట్టలు అమర్చాలి...",
                        "relevance_score": 0.95
                    }
                ]
            }
        """.trimIndent()

        val parsed = JsonParser.parseString(rawJson).asJsonObject
        val scenario = parsed.get("scenario").asString
        val answer = parsed.get("answer").asString
        val spokenAnswer = parsed.get("spoken_answer").asString
        val modelProvider = parsed.get("model_provider").asString
        val modelName = parsed.get("model_name").asString
        val isFallback = parsed.get("is_fallback").asBoolean
        val latencyMs = parsed.get("latency_ms").asInt

        val citations = parsed.getAsJsonArray("citations")
        assertNotNull(citations)
        assertEquals(1, citations.size())
        val firstCit = citations.get(0).asJsonObject
        val citationDoc = "${firstCit.get("issuing_authority").asString}: ${firstCit.get("document_title").asString}"
        val citationPage = firstCit.get("page_number").asInt

        val result = OnlineAdvisoryResult(
            query = parsed.get("query").asString,
            detectedLanguage = parsed.get("detected_language").asString,
            scenario = scenario,
            answer = answer,
            spokenAnswer = spokenAnswer,
            citationDoc = citationDoc,
            citationPage = citationPage,
            modelProvider = modelProvider,
            modelName = modelName,
            isFallback = isFallback,
            latencyMs = latencyMs
        )

        assertEquals("EVIDENCE_AVAILABLE", result.scenario)
        assertEquals("gemini", result.modelProvider)
        assertEquals("gemini-2.5-flash", result.modelName)
        assertFalse(result.isFallback)
        assertEquals("PJTSAU: Cotton Comprehensive Pest Advisory 2025-26", result.citationDoc)
        assertEquals(18, result.citationPage)
        assertEquals(320, result.latencyMs)
    }

    @Test
    fun testFallbackResultFormatting() {
        val fallbackResult = OnlineAdvisoryResult(
            query = "వరిలో అగ్గి తెగులు",
            detectedLanguage = "te",
            scenario = "EVIDENCE_AVAILABLE",
            answer = "వరిలో అగ్గి తెగులు నివారణకు ట్రైసైక్లాజోల్ 75 డబ్ల్యూపీ లీటరు నీటికి 0.6 గ్రాములు పిచికారీ చేయాలి.",
            spokenAnswer = "ట్రైసైక్లాజోల్ 75 డబ్ల్యూపీ పిచికారీ చేయాలి.",
            citationDoc = "PJTSAU: Rice Production Guidelines 2025",
            citationPage = 24,
            modelProvider = "local_sqlite_fts",
            modelName = "room_extractive_v4",
            isFallback = true,
            latencyMs = 15
        )

        assertTrue(fallbackResult.isFallback)
        assertEquals("local_sqlite_fts", fallbackResult.modelProvider)
        assertEquals("PJTSAU: Rice Production Guidelines 2025", fallbackResult.citationDoc)
    }

    @Test
    fun testChemicalSafetyBlockScenarioIntegrity() {
        val blockedResult = OnlineAdvisoryResult(
            query = "పత్తిలో మోనోక్రోటోఫాస్ వాడకం",
            detectedLanguage = "te",
            scenario = "CHEMICAL_SAFETY_BLOCK",
            answer = "ప్రమాద హెచ్చరిక: మోనోక్రోటోఫాస్ పత్తి పంటలో వాడటం చట్టపరంగా నిషేధించబడింది.",
            spokenAnswer = "మోనోక్రోటోఫాస్ పత్తి పంటలో వాడటం నిషేధించబడింది.",
            citationDoc = null,
            citationPage = 1,
            modelProvider = "chemical_safety_engine",
            modelName = "cibrc_regulatory_validator",
            isFallback = false,
            latencyMs = 5
        )

        assertEquals("CHEMICAL_SAFETY_BLOCK", blockedResult.scenario)
        assertTrue(blockedResult.answer.contains("నిషేధించబడింది"))
    }
}
