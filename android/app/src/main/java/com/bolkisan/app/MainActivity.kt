package com.bolkisan.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bolkisan.app.data.AppMode
import com.bolkisan.app.data.ModePreferences
import com.bolkisan.app.data.local.AppDatabase
import com.bolkisan.app.data.local.entities.InteractionEntity
import com.bolkisan.app.data.packs.KnowledgePackManager
import com.bolkisan.app.pipeline.*
import com.bolkisan.app.ui.components.VoiceState
import com.bolkisan.app.ui.screens.*
import com.bolkisan.app.ui.theme.BolKisanTheme
import com.bolkisan.app.util.NetworkMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val TAG = "BolKisanMain"

    private lateinit var database: AppDatabase
    private lateinit var packManager: KnowledgePackManager
    private lateinit var audioManager: AudioRecordingManager
    private lateinit var asrEngine: OfflineSpeechRecognitionEngine
    private lateinit var retrievalEngine: LocalKnowledgeRetrievalEngine
    private lateinit var ttsEngine: OfflineTextToSpeechEngine
    private lateinit var visionEngine: OfflineAgriculturalVisionEngine
    private lateinit var modePreferences: ModePreferences
    private val onlineApiClient = OnlineAdvisoryApiClient()

    private var hasAudioPermission by mutableStateOf(false)
    private var recordingJob: Job? = null
    private var testQueryHandler: ((String?, String?, String, String?) -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            Log.i(TAG, "[BOL_KISAN] PERMISSION_GRANTED: Audio recording permission granted")
        } else {
            Log.w(TAG, "[BOL_KISAN] PIPELINE_ERROR: Microphone permission denied by user")
            Toast.makeText(this, "Microphone permission required for voice advisory", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = (application as BolKisanApp).database
        packManager = KnowledgePackManager(this, database)
        audioManager = AudioRecordingManager()
        asrEngine = OfflineSpeechRecognitionEngine(this)
        retrievalEngine = LocalKnowledgeRetrievalEngine(database, packManager)
        ttsEngine = OfflineTextToSpeechEngine(this)
        visionEngine = OfflineAgriculturalVisionEngine(this, retrievalEngine)
        modePreferences = ModePreferences(this)

        checkAudioPermission()

        setContent {
            BolKisanTheme {
                val navController = rememberNavController()

                // State
                var selectedLanguage by remember { mutableStateOf("te") }
                var voiceState by remember { mutableStateOf(VoiceState.IDLE) }
                var currentTranscript by remember { mutableStateOf("") }
                var currentScenario by remember { mutableStateOf("") }
                var currentAnswer by remember { mutableStateOf("") }
                var currentCitationDoc by remember { mutableStateOf<String?>(null) }
                var currentCitationPage by remember { mutableStateOf(1) }
                var currentAppMode by remember { mutableStateOf(modePreferences.appMode) }
                var isConnectedToInternet by remember { mutableStateOf(NetworkMonitor.isOnline(this@MainActivity)) }
                var isOffline by remember { mutableStateOf(!isConnectedToInternet) }
                var hasAudioConsent by remember { mutableStateOf(false) }

                // Multimodal State
                var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
                var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
                var lastSubmittedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
                var isImageProcessing by remember { mutableStateOf(false) }

                // Database Flows
                val profileFlow by database.farmerProfileDao().getProfileFlow().collectAsState(initial = null)
                val interactionsFlow by database.interactionDao().getAllInteractionsFlow().collectAsState(initial = emptyList())
                val mandiPricesFlow by database.mandiPriceDao().getAllPricesFlow().collectAsState(initial = emptyList())

                LaunchedEffect(profileFlow) {
                    profileFlow?.let {
                        selectedLanguage = it.preferredLanguage
                        hasAudioConsent = it.hasAudioConsent
                    }
                }

                // Unified Question Processing Pipeline (Shared between Text and Voice inputs)
                suspend fun handleCommonQuestionSubmission(
                    queryText: String,
                    bitmap: Bitmap?,
                    targetLang: String,
                    interactionId: String,
                    source: String // "VOICE" or "TEXT"
                ) {
                    try {
                        Log.i(TAG, "[BOL_KISAN] QUESTION_SUBMITTED: interactionId=$interactionId, Source=$source, Query='$queryText', HasImage=${bitmap != null}, TargetLang=$targetLang")

                        voiceState = VoiceState.PROCESSING
                        lastSubmittedImageBitmap = bitmap

                        if (bitmap != null) {
                            isImageProcessing = true
                            currentTranscript = if (queryText.isNotBlank()) queryText else when (targetLang) {
                                "te" -> "(పంట చిత్రం విశ్లేషించబడుతోంది...)"
                                "hi" -> "(फसल की तस्वीर का विश्लेषण किया जा रहा है...)"
                                else -> "(Analyzing crop photo...)"
                            }

                            Log.i(TAG, "[BOL_KISAN] MULTIMODAL_INFERENCE: interactionId=$interactionId, Analyzing image (${bitmap.width}x${bitmap.height}) with query='$queryText' in lang=$targetLang")
                            val visionResult = visionEngine.analyzeCropImage(
                                bitmap = bitmap,
                                userProvidedCrop = profileFlow?.primaryCrop ?: "Cotton",
                                language = targetLang
                            )

                            isImageProcessing = false
                            currentScenario = visionResult.scenario
                            currentAnswer = visionResult.formattedAdvisory
                            currentCitationDoc = visionResult.citationDoc
                            currentCitationPage = visionResult.citationPage

                            Log.i(TAG, "[BOL_KISAN] ANSWER_GENERATED: interactionId=$interactionId, Scenario=${visionResult.scenario}, AnswerLength=${visionResult.formattedAdvisory.length}")
                            Log.i(TAG, "[BOL_KISAN] RESPONSE_DISPLAYED: interactionId=$interactionId, Scenario=${visionResult.scenario}")

                            retrievalEngine.persistAdvisoryInteraction(
                                query = if (queryText.isNotBlank()) queryText else "Crop Image Diagnosis (${visionResult.primaryDiagnosis?.commonNameEn ?: "Visual Inspection"})",
                                result = LocalAdvisoryResult(
                                    scenario = visionResult.scenario,
                                    answer = visionResult.formattedAdvisory,
                                    spokenAnswer = visionResult.spokenAdvisory,
                                    citationDoc = visionResult.citationDoc,
                                    citationPage = visionResult.citationPage
                                ),
                                language = targetLang
                            )

                            voiceState = VoiceState.SPEAKING
                            ttsEngine.speak(visionResult.spokenAdvisory, targetLang, interactionId) {
                                voiceState = VoiceState.IDLE
                            }
                        } else {
                            if (queryText.isBlank()) {
                                voiceState = VoiceState.IDLE
                                return
                            }
                            currentTranscript = queryText
                            voiceState = VoiceState.SEARCHING_DOCUMENTS

                            var resolvedAdvisory: LocalAdvisoryResult? = null
                            var usedOnline = false

                            if (currentAppMode == AppMode.ONLINE_AI) {
                                isConnectedToInternet = NetworkMonitor.isOnline(this@MainActivity)
                                isOffline = !isConnectedToInternet
                                if (isConnectedToInternet) {
                                    Log.i(TAG, "[BOL_KISAN] Mode=ONLINE_AI: Querying FastAPI backend for '$queryText'")
                                    val onlineRes = onlineApiClient.fetchAdvisory(
                                        baseUrl = modePreferences.backendUrl,
                                        query = queryText,
                                        language = targetLang,
                                        crop = profileFlow?.primaryCrop ?: "Cotton"
                                    )
                                    if (onlineRes.isSuccess) {
                                        val data = onlineRes.getOrThrow()
                                        resolvedAdvisory = LocalAdvisoryResult(
                                            scenario = data.scenario,
                                            answer = data.answer,
                                            spokenAnswer = data.spokenAnswer,
                                            citationDoc = data.citationDoc,
                                            citationPage = data.citationPage
                                        )
                                        usedOnline = true
                                        Log.i(TAG, "[BOL_KISAN] Online AI advisory received successfully (Scenario=${data.scenario}, Provider=${data.modelProvider})")
                                    } else {
                                        Log.w(TAG, "[BOL_KISAN] Online query failed: ${onlineRes.exceptionOrNull()?.message}. Falling back to local offline RAG.")
                                    }
                                } else {
                                    Log.i(TAG, "[BOL_KISAN] Device offline while in ONLINE_AI mode. Falling back to local offline RAG.")
                                }
                            }

                            if (resolvedAdvisory == null) {
                                Log.i(TAG, "[BOL_KISAN] Executing Local Room DB FTS5 Retrieval (Mode=${currentAppMode.name})")
                                resolvedAdvisory = retrievalEngine.getAdvisory(
                                    query = queryText,
                                    crop = profileFlow?.primaryCrop ?: "Cotton",
                                    language = targetLang,
                                    interactionId = interactionId
                                )
                            }

                            voiceState = VoiceState.PREPARING_RESPONSE
                            delay(50)
                            currentScenario = resolvedAdvisory.scenario
                            currentAnswer = resolvedAdvisory.answer
                            currentCitationDoc = resolvedAdvisory.citationDoc
                            currentCitationPage = resolvedAdvisory.citationPage

                            Log.i(TAG, "[BOL_KISAN] ANSWER_GENERATED: interactionId=$interactionId, UsedOnline=$usedOnline, Scenario=${resolvedAdvisory.scenario}, AnswerLength=${resolvedAdvisory.answer.length}")
                            Log.i(TAG, "[BOL_KISAN] RESPONSE_DISPLAYED: interactionId=$interactionId, Scenario=${resolvedAdvisory.scenario}")

                            retrievalEngine.persistAdvisoryInteraction(
                                query = queryText,
                                result = resolvedAdvisory,
                                language = targetLang
                            )

                            voiceState = VoiceState.SPEAKING
                            ttsEngine.speak(resolvedAdvisory.spokenAnswer, targetLang, interactionId) {
                                voiceState = VoiceState.IDLE
                            }
                        }
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: Submission failed: ${e.message} (interactionId=$interactionId)", e)
                        currentTranscript = when (targetLang) {
                            "te" -> "ప్రాసెసింగ్ లోపం: ${e.message}"
                            "hi" -> "प्रोसेसिंग त्रुटि: ${e.message}"
                            else -> "Processing error: ${e.message}"
                        }
                        voiceState = VoiceState.IDLE
                        isImageProcessing = false
                    }
                }

                DisposableEffect(Unit) {
                    testQueryHandler = { wavPath, textQuery, lang, imagePath ->
                        val testBitmap = if (imagePath != null) {
                            val imgFile = java.io.File(imagePath)
                            if (imgFile.exists()) {
                                Log.i(TAG, "[BOL_KISAN] TEST_IMAGE_LOADED: ${imgFile.absolutePath} (${imgFile.length()} bytes)")
                                android.graphics.BitmapFactory.decodeFile(imgFile.absolutePath)
                            } else {
                                Log.w(TAG, "[BOL_KISAN] Test image file not found: $imagePath")
                                null
                            }
                        } else null

                        val interactionId = UUID.randomUUID().toString()
                        if (wavPath != null) {
                            recordingJob?.cancel()
                            recordingJob = lifecycleScope.launch {
                                try {
                                    val file = java.io.File(wavPath)
                                    Log.i(TAG, "[BOL_KISAN] TEST_VOICE_FILE: Reading audio from ${file.absolutePath} (${file.length()} bytes)")
                                    voiceState = VoiceState.PROCESSING
                                    val asr = asrEngine.transcribeWavFile(file, lang)
                                    val resolvedTestLang = if (lang.isNotBlank() && lang in listOf("te", "hi", "en")) lang else (if (asr.detectedLanguage in listOf("te", "hi", "en")) asr.detectedLanguage else "te")
                                    selectedLanguage = resolvedTestLang
                                    currentTranscript = asr.transcript
                                    handleCommonQuestionSubmission(asr.transcript, testBitmap, resolvedTestLang, interactionId, "TEST_VOICE")
                                } catch (e: Throwable) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: Test wav query failed: ${e.message}", e)
                                    voiceState = VoiceState.IDLE
                                }
                            }
                        } else {
                            selectedLanguage = lang
                            recordingJob?.cancel()
                            recordingJob = lifecycleScope.launch {
                                handleCommonQuestionSubmission(textQuery ?: "", testBitmap, lang, interactionId, "TEST_TEXT")
                            }
                        }
                    }
                    handleTestIntent(intent)
                    onDispose {
                        testQueryHandler = null
                    }
                }

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            selectedLanguage = selectedLanguage,
                            isOffline = !isConnectedToInternet,
                            currentAppMode = currentAppMode,
                            onModeToggle = {
                                val nextMode = if (currentAppMode == AppMode.ONLINE_AI) AppMode.OFFLINE_KNOWLEDGE else AppMode.ONLINE_AI
                                currentAppMode = nextMode
                                modePreferences.appMode = nextMode
                                val banner = if (nextMode == AppMode.ONLINE_AI) {
                                    "🌐 Switched to Online AI Mode"
                                } else {
                                    "🌾 Switched to Offline Knowledge Mode"
                                }
                                Toast.makeText(this@MainActivity, banner, Toast.LENGTH_SHORT).show()
                            },
                            voiceState = voiceState,
                            currentTranscript = currentTranscript,
                            currentScenario = currentScenario,
                            currentAnswer = currentAnswer,
                            currentCitationDoc = currentCitationDoc,
                            currentCitationPage = currentCitationPage,
                            selectedImageUri = selectedImageUri,
                            selectedImageBitmap = selectedImageBitmap,
                            lastSubmittedImage = lastSubmittedImageBitmap,
                            isImageProcessing = isImageProcessing,
                            interactions = interactionsFlow,
                            onMicClick = {
                                Log.i(TAG, "[BOL_KISAN] MIC_CLICKED: Current state=$voiceState, HasPerm=$hasAudioPermission")

                                if (!hasAudioPermission) {
                                    checkAudioPermission()
                                    return@HomeScreen
                                }

                                if (!asrEngine.isModelReady()) {
                                    Log.w(TAG, "[BOL_KISAN] Mic clicked while ASR model is still loading")
                                    val msg = if (selectedLanguage == "te") {
                                        "వాయిస్ మోడల్ లోడ్ అవుతోంది... దయచేసి కొన్ని క్షణాలు వేచి ఉండండి."
                                    } else {
                                        "आवाज मॉडल लोड हो रहा है... कृपया कुछ सेकंड प्रतीक्षा करें।"
                                    }
                                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                    return@HomeScreen
                                }

                                if (voiceState == VoiceState.SPEAKING) {
                                    Log.i(TAG, "[BOL_KISAN] User tapped mic to interrupt speech (barge-in)")
                                    ttsEngine.stop()
                                    voiceState = VoiceState.IDLE
                                    return@HomeScreen
                                }

                                if (voiceState == VoiceState.LISTENING) {
                                    Log.i(TAG, "[BOL_KISAN] User tapped mic to stop recording early")
                                    audioManager.stopRecording()
                                    // Coroutine will naturally exit the loop and proceed to ASR -> handleCommonQuestionSubmission
                                } else if (voiceState == VoiceState.IDLE) {
                                    val interactionId = UUID.randomUUID().toString()
                                    val attachedImg = selectedImageBitmap

                                    recordingJob?.cancel()
                                    recordingJob = lifecycleScope.launch {
                                        try {
                                            voiceState = VoiceState.LISTENING

                                            // 1. Audio Recording & Adaptive VAD
                                            val audioBytes = audioManager.startRecording(
                                                maxDurationMs = 8000L,
                                                silenceTimeoutMs = 1800L,
                                                interactionId = interactionId
                                            )

                                            if (audioBytes.size < 3200) {
                                                Log.w(TAG, "[BOL_KISAN] Recording too short or empty (${audioBytes.size} bytes), interactionId=$interactionId")
                                                currentTranscript = when (selectedLanguage) {
                                                    "te" -> "(ధ్వని గుర్తించబడలేదు. దయచేసి మైక్ నొక్కి స్పష్టంగా మాట్లాడండి.)"
                                                    "hi" -> "(आवाज नहीं पहचानी गई। कृपया माइक दबाकर स्पष्ट बोलें।)"
                                                    else -> "(Voice not detected. Please tap microphone and speak clearly.)"
                                                }
                                                voiceState = VoiceState.IDLE
                                                // Image attachment is preserved so farmer can retry speaking or type
                                                return@launch
                                            }

                                            // 2. Offline Speech Recognition & LID
                                            voiceState = VoiceState.PROCESSING
                                            val asr = asrEngine.transcribe(audioBytes, selectedLanguage, interactionId)

                                            if (asr.transcript.isBlank()) {
                                                currentTranscript = when (selectedLanguage) {
                                                    "te" -> "(ధ్వని స్పష్టంగా లేదు. దయచేసి మళ్ళీ మాట్లాడండి.)"
                                                    "hi" -> "(आवाज स्पष्ट नहीं मिली। कृपया पुनः बोलें।)"
                                                    else -> "(Voice unclear. Please speak again.)"
                                                }
                                                voiceState = VoiceState.IDLE
                                                // Image attachment is preserved so farmer can retry speaking or type
                                                return@launch
                                            }

                                            currentTranscript = asr.transcript
                                            // The user's selected language is authoritative. Do not override with ASR inference.
                                            val resolvedLang = if (selectedLanguage.isNotBlank() && selectedLanguage in listOf("te", "hi", "en")) selectedLanguage else (if (asr.detectedLanguage in listOf("te", "hi", "en")) asr.detectedLanguage else "te")
                                            selectedLanguage = resolvedLang

                                            // Clear attached image from input dock now that submission is underway
                                            if (attachedImg != null) {
                                                selectedImageUri = null
                                                selectedImageBitmap = null
                                            }

                                            // 3. Connect Voice directly to Shared Question Handler (No self-cancellation!)
                                            handleCommonQuestionSubmission(asr.transcript, attachedImg, resolvedLang, interactionId, "VOICE")

                                        } catch (e: Throwable) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: Unhandled error in voice pipeline: ${e.message} (interactionId=$interactionId)", e)
                                            currentTranscript = when (selectedLanguage) {
                                                "te" -> "ప్రాసెసింగ్ లోపం: ${e.message}"
                                                "hi" -> "प्रोसेसिंग त्रुटि: ${e.message}"
                                                else -> "Processing error: ${e.message}"
                                            }
                                            voiceState = VoiceState.IDLE
                                        }
                                    }
                                }
                            },
                            onLanguageSwitch = { newLang ->
                                selectedLanguage = newLang
                                lifecycleScope.launch {
                                    database.farmerProfileDao().updateLanguage("farmer_primary", newLang)
                                }
                            },
                            onImageSelected = { uri, bitmap ->
                                selectedImageUri = uri
                                selectedImageBitmap = bitmap
                            },
                            onImageRemoved = {
                                selectedImageUri = null
                                selectedImageBitmap = null
                            },
                            onSendQuery = { queryText, uri, bitmap ->
                                val img = selectedImageBitmap ?: bitmap
                                selectedImageUri = null
                                selectedImageBitmap = null
                                val interactionId = UUID.randomUUID().toString()
                                recordingJob?.cancel()
                                recordingJob = lifecycleScope.launch {
                                    handleCommonQuestionSubmission(queryText, img, selectedLanguage, interactionId, "TEXT")
                                }
                            },
                            onNavigateToMandi = { navController.navigate("mandi") },
                            onNavigateToHistory = { navController.navigate("history") },
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }

                    composable("mandi") {
                        MandiScreen(
                            prices = mandiPricesFlow,
                            isOffline = isOffline,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("history") {
                        HistoryScreen(
                            interactions = interactionsFlow,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            currentLanguage = selectedLanguage,
                            hasAudioConsent = hasAudioConsent,
                            onLanguageChange = { newLang ->
                                selectedLanguage = newLang
                                lifecycleScope.launch {
                                    database.farmerProfileDao().updateLanguage("farmer_primary", newLang)
                                }
                            },
                            onConsentChange = { newConsent ->
                                hasAudioConsent = newConsent
                                lifecycleScope.launch {
                                    database.farmerProfileDao().updateConsent("farmer_primary", newConsent)
                                }
                            },
                            onManualSync = {
                                Toast.makeText(this@MainActivity, "WorkManager sync queued for background execution", Toast.LENGTH_SHORT).show()
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun checkAudioPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                hasAudioPermission = true
                Log.i(TAG, "[BOL_KISAN] PERMISSION_GRANTED: Audio recording permission already granted")
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleTestIntent(intent)
    }

    private fun handleTestIntent(intent: android.content.Intent?) {
        if (intent?.action == "com.bolkisan.app.TEST_VOICE_QUERY") {
            val wavPath = intent.getStringExtra("wav_path")
            val query = intent.getStringExtra("query")
            val lang = intent.getStringExtra("language") ?: "te"
            val imagePath = intent.getStringExtra("image_path")
            Log.i(TAG, "[BOL_KISAN] Received TEST_VOICE_QUERY: wav=$wavPath, query=$query, lang=$lang, image_path=$imagePath")
            testQueryHandler?.invoke(wavPath, query, lang, imagePath)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingJob?.cancel()
        ttsEngine.shutdown()
        audioManager.stopRecording()
        asrEngine.release()
    }
}
