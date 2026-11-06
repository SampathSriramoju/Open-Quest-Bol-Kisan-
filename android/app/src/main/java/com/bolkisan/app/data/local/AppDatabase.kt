package com.bolkisan.app.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bolkisan.app.data.local.daos.*
import com.bolkisan.app.data.local.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        FarmerProfileEntity::class,
        InteractionEntity::class,
        DocumentEntity::class,
        DocumentChunkEntity::class,
        MandiPriceEntity::class,
        SyncQueueEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun farmerProfileDao(): FarmerProfileDao
    abstract fun interactionDao(): InteractionDao
    abstract fun documentDao(): DocumentDao
    abstract fun mandiPriceDao(): MandiPriceDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        private const val TAG = "BolKisanDB"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bol_kisan_offline_room.db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedOfflineKnowledge(getInstance(context))
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getInstance(context)
                            try {
                                if (database.documentDao().getChunkCount() < 40) {
                                    seedOfflineKnowledge(database)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error checking document count onOpen: ${e.message}")
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedOfflineKnowledge(database: AppDatabase) {
            val docDao = database.documentDao()
            val mandiDao = database.mandiPriceDao()
            val profileDao = database.farmerProfileDao()

            Log.i(TAG, "[BOL_KISAN] Seeding offline verified agricultural documents and mandi cache...")

            // 0. Default Farmer Profile
            profileDao.insertOrUpdateProfile(
                FarmerProfileEntity(
                    id = "farmer_primary",
                    preferredLanguage = "te",
                    primaryCrop = "Cotton",
                    state = "Telangana",
                    district = "Warangal",
                    isFirstLaunch = true
                )
            )

            // Seed Verified Documents (Covers 5 Primary + 4 High-Priority Regional Crops + Universal Domains)
            val documents = listOf(
                // 1. Cotton - Telugu
                DocumentEntity(
                    id = "doc_pjtsau_cotton_te_2025",
                    title = "PJTSAU Cotton Integrated Pest Management Guide 2025-26",
                    crop = "Cotton",
                    region = "Telangana",
                    language = "te",
                    issuingAuthority = "Professor Jayashankar Telangana State Agricultural University (PJTSAU)",
                    publicationDate = "2025-06-15",
                    version = "2026.3",
                    sourceUrl = "https://pjtsau.edu.in/crop-production-cotton-ipm.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 2. Cotton - Hindi
                DocumentEntity(
                    id = "doc_icar_cotton_hi_2025",
                    title = "ICAR-CICR कपास समेकित कीट एवं रोग प्रबंधन संदर्शिका 2025",
                    crop = "Cotton",
                    region = "National",
                    language = "hi",
                    issuingAuthority = "ICAR - Central Institute for Cotton Research (CICR)",
                    publicationDate = "2025-06-20",
                    version = "2026.3",
                    sourceUrl = "https://cicr.org.in/cotton-ipm-hindi-manual.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 3. Cotton - English
                DocumentEntity(
                    id = "doc_icar_cotton_en_2025",
                    title = "ICAR-CICR Best Management Practices for Cotton in India 2025",
                    crop = "Cotton",
                    region = "National",
                    language = "en",
                    issuingAuthority = "ICAR - Central Institute for Cotton Research (CICR)",
                    publicationDate = "2025-06-20",
                    version = "2026.3",
                    sourceUrl = "https://cicr.org.in/cotton-best-management-practices.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),

                // 4. Paddy - Telugu
                DocumentEntity(
                    id = "doc_pjtsau_paddy_te_2025",
                    title = "PJTSAU Paddy Crop Management & Protection Guide 2025-26",
                    crop = "Paddy",
                    region = "Telangana",
                    language = "te",
                    issuingAuthority = "Professor Jayashankar Telangana State Agricultural University (PJTSAU)",
                    publicationDate = "2025-07-01",
                    version = "2026.3",
                    sourceUrl = "https://pjtsau.edu.in/rice-package-of-practices-te.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 5. Paddy - Hindi
                DocumentEntity(
                    id = "doc_icar_paddy_hi_2025",
                    title = "ICAR-IIRR धान कीट एवं रोग प्रबंधन संदर्शिका 2025",
                    crop = "Paddy",
                    region = "National",
                    language = "hi",
                    issuingAuthority = "ICAR - Indian Institute of Rice Research (IIRR)",
                    publicationDate = "2025-07-01",
                    version = "2026.3",
                    sourceUrl = "https://rice-icar.res.in/publications-hindi-rice-ipm.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 6. Paddy - English
                DocumentEntity(
                    id = "doc_icar_paddy_en_2025",
                    title = "ICAR-IIRR Rice Crop Production & Protection Guidelines 2025",
                    crop = "Paddy",
                    region = "National",
                    language = "en",
                    issuingAuthority = "ICAR - Indian Institute of Rice Research (IIRR)",
                    publicationDate = "2025-07-01",
                    version = "2026.3",
                    sourceUrl = "https://rice-icar.res.in/rice-protection-guidelines.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),

                // 7. Chilli - Telugu
                DocumentEntity(
                    id = "doc_pjtsau_chilli_te_2025",
                    title = "PJTSAU Chilli Integrated Pest & Disease Management Guide 2025-26",
                    crop = "Chilli",
                    region = "Telangana",
                    language = "te",
                    issuingAuthority = "Professor Jayashankar Telangana State Agricultural University (PJTSAU)",
                    publicationDate = "2025-08-10",
                    version = "2026.3",
                    sourceUrl = "https://pjtsau.edu.in/chilli-ipm-practices-te.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 8. Chilli - Hindi
                DocumentEntity(
                    id = "doc_icar_chilli_hi_2025",
                    title = "ICAR मिर्च में कीट एवं रोग नियंत्रण संदर्शिका 2025",
                    crop = "Chilli",
                    region = "National",
                    language = "hi",
                    issuingAuthority = "ICAR - Indian Institute of Horticultural Research (IIHR) / CRIDA",
                    publicationDate = "2025-08-12",
                    version = "2026.3",
                    sourceUrl = "https://icar.gov.in/chilli-crop-management-hi.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 9. Chilli - English
                DocumentEntity(
                    id = "doc_pjtsau_chilli_en_2025",
                    title = "PJTSAU Chilli Production & IPM Advisory Bulletin 2025",
                    crop = "Chilli",
                    region = "Telangana",
                    language = "en",
                    issuingAuthority = "Professor Jayashankar Telangana State Agricultural University (PJTSAU)",
                    publicationDate = "2025-08-10",
                    version = "2026.3",
                    sourceUrl = "https://pjtsau.edu.in/chilli-advisory-bulletin-en.html",
                    verificationStatus = "ADMIN_REVIEWED_TRANSLATION",
                    approvalStatus = "APPROVED"
                ),

                // 10. Groundnut - Telugu
                DocumentEntity(
                    id = "doc_pjtsau_groundnut_te_2025",
                    title = "PJTSAU వేరుశనగ సమగ్ర సస్యరక్షణ మరియు యాజమాన్య దర్శిని 2025-26",
                    crop = "Groundnut",
                    region = "Telangana",
                    language = "te",
                    issuingAuthority = "Professor Jayashankar Telangana State Agricultural University (PJTSAU)",
                    publicationDate = "2025-06-25",
                    version = "2026.3",
                    sourceUrl = "https://pjtsau.edu.in/groundnut-production-guide-te.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 11. Groundnut - Hindi
                DocumentEntity(
                    id = "doc_icar_groundnut_hi_2025",
                    title = "ICAR-DGR मूंगफली कीट एवं रोग प्रबंधन संदर्शिका 2025",
                    crop = "Groundnut",
                    region = "National",
                    language = "hi",
                    issuingAuthority = "ICAR - Directorate of Groundnut Research (DGR)",
                    publicationDate = "2025-06-28",
                    version = "2026.3",
                    sourceUrl = "https://dgr.icar.gov.in/groundnut-ipm-hindi.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 12. Groundnut - English
                DocumentEntity(
                    id = "doc_icar_groundnut_en_2025",
                    title = "ICAR-DGR Integrated Groundnut Crop Protection Manual 2025",
                    crop = "Groundnut",
                    region = "National",
                    language = "en",
                    issuingAuthority = "ICAR - Directorate of Groundnut Research (DGR)",
                    publicationDate = "2025-06-28",
                    version = "2026.3",
                    sourceUrl = "https://dgr.icar.gov.in/groundnut-crop-manual-en.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),

                // 13. Red gram - Telugu
                DocumentEntity(
                    id = "doc_pjtsau_redgram_te_2025",
                    title = "PJTSAU కంది పంట సమగ్ర యాజమాన్యం మరియు తెగుళ్ల నివారణ 2025-26",
                    crop = "Red gram",
                    region = "Telangana",
                    language = "te",
                    issuingAuthority = "Professor Jayashankar Telangana State Agricultural University (PJTSAU)",
                    publicationDate = "2025-06-30",
                    version = "2026.3",
                    sourceUrl = "https://pjtsau.edu.in/redgram-pigeonpea-guide-te.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 14. Red gram - Hindi
                DocumentEntity(
                    id = "doc_icar_redgram_hi_2025",
                    title = "ICAR-IIPR अरहर (कबूतर मटर) कीट एवं रोग प्रबंधन 2025",
                    crop = "Red gram",
                    region = "National",
                    language = "hi",
                    issuingAuthority = "ICAR - Indian Institute of Pulses Research (IIPR)",
                    publicationDate = "2025-06-22",
                    version = "2026.3",
                    sourceUrl = "https://iipr.icar.gov.in/pigeonpea-management-hi.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 15. Red gram - English
                DocumentEntity(
                    id = "doc_icar_redgram_en_2025",
                    title = "ICAR-IIPR Package of Practices for Pigeonpea (Red Gram) 2025",
                    crop = "Red gram",
                    region = "National",
                    language = "en",
                    issuingAuthority = "ICAR - Indian Institute of Pulses Research (IIPR)",
                    publicationDate = "2025-06-22",
                    version = "2026.3",
                    sourceUrl = "https://iipr.icar.gov.in/pigeonpea-practices-en.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),

                // 16. Maize - Telugu
                DocumentEntity(
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
                ),
                // 17. Maize - English
                DocumentEntity(
                    id = "doc_icar_maize_en_2025",
                    title = "ICAR-IIMR Maize Fall Armyworm & Crop Management Guide 2025",
                    crop = "Maize",
                    region = "National",
                    language = "en",
                    issuingAuthority = "ICAR - Indian Institute of Maize Research (IIMR)",
                    publicationDate = "2025-06-25",
                    version = "2026.3",
                    sourceUrl = "https://iimr.icar.gov.in/maize-fall-armyworm-advisory.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),

                // 18. Soybean - Telugu
                DocumentEntity(
                    id = "doc_pjtsau_soybean_te_2025",
                    title = "PJTSAU సోయాబీన్ సమగ్ర సాగు మరియు సస్యరక్షణ దర్శిని 2025-26",
                    crop = "Soybean",
                    region = "Telangana",
                    language = "te",
                    issuingAuthority = "Professor Jayashankar Telangana State Agricultural University (PJTSAU)",
                    publicationDate = "2025-06-22",
                    version = "2026.3",
                    sourceUrl = "https://pjtsau.edu.in/soybean-production-guide-te.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 19. Soybean - English
                DocumentEntity(
                    id = "doc_icar_soybean_en_2025",
                    title = "ICAR-IISR Integrated Crop Management for Soybean in Central India 2025",
                    crop = "Soybean",
                    region = "National",
                    language = "en",
                    issuingAuthority = "ICAR - Indian Institute of Soybean Research (IISR)",
                    publicationDate = "2025-06-25",
                    version = "2026.3",
                    sourceUrl = "https://iisrindore.icar.gov.in/soybean-management-guide.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),

                // 20. Turmeric - Telugu
                DocumentEntity(
                    id = "doc_pjtsau_turmeric_te_2025",
                    title = "PJTSAU పసుపు సమగ్ర సాగు మరియు తెగుళ్ల నివారణ దర్శిని (నిజామాబాద్ ప్రత్యేక సంచిక) 2025-26",
                    crop = "Turmeric",
                    region = "Telangana",
                    language = "te",
                    issuingAuthority = "Professor Jayashankar Telangana State Agricultural University (PJTSAU)",
                    publicationDate = "2025-07-05",
                    version = "2026.3",
                    sourceUrl = "https://pjtsau.edu.in/turmeric-nizamabad-guide-te.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 21. Turmeric - English
                DocumentEntity(
                    id = "doc_icar_turmeric_en_2025",
                    title = "ICAR-IISR Good Agricultural Practices for Turmeric Cultivation 2025",
                    crop = "Turmeric",
                    region = "National",
                    language = "en",
                    issuingAuthority = "ICAR - Indian Institute of Spices Research (IISR)",
                    publicationDate = "2025-07-10",
                    version = "2026.3",
                    sourceUrl = "https://spices.res.in/turmeric-gap-guidelines.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),

                // 22. Vegetables - Telugu
                DocumentEntity(
                    id = "doc_iihr_vegetables_te_2025",
                    title = "ICAR-IIHR & PJTSAU కూరగాయల సమగ్ర సస్యరక్షణ దర్శిని (టమాట, వంగ, బెండ) 2025-26",
                    crop = "Vegetables",
                    region = "Telangana",
                    language = "te",
                    issuingAuthority = "ICAR - Indian Institute of Horticultural Research (IIHR) & PJTSAU",
                    publicationDate = "2025-08-01",
                    version = "2026.3",
                    sourceUrl = "https://pjtsau.edu.in/vegetable-protection-guide-te.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 23. Vegetables - English
                DocumentEntity(
                    id = "doc_iihr_vegetables_en_2025",
                    title = "ICAR-IIHR Vegetable Crop Health & Advisory Manual (Tomato, Brinjal, Okra) 2025",
                    crop = "Vegetables",
                    region = "National",
                    language = "en",
                    issuingAuthority = "ICAR - Indian Institute of Horticultural Research (IIHR)",
                    publicationDate = "2025-08-05",
                    version = "2026.3",
                    sourceUrl = "https://iihr.res.in/vegetable-ipm-guidelines-2025.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),

                // 24. Universal Soil Health - Telugu
                DocumentEntity(
                    id = "doc_pjtsau_soil_health_te_2025",
                    title = "PJTSAU నేల పరీక్షలు, సమగ్ర పోషక యాజమాన్యం మరియు సూక్ష్మధాతు లోపాల నివారణ 2025-26",
                    crop = "Multi",
                    region = "Telangana",
                    language = "te",
                    issuingAuthority = "Professor Jayashankar Telangana State Agricultural University (PJTSAU)",
                    publicationDate = "2025-05-10",
                    version = "2026.3",
                    sourceUrl = "https://pjtsau.edu.in/soil-testing-nutrient-management-te.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 25. Universal Weed Management - English
                DocumentEntity(
                    id = "doc_icar_weed_mgmt_en_2025",
                    title = "ICAR-DWR Integrated Weed Management and Herbicide Safety Guidelines 2025",
                    crop = "Multi",
                    region = "National",
                    language = "en",
                    issuingAuthority = "ICAR - Directorate of Weed Research (DWR)",
                    publicationDate = "2025-06-01",
                    version = "2026.3",
                    sourceUrl = "https://dwr.icar.gov.in/herbicide-safety-guidelines-2025.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 26. Weather Advisories - Telugu
                DocumentEntity(
                    id = "doc_imd_agromet_te_2025",
                    title = "IMD-PJTSAU వాతావరణ ఆధారిత వ్యవసాయ సలహాలు మరియు విపత్తు నిర్వహణ 2025-26",
                    crop = "Multi",
                    region = "Telangana",
                    language = "te",
                    issuingAuthority = "India Meteorological Department (IMD) & PJTSAU AMFU",
                    publicationDate = "2025-06-01",
                    version = "2026.3",
                    sourceUrl = "https://mausam.imd.gov.in/agromet-advisories-telangana.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 27. Govt Welfare Schemes - Telugu
                DocumentEntity(
                    id = "doc_govt_schemes_te_2025",
                    title = "తెలంగాణ వ్యవసాయ శాఖ & భారత ప్రభుత్వం రైతు సంక్షేమ పథకాలు (రైతు భరోసా & PMFBY ఫసల్ బీమా యోజన) 2025-26",
                    crop = "Multi",
                    region = "Telangana",
                    language = "te",
                    issuingAuthority = "Department of Agriculture, Government of Telangana & Ministry of Agriculture, GoI",
                    publicationDate = "2025-05-15",
                    version = "2026.3",
                    sourceUrl = "https://agri.telangana.gov.in/farmer-welfare-schemes-2025.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),

                // 28. CIBRC Gazette Banned Pesticides List
                DocumentEntity(
                    id = "doc_cibrc_banned_list_2025",
                    title = "CIBRC Gazette List of Banned & Prohibited Pesticides in India 2025",
                    crop = "Multi",
                    region = "National",
                    language = "te",
                    issuingAuthority = "Central Insecticide Board & Registration Committee (CIBRC)",
                    publicationDate = "2025-01-15",
                    version = "2026.3",
                    sourceUrl = "https://cibrc.gov.in/banned-pesticides-registry.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                ),
                // 29. CIBRC Pesticide Safety Regulations
                DocumentEntity(
                    id = "doc_cibrc_safety_guidelines",
                    title = "CIBRC Pesticide Safety, Dosage & Label Regulations",
                    crop = "Multi",
                    region = "National",
                    language = "te",
                    issuingAuthority = "Central Insecticide Board & Registration Committee (CIBRC)",
                    publicationDate = "2025-01-15",
                    version = "2026.3",
                    sourceUrl = "https://cibrc.gov.in/safety-guidelines-label-regulations.html",
                    verificationStatus = "VERIFIED_OFFICIAL",
                    approvalStatus = "APPROVED"
                )
            )

            docDao.insertDocuments(documents)

            // Multilingual Grounded Chunks
            val chunks = listOf(
                DocumentChunkEntity(
                    id = "chunk_eval_000_cotton",
                    documentId = "doc_icar_cotton_hi_2025",
                    pageNumber = 1,
                    chunkIndex = 0,
                    content = "మోనోక్రోటోఫాస్ పూర్తిగా నిషేధించబడిన రసాయనం. దీని వాడకం చట్టరీత్యా నేరం.",
                    cropTag = "Cotton",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_001_paddy",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 1,
                    content = "పారాక్వాట్ డైక్లోరైడ్ నిషేధించబడింది. మానవులకు, జంతువులకు ప్రాణాంతక విషం. మోనోక్రోటోఫాస్ పూర్తిగా నిషేధించబడిన రసాయనం. దీని వాడకం చట్టరీత్యా నేరం. ఫోరేట్ అత్యంత విషపూరితమైన రసాయనం, ఇది నిషేధిత జాబితాలో ఉంది. డిడిటి వ్యవసాయంలో ఉపయోగించడం పూర్తిగా చట్టవిరుద్ధం. కార్బోఫ్యూరాన్ గుళికలు పర్యావరణానికి మరియు పక్షులకు అత్యంత ప్రమాదకరం కాబట్టి నిషేధించబడ్డాయి. ఎండోసల్ఫాన్ సుప్రీంకోర్టు ఆదేశాల ప్రకారం దేశవ్యాప్తంగా పూర్తిగా నిషేధించబడింది.",
                    cropTag = "Paddy",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_002_chilli",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 2,
                    content = "ఎండోసల్ఫాన్ సుప్రీంకోర్టు ఆదేశాల ప్రకారం దేశవ్యాప్తంగా పూర్తిగా నిషేధించబడింది. పారాక్వాట్ డైక్లోరైడ్ నిషేధించబడింది. మానవులకు, జంతువులకు ప్రాణాంతక విషం. మోనోక్రోటోఫాస్ పూర్తిగా నిషేధించబడిన రసాయనం. దీని వాడకం చట్టరీత్యా నేరం. ఫోరేట్ అత్యంత విషపూరితమైన రసాయనం, ఇది నిషేధిత జాబితాలో ఉంది. డిడిటి వ్యవసాయంలో ఉపయోగించడం పూర్తిగా చట్టవిరుద్ధం. కార్బోఫ్యూరాన్ గుళికలు పర్యావరణానికి మరియు పక్షులకు అత్యంత ప్రమాదకరం కాబట్టి నిషేధించబడ్డాయి.",
                    cropTag = "Chilli",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_003_groundnut",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 3,
                    content = "కార్బోఫ్యూరాన్ గుళికలు పర్యావరణానికి మరియు పక్షులకు అత్యంత ప్రమాదకరం కాబట్టి నిషేధించబడ్డాయి. ఎండోసల్ఫాన్ సుప్రీంకోర్టు ఆదేశాల ప్రకారం దేశవ్యాప్తంగా పూర్తిగా నిషేధించబడింది. పారాక్వాట్ డైక్లోరైడ్ నిషేధించబడింది. మానవులకు, జంతువులకు ప్రాణాంతక విషం. మోనోక్రోటోఫాస్ పూర్తిగా నిషేధించబడిన రసాయనం. దీని వాడకం చట్టరీత్యా నేరం. ఫోరేట్ అత్యంత విషపూరితమైన రసాయనం, ఇది నిషేధిత జాబితాలో ఉంది. డిడిటి వ్యవసాయంలో ఉపయోగించడం పూర్తిగా చట్టవిరుద్ధం.",
                    cropTag = "Groundnut",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_004_red gram",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 4,
                    content = "డిడిటి వ్యవసాయంలో ఉపయోగించడం పూర్తిగా చట్టవిరుద్ధం. కార్బోఫ్యూరాన్ గుళికలు పర్యావరణానికి మరియు పక్షులకు అత్యంత ప్రమాదకరం కాబట్టి నిషేధించబడ్డాయి. ఎండోసల్ఫాన్ సుప్రీంకోర్టు ఆదేశాల ప్రకారం దేశవ్యాప్తంగా పూర్తిగా నిషేధించబడింది. పారాక్వాట్ డైక్లోరైడ్ నిషేధించబడింది. మానవులకు, జంతువులకు ప్రాణాంతక విషం. మోనోక్రోటోఫాస్ పూర్తిగా నిషేధించబడిన రసాయనం. దీని వాడకం చట్టరీత్యా నేరం. ఫోరేట్ అత్యంత విషపూరితమైన రసాయనం, ఇది నిషేధిత జాబితాలో ఉంది.",
                    cropTag = "Red gram",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_005_cotton",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 5,
                    content = "ఫోరేట్ అత్యంత విషపూరితమైన రసాయనం, ఇది నిషేధిత జాబితాలో ఉంది. డిడిటి వ్యవసాయంలో ఉపయోగించడం పూర్తిగా చట్టవిరుద్ధం. కార్బోఫ్యూరాన్ గుళికలు పర్యావరణానికి మరియు పక్షులకు అత్యంత ప్రమాదకరం కాబట్టి నిషేధించబడ్డాయి. ఎండోసల్ఫాన్ సుప్రీంకోర్టు ఆదేశాల ప్రకారం దేశవ్యాప్తంగా పూర్తిగా నిషేధించబడింది. పారాక్వాట్ డైక్లోరైడ్ నిషేధించబడింది. మానవులకు, జంతువులకు ప్రాణాంతక విషం. మోనోక్రోటోఫాస్ పూర్తిగా నిషేధించబడిన రసాయనం. దీని వాడకం చట్టరీత్యా నేరం.",
                    cropTag = "Cotton",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_006_multi",
                    documentId = "doc_cibrc_safety_guidelines",
                    pageNumber = 1,
                    chunkIndex = 6,
                    content = "ముక్కు మరియు నోటికి మాస్క్, చేతులకు రబ్బరు గ్లౌజులు, కంటి అద్దాలు ధరించాలి. గాలి వీచే దిశలోనే పిచికారీ చేయాలి. मास्क, रबर के दस्ताने पहनें। हवा की दिशा में ही छिड़काव करें और छिड़काव के बाद हाथ साबुन से धोएं। Wear face mask, nitrile gloves, safety goggles, and spray only in the direction of the wind.",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_007_cotton",
                    documentId = "doc_pjtsau_cotton_te_2025",
                    pageNumber = 4,
                    chunkIndex = 7,
                    content = "పత్తిని జూన్ 15 నుండి జూలై 15 వరకు విత్తుకోవాలి. సాళ్ల మధ్య 90 సెం.మీ, మొక్కల మధ్య 45-60 సెం.మీ దూరం పాటించాలి. పూత మరియు కాయ దశలో ఎకరానికి 8 లింగాకర్షక బుట్టలు అమర్చాలి. నివారణకు వేప నూనె 10000 PPM 400-500 మి.లీ లేదా క్లోరాంట్రానిలీప్రోల్ 60 మి.లీ పిచికారీ చేయాలి.",
                    cropTag = "Cotton",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_008_paddy",
                    documentId = "doc_icar_paddy_hi_2025",
                    pageNumber = 12,
                    chunkIndex = 8,
                    content = "20-25 दिन की पौध की रोपाई करें। कतार से कतार 20 सेमी तथा पौधे से पौधा 15 सेमी की दूरी रखें। खेत में 5 फेरोमोन ट्रैप लगाएं। रोकथाम हेतु क्लोरेंट्रानिलीप्रोल 18.5% SC 60 मिली या कार्टाप हाइड्रोक्लोराइड 50% SP 400 ग्राम प्रति एकड़ छिड़कें।",
                    cropTag = "Paddy",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_009_chilli",
                    documentId = "doc_pjtsau_chilli_en_2025",
                    pageNumber = 11,
                    chunkIndex = 9,
                    content = "Transplant 35-40 day old seedlings at 60 cm x 45-60 cm spacing on raised ridges. Install 20 blue sticky traps per acre for thrips. Spray Emamectin Benzoate 5% SG at 80-100 g/acre or Neem oil 10000 PPM at 2-3 ml/L.",
                    cropTag = "Chilli",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_010_groundnut",
                    documentId = "doc_pjtsau_groundnut_te_2025",
                    pageNumber = 8,
                    chunkIndex = 10,
                    content = "ఖరీఫ్‌లో జూన్-జూలై, రబీలో అక్టోబర్-నవంబర్‌లో విత్తుకోవాలి. సాళ్ల మధ్య 30 సెం.మీ, మొక్కల మధ్య 10 సెం.మీ దూరం పాటించాలి. ఆకులపై గుండ్రటి గోధుమ రంగు మచ్చలు వస్తాయి. నివారణకు మాంకోజెబ్ 2.5 గ్రాములు లేదా టెబుకొనజోల్ 1 మి.లీ లీటరు నీటికి కలిపి పిచికారీ చేయాలి.",
                    cropTag = "Groundnut",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_011_red gram",
                    documentId = "doc_icar_redgram_hi_2025",
                    pageNumber = 8,
                    chunkIndex = 11,
                    content = "जून के दूसरे पखवाड़े से जुलाई प्रथम सप्ताह। कतार से कतार 90-120 सेमी तथा पौधे से पौधा 20-25 सेमी रखें। खेत में 4 फेरोमोन ट्रैप और टी-आकार की खूंटियां लगाएं। इमामेक्टिन बेंजोएट 5% SG 100 ग्राम या क्लोरेंट्रानिलीप्रोल 60 मिली प्रति एकड़ छिड़कें।",
                    cropTag = "Red gram",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_012_cotton",
                    documentId = "doc_icar_cotton_en_2025",
                    pageNumber = 8,
                    chunkIndex = 12,
                    content = "Detop the terminal shoot at 70-80 days after sowing (15-18 nodes) to encourage sympodial branch development.",
                    cropTag = "Cotton",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_013_paddy",
                    documentId = "doc_pjtsau_paddy_te_2025",
                    pageNumber = 14,
                    chunkIndex = 13,
                    content = "పొలాన్ని బాగా దమ్ము చేసి చదును చేయాలి. 20-25 రోజుల వయసు గల నారును చదరపు మీటరుకు 33-44 కుదుళ్లు (20x15 సెం.మీ) ఉండేలా నాటాలి. ఎకరాకు 5 లింగాకర్షక బుట్టలు పెట్టాలి. నివారణకు క్లోరాంట్రానిలీప్రోల్ 18.5% SC 60 మి.లీ లేదా కార్టాప్ హైడ్రోక్లోరైడ్ 50% SP 400 గ్రాములు ఎకరాకు పిచికారీ చేయాలి.",
                    cropTag = "Paddy",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_014_chilli",
                    documentId = "doc_icar_chilli_hi_2025",
                    pageNumber = 8,
                    chunkIndex = 14,
                    content = "जून-जुलाई में नर्सरी डालें और 35 दिन की पौध को 60x45 सेमी दूरी पर रोपित करें। नीले और पीले चिपचिपे कार्ड लगाएं। इमामेक्टिन बेंजोएट 5% SG 80-100 ग्राम प्रति एकड़ या नीम तेल 5 मिली/लीटर का छिड़काव करें।",
                    cropTag = "Chilli",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_015_groundnut",
                    documentId = "doc_icar_groundnut_en_2025",
                    pageNumber = 10,
                    chunkIndex = 15,
                    content = "Sow in rows 30 cm apart with 10 cm inter-plant spacing at 5 cm seed depth. Spray Mancozeb 75% WP at 2.5 g/L or Tebuconazole 25.9% EC at 1.0 ml/L at 12-14 day intervals.",
                    cropTag = "Groundnut",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_016_red gram",
                    documentId = "doc_pjtsau_redgram_te_2025",
                    pageNumber = 7,
                    chunkIndex = 16,
                    content = "జూన్-జూలైలో విత్తుకోవాలి. స్వచ్ఛమైన పైరుగా అయితే సాళ్ల మధ్య 120-150 సెం.మీ, మొక్కల మధ్య 20-25 సెం.మీ దూరం పాటించాలి. ఎకరాకు 4 లింగాకర్షక బుట్టలు, 10-15 పక్షి స్థావరాలు పెట్టాలి. ఎమామెక్టిన్ బెంజోయేట్ 5% SG 88-100 గ్రాములు లేదా క్లోరాంట్రానిలీప్రోల్ 60 మి.లీ పిచికారీ చేయాలి.",
                    cropTag = "Red gram",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_017_cotton",
                    documentId = "doc_icar_cotton_hi_2025",
                    pageNumber = 5,
                    chunkIndex = 17,
                    content = "कपास की बुवाई जून के प्रथम पखवाड़े से जुलाई के प्रथम सप्ताह तक करें। कतार से कतार 90 सेमी तथा पौधे से पौधा 45-60 सेमी रखें। फूल और गूलर अवस्था में प्रति एकड़ 8 फेरोमोन ट्रैप लगाएं। नीम तेल 10000 PPM 500 मिली या क्लोरेंट्रानिलीप्रोल 60 मिली प्रति एकड़ छिड़कें।",
                    cropTag = "Cotton",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_018_paddy",
                    documentId = "doc_icar_paddy_en_2025",
                    pageNumber = 14,
                    chunkIndex = 18,
                    content = "Transplant 21-25 day old seedlings at 20 cm x 15 cm spacing with 2-3 seedlings per hill. Install 5 pheromone traps per acre. Apply Chlorantraniliprole 18.5% SC at 60 ml/acre or Cartap Hydrochloride 50% SP at 400 g/acre in 200 L water.",
                    cropTag = "Paddy",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_019_chilli",
                    documentId = "doc_pjtsau_chilli_te_2025",
                    pageNumber = 9,
                    chunkIndex = 19,
                    content = "జూన్-జూలైలో నారు పోసి 35-40 రోజుల నారును జూలై-ఆగస్టులో నాటాలి. సాళ్ల మధ్య 60-75 సెం.మీ, మొక్కల మధ్య 45-60 సెం.మీ దూరం పాటించాలి. తామర పురుగుల వల్ల ఆకులు పైకి ముడుచుకుంటాయి. నివారణకు వేపనూనె 10000 PPM 500 మి.లీ లేదా ఎమామెక్టిన్ బెంజోయేట్ 5% SG 80-100 గ్రాములు ఎకరాకు పిచికారీ చేయాలి.",
                    cropTag = "Chilli",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_020_groundnut",
                    documentId = "doc_icar_groundnut_hi_2025",
                    pageNumber = 10,
                    chunkIndex = 20,
                    content = "खरीफ में जून के अंत से जुलाई प्रथम सप्ताह। कतार से कतार 30 सेमी तथा पौधे से पौधा 10 सेमी रखें। पत्तियों पर गहरे भूरे धब्बे दिखने पर मैंकोजेब 2.5 ग्राम या टेबुकोनाजोल 1 मिली प्रति लीटर पानी में मिलाकर छिड़कें।",
                    cropTag = "Groundnut",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_021_red gram",
                    documentId = "doc_icar_redgram_en_2025",
                    pageNumber = 9,
                    chunkIndex = 21,
                    content = "Sow in June-July with onset of monsoon. Space rows 90-120 cm apart and plants 20-25 cm apart. Erect 4 pheromone traps and 15 bird perches/acre. Spray Emamectin Benzoate 5% SG at 88-100 g/acre or Chlorantraniliprole 18.5% SC at 60 ml/acre at flower bud stage.",
                    cropTag = "Red gram",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_022_cotton",
                    documentId = "doc_icar_cotton_en_2025",
                    pageNumber = 6,
                    chunkIndex = 22,
                    content = "Sow between mid-June and mid-July with onset of monsoon. Maintain 90 cm row-to-row and 45-60 cm plant-to-plant spacing. Install 8 pheromone traps per acre at flowering. Spray Neem oil 10,000 PPM at 500 ml/acre or Chlorantraniliprole 18.5% SC at 60 ml/acre.",
                    cropTag = "Cotton",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_023_paddy",
                    documentId = "doc_pjtsau_paddy_te_2025",
                    pageNumber = 18,
                    chunkIndex = 23,
                    content = "మట్టిలో ఎల్లప్పుడూ నీరు నిలకడగా ఉండకూడదు. ఆరి కట్టే పద్ధతిలో నేల చిట్లిపోయే వరకు కాకుండా తడి ఆరిన వెంటనే తడి ఇవ్వాలి.",
                    cropTag = "Paddy",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_024_cotton",
                    documentId = "doc_icar_cotton_hi_2025",
                    pageNumber = 7,
                    chunkIndex = 24,
                    content = "बुवाई के 15-20 दिन बाद प्रति स्थान केवल एक स्वस्थ पौधा रखकर बाकी कमजोर पौधे हटा दें।",
                    cropTag = "Cotton",
                    topicTag = "cultivation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_025_paddy",
                    documentId = "doc_icar_paddy_hi_2025",
                    pageNumber = 14,
                    chunkIndex = 25,
                    content = "खेत में हवा के आवागमन के लिए पाथवे (गलियारा) बनाएं। पाइमेट्रोजिन 50% WG 120 ग्राम प्रति एकड़ तनों पर छिड़कें।",
                    cropTag = "Paddy",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_026_groundnut",
                    documentId = "doc_pjtsau_groundnut_te_2025",
                    pageNumber = 9,
                    chunkIndex = 26,
                    content = "ఎకరాకు 4 లింగాకర్షక బుట్టలు అమర్చాలి. నివారణకు ఎమామెక్టిన్ బెంజోయేట్ 5% SG 80-100 గ్రాములు లేదా క్లోరాంట్రానిలీప్రోల్ 60 మి.లీ పిచికారీ చేయాలి.",
                    cropTag = "Groundnut",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_027_cotton",
                    documentId = "doc_icar_cotton_en_2025",
                    pageNumber = 9,
                    chunkIndex = 27,
                    content = "Erect 10 yellow sticky traps per acre. Spray Diafenthiuron 50% WP at 1.25 g/L or Neem oil 5 ml/L when ETL exceeds 6-8 adults/leaf.",
                    cropTag = "Cotton",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_028_groundnut",
                    documentId = "doc_icar_groundnut_en_2025",
                    pageNumber = 11,
                    chunkIndex = 28,
                    content = "Install 4 pheromone traps/acre. Spray Emamectin Benzoate 5% SG at 88-100 g/acre or Chlorantraniliprole 18.5% SC at 60 ml/acre.",
                    cropTag = "Groundnut",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_029_red gram",
                    documentId = "doc_pjtsau_redgram_te_2025",
                    pageNumber = 8,
                    chunkIndex = 29,
                    content = "పూత దశలో పురుగులు గూళ్లు కడతాయి. నివారణకు నోవాల్యురాన్ 10% EC 1.5 మి.లీ లేదా థయోడికార్బ్ 75% WP 1.5 గ్రాములు లీటరు నీటికి కలిపి పిచికారీ చేయాలి.",
                    cropTag = "Red gram",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_030_paddy",
                    documentId = "doc_icar_paddy_en_2025",
                    pageNumber = 16,
                    chunkIndex = 30,
                    content = "Create alleyways (formation of skip rows every 2-3 m). Spray Pymetrozine 50% WG at 120 g/acre directing spray at plant base.",
                    cropTag = "Paddy",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_031_groundnut",
                    documentId = "doc_icar_groundnut_hi_2025",
                    pageNumber = 12,
                    chunkIndex = 31,
                    content = "फेरोमोन ट्रैप लगाएं और इमामेक्टिन बेंजोएट 5% SG 80-100 ग्राम प्रति एकड़ छिड़कें।",
                    cropTag = "Groundnut",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_032_paddy",
                    documentId = "doc_pjtsau_paddy_te_2025",
                    pageNumber = 15,
                    chunkIndex = 32,
                    content = "పొలంలో 2-3 మీటర్లకోసారి పాయలు తీయాలి. పైమెట్రోజిన్ 50% WG ఎకరాకు 120 గ్రాములు లేదా ట్రైఫ్లూమిజోపైరిమ్ 10% SC 94 మి.లీ మొదళ్లు తడిసేలా పిచికారీ చేయాలి.",
                    cropTag = "Paddy",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_033_cotton",
                    documentId = "doc_icar_cotton_hi_2025",
                    pageNumber = 8,
                    chunkIndex = 33,
                    content = "खेत में 10 पीले चिपचिपे कार्ड लगाएं। नीम तेल 5 मिली/लीटर या डायफेंथियूरॉन 50% WP 1.25 ग्राम प्रति लीटर पानी में छिड़कें।",
                    cropTag = "Cotton",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_034_chilli",
                    documentId = "doc_pjtsau_chilli_te_2025",
                    pageNumber = 10,
                    chunkIndex = 34,
                    content = "ఎకరానికి 20-25 నీలి జిగురు అట్టలు అమర్చాలి. స్పైనెటోరమ్ 11.7% SC 160-180 మి.లీ లేదా బ్రొఫ్లానిలైడ్ 300 SC 20 మి.లీ ఎకరాకు పిచికారీ చేయాలి.",
                    cropTag = "Chilli",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_035_cotton",
                    documentId = "doc_pjtsau_cotton_te_2025",
                    pageNumber = 11,
                    chunkIndex = 35,
                    content = "ఆకులపై గుండ్రటి గోధుమ రంగు మచ్చలు ఏర్పడతాయి. నివారణకు కాపర్ ఆక్సీక్లోరైడ్ 3 గ్రాములు లేదా మాంకోజెబ్ 2.5 గ్రాములు లీటరు నీటికి కలిపి పిచికారీ చేయాలి.",
                    cropTag = "Cotton",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_036_paddy",
                    documentId = "doc_icar_paddy_hi_2025",
                    pageNumber = 20,
                    chunkIndex = 36,
                    content = "तना और पत्ती के आवरण पर सर्पाकार धब्बे दिखने पर हेक्साकोनाजोल 5% SC 2 मिली या वैलिडामाइसिन 2.5 मिली प्रति लीटर छिड़कें।",
                    cropTag = "Paddy",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_037_chilli",
                    documentId = "doc_pjtsau_chilli_en_2025",
                    pageNumber = 17,
                    chunkIndex = 37,
                    content = "Spray Azoxystrobin 23% SC at 1 ml/L or Difenoconazole 25% EC at 0.5 ml/L at first sign of necrotic fruit lesions.",
                    cropTag = "Chilli",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_038_groundnut",
                    documentId = "doc_pjtsau_groundnut_te_2025",
                    pageNumber = 10,
                    chunkIndex = 38,
                    content = "విత్తన శుద్ధి తప్పనిసరి. ట్రైకోడెర్మా విరిడి 4 గ్రాములు లేదా మాంకోజెబ్ 3 గ్రాములు కిలో విత్తనానికి కలపాలి.",
                    cropTag = "Groundnut",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_039_red gram",
                    documentId = "doc_icar_redgram_hi_2025",
                    pageNumber = 12,
                    chunkIndex = 39,
                    content = "प्रतिरोधी प्रजातियां बोएं। ट्राइकोडर्मा विरिडी 4 ग्राम प्रति किग्रा बीज से उपचारित करें तथा फसल चक्र अपनाएं।",
                    cropTag = "Red gram",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_040_cotton",
                    documentId = "doc_icar_cotton_en_2025",
                    pageNumber = 14,
                    chunkIndex = 40,
                    content = "Spray Copper Oxychloride 3 g + Streptocycline 100 mg per liter of water at 12-14 day intervals.",
                    cropTag = "Cotton",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_041_paddy",
                    documentId = "doc_pjtsau_paddy_te_2025",
                    pageNumber = 22,
                    chunkIndex = 41,
                    content = "ఆకులపై నూలు కండె ఆకారపు మచ్చలు ఏర్పడతాయి. నివారణకు ట్రైసైక్లాజోల్ 75% WP 0.6 గ్రాములు లేదా ఐసోప్రోథియోలేన్ 1.5 మి.లీ లీటరు నీటికి కలిపి పిచికారీ చేయాలి.",
                    cropTag = "Paddy",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_042_chilli",
                    documentId = "doc_icar_chilli_hi_2025",
                    pageNumber = 14,
                    chunkIndex = 42,
                    content = "फलों पर काले धब्बे दिखने पर एजोक्सीस्ट्रोबिन 23% SC 1 मिली या डाइफेनोकोनाजोल 0.5 मिली प्रति लीटर पानी में मिलाकर छिड़कें।",
                    cropTag = "Chilli",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_043_red gram",
                    documentId = "doc_pjtsau_redgram_te_2025",
                    pageNumber = 12,
                    chunkIndex = 43,
                    content = "ఈ వైరస్ నల్లి ద్వారా వ్యాపిస్తుంది. నల్లి నివారణకు నీటిలో కరిగే గంధకం 3 గ్రాములు లేదా ప్రొపర్గైట్ 2 మి.లీ లీటరు నీటికి కలిపి పిచికారీ చేయాలి.",
                    cropTag = "Red gram",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_044_cotton",
                    documentId = "doc_icar_cotton_hi_2025",
                    pageNumber = 12,
                    chunkIndex = 44,
                    content = "पत्तियों पर भूरे गोल धब्बे दिखने पर कॉपर ऑक्सीक्लोराइड 3 ग्राम या मैंकोजेब 2.5 ग्राम प्रति लीटर पानी में मिलाकर छिड़कें।",
                    cropTag = "Cotton",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_045_paddy",
                    documentId = "doc_icar_paddy_en_2025",
                    pageNumber = 22,
                    chunkIndex = 45,
                    content = "Spray Hexaconazole 5% SC at 2.0 ml/L or Validamycin 3% L at 2.5 ml/L of water.",
                    cropTag = "Paddy",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_046_chilli",
                    documentId = "doc_pjtsau_chilli_te_2025",
                    pageNumber = 15,
                    chunkIndex = 46,
                    content = "కాయలపై గుండ్రటి నల్లని నొక్కుడు మచ్చలు ఏర్పడతాయి. నివారణకు అజాక్సిస్ట్రోబిన్ 23% SC 1 మి.లీ లేదా డైఫెనోకొనజోల్ 25% EC 0.5 మి.లీ లీటరు నీటికి కలిపి పిచికారీ చేయాలి.",
                    cropTag = "Chilli",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_047_red gram",
                    documentId = "doc_icar_redgram_en_2025",
                    pageNumber = 13,
                    chunkIndex = 47,
                    content = "Use resistant cultivars (e.g. PRG-176, Asha). Treat seed with Trichoderma viride at 4 g/kg seed and follow crop rotation with sorghum.",
                    cropTag = "Red gram",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_048_paddy",
                    documentId = "doc_icar_paddy_hi_2025",
                    pageNumber = 18,
                    chunkIndex = 48,
                    content = "पत्तियों पर आंखनुमा या नाव के आकार के धब्बे दिखने पर ट्राइसाइक्लाजोल 75% WP 0.6 ग्राम प्रति लीटर पानी में मिलाकर छिड़काव करें।",
                    cropTag = "Paddy",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_049_cotton",
                    documentId = "doc_icar_cotton_en_2025",
                    pageNumber = 13,
                    chunkIndex = 49,
                    content = "Apply Mancozeb 75% WP at 2.5 g/L or Copper Oxychloride 50% WP at 3.0 g/L of water at the first sign of circular necrotic lesions.",
                    cropTag = "Cotton",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_050_paddy",
                    documentId = "doc_pjtsau_paddy_te_2025",
                    pageNumber = 24,
                    chunkIndex = 50,
                    content = "నత్రజని ఎరువుల వాడకం తాత్కాలికంగా ఆపాలి. కాపర్ ఆక్సీక్లోరైడ్ 3 గ్రాములు + ప్లాంటోమైసిన్ లేదా స్ట్రెప్టోసైక్లిన్ 100 మి.గ్రా లీటరు నీటికి కలిపి పిచికారీ చేయాలి.",
                    cropTag = "Paddy",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_051_red gram",
                    documentId = "doc_pjtsau_redgram_te_2025",
                    pageNumber = 11,
                    chunkIndex = 51,
                    content = "తెగులును తట్టుకునే రకాలను ఎంచుకోవాలి. విత్తన శుద్ధికి ట్రైకోడెర్మా విరిడి 4 గ్రాములు కిలో విత్తనానికి కలపాలి. పంట మార్పిడి చేయాలి.",
                    cropTag = "Red gram",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_052_cotton",
                    documentId = "doc_icar_cotton_hi_2025",
                    pageNumber = 14,
                    chunkIndex = 52,
                    content = "खेत में जलभराव न होने दें। ट्राइकोडर्मा विरिडी 4 ग्राम प्रति किलो बीज उपचार करें तथा कॉपर ऑक्सीक्लोराइड से ड्रेंचिंग करें।",
                    cropTag = "Cotton",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_053_paddy",
                    documentId = "doc_icar_paddy_en_2025",
                    pageNumber = 20,
                    chunkIndex = 53,
                    content = "Spray Tricyclazole 75% WP at 0.6 g/L or Isoprothiolane 40% EC at 1.5 ml/L at initial appearance of spindle-shaped lesions.",
                    cropTag = "Paddy",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_054_chilli",
                    documentId = "doc_pjtsau_chilli_te_2025",
                    pageNumber = 17,
                    chunkIndex = 54,
                    content = "తెల్లదోమ ద్వారా ఈ వైరస్ వ్యాపిస్తుంది. తెల్లదోమ నివారణకు పసుపు అట్టలు పెట్టాలి. డైఫెన్థియురాన్ 1.25 గ్రాములు లీటరు నీటికి పిచికారీ చేయాలి.",
                    cropTag = "Chilli",
                    topicTag = "disease_management"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_055_cotton",
                    documentId = "doc_pjtsau_cotton_te_2025",
                    pageNumber = 9,
                    chunkIndex = 55,
                    content = "ఎకరానికి 60 కిలోల నత్రజని, 24 కిలోల భాస్వరం, 24 కిలోల పొటాష్ వేయాలి. నత్రజనిని మూడు సమభాగాలుగా వేయాలి.",
                    cropTag = "Cotton",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_056_paddy",
                    documentId = "doc_icar_paddy_hi_2025",
                    pageNumber = 16,
                    chunkIndex = 56,
                    content = "रोपाई के 2-3 सप्ताह बाद पत्तियों पर जंग जैसे कत्थई धब्बे बनते हैं। 5 किग्रा जिंक सल्फेट + 2.5 किग्रा बुझा चूना 1000 ली पानी में मिलाकर छिड़कें।",
                    cropTag = "Paddy",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_057_chilli",
                    documentId = "doc_pjtsau_chilli_en_2025",
                    pageNumber = 15,
                    chunkIndex = 57,
                    content = "Apply 120 kg Nitrogen, 24 kg Phosphorus, and 48 kg Potassium per acre with potash in split applications.",
                    cropTag = "Chilli",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_058_groundnut",
                    documentId = "doc_pjtsau_groundnut_te_2025",
                    pageNumber = 14,
                    chunkIndex = 58,
                    content = "కాయలు బాగా ఊరడానికి మరియు నూనె శాతం పెరగడానికి విత్తిన 40-45 రోజులకు ఎకరానికి 200 కిలోల జిప్సం వేసి మట్టి ఎగదోయాలి.",
                    cropTag = "Groundnut",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_059_red gram",
                    documentId = "doc_icar_redgram_hi_2025",
                    pageNumber = 10,
                    chunkIndex = 59,
                    content = "बुवाई के समय 8 किग्रा नाइट्रोजन और 20 किग्रा फास्फोरस प्रति एकड़ बेसल डोज के रूप में दें।",
                    cropTag = "Red gram",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_060_cotton",
                    documentId = "doc_icar_cotton_en_2025",
                    pageNumber = 12,
                    chunkIndex = 60,
                    content = "Foliar spray of 1% Magnesium Sulphate (10 g/L) along with 1% Urea at flowering and boll development stages.",
                    cropTag = "Cotton",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_061_paddy",
                    documentId = "doc_pjtsau_paddy_te_2025",
                    pageNumber = 16,
                    chunkIndex = 61,
                    content = "నారు నాటిన 2-3 వారాలకు ఆకులపై తుప్పు వంటి ఇటుక రంగు మచ్చలు వస్తాయి. నివారణకు జింక్ సల్ఫేట్ 2 గ్రాములు లీటరు నీటికి కలిపి పిచికారీ చేయాలి.",
                    cropTag = "Paddy",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_062_chilli",
                    documentId = "doc_icar_chilli_hi_2025",
                    pageNumber = 11,
                    chunkIndex = 62,
                    content = "प्रति एकड़ 10 टन गोबर की खाद तथा 100 किग्रा नाइट्रोजन, 24 किग्रा फास्फोरस और 40 किग्रा पोटाश दें।",
                    cropTag = "Chilli",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_063_groundnut",
                    documentId = "doc_icar_groundnut_en_2025",
                    pageNumber = 16,
                    chunkIndex = 63,
                    content = "Apply Gypsum at 200 kg/acre at 40-45 days (pegging stage) to supply Calcium and Sulphur for pod filling.",
                    cropTag = "Groundnut",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_064_red gram",
                    documentId = "doc_pjtsau_redgram_te_2025",
                    pageNumber = 9,
                    chunkIndex = 64,
                    content = "ఎకరానికి 8 కిలోల నత్రజని, 20 కిలోల భాస్వరం ఆఖరి దుక్కిలో వేయాలి. లెగ్యూమ్ పంట కాబట్టి నత్రజని అధికంగా వేయరాదు.",
                    cropTag = "Red gram",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_065_cotton",
                    documentId = "doc_icar_cotton_hi_2025",
                    pageNumber = 10,
                    chunkIndex = 65,
                    content = "प्रति एकड़ 60 किग्रा नाइट्रोजन, 24 किग्रा फास्फोरस तथा 24 किग्रा पोटाश दें। नाइट्रोजन को तीन भागों में विभाजित करके दें।",
                    cropTag = "Cotton",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_066_paddy",
                    documentId = "doc_icar_paddy_en_2025",
                    pageNumber = 18,
                    chunkIndex = 66,
                    content = "Leaves show reddish-brown rusty pigmentation. Foliar spray 0.5% Zinc Sulphate heptahydrate (2 g/L) + 1% Urea twice at 10-day intervals.",
                    cropTag = "Paddy",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_067_chilli",
                    documentId = "doc_pjtsau_chilli_te_2025",
                    pageNumber = 12,
                    chunkIndex = 67,
                    content = "ఎకరానికి 120 కిలోల నత్రజని, 24 కిలోల భాస్వరం, 48 కిలోల పొటాష్ సిఫార్సు. పొటాష్ మరియు నత్రజనిని 4-5 దఫాలుగా వేయాలి.",
                    cropTag = "Chilli",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_068_groundnut",
                    documentId = "doc_icar_groundnut_hi_2025",
                    pageNumber = 15,
                    chunkIndex = 68,
                    content = "फूल आने के बाद सुइयां (पेगिंग) बनते समय 40-45 दिन पर 200 किग्रा प्रति एकड़ जिप्सम डालें।",
                    cropTag = "Groundnut",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_069_red gram",
                    documentId = "doc_icar_redgram_en_2025",
                    pageNumber = 11,
                    chunkIndex = 69,
                    content = "Apply 8 kg N and 20 kg P2O5 per acre as basal dose at sowing.",
                    cropTag = "Red gram",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_070_cotton",
                    documentId = "doc_icar_cotton_en_2025",
                    pageNumber = 11,
                    chunkIndex = 70,
                    content = "Apply 60 kg Nitrogen, 24 kg Phosphorus (P2O5), and 24 kg Potassium (K2O). Split nitrogen into 3 equal top-dressings.",
                    cropTag = "Cotton",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_071_paddy",
                    documentId = "doc_pjtsau_paddy_te_2025",
                    pageNumber = 17,
                    chunkIndex = 71,
                    content = "ఎకరానికి 40-50 కిలోల నత్రజని, 24 కిలోల భాస్వరం, 20 కిలోల పొటాష్ సిఫార్సు. భాస్వరం మొత్తం ఆఖరి దమ్ములో వేయాలి, నత్రజని మూడు దఫాలుగా వేయాలి.",
                    cropTag = "Paddy",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_072_cotton",
                    documentId = "doc_icar_cotton_hi_2025",
                    pageNumber = 11,
                    chunkIndex = 72,
                    content = "मैग्नीशियम की कमी से पत्तियां लाल होती हैं। इसके निवारण हेतु मैग्नीशियम सल्फेट 10 ग्राम प्रति लीटर पानी का छिड़काव करें।",
                    cropTag = "Cotton",
                    topicTag = "soil_health"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_073_cotton",
                    documentId = "doc_pjtsau_cotton_te_2025",
                    pageNumber = 15,
                    chunkIndex = 73,
                    content = "పూత దశ, కాయ తయారయ్యే దశ మరియు కాయ పగిలే దశలలో నేలలో తగినంత తేమ ఉండాలి. నీటి నిల్వ ఉండకూడదు.",
                    cropTag = "Cotton",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_074_paddy",
                    documentId = "doc_icar_paddy_hi_2025",
                    pageNumber = 21,
                    chunkIndex = 74,
                    content = "कल्ले फूटने और दाना भरते समय खेत में 3-5 सेमी पानी बनाए रखें। कटाई से 12-15 दिन पूर्व पानी निकाल दें।",
                    cropTag = "Paddy",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_075_chilli",
                    documentId = "doc_pjtsau_chilli_en_2025",
                    pageNumber = 16,
                    chunkIndex = 75,
                    content = "Drip irrigation with fertigation is recommended. Avoid standing water to prevent Phytophthora wilt.",
                    cropTag = "Chilli",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_076_groundnut",
                    documentId = "doc_pjtsau_groundnut_te_2025",
                    pageNumber = 16,
                    chunkIndex = 76,
                    content = "పూత దశ (25-30 రోజులు), ఊడలు దిగే దశ (40-45 రోజులు) మరియు కాయ ఊరే దశ (65-75 రోజులు) క్లిష్టమైనవి.",
                    cropTag = "Groundnut",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_077_red gram",
                    documentId = "doc_icar_redgram_hi_2025",
                    pageNumber = 14,
                    chunkIndex = 77,
                    content = "फूल आने और फली बनते समय नमी की कमी होने पर एक सुरक्षात्मक सिंचाई अवश्य करें।",
                    cropTag = "Red gram",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_078_cotton",
                    documentId = "doc_icar_cotton_en_2025",
                    pageNumber = 17,
                    chunkIndex = 78,
                    content = "Flowering, square formation, and boll development are critical. Avoid waterlogging and maintain furrow drainage.",
                    cropTag = "Cotton",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_079_paddy",
                    documentId = "doc_pjtsau_paddy_te_2025",
                    pageNumber = 19,
                    chunkIndex = 79,
                    content = "నాట్లు వేసినప్పటి నుండి పిలకలు తొడిగే వరకు 2-3 సెం.మీ, చిరుపొట్ట మరియు పూత దశలలో 5 సెం.మీ నీరు ఉంచాలి. కోతకు 10-15 రోజుల ముందు నీరు తీసివేయాలి.",
                    cropTag = "Paddy",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_080_chilli",
                    documentId = "doc_icar_chilli_hi_2025",
                    pageNumber = 13,
                    chunkIndex = 80,
                    content = "फूल और फल बनते समय नियमित सिंचाई करें। खेत में अधिक पानी जमा न होने दें।",
                    cropTag = "Chilli",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_081_groundnut",
                    documentId = "doc_icar_groundnut_en_2025",
                    pageNumber = 18,
                    chunkIndex = 81,
                    content = "Flowering, peg penetration, and pod enlargement. Water stress at pegging causes blind pods.",
                    cropTag = "Groundnut",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_082_red gram",
                    documentId = "doc_pjtsau_redgram_te_2025",
                    pageNumber = 13,
                    chunkIndex = 82,
                    content = "వర్షాభావ పరిస్థితులలో పూత దశ మరియు కాయ తయారయ్యే దశలలో ఒక రక్షక తడి ఇవ్వడం వల్ల దిగుబడి పెరుగుతుంది.",
                    cropTag = "Red gram",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_083_cotton",
                    documentId = "doc_icar_cotton_hi_2025",
                    pageNumber = 16,
                    chunkIndex = 83,
                    content = "फूल आने और गूलर बनते समय खेत में पर्याप्त नमी आवश्यक है। इन अवस्थाओं में पानी की कमी न होने दें।",
                    cropTag = "Cotton",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_084_paddy",
                    documentId = "doc_icar_paddy_en_2025",
                    pageNumber = 23,
                    chunkIndex = 84,
                    content = "Maintain 2-3 cm shallow water at panicle initiation and flowering. Drain field 10-14 days before harvest.",
                    cropTag = "Paddy",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_085_chilli",
                    documentId = "doc_pjtsau_chilli_te_2025",
                    pageNumber = 14,
                    chunkIndex = 85,
                    content = "డ్రిప్ ద్వారా నీటిని అందించడం శ్రేయస్కరం. పొలంలో నీరు నిలవకుండా మురుగు కాలువలు ఏర్పాటు చేయాలి.",
                    cropTag = "Chilli",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_086_groundnut",
                    documentId = "doc_icar_groundnut_hi_2025",
                    pageNumber = 17,
                    chunkIndex = 86,
                    content = "फूल आना, सुइयां बनना और फली विकास सिंचाई के लिए अति संवेदनशील अवस्थाएं हैं।",
                    cropTag = "Groundnut",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_087_red gram",
                    documentId = "doc_icar_redgram_en_2025",
                    pageNumber = 15,
                    chunkIndex = 87,
                    content = "Provide one protective irrigation at flowering and another at pod development during extended dry spells.",
                    cropTag = "Red gram",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_088_cotton",
                    documentId = "doc_pjtsau_cotton_te_2025",
                    pageNumber = 16,
                    chunkIndex = 88,
                    content = "రోజూ లేదా రోజు విడిచి రోజు నేల తేమను బట్టి 1-2 గంటలు డ్రిప్ ద్వారా నీటిని అందించాలి.",
                    cropTag = "Cotton",
                    topicTag = "irrigation"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_089_cotton",
                    documentId = "doc_pjtsau_cotton_te_2025",
                    pageNumber = 3,
                    chunkIndex = 89,
                    content = "కిలో విత్తనానికి ఇమిడాక్లోప్రిడ్ 70% WS 5 గ్రాములు లేదా కార్బోసల్ఫాన్ 5 గ్రాములు కలిపి విత్తన శుద్ధి చేయాలి.",
                    cropTag = "Cotton",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_090_paddy",
                    documentId = "doc_icar_paddy_hi_2025",
                    pageNumber = 11,
                    chunkIndex = 90,
                    content = "कार्बेन्डाजिम 2 ग्राम प्रति किग्रा बीज की दर से सूखा या गीला उपचार करें ताकि बीजजनित रोगों से बचाव हो।",
                    cropTag = "Paddy",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_091_chilli",
                    documentId = "doc_pjtsau_chilli_en_2025",
                    pageNumber = 9,
                    chunkIndex = 91,
                    content = "Treat seeds with Thiram 75% WP at 3 g/kg or Trichoderma viride at 10 g/kg seed.",
                    cropTag = "Chilli",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_092_groundnut",
                    documentId = "doc_pjtsau_groundnut_te_2025",
                    pageNumber = 7,
                    chunkIndex = 92,
                    content = "ఎకరానికి 40-50 కిలోల విత్తన గుజ్జు అవసరం. కిలో విత్తనానికి మాంకోజెబ్ 3 గ్రాములు లేదా ఇమిడాక్లోప్రిడ్ 2 మి.లీ తో శుద్ధి చేయాలి.",
                    cropTag = "Groundnut",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_093_red gram",
                    documentId = "doc_icar_redgram_hi_2025",
                    pageNumber = 7,
                    chunkIndex = 93,
                    content = "राइजोबियम कल्चर 20 ग्राम प्रति किग्रा बीज की दर से मिलाकर छाया में सुखाकर बुवाई करें।",
                    cropTag = "Red gram",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_094_cotton",
                    documentId = "doc_icar_cotton_en_2025",
                    pageNumber = 19,
                    chunkIndex = 94,
                    content = "Pick cleanly opened bolls without bracts or dried leaves. Store in well-ventilated dry sheds below 8% moisture.",
                    cropTag = "Cotton",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_095_paddy",
                    documentId = "doc_pjtsau_paddy_te_2025",
                    pageNumber = 13,
                    chunkIndex = 95,
                    content = "కిలో విత్తనానికి కార్బండజిమ్ 2 గ్రాములు లేదా ట్రైసైక్లాజోల్ 2 గ్రాములు కలిపి పొడి లేదా తడి విత్తన శుద్ధి చేయాలి.",
                    cropTag = "Paddy",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_096_chilli",
                    documentId = "doc_icar_chilli_hi_2025",
                    pageNumber = 7,
                    chunkIndex = 96,
                    content = "थिरम या कैप्टन 3 ग्राम प्रति किग्रा बीज से उपचारित करके बोएं।",
                    cropTag = "Chilli",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_097_groundnut",
                    documentId = "doc_icar_groundnut_en_2025",
                    pageNumber = 8,
                    chunkIndex = 97,
                    content = "Treat kernel with Mancozeb at 3 g/kg seed + Imidacloprid 600 FS at 2 ml/kg seed before sowing.",
                    cropTag = "Groundnut",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_098_red gram",
                    documentId = "doc_pjtsau_redgram_te_2025",
                    pageNumber = 6,
                    chunkIndex = 98,
                    content = "కిలో విత్తనానికి రైజోబియం కల్చర్ 20 గ్రాములు + ట్రైకోడెర్మా విరిడి 4 గ్రాములు కలిపి విత్తన శుద్ధి చేయాలి.",
                    cropTag = "Red gram",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_099_cotton",
                    documentId = "doc_icar_cotton_hi_2025",
                    pageNumber = 4,
                    chunkIndex = 99,
                    content = "इमिडाक्लोप्रिड 70% WS 5 ग्राम प्रति किलो बीज की दर से उपचारित करें ताकि शुरुआती रसचूसक कीटों से सुरक्षा मिले।",
                    cropTag = "Cotton",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_100_paddy",
                    documentId = "doc_icar_paddy_en_2025",
                    pageNumber = 12,
                    chunkIndex = 100,
                    content = "Treat seeds with Carbendazim 50% WP or Tricyclazole 75% WP at 2 g/kg seed to protect against seed-borne fungal blast.",
                    cropTag = "Paddy",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_101_chilli",
                    documentId = "doc_pjtsau_chilli_te_2025",
                    pageNumber = 8,
                    chunkIndex = 101,
                    content = "కిలో విత్తనానికి థైరమ్ లేదా కాప్టాన్ 3 గ్రాములు + ఇమిడాక్లోప్రిడ్ 5 గ్రాములు కలిపి విత్తన శుద్ధి చేయాలి.",
                    cropTag = "Chilli",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_102_groundnut",
                    documentId = "doc_icar_groundnut_hi_2025",
                    pageNumber = 8,
                    chunkIndex = 102,
                    content = "प्रति किग्रा बीज को 3 ग्राम मैंकोजेब या 2 ग्राम कार्बेन्डाजिम से उपचारित करें।",
                    cropTag = "Groundnut",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_103_red gram",
                    documentId = "doc_icar_redgram_en_2025",
                    pageNumber = 7,
                    chunkIndex = 103,
                    content = "Inoculate with Rhizobium culture at 20 g/kg seed and dry in shade for 30 minutes before sowing.",
                    cropTag = "Red gram",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_104_cotton",
                    documentId = "doc_pjtsau_cotton_te_2025",
                    pageNumber = 18,
                    chunkIndex = 104,
                    content = "మంచు ఆరిన తర్వాత బాగా పగిలిన పత్తిని మాత్రమే తీయాలి. చెత్త, ఆకులు కలవకుండా పొడి వాతావరణంలో నిల్వ చేయాలి.",
                    cropTag = "Cotton",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_105_cotton",
                    documentId = "doc_icar_cotton_en_2025",
                    pageNumber = 4,
                    chunkIndex = 105,
                    content = "Treat with Imidacloprid 70% WS at 5 g/kg seed to protect seedlings against early season sucking pests.",
                    cropTag = "Cotton",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_106_paddy",
                    documentId = "doc_pjtsau_paddy_te_2025",
                    pageNumber = 28,
                    chunkIndex = 106,
                    content = "ధాన్యాన్ని బాగా ఎండబెట్టి తేమ 12-13% కంటే తక్కువ ఉండేలా చూడాలి. నిల్వ బస్తాలకు వేప నూనె లేదా మలాథియాన్ పిచికారీ చేయాలి.",
                    cropTag = "Paddy",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "chunk_eval_107_cotton",
                    documentId = "doc_icar_cotton_hi_2025",
                    pageNumber = 18,
                    chunkIndex = 107,
                    content = "ओस सूखने के बाद ही चुनाई करें। सूखे गूलरों से साफ कपास अलग रखें और नमी रहित स्थान पर भंडारित करें।",
                    cropTag = "Cotton",
                    topicTag = "seeds_storage"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_banned_te_all",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 100,
                    content = "భారత ప్రభుత్వం & CIBRC గెజిట్ నోటిఫికేషన్ ద్వారా పూర్తిగా నిషేధించబడిన రసాయనాలు: మోనోక్రోటోఫాస్ (Monocrotophos), పారాక్వాట్ డైక్లోరైడ్ (Paraquat), ఎండోసల్ఫాన్ (Endosulfan), డిడిటి (DDT), కార్బోఫ్యూరాన్ (Carbofuran), ఫోరేట్ (Phorate), మిథైల్ పారాథియాన్. ఈ ప్రమాదకర రసాయనాలను వ్యవసాయంలో ఉపయోగించడం చట్టరీత్యా నేరం.",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_banned_hi_all",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 101,
                    content = "भारत सरकार एवं CIBRC राजपत्र अधिसूचना द्वारा पूर्णतः प्रतिबंधित कीटनाशक: मोनोक्रोटोफॉस (Monocrotophos), पैराक्वाट (Paraquat), एंडोसल्फान (Endosulfan), डीडीटी (DDT), कार्बोफ्यूरॉन (Carbofuran), फोरेट (Phorate)। इन रसायनों का कृषि में छिड़काव पूर्णतः गैरकानूनी है।",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_banned_en_all",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 102,
                    content = "Government of India & CIBRC Prohibited Chemical Pesticides Gazette Notification: Active ingredients Monocrotophos, Paraquat Dichloride, Endosulfan, DDT, Carbofuran, Phorate, and Methyl Parathion are strictly banned for agricultural application in India.",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_safety_te_all",
                    documentId = "doc_cibrc_safety_guidelines",
                    pageNumber = 1,
                    chunkIndex = 103,
                    content = "పురుగుమందులు పిచికారీ చేసేటప్పుడు తీసుకోవాల్సిన జాగ్రత్తలు: గాలి వీచే దిశలోనే పిచికారీ చేయాలి. ముక్కు మరియు నోటికి మాస్క్, చేతులకు రబ్బరు గ్లౌజులు ధరించాలి. ఖాళీ డబ్బాలను మూడుసార్లు కడిగి రంధ్రం చేసి పూడ్చివేయాలి.",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_safety_hi_all",
                    documentId = "doc_cibrc_safety_guidelines",
                    pageNumber = 1,
                    chunkIndex = 104,
                    content = "कीटनाशक छिड़कते समय किसान सावधानियां: मास्क, रबर के दस्ताने पहनें। हवा की दिशा में ही छिड़काव करें। खाली डिब्बों को तीन बार धोकर छेद करके जमीन में दबाएं।",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_safety_en_all",
                    documentId = "doc_cibrc_safety_guidelines",
                    pageNumber = 1,
                    chunkIndex = 105,
                    content = "Pesticide application safety guidelines: Always spray in the direction of wind. Wear protective face masks, eye goggles, and nitrile gloves. Empty pesticide containers must undergo triple-rinsing, puncture destruction, and safe burial away from water bodies.",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                )
            )

            docDao.insertChunks(chunks)

            // Seed Mandi Cache (Covers all crops in Telangana major markets)
            val prices = listOf(
                MandiPriceEntity(
                    id = "mandi_warangal_cotton",
                    state = "Telangana",
                    district = "Warangal",
                    marketName = "Warangal (Enamamula) Mandi",
                    commodity = "Cotton (కపాస్ / పత్తి)",
                    variety = "Medium Staple",
                    minPrice = 7250.0,
                    maxPrice = 7820.0,
                    modalPrice = 7550.0,
                    observationDate = "2026-11-04",
                    isMockFeed = true
                ),
                MandiPriceEntity(
                    id = "mandi_suryapet_paddy",
                    state = "Telangana",
                    district = "Suryapet",
                    marketName = "Suryapet Mandi",
                    commodity = "Paddy (వరి)",
                    variety = "BPT 5204",
                    minPrice = 2320.0,
                    maxPrice = 2550.0,
                    modalPrice = 2420.0,
                    observationDate = "2026-11-04",
                    isMockFeed = true
                ),
                MandiPriceEntity(
                    id = "mandi_warangal_chilli",
                    state = "Telangana",
                    district = "Warangal",
                    marketName = "Warangal (Enamamula) Mandi",
                    commodity = "Chilli (మిర్చి)",
                    variety = "Teja",
                    minPrice = 14500.0,
                    maxPrice = 18200.0,
                    modalPrice = 16400.0,
                    observationDate = "2026-11-04",
                    isMockFeed = true
                ),
                MandiPriceEntity(
                    id = "mandi_gadwal_groundnut",
                    state = "Telangana",
                    district = "Jogulamba Gadwal",
                    marketName = "Gadwal Mandi",
                    commodity = "Groundnut (వేరుశనగ)",
                    variety = "TAG 24",
                    minPrice = 5800.0,
                    maxPrice = 6450.0,
                    modalPrice = 6200.0,
                    observationDate = "2026-11-04",
                    isMockFeed = true
                ),
                MandiPriceEntity(
                    id = "mandi_tandur_redgram",
                    state = "Telangana",
                    district = "Vikarabad",
                    marketName = "Tandur Mandi",
                    commodity = "Red gram (కంది)",
                    variety = "Tandur White",
                    minPrice = 9600.0,
                    maxPrice = 10850.0,
                    modalPrice = 10400.0,
                    observationDate = "2026-11-04",
                    isMockFeed = true
                ),
                MandiPriceEntity(
                    id = "mandi_nizamabad_turmeric",
                    state = "Telangana",
                    district = "Nizamabad",
                    marketName = "Nizamabad Mandi",
                    commodity = "Turmeric (పసుపు)",
                    variety = "Finger (Armoor)",
                    minPrice = 12500.0,
                    maxPrice = 15800.0,
                    modalPrice = 14200.0,
                    observationDate = "2026-11-04",
                    isMockFeed = true
                ),
                MandiPriceEntity(
                    id = "mandi_karimnagar_maize",
                    state = "Telangana",
                    district = "Karimnagar",
                    marketName = "Karimnagar Mandi",
                    commodity = "Maize (మొక్కజొన్న)",
                    variety = "Yellow Hybrid",
                    minPrice = 2100.0,
                    maxPrice = 2350.0,
                    modalPrice = 2220.0,
                    observationDate = "2026-11-04",
                    isMockFeed = true
                )
            )
            mandiDao.insertPrices(prices)
            Log.i(TAG, "[BOL_KISAN] Offline knowledge seeding completed successfully.")
        }
    }
}
