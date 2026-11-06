package com.bolkisan.app.pipeline

import com.bolkisan.app.data.local.AppDatabase
import com.bolkisan.app.data.local.daos.*
import com.bolkisan.app.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RAGAndChemicalSafetyTest {

    private lateinit var fakeDb: FakeAppDatabase
    private lateinit var retrievalEngine: LocalKnowledgeRetrievalEngine

    class FakeDocumentDao : DocumentDao {
        private val docs = mutableMapOf<String, DocumentEntity>()
        private val chunks = mutableListOf<DocumentChunkEntity>()

        override suspend fun getChunkCount(): Int = chunks.size

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
                    docTitle = d?.title ?: "Unknown Doc",
                    issuingAuthority = d?.issuingAuthority ?: "Unknown Authority"
                )
            }.take(15)
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
                    docTitle = d?.title ?: "Unknown Doc",
                    issuingAuthority = d?.issuingAuthority ?: "Unknown Authority"
                )
            }.take(10)
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
        override suspend fun insertInteraction(interaction: InteractionEntity) {
            inserted.add(interaction)
        }
        override suspend fun markAsSynced(ids: List<String>) {
            for (i in inserted.indices) {
                if (inserted[i].id in ids) {
                    inserted[i] = inserted[i].copy(isSynced = true)
                }
            }
        }
        override suspend fun clearHistory() {
            inserted.clear()
        }
    }

    class FakeAppDatabase(
        private val fakeDocDao: FakeDocumentDao,
        private val fakeInterDao: FakeInteractionDao
    ) : AppDatabase() {
        override fun documentDao(): DocumentDao = fakeDocDao
        override fun interactionDao(): InteractionDao = fakeInterDao
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

        // Populate verified seeds matching AppDatabase.seedOfflineKnowledge
        val doc1 = DocumentEntity(
            id = "doc_pjtsau_cotton_te_2025",
            title = "PJTSAU Cotton Integrated Pest Management Guide 2025-26",
            crop = "Cotton",
            region = "Telangana",
            language = "te",
            issuingAuthority = "Professor Jayashankar Telangana State Agricultural University (PJTSAU)",
            publicationDate = "2025-06-15",
            version = "2026.2",
            sourceUrl = "https://pjtsau.edu.in/cotton-ipm-guide-te.html",
            verificationStatus = "VERIFIED_OFFICIAL",
            approvalStatus = "APPROVED"
        )
        val doc2 = DocumentEntity(
            id = "doc_icar_cotton_hi_2025",
            title = "ICAR-CICR कपास कीट एवं रोग प्रबंधन संदर्शिका 2025",
            crop = "Cotton",
            region = "National",
            language = "hi",
            issuingAuthority = "ICAR - Central Institute for Cotton Research (CICR)",
            publicationDate = "2025-06-20",
            version = "2026.2",
            sourceUrl = "https://cicr.org.in/cotton-pest-management-hi.html",
            verificationStatus = "VERIFIED_OFFICIAL",
            approvalStatus = "APPROVED"
        )
        val doc3 = DocumentEntity(
            id = "doc_icar_cotton_en_2025",
            title = "ICAR-CICR Cotton IPM & Pink Bollworm Advisory Bulletin 2025-26",
            crop = "Cotton",
            region = "National",
            language = "en",
            issuingAuthority = "ICAR - Central Institute for Cotton Research (CICR)",
            publicationDate = "2025-06-20",
            version = "2026.2",
            sourceUrl = "https://cicr.org.in/cotton-pink-bollworm-bulletin.html",
            verificationStatus = "VERIFIED_OFFICIAL",
            approvalStatus = "APPROVED"
        )
        val doc4 = DocumentEntity(
            id = "doc_pjtsau_paddy_te_2025",
            title = "PJTSAU Paddy Crop Management & Protection Guide 2025-26",
            crop = "Paddy",
            region = "Telangana",
            language = "te",
            issuingAuthority = "Professor Jayashankar Telangana State Agricultural University (PJTSAU)",
            publicationDate = "2025-07-01",
            version = "2026.2",
            sourceUrl = "https://pjtsau.edu.in/rice-package-of-practices-te.html",
            verificationStatus = "VERIFIED_OFFICIAL",
            approvalStatus = "APPROVED"
        )
        val doc5 = DocumentEntity(
            id = "doc_icar_paddy_en_2025",
            title = "ICAR-IIRR Rice Crop Production & Protection Guidelines 2025",
            crop = "Paddy",
            region = "National",
            language = "en",
            issuingAuthority = "ICAR - Indian Institute of Rice Research (IIRR)",
            publicationDate = "2025-07-01",
            version = "2026.2",
            sourceUrl = "https://rice-icar.res.in/rice-protection-guidelines.html",
            verificationStatus = "VERIFIED_OFFICIAL",
            approvalStatus = "APPROVED"
        )
        val doc6 = DocumentEntity(
            id = "doc_cibrc_banned_list_2025",
            title = "CIBRC Gazette List of Banned and Restricted Pesticides in India 2025",
            crop = "Multi",
            region = "National",
            language = "te",
            issuingAuthority = "Central Insecticide Board & Registration Committee (CIBRC)",
            publicationDate = "2025-01-15",
            version = "2026.2",
            sourceUrl = "https://cibrc.gov.in/banned-pesticides-list-2025.html",
            verificationStatus = "VERIFIED_OFFICIAL",
            approvalStatus = "APPROVED"
        )
        val doc7 = DocumentEntity(
            id = "doc_cibrc_safety_guidelines",
            title = "CIBRC Pesticide Safety, Dosage & Label Regulations",
            crop = "Multi",
            region = "National",
            language = "te",
            issuingAuthority = "Central Insecticide Board & Registration Committee (CIBRC)",
            publicationDate = "2025-01-15",
            version = "2026.2",
            sourceUrl = "https://cibrc.gov.in/safety-guidelines-label-regulations.html",
            verificationStatus = "VERIFIED_OFFICIAL",
            approvalStatus = "APPROVED"
        )
        val doc8 = DocumentEntity(
            id = "doc_pjtsau_maize_te_2025",
            title = "PJTSAU మొక్కజొన్న సమగ్ర సస్యరక్షణ మరియు యాజమాన్య దర్శిని 2025-26",
            crop = "Maize",
            region = "Telangana",
            language = "te",
            issuingAuthority = "Professor Jayashankar Telangana State Agricultural University (PJTSAU)",
            publicationDate = "2025-06-20",
            version = "2026.3",
            sourceUrl = "https://pjtsau.edu.in/maize-package-of-practices-te.html",
            verificationStatus = "VERIFIED_OFFICIAL",
            approvalStatus = "APPROVED"
        )

        docDao.insertDocuments(listOf(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8))

        val chunks = listOf(
            DocumentChunkEntity(
                id = "ch_cotton_pbw_te_01",
                documentId = doc1.id,
                pageNumber = 4,
                chunkIndex = 0,
                content = "పత్తిలో గులాబీ రంగు పురుగు నివారణ మరియు యాజమాన్యం (Cotton Pink Bollworm Control): పూత మరియు కాయ దశలో ఎకరానికి 8 లింగాకర్షక బుట్టలను అమర్చాలి. నివారణకు: వేప నూనె 10,000 PPM ఎకరానికి 400-500 మి.లీ 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. పురుగు ఉధృతిని బట్టి పిచికారీ చేయాలి. పత్తి పంటలో గులాబీ రంగు పురుగు సమస్యలు సమర్థవంతంగా అదుపులోకి వస్తాయి.",
                cropTag = "Cotton",
                topicTag = "pest_management"
            ),
            DocumentChunkEntity(
                id = "ch_cotton_yellow_te_02",
                documentId = doc1.id,
                pageNumber = 7,
                chunkIndex = 1,
                content = "పత్తిలో ఆకులు పసుపు రంగులోకి మారుతున్నాయి (ఆకులు పసుపుబారడం మరియు రసం పీల్చే పురుగుల నివారణ): తెల్లదోమ మరియు జెస్సిడ్స్ ఆకుల రసాన్ని పీల్చడం వల్ల ఆకులు, ఆకుల అంచులు పసుపు రంగులోకి మారుతాయి / మారుతున్నాయి. నివారణకు పసుపు మరియు నీలి రంగు జిగురు అట్టలను ఎకరానికి 10 చొప్పున అమర్చాలి. వేపనూనె 5 మి.లీ లీటరు నీటికి కలిపి పిచికారీ చేయాలి. నత్రజని అధికంగా వేయవద్దు.",
                cropTag = "Cotton",
                topicTag = "symptom_yellowing"
            ),
            DocumentChunkEntity(
                id = "ch_cotton_pbw_hi_01",
                documentId = doc2.id,
                pageNumber = 5,
                chunkIndex = 0,
                content = "कपास की फसल में मुख्य समस्याएं एवं गुलाबी सुंडी (Pink Bollworm) का नियंत्रण: फूल और गूलर बनने की अवस्था में प्रति एकड़ 8 फेरोमोन ट्रैप लगाएं। नियंत्रण के लिए नीम का तेल (Neem oil 10000 PPM) 400-500 मिली प्रति एकड़ 200 लीटर पानी में मिलाकर छिड़काव करें। लगातार 3 दिन तक 8 पतंगे मिलने पर तुरंत उपचार करें।",
                cropTag = "Cotton",
                topicTag = "pest_management"
            ),
            DocumentChunkEntity(
                id = "ch_cotton_pbw_en_01",
                documentId = doc3.id,
                pageNumber = 6,
                chunkIndex = 0,
                content = "Cotton Pink Bollworm management and prevention: Install 8 pheromone traps per acre during flowering and boll formation stage. Economic Threshold Level (ETL) is 8 moths per trap per day for 3 consecutive days. For control, spray Neem oil 10,000 PPM at 400-500 ml per acre in 200 liters of water or Chlorantraniliprole 18.5% SC at 60 ml per acre.",
                cropTag = "Cotton",
                topicTag = "pest_management"
            ),
            DocumentChunkEntity(
                id = "ch_cotton_yellow_en_02",
                documentId = doc3.id,
                pageNumber = 10,
                chunkIndex = 1,
                content = "Cotton leaves turning yellow and sucking pest control: Whiteflies and jassids suck cell sap, causing cotton leaf edges to curl and turn yellow. Control with 10 yellow and blue sticky traps per acre. Spray 5% neem seed kernel extract (NSKE) or neem oil at 5 ml per liter of water. Avoid excessive nitrogenous fertilizers.",
                cropTag = "Cotton",
                topicTag = "symptom_yellowing"
            ),
            DocumentChunkEntity(
                id = "ch_paddy_stem_te_01",
                documentId = doc4.id,
                pageNumber = 14,
                chunkIndex = 0,
                content = "వరిలో కాండం తొలుచు పురుగు (Stem Borer) మరియు చనిపోయిన మొవ్వులు (Dead hearts) నివారణ: సిఫార్సు చేసిన క్లోరాంట్రానిలీప్రోల్ (Chlorantraniliprole 18.5% SC) 60 మి.లీ ఎకరాకు 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. ఎకరానికి 5 లింగాకర్షక బుట్టలను అమర్చాలి.",
                cropTag = "Paddy",
                topicTag = "pest_management"
            ),
            DocumentChunkEntity(
                id = "ch_paddy_yellow_te_02",
                documentId = doc4.id,
                pageNumber = 16,
                chunkIndex = 1,
                content = "వరిలో ఆకులు పసుపు రంగులోకి మారుతున్నాయి (వరి ఆకులు పసుపుబారడం మరియు జింక్ లోపం నివారణ): వరి పైరులో జింక్ లేదా ఇనుము లోపం వల్ల లేదా కాండం తొలుచు పురుగు ఉధృతి వల్ల ఆకులు పసుపు రంగులోకి మారుతాయి. నివారణకు జింక్ సల్ఫేట్ 2 గ్రాములు లీటరు నీటికి కలిపి పిచికారీ చేయాలి.",
                cropTag = "Paddy",
                topicTag = "symptom_yellowing"
            ),
            DocumentChunkEntity(
                id = "ch_paddy_stem_en_01",
                documentId = doc5.id,
                pageNumber = 14,
                chunkIndex = 0,
                content = "Paddy rice stem borer and dead heart control: Upon noticing dead hearts during vegetative stage or white ears at heading, apply Chlorantraniliprole 18.5% SC at 60 ml per acre mixed in 200 liters of water. Install 5 pheromone traps per acre to monitor yellow stem borer adult activity.",
                cropTag = "Paddy",
                topicTag = "pest_management"
            ),
            DocumentChunkEntity(
                id = "ch_cibrc_banned_te_01",
                documentId = doc6.id,
                pageNumber = 1,
                chunkIndex = 0,
                content = "భారత ప్రభుత్వం & CIBRC గెజిట్ నోటిఫికేషన్ ద్వారా పూర్తిగా నిషేధించబడిన రసాయనాలు: మోనోక్రోటోఫాస్ (Monocrotophos), పారాక్వాట్ డైక్లోరైడ్ (Paraquat), ఎండోసల్ఫాన్ (Endosulfan), డిడిటి (DDT), కార్బోఫ్యూరాన్ (Carbofuran), ఫోరేట్ (Phorate), మిథైల్ పారాథియాన్. ఈ ప్రమాదకర రసాయనాలను వ్యవసాయంలో ఉపయోగించడం చట్టరీత్యా నేరం మరియు పంటలకు, ప్రజలకు ప్రాణాంతక ప్రమాదం.",
                cropTag = "Multi",
                topicTag = "chemical_safety"
            ),
            DocumentChunkEntity(
                id = "ch_maize_faw_te_01",
                documentId = doc8.id,
                pageNumber = 6,
                chunkIndex = 0,
                content = "మొక్కజొన్నలో కత్తెర పురుగు నివారణ (Maize Fall Armyworm - Spodoptera frugiperda Control): మొలక దశ నుండి కత్తెర పురుగు ఆకులను, సుడులను కొరికి నష్టపరుస్తుంది. నివారణకు: విత్తన శుద్ధికి సయాంట్రానిలీప్రోల్ 19.8% + థయామిథాక్సమ్ 19.8% FS 6 మి.లీ కిలో విత్తనానికి కలపాలి. పంట తొలిదశలో ఎకరాకు 4 లింగాకర్షక బుట్టలు అమర్చాలి. పిచికారీకి: ఎమామెక్టిన్ బెంజోయేట్ 5% SG 80 గ్రాములు లేదా క్లోరాంట్రానిలీప్రోల్ 18.5% SC 80 మి.లీ ఎకరాకు 200 లీటర్ల నీటిలో కలిపి సుడులలో పడేలా పిచికారీ చేయాలి.",
                cropTag = "Maize",
                topicTag = "pest_management"
            )
        )
        docDao.insertChunks(chunks)

        retrievalEngine = LocalKnowledgeRetrievalEngine(fakeDb)
    }

    // 1. Verify Telugu Cotton Pink Bollworm Query
    @Test
    fun testTeluguPinkBollwormRetrieval() = runBlocking {
        val query = "పత్తిలో గులాబీ రంగు పురుగు నివారణ"
        val res = retrievalEngine.getAdvisory(query, crop = "Cotton", language = "te")

        assertEquals("EVIDENCE_AVAILABLE", res.scenario)
        assertNotNull(res.citationDoc)
        assertTrue(res.citationDoc!!.contains("PJTSAU"))
        assertEquals(4, res.citationPage)
        assertTrue(res.answer.contains("PJTSAU"))
        assertTrue(res.answer.contains("పేజీ 4"))
        assertTrue(res.spokenAnswer.contains("వేప నూనె") || res.spokenAnswer.contains("లింగాకర్షక బుట్టలను"))
    }

    // 2. Verify Telugu Leaves Turning Yellow Query
    @Test
    fun testTeluguYellowingLeavesRetrieval() = runBlocking {
        val query = "ఆకులు పసుపు రంగులోకి మారుతున్నాయి"
        val res = retrievalEngine.getAdvisory(query, crop = "Cotton", language = "te")

        assertEquals("EVIDENCE_AVAILABLE", res.scenario)
        assertNotNull(res.citationDoc)
        assertTrue(res.citationDoc!!.contains("PJTSAU"))
        assertEquals(7, res.citationPage)
        assertTrue(res.answer.contains("జేస్సిడ్స్") || res.answer.contains("తెల్లదోమ") || res.answer.contains("పసుపు మరియు నీలి రంగు"))
    }

    // 3. Verify Banned Chemicals: Monocrotophos, Paraquat, Endosulfan, DDT
    @Test
    fun testBannedChemicalsBlockedWithCIBRCCitation() = runBlocking {
        val bannedQueries = listOf(
            "మోనోక్రోటోఫాస్ ఎంత స్ప్రే చేయాలి?",
            "పారాక్వాట్ పిచికారీ మోతాదు ఎంత?",
            "ఎండోసల్ఫాన్ పత్తిలో వాడవచ్చా?",
            "డిడిటి వాడకం ఎలా?"
        )

        for (q in bannedQueries) {
            val res = retrievalEngine.getAdvisory(q, crop = "Cotton", language = "te")
            assertEquals("Failed for query: $q", "CHEMICAL_SAFETY_BLOCK", res.scenario)
            assertTrue("Requires escalation for $q", res.requiresEscalation)
            assertNotNull("Citation doc must not be null for $q", res.citationDoc)
            assertTrue("Citation must reference CIBRC for $q", res.citationDoc!!.contains("CIBRC"))
            assertEquals("Citation page must be 1 for $q", 1, res.citationPage)
            assertTrue("Answer must mention CIBRC for $q", res.answer.contains("CIBRC"))
            assertTrue("Answer must mention illegal / banned for $q", res.answer.contains("నిషేధించబడింది") || res.answer.contains("చట్టరీత్యా నేరం"))
        }
    }

    // 4. Verify Out-of-Scope Non-Agri Traps: ట్రాక్టర్, క్యాన్సర్, బంగారం
    @Test
    fun testOutOfScopeRefusalStrictZeroHallucination() = runBlocking {
        val nonAgriQueries = listOf(
            "ట్రాక్టర్ కొనుగోలు ఎలా చేయాలి?",
            "క్యాన్సర్ చికిత్స ఏమిటి?",
            "బంగారం ధర ఎంత ఉంది?"
        )

        for (q in nonAgriQueries) {
            val res = retrievalEngine.getAdvisory(q, crop = "Cotton", language = "te")
            assertEquals("Failed for query: $q", "EVIDENCE_UNAVAILABLE_REFUSAL", res.scenario)
            assertNull("Citation doc must be null for $q", res.citationDoc)
            assertEquals(0, res.citationPage)
            assertTrue("Answer must contain strict refusal for $q", res.answer.contains("ఎటువంటి సమాచారం లభించలేదు"))
        }
    }

    // 5. Verify Chemical Incompatibility Tank Mix Block
    @Test
    fun testIncompatibleTankMixBlock() = runBlocking {
        val query = "వేపనూనె మరియు బోర్డో మిశ్రమం కలిపి స్ప్రే చేయవచ్చా?"
        val res = retrievalEngine.getAdvisory(query, crop = "Cotton", language = "te")

        assertEquals("CHEMICAL_SAFETY_BLOCK", res.scenario)
        assertTrue(res.requiresEscalation)
        assertNotNull(res.citationDoc)
        assertTrue(res.citationDoc!!.contains("CIBRC"))
    }

    // 6. Verify Interaction Persistence in Room Table
    @Test
    fun testInteractionPersistedToRoom() = runBlocking {
        val fakeInterDao = fakeDb.interactionDao() as FakeInteractionDao
        assertEquals(0, fakeInterDao.inserted.size)

        val query = "పత్తిలో గులాబీ రంగు పురుగు నివారణ"
        val advisory = retrievalEngine.getAdvisory(query, crop = "Cotton", language = "te")
        val saved = retrievalEngine.persistAdvisoryInteraction(query, advisory, "te")

        assertEquals(1, fakeInterDao.inserted.size)
        val recorded = fakeInterDao.inserted.first()
        assertEquals(saved.id, recorded.id)
        assertEquals(query, recorded.rawTranscript)
        assertEquals("EVIDENCE_AVAILABLE", recorded.responseScenario)
        assertEquals("te", recorded.detectedLanguage)
        assertEquals(advisory.answer, recorded.responseText)
        assertEquals(advisory.citationDoc, recorded.citationDoc)
        assertEquals(advisory.citationPage, recorded.citationPage)
        assertFalse(recorded.isSynced)
    }

    // 7. Verify English Cotton Pink Bollworm Query
    @Test
    fun testEnglishPinkBollwormRetrieval() = runBlocking {
        val query = "How to control pink bollworm in cotton?"
        val res = retrievalEngine.getAdvisory(query, crop = "Cotton", language = "en")

        assertEquals("EVIDENCE_AVAILABLE", res.scenario)
        assertNotNull(res.citationDoc)
        assertTrue(res.citationDoc!!.contains("CICR") || res.citationDoc!!.contains("PJTSAU"))
        assertTrue(res.citationPage > 0)
        assertTrue(res.answer.contains("pheromone traps") || res.answer.contains("Neem oil") || res.answer.contains("Pink Bollworm"))
        assertTrue(res.spokenAnswer.isNotEmpty())
    }

    // 8. Verify English Paddy Stem Borer Query
    @Test
    fun testEnglishPaddyStemBorerRetrieval() = runBlocking {
        val query = "How to control stem borer in paddy?"
        val res = retrievalEngine.getAdvisory(query, crop = "Paddy", language = "en")

        assertEquals("EVIDENCE_AVAILABLE", res.scenario)
        assertNotNull(res.citationDoc)
        assertTrue(res.citationDoc!!.contains("IIRR") || res.citationDoc!!.contains("PJTSAU"))
        assertTrue(res.citationPage > 0)
        assertTrue(res.answer.contains("Chlorantraniliprole") || res.answer.contains("stem borer") || res.answer.contains("pheromone traps"))
    }

    // 9. Verify English Banned Chemical Safety Blocking
    @Test
    fun testEnglishBannedChemicalSafetyBlock() = runBlocking {
        val queries = listOf(
            "Can I spray monocrotophos in cotton?",
            "What is the dosage of paraquat for weed control?"
        )
        for (q in queries) {
            val res = retrievalEngine.getAdvisory(q, crop = "Cotton", language = "en")
            assertEquals("Failed for query: $q", "CHEMICAL_SAFETY_BLOCK", res.scenario)
            assertTrue(res.requiresEscalation)
            assertNotNull(res.citationDoc)
            assertTrue(res.citationDoc!!.contains("CIBRC"))
            assertEquals(1, res.citationPage)
            assertTrue(res.answer.contains("CIBRC") && res.answer.contains("BANNED", ignoreCase = true))
        }
    }

    // 10. Verify English Out-of-Scope Non-Agri Trap Refusals
    @Test
    fun testEnglishOutOfScopeRefusal() = runBlocking {
        val nonAgriQueries = listOf(
            "What is the price of a tractor?",
            "How to treat cancer?",
            "What is the gold rate today?"
        )
        for (q in nonAgriQueries) {
            val res = retrievalEngine.getAdvisory(q, crop = "Cotton", language = "en")
            assertEquals("Failed for query: $q", "EVIDENCE_UNAVAILABLE_REFUSAL", res.scenario)
            assertNull(res.citationDoc)
            assertEquals(0, res.citationPage)
            assertTrue(res.answer.contains("no relevant information was found", ignoreCase = true))
        }
    }

    // 11. Verify Hindi Pink Bollworm Query
    @Test
    fun testHindiPinkBollwormRetrieval() = runBlocking {
        val query = "कपास में गुलाबी सुंडी का नियंत्रण कैसे करें?"
        val res = retrievalEngine.getAdvisory(query, crop = "Cotton", language = "hi")

        assertEquals("EVIDENCE_AVAILABLE", res.scenario)
        assertNotNull(res.citationDoc)
        assertTrue(res.citationDoc!!.contains("CICR") || res.citationDoc!!.contains("PJTSAU"))
        assertTrue(res.citationPage > 0)
        assertTrue(res.answer.contains("गुलाबी सुंडी") || res.answer.contains("फेरोमोन ट्रैप") || res.answer.contains("नीम"))
    }

    // 12. Verify Cross-Language Retrieval
    @Test
    fun testCrossLanguageRetrieval() = runBlocking {
        val query = "How to treat yellowing leaves in cotton crop?"
        val res = retrievalEngine.getAdvisory(query, crop = "Cotton", language = "en")

        assertEquals("EVIDENCE_AVAILABLE", res.scenario)
        assertNotNull(res.citationDoc)
        assertTrue(res.citationPage > 0)
        assertTrue(res.answer.contains("yellow") || res.answer.contains("sucking") || res.answer.contains("traps"))
    }

    // 13. Verify Maize Fall Armyworm Retrieval (Expanded High-Priority Telangana Crop)
    @Test
    fun testMaizeFallArmywormRetrieval() = runBlocking {
        val query = "మొక్కజొన్నలో కత్తెర పురుగు నివారణ ఎలా చేయాలి?"
        val res = retrievalEngine.getAdvisory(query, crop = "Maize", language = "te")

        assertEquals("EVIDENCE_AVAILABLE", res.scenario)
        assertNotNull(res.citationDoc)
        assertTrue(res.citationDoc!!.contains("PJTSAU"))
        assertEquals(6, res.citationPage)
        assertTrue(res.answer.contains("కత్తెర పురుగు") || res.answer.contains("క్లోరాంట్రానిలీప్రోల్") || res.answer.contains("ఎమామెక్టిన్"))
    }

    // 14. Verify Hindi Question with "कारण" (reason/cause) does NOT get trapped by "कार" substring
    @Test
    fun testHindiQuestionWithKaranNotRefused() = runBlocking {
        val query = "कपास में गुलाबी सुंडी का क्या कारण है और रोकथाम कैसे करें?"
        val res = retrievalEngine.getAdvisory(
            query = query,
            crop = "Cotton",
            language = "hi",
            interactionId = "test_hindi_karan_01"
        )

        assertEquals("EVIDENCE_AVAILABLE", res.scenario)
        assertNotNull(res.citationDoc)
        assertTrue(res.citationPage > 0)
        assertTrue(res.answer.contains("गुलाबी सुंडी") || res.answer.contains("फेरोमोन ट्रैप") || res.answer.contains("नीम"))
    }
}

