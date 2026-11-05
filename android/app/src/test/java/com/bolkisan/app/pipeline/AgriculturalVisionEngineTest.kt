package com.bolkisan.app.pipeline

import android.content.Context
import android.graphics.Bitmap
import com.bolkisan.app.data.local.AppDatabase
import com.bolkisan.app.data.local.daos.*
import com.bolkisan.app.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AgriculturalVisionEngineTest {

    private lateinit var fakeDb: FakeAppDatabase
    private lateinit var retrievalEngine: LocalKnowledgeRetrievalEngine

    class FakeDocumentDao : DocumentDao {
        private val docs = mutableMapOf<String, DocumentEntity>()
        private val chunks = mutableListOf<DocumentChunkEntity>()

        override suspend fun searchChunksByKeyword(keyword: String): List<ChunkWithDoc> {
            val kw = keyword.lowercase()
            return chunks.filter { it.content.lowercase().contains(kw) }.map { c ->
                val d = docs[c.documentId]
                ChunkWithDoc(
                    id = c.id,
                    documentId = c.documentId,
                    pageNumber = c.pageNumber,
                    chunkIndex = c.chunkIndex,
                    content = c.content,
                    cropTag = c.cropTag,
                    topicTag = c.topicTag,
                    docTitle = d?.title ?: "PJTSAU Guide",
                    issuingAuthority = d?.issuingAuthority ?: "Professor Jayashankar Telangana State Agricultural University (PJTSAU)"
                )
            }.take(10)
        }

        override suspend fun getChunksByCrop(crop: String): List<ChunkWithDoc> {
            return chunks.filter { it.cropTag.equals(crop, ignoreCase = true) }.map { c ->
                val d = docs[c.documentId]
                ChunkWithDoc(
                    id = c.id,
                    documentId = c.documentId,
                    pageNumber = c.pageNumber,
                    chunkIndex = c.chunkIndex,
                    content = c.content,
                    cropTag = c.cropTag,
                    topicTag = c.topicTag,
                    docTitle = d?.title ?: "PJTSAU Guide",
                    issuingAuthority = d?.issuingAuthority ?: "PJTSAU"
                )
            }
        }

        override suspend fun insertDocuments(docs: List<DocumentEntity>) {
            docs.forEach { this.docs[it.id] = it }
        }

        override suspend fun insertChunks(chunks: List<DocumentChunkEntity>) {
            this.chunks.addAll(chunks)
        }

        override suspend fun getDocumentCount(): Int = docs.size
    }

    class FakeInteractionDao : InteractionDao {
        val inserted = mutableListOf<InteractionEntity>()
        override fun getAllInteractionsFlow(): Flow<List<InteractionEntity>> = flowOf(inserted)
        override suspend fun getPendingUnsyncedInteractions(): List<InteractionEntity> = inserted.filter { !it.isSynced }
        override suspend fun insertInteraction(interaction: InteractionEntity) { inserted.add(interaction) }
        override suspend fun markAsSynced(ids: List<String>) {}
        override suspend fun clearHistory() { inserted.clear() }
    }

    class FakeAppDatabase(
        private val docDao: FakeDocumentDao,
        private val interDao: FakeInteractionDao
    ) : AppDatabase() {
        override fun documentDao(): DocumentDao = docDao
        override fun interactionDao(): InteractionDao = interDao
        override fun farmerProfileDao(): FarmerProfileDao = throw NotImplementedError()
        override fun mandiPriceDao(): MandiPriceDao = throw NotImplementedError()
        override fun syncQueueDao(): SyncQueueDao = throw NotImplementedError()
        override fun clearAllTables() {}
        override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
            return androidx.room.InvalidationTracker(this, "local_chunks")
        }
        override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper {
            throw NotImplementedError()
        }
    }

    @Before
    fun setup() = runBlocking {
        val docDao = FakeDocumentDao()
        val interDao = FakeInteractionDao()
        fakeDb = FakeAppDatabase(docDao, interDao)

        // Seed knowledge chunks for the 5 crops
        val docs = listOf(
            DocumentEntity("doc_cotton", "PJTSAU Cotton Integrated Pest Management Guide 2025-26", "Cotton", "Telangana", "te", "PJTSAU", "2025-06-15", "2025.1", "", "VERIFIED_OFFICIAL", "APPROVED"),
            DocumentEntity("doc_paddy", "PJTSAU Paddy Crop Management & Protection Guide 2025-26", "Paddy", "Telangana", "te", "PJTSAU", "2025-07-01", "2025.1", "", "VERIFIED_OFFICIAL", "APPROVED"),
            DocumentEntity("doc_chilli", "PJTSAU Chilli Integrated Pest & Disease Management Guide 2025-26", "Chilli", "Telangana", "te", "PJTSAU", "2025-08-10", "2025.1", "", "VERIFIED_OFFICIAL", "APPROVED"),
            DocumentEntity("doc_groundnut", "PJTSAU వేరుశనగ సమగ్ర సస్యరక్షణ మరియు యాజమాన్య దర్శిని 2025-26", "Groundnut", "Telangana", "te", "PJTSAU", "2025-06-25", "2025.1", "", "VERIFIED_OFFICIAL", "APPROVED"),
            DocumentEntity("doc_redgram", "PJTSAU కంది పంట సమగ్ర యాజమాన్యం మరియు తెగుళ్ల నివారణ 2025-26", "Red gram", "Telangana", "te", "PJTSAU", "2025-06-30", "2025.1", "", "VERIFIED_OFFICIAL", "APPROVED")
        )
        docDao.insertDocuments(docs)

        val chunks = listOf(
            DocumentChunkEntity("c_pbw", "doc_cotton", 12, 0, "పత్తిలో గులాబీ రంగు పురుగు నివారణకు ఎకరానికి 8 లింగాకర్షక బుట్టలు అమర్చాలి. వేపనూనె 5 మి.లీ లీటరు నీటికి కలపాలి. ఆర్థిక నష్ట పరిమితి దాటితే ఎమామెక్టిన్ బెంజోయేట్ 5% SG 88-100 గ్రాములు ఎకరాకు వాడాలి.", "Cotton", "pink_bollworm"),
            DocumentChunkEntity("c_stem", "doc_paddy", 15, 0, "వరిలో కాండం తొలుచు పురుగు మొవ్వు కుళ్లు నివారణకు క్లోరాంట్రానిలీప్రోల్ 18.5% SC ఎకరాకు 60 మి.లీ 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి.", "Paddy", "stem_borer"),
            DocumentChunkEntity("c_thrips", "doc_chilli", 6, 0, "మిరపలో తామర పురుగులకు ఎకరానికి 15-20 నీలిరంగు జిగురు అట్టలు అమర్చాలి. వేపనూనె 10000 PPM 5 మి.లీ లీటరు నీటికి కలిపి పిచికారీ చేయాలి.", "Chilli", "thrips"),
            DocumentChunkEntity("c_tikka", "doc_groundnut", 9, 0, "వేరుశనగలో టిక్కా ఆకుమచ్చ తెగులు నివారణకు మాంకోజెబ్ 2 గ్రాములు లీటరు నీటికి లేదా టెబుకొనజోల్ 1 మి.లీ లీటరు నీటికి కలిపి పిచికారీ చేయాలి.", "Groundnut", "tikka"),
            DocumentChunkEntity("c_pod", "doc_redgram", 11, 0, "కందిలో కాయతొలుచు పురుగు నివారణకు ఎకరానికి 5-8 లింగాకర్షక బుట్టలు అమర్చాలి. పురుగు ఉధృతిని బట్టి క్లోరాంట్రానిలీప్రోల్ 18.5% SC 60 మి.లీ ఎకరాకు వాడాలి.", "Red gram", "pod_borer")
        )
        docDao.insertChunks(chunks)

        retrievalEngine = LocalKnowledgeRetrievalEngine(fakeDb)
    }

    @Test
    fun testAgriculturalOntologyCoverage() {
        val engine = OfflineAgriculturalVisionEngine(
            context = object : android.content.ContextWrapper(null) {},
            retrievalEngine = retrievalEngine
        )

        val crops = engine.supportedCrops
        assertEquals(5, crops.size)
        assertTrue(crops.contains("Cotton"))
        assertTrue(crops.contains("Paddy"))
        assertTrue(crops.contains("Chilli"))
        assertTrue(crops.contains("Groundnut"))
        assertTrue(crops.contains("Red gram"))

        // Check classes cover all 5 crops plus OOD
        val classes = engine.agriculturalClasses
        assertTrue(classes.size >= 18)

        val cottonPBW = classes.firstOrNull { it.classCode == "COTTON_PINK_BOLLWORM" }
        assertNotNull(cottonPBW)
        assertEquals("Cotton", cottonPBW?.crop)
        assertEquals(AgronomicSymptomType.PEST_INFESTATION, cottonPBW?.symptomType)
        assertTrue(cottonPBW?.distinguishingQuestions?.isNotEmpty() == true)

        val paddyStem = classes.firstOrNull { it.classCode == "PADDY_STEM_BORER_DEADHEART" }
        assertNotNull(paddyStem)
        assertEquals("Paddy", paddyStem?.crop)

        val chilliThrips = classes.firstOrNull { it.classCode == "CHILLI_THRIPS_MITES_CURL" }
        assertNotNull(chilliThrips)
        assertEquals("Chilli", chilliThrips?.crop)

        val groundnutTikka = classes.firstOrNull { it.classCode == "GROUNDNUT_TIKKA_LEAF_SPOT" }
        assertNotNull(groundnutTikka)
        assertEquals("Groundnut", groundnutTikka?.crop)

        val redgramBorer = classes.firstOrNull { it.classCode == "REDGRAM_POD_BORER" }
        assertNotNull(redgramBorer)
        assertEquals("Red gram", redgramBorer?.crop)

        val machinery = classes.firstOrNull { it.classCode == "MACHINERY_EQUIPMENT" }
        assertNotNull(machinery)
        assertEquals(AgronomicSymptomType.NON_PLANT, machinery?.symptomType)
    }

    @Test
    fun testQualityGuardThresholds() {
        assertEquals(115.0f, OfflineAgriculturalVisionEngine.LAPLACIAN_BLUR_THRESHOLD, 0.1f)
        assertEquals(38.0f, OfflineAgriculturalVisionEngine.MIN_ACCEPTABLE_LUMINANCE, 0.1f)
        assertEquals(222.0f, OfflineAgriculturalVisionEngine.MAX_ACCEPTABLE_LUMINANCE, 0.1f)
    }

    @Test
    fun testTriEngineChemicalSafetyVerificationOnDiseaseDiagnosis() = runBlocking {
        // When a fungal disease like Tikka Leaf Spot is diagnosed, ensure chemical safety blocks Coragen insecticide!
        val queryCoragenForLeafSpot = "వేరుశనగలో ఆకుమచ్చ తెగులు నివారణకు కొరాజెన్ (coragen) వాడవచ్చా?"
        val safetyCheck = ChemicalSafetyEngine.validateChemical(queryCoragenForLeafSpot, "Groundnut", "te")

        assertFalse("Insecticide must be blocked for fungal leaf spot disease", safetyCheck.isSafe)
        assertEquals("CHEMICAL_SAFETY_BLOCK", safetyCheck.scenario)
        assertTrue(safetyCheck.requiresEscalation)
        assertTrue(safetyCheck.message.contains("శిలీంధ్ర తెగుళ్లకు") || safetyCheck.message.contains("కీటకనాశని"))

        // When approved chemical (Chlorantraniliprole 18.5% SC) is used for stem borer, verify it passes safety
        val queryApprovedStemBorer = "వరిలో కాండం తొలుచు పురుగు నివారణకు క్లోరాంట్రానిలీప్రోల్ 18.5% SC మోతాదు ఎంత?"
        val safetyApproved = ChemicalSafetyEngine.validateChemical(queryApprovedStemBorer, "Paddy", "te")
        assertTrue("Approved chemical formulation must pass safety check", safetyApproved.isSafe)
        assertEquals("EVIDENCE_AVAILABLE", safetyApproved.scenario)
        assertTrue(safetyApproved.message.contains("60 మి.లీ"))
    }

    @Test
    fun testRAGIntegrationWithPJTSAUVerifiedAdvisory() = runBlocking {
        val advisory = retrievalEngine.getAdvisory("పత్తిలో గులాబీ రంగు పురుగు నివారణ", "Cotton", "te")
        assertEquals("EVIDENCE_AVAILABLE", advisory.scenario)
        assertTrue(advisory.answer.contains("PJTSAU") || advisory.answer.contains("లింగాకర్షక బుట్టలు"))
        assertNotNull(advisory.citationDoc)
    }

    @Test
    fun testFiveCropsFullCoverageWithCitations() {
        val engine = OfflineAgriculturalVisionEngine(
            context = object : android.content.ContextWrapper(null) {},
            retrievalEngine = retrievalEngine
        )

        val classes = engine.agriculturalClasses
        val requiredCrops = listOf("Cotton", "Paddy", "Chilli", "Groundnut", "Red gram")
        for (crop in requiredCrops) {
            val cropClasses = classes.filter { it.crop == crop }
            assertTrue("Crop $crop must have at least 2 distinct disease/pest classes", cropClasses.size >= 2)
            cropClasses.forEach { hypothesis ->
                assertNotNull("Citation doc required for ${hypothesis.classCode}", hypothesis.verifiedCitationDoc)
                assertTrue("Page number must be valid for ${hypothesis.classCode}", hypothesis.citationPage > 0)
                assertTrue("Common name TE required for ${hypothesis.classCode}", hypothesis.commonNameTe.isNotBlank())
                assertTrue("Common name HI required for ${hypothesis.classCode}", hypothesis.commonNameHi.isNotBlank())
                assertTrue("Scientific name required for ${hypothesis.classCode}", hypothesis.scientificName.isNotBlank())
            }
        }
    }

    @Test
    fun testMachineryDiscriminationOntology() {
        val engine = OfflineAgriculturalVisionEngine(
            context = object : android.content.ContextWrapper(null) {},
            retrievalEngine = retrievalEngine
        )

        val machineryClass = engine.agriculturalClasses.first { it.classCode == "MACHINERY_EQUIPMENT" }
        assertEquals(AgronomicSymptomType.NON_PLANT, machineryClass.symptomType)
        assertEquals("None", machineryClass.crop)
        assertTrue(machineryClass.diagnosticVisualSigns.contains("Mechanical") || machineryClass.diagnosticVisualSigns.contains("metallic"))

        val nonAgriClass = engine.agriculturalClasses.first { it.classCode == "NON_AGRICULTURAL_OBJECT" }
        assertEquals(AgronomicSymptomType.NON_PLANT, nonAgriClass.symptomType)
        assertEquals("None", nonAgriClass.crop)
    }

    @Test
    fun testBannedChemicalStatutoryRejectionOnDiagnosis() = runBlocking {
        // Monocrotophos must trigger statutory 100% block with CIBRC Page 1 citation
        val bannedChemicals = listOf(
            "మోనోక్రోటోఫాస్" to "Cotton",
            "పారాక్వాట్" to "Paddy",
            "ఎండోసల్ఫాన్" to "Chilli",
            "కార్బోఫ్యూరాన్" to "Groundnut",
            "ఫోరేట్" to "Red gram"
        )

        for ((chemical, crop) in bannedChemicals) {
            val query = "$crop పంటలో $chemical పురుగు మందు వాడవచ్చా?"
            val result = ChemicalSafetyEngine.validateChemical(query, crop, "te")
            assertFalse("Banned chemical $chemical must be rejected", result.isSafe)
            assertEquals("CHEMICAL_SAFETY_BLOCK", result.scenario)
            assertTrue("Escalation required for $chemical", result.requiresEscalation)
            assertTrue(result.citationDoc?.contains("CIBRC") == true)
            assertEquals(1, result.citationPage)
        }
    }

    @Test
    fun testAdversarialMachineryTrapRejection() = runBlocking {
        val tractorTraps = listOf(
            "ట్రాక్టర్ కొనుగోలు ఎలా చేయాలి?",
            "పవర్ టిల్లర్ రిపేర్ విధానం ఏమిటి?",
            "స్ప్రేయర్ నాజిిల్ ధర ఎంత?"
        )
        for (trap in tractorTraps) {
            val res = retrievalEngine.getAdvisory(trap, "Cotton", "te")
            assertEquals("EVIDENCE_UNAVAILABLE_REFUSAL", res.scenario)
            assertNull(res.citationDoc)
            assertTrue(res.answer.contains("ఎటువంటి సమాచారం లభించలేదు"))
        }
    }

    @Test
    fun testCrossLingualAdvisoryGenerationInTeluguAndHindi() = runBlocking {
        // Telugu query for Cotton Pink Bollworm
        val teResult = retrievalEngine.getAdvisory("పత్తిలో గులాబీ రంగు పురుగు నివారణ", "Cotton", "te")
        assertEquals("EVIDENCE_AVAILABLE", teResult.scenario)
        assertTrue("Telugu output must contain Telugu script", teResult.answer.any { it in '\u0C00'..'\u0C7F' })
        assertEquals(12, teResult.citationPage)

        // Seed a Hindi chunk to test Hindi retrieval
        val docDao = fakeDb.documentDao()
        docDao.insertChunks(listOf(
            DocumentChunkEntity("c_pbw_hi", "doc_cotton", 5, 1, "कपास में गुलाबी सुंडी का नियंत्रण: 8 फेरोमोन ट्रैप लगाएं और नीम का तेल 5 मिली प्रति लीटर छिड़कें।", "Cotton", "pink_bollworm")
        ))

        // Hindi query for Cotton Pink Bollworm
        val hiResult = retrievalEngine.getAdvisory("कपास में गुलाबी सुंडी का नियंत्रण", "Cotton", "hi")
        assertEquals("EVIDENCE_AVAILABLE", hiResult.scenario)
        assertTrue("Hindi output must contain Devanagari script", hiResult.answer.any { it in '\u0900'..'\u097F' })
        assertEquals(5, hiResult.citationPage)
    }
}
