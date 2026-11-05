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
    version = 3,
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
                                if (database.documentDao().getDocumentCount() < 10) {
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
                // ------------------ COTTON ------------------
                DocumentChunkEntity(
                    id = "ch_cotton_pbw_te_01",
                    documentId = "doc_pjtsau_cotton_te_2025",
                    pageNumber = 4,
                    chunkIndex = 0,
                    content = "పత్తిలో గులాబీ రంగు పురుగు నివారణ మరియు యాజమాన్యం (Cotton Pink Bollworm Control): పూత మరియు కాయ దశలో ఎకరానికి 8 లింగాకర్షక బుట్టలను అమర్చాలి. నివారణకు: వేప నూనె 10,000 PPM ఎకరానికి 400-500 మి.లీ 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. పురుగు ఉధృతిని బట్టి పిచికారీ చేయాలి. పత్తి పంటలో గులాబీ రంగు పురుగు సమస్యలు సమర్థవంతంగా అదుపులోకి వస్తాయి.",
                    cropTag = "Cotton",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "ch_cotton_yellow_te_02",
                    documentId = "doc_pjtsau_cotton_te_2025",
                    pageNumber = 7,
                    chunkIndex = 1,
                    content = "పత్తిలో ఆకులు పసుపు రంగులోకి మారుతున్నాయి (ఆకులు పసుపుబారడం మరియు రసం పీల్చే పురుగుల నివారణ): తెల్లదోమ మరియు జెస్సిడ్స్ ఆకుల రసాన్ని పీల్చడం వల్ల ఆకులు, ఆకుల అంచులు పసుపు రంగులోకి మారుతాయి / మారుతున్నాయి. నివారణకు పసుపు మరియు నీలి రంగు జిగురు అట్టలను ఎకరానికి 10 చొప్పున అమర్చాలి. వేపనూనె 5 మి.లీ లీటరు నీటికి కలిపి పిచికారీ చేయాలి. నత్రజని అధికంగా వేయవద్దు.",
                    cropTag = "Cotton",
                    topicTag = "symptom_yellowing"
                ),
                DocumentChunkEntity(
                    id = "ch_cotton_pbw_hi_01",
                    documentId = "doc_icar_cotton_hi_2025",
                    pageNumber = 5,
                    chunkIndex = 0,
                    content = "कपास की फसल में मुख्य समस्याएं एवं गुलाबी सुंडी (Pink Bollworm) का नियंत्रण: फूल और गूलर बनने की अवस्था में प्रति एकड़ 8 फेरोमोन ट्रैप लगाएं। नियंत्रण के लिए नीम का तेल (Neem oil 10000 PPM) 400-500 मिली प्रति एकड़ 200 लीटर पानी में मिलाकर छिड़काव करें। लगातार 3 दिन तक 8 पतंगे मिलने पर तुरंत उपचार करें।",
                    cropTag = "Cotton",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "ch_cotton_pbw_en_01",
                    documentId = "doc_icar_cotton_en_2025",
                    pageNumber = 6,
                    chunkIndex = 0,
                    content = "Cotton Pink Bollworm management and prevention: Install 8 pheromone traps per acre during flowering and boll formation stage. Economic Threshold Level (ETL) is 8 moths per trap per day for 3 consecutive days. For control, spray Neem oil 10,000 PPM at 400-500 ml per acre in 200 liters of water or Chlorantraniliprole 18.5% SC at 60 ml per acre.",
                    cropTag = "Cotton",
                    topicTag = "pest_management"
                ),

                // ------------------ PADDY ------------------
                DocumentChunkEntity(
                    id = "ch_paddy_stem_te_01",
                    documentId = "doc_pjtsau_paddy_te_2025",
                    pageNumber = 14,
                    chunkIndex = 0,
                    content = "వరిలో కాండం తొలుచు పురుగు (Stem Borer) మరియు చనిపోయిన మొవ్వులు (Dead hearts) నివారణ: సిఫార్సు చేసిన క్లోరాంట్రానిలీప్రోల్ (Chlorantraniliprole 18.5% SC) 60 మి.లీ ఎకరాకు 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. ఎకరానికి 5 లింగాకర్షక బుట్టలను అమర్చాలి.",
                    cropTag = "Paddy",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "ch_paddy_yellow_te_02",
                    documentId = "doc_pjtsau_paddy_te_2025",
                    pageNumber = 16,
                    chunkIndex = 1,
                    content = "వరిలో ఆకులు పసుపు రంగులోకి మారుతున్నాయి (వరి ఆకులు పసుపుబారడం మరియు జింక్ లోపం నివారణ): వరి పైరులో జింక్ లేదా ఇనుము లోపం వల్ల లేదా కాండం తొలుచు పురుగు ఉధృతి వల్ల ఆకులు పసుపు రంగులోకి మారుతాయి. నివారణకు జింక్ సల్ఫేట్ 2 గ్రాములు లీటరు నీటికి కలిపి పిచికారీ చేయాలి.",
                    cropTag = "Paddy",
                    topicTag = "symptom_yellowing"
                ),
                DocumentChunkEntity(
                    id = "ch_paddy_stem_hi_01",
                    documentId = "doc_icar_paddy_hi_2025",
                    pageNumber = 12,
                    chunkIndex = 0,
                    content = "धान में तना छेदक (Stem Borer) एवं मृत गोभ (Dead heart) की रोकथाम: फसल में लक्षण दिखने पर क्लोरांट्रानिलीप्रोल (Chlorantraniliprole 18.5% SC) 60 मिली प्रति एकड़ 200 लीटर पानी में मिलाकर छिड़काव करें। 5 फेरोमोन ट्रैप प्रति एकड़ लगाएं।",
                    cropTag = "Paddy",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "ch_paddy_stem_en_01",
                    documentId = "doc_icar_paddy_en_2025",
                    pageNumber = 14,
                    chunkIndex = 0,
                    content = "Paddy rice stem borer and dead heart control: Upon noticing dead hearts during vegetative stage or white ears at heading, apply Chlorantraniliprole 18.5% SC at 60 ml per acre mixed in 200 liters of water. Install 5 pheromone traps per acre to monitor yellow stem borer adult activity.",
                    cropTag = "Paddy",
                    topicTag = "pest_management"
                ),

                // ------------------ CHILLI ------------------
                DocumentChunkEntity(
                    id = "ch_chilli_thrips_te_01",
                    documentId = "doc_pjtsau_chilli_te_2025",
                    pageNumber = 9,
                    chunkIndex = 0,
                    content = "మిరపలో తామర పురుగులు, నల్లి మరియు ఆకుముడుత నివారణ (Chilli Thrips & Mite Control): తామర పురుగుల వల్ల ఆకులు పైకి, నల్లి వల్ల ఆకులు క్రిందికి ముడుచుకుంటాయి. నివారణకు వేపనూనె 10,000 PPM ఎకరానికి 400-500 మి.లీ లేదా ఎకరాకు 80-100 గ్రాముల ఎమామెక్టిన్ బెంజోయేట్ 5% SG 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. ఎకరానికి 15-20 నీలి మరియు పసుపు జిగురు అట్టలను అమర్చాలి.",
                    cropTag = "Chilli",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "ch_chilli_thrips_en_01",
                    documentId = "doc_pjtsau_chilli_en_2025",
                    pageNumber = 11,
                    chunkIndex = 0,
                    content = "Chilli thrips, yellow mites, and leaf curl management: Upward curling is caused by thrips, while downward curling indicates yellow mites. Spray Neem oil 10,000 PPM at 2-3 ml per liter of water, or Emamectin Benzoate 5% SG at 80-100 grams per acre in 200 liters of water. Install 15-20 blue and yellow sticky traps per acre.",
                    cropTag = "Chilli",
                    topicTag = "pest_management"
                ),

                // ------------------ GROUNDNUT ------------------
                DocumentChunkEntity(
                    id = "ch_groundnut_tikka_te_01",
                    documentId = "doc_pjtsau_groundnut_te_2025",
                    pageNumber = 8,
                    chunkIndex = 0,
                    content = "వేరుశనగలో టిక్కా ఆకుమచ్చ తెగులు నివారణ (Groundnut Tikka Leaf Spot Control): ఆకులపై గోధుమ రంగు గుండ్రటి మచ్చలు ఏర్పడి ఆకులు రాలిపోతాయి. నివారణకు మాంకోజెబ్ (Mancozeb 75% WP) 2-2.5 గ్రాములు లేదా టెబుకొనజోల్ 1 మి.లీ లీటరు నీటికి కలిపి 10-12 రోజుల వ్యవధిలో రెండుసార్లు పిచికారీ చేయాలి. విత్తన శుద్ధి తప్పనిసరి.",
                    cropTag = "Groundnut",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "ch_groundnut_tikka_en_01",
                    documentId = "doc_icar_groundnut_en_2025",
                    pageNumber = 10,
                    chunkIndex = 0,
                    content = "Groundnut Tikka leaf spot (Cercospora leaf spot) and collar rot control: Early and late leaf spots cause circular dark brown necrotic spots leading to severe defoliation. Spray Mancozeb 75% WP at 2.5 g/L or Tebuconazole 25.9% EC at 1 ml/L of water at 12-14 day intervals.",
                    cropTag = "Groundnut",
                    topicTag = "pest_management"
                ),

                // ------------------ RED GRAM ------------------
                DocumentChunkEntity(
                    id = "ch_redgram_podborer_te_01",
                    documentId = "doc_pjtsau_redgram_te_2025",
                    pageNumber = 7,
                    chunkIndex = 0,
                    content = "కందిలో కాయతొలుచు పురుగు / శనగపచ్చ పురుగు నివారణ (Red gram / Pigeonpea Pod Borer Control): పూత మరియు పిందె దశలో హెలికోవెర్పా లార్వా పూతను, కాయలను తొలచి తింటుంది. నివారణకు ఎకరాకు 4 లింగాకర్షక బుట్టలు, 10-15 పక్షి స్థావరాలను అమర్చాలి. పిచికారీకి: ఎమామెక్టిన్ బెంజోయేట్ 5% SG ఎకరాకు 88-100 గ్రాములు లేదా క్లోరాంట్రానిలీప్రోల్ 18.5% SC 60 మి.లీ 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి.",
                    cropTag = "Red gram",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "ch_redgram_podborer_en_01",
                    documentId = "doc_icar_redgram_en_2025",
                    pageNumber = 9,
                    chunkIndex = 0,
                    content = "Red gram (Pigeonpea) pod borer Helicoverpa armigera management: Caterpillars feed on flowers, buds, and bore circular holes into developing pods. Install 4 pheromone traps and 10-15 bird perches per acre. Spray Emamectin Benzoate 5% SG at 88-100 g/acre or Chlorantraniliprole 18.5% SC at 60 ml per acre in 200 liters of water at flower bud initiation.",
                    cropTag = "Red gram",
                    topicTag = "pest_management"
                ),

                // ------------------ MAIZE ------------------
                DocumentChunkEntity(
                    id = "ch_maize_faw_te_01",
                    documentId = "doc_pjtsau_maize_te_2025",
                    pageNumber = 6,
                    chunkIndex = 0,
                    content = "మొక్కజొన్నలో కత్తెర పురుగు నివారణ (Maize Fall Armyworm - Spodoptera frugiperda Control): మొలక దశ నుండి కత్తెర పురుగు ఆకులను, సుడులను కొరికి నష్టపరుస్తుంది. నివారణకు: విత్తన శుద్ధికి సయాంట్రానిలీప్రోల్ 19.8% + థయామిథాక్సమ్ 19.8% FS 6 మి.లీ కిలో విత్తనానికి కలపాలి. పంట తొలిదశలో ఎకరాకు 4 లింగాకర్షక బుట్టలు అమర్చాలి. పిచికారీకి: ఎమామెక్టిన్ బెంజోయేట్ 5% SG 80 గ్రాములు లేదా క్లోరాంట్రానిలీప్రోల్ 18.5% SC 80 మి.లీ ఎకరాకు 200 లీటర్ల నీటిలో కలిపి సుడులలో పడేలా పిచికారీ చేయాలి.",
                    cropTag = "Maize",
                    topicTag = "pest_management"
                ),
                DocumentChunkEntity(
                    id = "ch_maize_faw_en_01",
                    documentId = "doc_icar_maize_en_2025",
                    pageNumber = 5,
                    chunkIndex = 0,
                    content = "Maize Fall Armyworm (FAW - Spodoptera frugiperda) Integrated Management: Larvae cause extensive defoliation and bore into plant whorls. Practice seed treatment with Cyantraniliprole 19.8% + Thiamethoxam 19.8% FS at 6 ml/kg seed. Install 4 pheromone traps per acre. For chemical control, direct spray into plant whorls using Emamectin Benzoate 5% SG at 80 g/acre or Chlorantraniliprole 18.5% SC at 80 ml/acre in 200 liters of water.",
                    cropTag = "Maize",
                    topicTag = "pest_management"
                ),

                // ------------------ SOYBEAN ------------------
                DocumentChunkEntity(
                    id = "ch_soybean_girdle_te_01",
                    documentId = "doc_pjtsau_soybean_te_2025",
                    pageNumber = 7,
                    chunkIndex = 0,
                    content = "సోయాబీన్‌లో రింగ్ కట్టర్ / గిర్డిల్ బీటిల్ (Girdle Beetle) మరియు పొగాకు లద్దెపురుగు నివారణ: గిర్డిల్ బీటిల్ కాండం చుట్టూ రెండు రింగుల వంటి గాట్లు పెట్టి గుడ్లు పెడుతుంది, దాంతో ఆకులు, కొమ్మలు వాడిపోతాయి. నివారణకు: క్లోరాంట్రానిలీప్రోల్ 18.5% SC 60 మి.లీ లేదా థయామిథాక్సమ్ 12.6% + లాంబ్డా సైహలోథ్రిన్ 9.5% ZC 50 మి.లీ ఎకరాకు 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి.",
                    cropTag = "Soybean",
                    topicTag = "pest_management"
                ),

                // ------------------ TURMERIC ------------------
                DocumentChunkEntity(
                    id = "ch_turmeric_rot_te_01",
                    documentId = "doc_pjtsau_turmeric_te_2025",
                    pageNumber = 9,
                    chunkIndex = 0,
                    content = "పసుపులో దుంప కుళ్లు తెగులు (Turmeric Rhizome Rot - Pythium aphanidermatum) నివారణ: మురుగు నీరు నిలవడం వల్ల దుంపలు కుళ్ళిపోయి మొక్కలు పసుపుబారి చనిపోతాయి. నివారణకు: విత్తే ముందు విత్తన దుంపలను మాంకోజెబ్ 3 గ్రాములు + మెటలాక్సిల్ 2 గ్రాములు లీటరు నీటిలో కలిపి 30 నిమిషాలు నానబెట్టాలి. పొలంలో తెగులు కనిపించినప్పుడు కాపర్ ఆక్సీక్లోరైడ్ 3 గ్రాములు లీటరు నీటికి కలిపి దుంపల వద్ద నేల బాగా తడిసేలా డ్రెంచింగ్ చేయాలి.",
                    cropTag = "Turmeric",
                    topicTag = "disease_management"
                ),

                // ------------------ VEGETABLES ------------------
                DocumentChunkEntity(
                    id = "ch_veg_brinjal_te_01",
                    documentId = "doc_iihr_vegetables_te_2025",
                    pageNumber = 16,
                    chunkIndex = 0,
                    content = "వంగలో కాండం మరియు కాయ తొలుచు పురుగు (Brinjal Shoot & Fruit Borer - Leucinodes orbonalis): మొవ్వులు వాడిపోవడం మరియు కాయలపై రంధ్రాలు ఏర్పడతాయి. నివారణకు: విరిగిపోయిన మొవ్వులను తుంచి నాశనం చేయాలి. ఎకరానికి 10 లూసిల్యూర్ లింగాకర్షక బుట్టలు అమర్చాలి. పిచికారీకి: ఎమామెక్టిన్ బెంజోయేట్ 5% SG 80 గ్రాములు లేదా స్పినోసాడ్ 45% SC 60-70 మి.లీ ఎకరాకు 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి.",
                    cropTag = "Vegetables",
                    topicTag = "pest_management"
                ),

                // ------------------ UNIVERSAL PRACTICES & SCHEMES ------------------
                DocumentChunkEntity(
                    id = "ch_soil_sampling_te_01",
                    documentId = "doc_pjtsau_soil_health_te_2025",
                    pageNumber = 5,
                    chunkIndex = 0,
                    content = "నేల నమూనా సేకరించే సరైన పద్ధతి (Scientific Soil Sampling Methodology): పంట కోత తర్వాత లేదా వేసవిలో దుక్కులు దున్నకముందు నేల నమూనా సేకరించాలి. పొలంలో 'V' ఆకారంలో 15 సెం.మీ (వరి, పత్తికి) గుంత తీసి పైనుండి కింది వరకు ఒక పలుచటి మట్టి పొరను సేకరించాలి. పొలంలో 10-12 చోట్ల మట్టిని సేకరించి బాగా కలిపి నాలుగు భాగాలుగా విభజించి అర కిలో నమూనాను ల్యాబ్‌కు పంపాలి.",
                    cropTag = "Multi",
                    topicTag = "soil_testing"
                ),
                DocumentChunkEntity(
                    id = "ch_weed_safety_en_01",
                    documentId = "doc_icar_weed_mgmt_en_2025",
                    pageNumber = 6,
                    chunkIndex = 0,
                    content = "Integrated Weed Management & Herbicide Safety Rules: Apply pre-emergence herbicides (such as Pendimethalin 30% EC at 1.0-1.3 L/acre) within 48-72 hours of sowing under adequate soil moisture. Use flat-fan or flood-jet nozzles for uniform ground coverage. Avoid spraying in windy weather to prevent herbicide drift onto adjacent sensitive broadleaf crops.",
                    cropTag = "Multi",
                    topicTag = "weed_management"
                ),
                DocumentChunkEntity(
                    id = "ch_weather_drainage_te_01",
                    documentId = "doc_imd_agromet_te_2025",
                    pageNumber = 4,
                    chunkIndex = 0,
                    content = "భారీ వర్షాలు మరియు మురుగునీటి యాజమాన్యం: వాతావరణ శాఖ భారీ వర్ష సూచన ఇచ్చినప్పుడు పత్తి, మిరప, మొక్కజొన్న మరియు సోయాబీన్ పంటలలో వెంటనే మురుగునీటి కాల్వలను శుభ్రం చేసి నీరు బయటకు పోయేలా చూడాలి. వేర్ల వద్ద నీరు 24 గంటల కంటే ఎక్కువ నిలిస్తే వేరుకుళ్లు తెగులు వ్యాపిస్తుంది. వర్షం తగ్గిన తర్వాత ఎకరానికి 20-25 కిలోల యూరియా + 10 కిలోల పొటాష్ వేయాలి.",
                    cropTag = "Multi",
                    topicTag = "weather_advisory"
                ),
                DocumentChunkEntity(
                    id = "ch_govt_schemes_te_01",
                    documentId = "doc_govt_schemes_te_2025",
                    pageNumber = 3,
                    chunkIndex = 0,
                    content = "రైతు భరోసా పథకం మరియు PMFBY పంట బీమా: తెలంగాణ రాష్ట్ర ప్రభుత్వ నిబంధనల ప్రకారం పట్టాదారు పాస్ పుస్తకం కలిగిన అర్హులైన ప్రతి రైతుకు పంట పెట్టుబడి సహాయం జమ చేయబడుతుంది. ప్రకృతి విపత్తుల వల్ల పంట నష్టపోతే PMFBY బీమా పరిహారం కోసం 72 గంటలలోపు టోల్ ఫ్రీ నంబర్ 14447 లేదా క్రాప్ ఇన్సూరెన్స్ యాప్ ద్వారా వివరాలు నమోదు చేయాలి.",
                    cropTag = "Multi",
                    topicTag = "government_schemes"
                ),

                // ------------------ CHEMICAL SAFETY & BANNED LIST ------------------
                DocumentChunkEntity(
                    id = "ch_cibrc_banned_te_01",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 0,
                    content = "భారత ప్రభుత్వం & CIBRC గెజిట్ నోటిఫికేషన్ ద్వారా పూర్తిగా నిషేధించబడిన రసాయనాలు: మోనోక్రోటోఫాస్ (Monocrotophos), పారాక్వాట్ డైక్లోరైడ్ (Paraquat), ఎండోసల్ఫాన్ (Endosulfan), డిడిటి (DDT), కార్బోఫ్యూరాన్ (Carbofuran), ఫోరేట్ (Phorate), మిథైల్ పారాథియాన్. ఈ ప్రమాదకర రసాయనాలను వ్యవసాయంలో ఉపయోగించడం చట్టరీత్యా నేరం మరియు పంటలకు, ప్రజలకు ప్రాణాంతక ప్రమాదం.",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_banned_hi_02",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 1,
                    content = "भारत सरकार एवं CIBRC राजपत्र अधिसूचना द्वारा पूर्णतः प्रतिबंधित कीटनाशक: मोनोक्रोटोफॉस (Monocrotophos), पैराक्वाट (Paraquat), एंडोसल्फान (Endosulfan), डीडीटी (DDT), कार्बोफ्यूरॉन (Carbofuran), फोरेट (Phorate)। इन रसायनों का कृषि में छिड़काव पूर्णतः गैरकानूनी एवं विषैला है।",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_banned_en_03",
                    documentId = "doc_cibrc_banned_list_2025",
                    pageNumber = 1,
                    chunkIndex = 2,
                    content = "Government of India & CIBRC Prohibited Chemical Pesticides Gazette Notification: Active ingredients Monocrotophos, Paraquat Dichloride, Endosulfan, DDT, Carbofuran, Phorate, and Methyl Parathion are strictly banned for manufacture, import, sale, and agricultural application in India. Unauthorized usage is a cognizable legal offense under the Insecticides Act.",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_safe_01",
                    documentId = "doc_cibrc_safety_guidelines",
                    pageNumber = 1,
                    chunkIndex = 0,
                    content = "పురుగుమందులు పిచికారీ చేసేటప్పుడు తీసుకోవాల్సిన జాగ్రత్తలు: గాలి వీచే దిశలోనే పిచికారీ చేయాలి. ముక్కు మరియు నోటికి మాస్క్, చేతులకు రబ్బరు గ్లౌజులు ధరించాలి. ఖాళీ డబ్బాలను మూడుసార్లు కడిగి రంధ్రం చేసి పూడ్చివేయాలి. నిరీక్షణ కాలాన్ని (PHI) ఖచ్చితంగా పాటించాలి.",
                    cropTag = "Multi",
                    topicTag = "chemical_safety"
                ),
                DocumentChunkEntity(
                    id = "ch_cibrc_safe_en_02",
                    documentId = "doc_cibrc_safety_guidelines",
                    pageNumber = 1,
                    chunkIndex = 1,
                    content = "Pesticide application safety guidelines and mandatory precautions: Always spray in the direction of wind. Wear protective face masks, eye goggles, and nitrile rubber gloves. Empty pesticide containers must undergo triple-rinsing, puncture destruction, and safe burial away from water bodies. Adhere strictly to the recommended Pre-Harvest Interval (PHI).",
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
