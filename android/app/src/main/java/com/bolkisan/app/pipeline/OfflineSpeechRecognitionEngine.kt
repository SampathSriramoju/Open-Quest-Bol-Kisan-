package com.bolkisan.app.pipeline

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

data class AsrResult(
    val transcript: String,
    val detectedLanguage: String,
    val confidence: Float,
    val modelUsed: String,
    val latencyMs: Long,
    val isChemicalAmbiguous: Boolean = false,
    val isModelLoaded: Boolean = true
)

interface ISpeechRecognizer {
    fun isModelReady(): Boolean
    fun transcribe(audioBytes: ByteArray, preferredLanguage: String): AsrResult
    fun transcribeWavFile(wavFile: File, preferredLanguage: String): AsrResult
}

/**
 * Production Tri-Lingual Offline Speech Recognition Engine for Bol Kisan.
 * Uses Sherpa-ONNX C++ runtime with AI4Bharat IndicConformer int8 ONNX acoustic weights.
 *
 * Supports:
 * - Telugu: model_te.int8.onnx (AI4Bharat IndicConformer Telugu export, 188.4 MB) -> tokens.txt (5,633 Indic tokens)
 * - Hindi: model_hi.int8.onnx (AI4Bharat IndicConformer Hindi export, 187.8 MB) -> tokens.txt (5,633 Indic tokens)
 * - English: model_en.int8.onnx (AI4Bharat IndicConformer English export, 174.6 MB) -> tokens_en.txt (1,025 English tokens)
 * - Fallback: model.int8.onnx (single model fallback)
 *
 * Asynchronously loads models on a dedicated background thread with double-checked locking and non-blocking
 * lazy initialization. Dynamic tri-lingual routing based on preferredLanguage and script detection.
 * Maintains null AssetManager requirement for filesystem paths to avoid native C++ SIGABRT.
 */
class OfflineSpeechRecognitionEngine(private val context: Context) : ISpeechRecognizer {

    private val TAG = "BolKisanASR"

    @Volatile
    private var recognizerTe: OfflineRecognizer? = null

    @Volatile
    private var recognizerHi: OfflineRecognizer? = null

    @Volatile
    private var recognizerEn: OfflineRecognizer? = null

    @Volatile
    private var recognizerFallback: OfflineRecognizer? = null

    private var modelFileTe: File? = null
    private var modelFileHi: File? = null
    private var modelFileEn: File? = null
    private var modelFileFallback: File? = null

    private var tokensFile: File? = null
    private var tokensFileEn: File? = null

    private var modelNameTe: String = "not_loaded"
    private var modelNameHi: String = "not_loaded"
    private var modelNameEn: String = "not_loaded"
    private var modelNameFallback: String = "not_loaded"

    private val initLock = Any()
    private val recognitionLock = Any()

    val loadedModelName: String
        get() = "TE: $modelNameTe | HI: $modelNameHi | EN: $modelNameEn | Fallback: $modelNameFallback"

    val isSherpaLoaded: Boolean
        get() = isModelReady()

    init {
        // Initialize tri-lingual ASR models in a background thread to prevent UI stutter during ONNX graph loading
        Thread({
            initializeEngine()
        }, "BolKisanTriAsrInit").start()
    }

    private fun initializeEngine() {
        Log.i(TAG, "[BOL_KISAN] ASR_MODEL_LOADING: Initializing tri-lingual ASR (Telugu, Hindi, English IndicConformer int8)...")
        try {
            val internalModelsDir = File(context.filesDir, "models")
            if (!internalModelsDir.exists()) internalModelsDir.mkdirs()

            // 1. Resolve tokens files
            // Indic multilingual vocabulary (Telugu & Hindi)
            tokensFile = resolveTokensFile("tokens.txt", internalModelsDir)
            if (tokensFile == null) {
                Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: tokens.txt not found in storage or assets!")
            } else {
                Log.i(TAG, "[BOL_KISAN] Indic tokens resolved: ${tokensFile!!.absolutePath} (${tokensFile!!.length()} bytes)")
            }

            // English vocabulary (1,025 tokens)
            tokensFileEn = resolveTokensFile("tokens_en.txt", internalModelsDir)
            if (tokensFileEn == null) {
                Log.w(TAG, "[BOL_KISAN] tokens_en.txt not found in storage or assets; English ASR may be unavailable")
            } else {
                Log.i(TAG, "[BOL_KISAN] English tokens resolved: ${tokensFileEn!!.absolutePath} (${tokensFileEn!!.length()} bytes)")
            }

            // 2. Discover or extract model files
            // Telugu model: model_te.int8.onnx (or te/model.int8.onnx)
            modelFileTe = findInStorage("model_te.int8.onnx", "te/model.int8.onnx")
                ?: extractAssetIfPresent("models/model_te.int8.onnx", File(internalModelsDir, "model_te.int8.onnx"))
                ?: extractAssetIfPresent("models/te/model.int8.onnx", File(internalModelsDir, "model_te.int8.onnx"))

            // Hindi model: model_hi.int8.onnx (or hi/model.int8.onnx)
            modelFileHi = findInStorage("model_hi.int8.onnx", "hi/model.int8.onnx")
                ?: extractAssetIfPresent("models/model_hi.int8.onnx", File(internalModelsDir, "model_hi.int8.onnx"))
                ?: extractAssetIfPresent("models/hi/model.int8.onnx", File(internalModelsDir, "model_hi.int8.onnx"))

            // English model: model_en.int8.onnx (or en/model.int8.onnx)
            modelFileEn = findInStorage("model_en.int8.onnx", "en/model.int8.onnx")
                ?: extractAssetIfPresent("models/model_en.int8.onnx", File(internalModelsDir, "model_en.int8.onnx"))
                ?: extractAssetIfPresent("models/en/model.int8.onnx", File(internalModelsDir, "model_en.int8.onnx"))

            // Single / Generic fallback model: model.int8.onnx
            modelFileFallback = findInStorage("model.int8.onnx")
                ?: extractAssetIfPresent("models/model.int8.onnx", File(internalModelsDir, "model.int8.onnx"))

            // If Hindi model not found separately, but fallback model.int8.onnx exists,
            // we know model.int8.onnx is the Hindi export
            if (modelFileHi == null && modelFileFallback != null) {
                modelFileHi = modelFileFallback
                Log.i(TAG, "[BOL_KISAN] Using model.int8.onnx as Hindi model source")
            }

            Log.i(TAG, "[BOL_KISAN] Discovered models -> Telugu: ${modelFileTe?.name ?: "none"}, Hindi: ${modelFileHi?.name ?: "none"}, English: ${modelFileEn?.name ?: "none"}, Fallback: ${modelFileFallback?.name ?: "none"}")

            // 3. Asynchronously load Telugu recognizer (primary language for Bol Kisan)
            if (modelFileTe != null && tokensFile != null) {
                try {
                    Log.i(TAG, "[BOL_KISAN] ASR_MODEL_LOADING: Loading Telugu IndicConformer (${modelFileTe!!.absolutePath})...")
                    val teRec = buildRecognizer(modelFileTe!!, tokensFile!!)
                    synchronized(initLock) {
                        recognizerTe = teRec
                        modelNameTe = "AI4Bharat IndicConformer Telugu int8 (${modelFileTe!!.length() / 1024 / 1024}MB)"
                    }
                    Log.i(TAG, "[BOL_KISAN] ASR_MODEL_LOADED: Telugu Recognizer Ready ($modelNameTe)")
                } catch (e: Throwable) {
                    Log.e(TAG, "[BOL_KISAN] Failed to build Telugu recognizer: ${e.message}", e)
                    modelNameTe = "ERROR: ${e.message}"
                }
            }

            // 4. Asynchronously load Hindi recognizer
            if (modelFileHi != null && tokensFile != null) {
                try {
                    Log.i(TAG, "[BOL_KISAN] ASR_MODEL_LOADING: Loading Hindi IndicConformer (${modelFileHi!!.absolutePath})...")
                    val hiRec = buildRecognizer(modelFileHi!!, tokensFile!!)
                    synchronized(initLock) {
                        recognizerHi = hiRec
                        modelNameHi = "AI4Bharat IndicConformer Hindi int8 (${modelFileHi!!.length() / 1024 / 1024}MB)"
                    }
                    Log.i(TAG, "[BOL_KISAN] ASR_MODEL_LOADED: Hindi Recognizer Ready ($modelNameHi)")
                } catch (e: Throwable) {
                    Log.e(TAG, "[BOL_KISAN] Failed to build Hindi recognizer: ${e.message}", e)
                    modelNameHi = "ERROR: ${e.message}"
                }
            }

            // 5. Asynchronously load English recognizer
            if (modelFileEn != null && tokensFileEn != null) {
                try {
                    Log.i(TAG, "[BOL_KISAN] ASR_MODEL_LOADING: Loading English IndicConformer (${modelFileEn!!.absolutePath})...")
                    val enRec = buildRecognizer(modelFileEn!!, tokensFileEn!!)
                    synchronized(initLock) {
                        recognizerEn = enRec
                        modelNameEn = "AI4Bharat IndicConformer English int8 (${modelFileEn!!.length() / 1024 / 1024}MB)"
                    }
                    Log.i(TAG, "[BOL_KISAN] ASR_MODEL_LOADED: English Recognizer Ready ($modelNameEn)")
                } catch (e: Throwable) {
                    Log.e(TAG, "[BOL_KISAN] Failed to build English recognizer: ${e.message}", e)
                    modelNameEn = "ERROR: ${e.message}"
                }
            }

            // 6. Fallback single model initialization if none loaded
            if (recognizerTe == null && recognizerHi == null && recognizerEn == null && modelFileFallback != null && tokensFile != null) {
                try {
                    Log.i(TAG, "[BOL_KISAN] ASR_MODEL_LOADING: Loading Fallback IndicConformer (${modelFileFallback!!.absolutePath})...")
                    val fbRec = buildRecognizer(modelFileFallback!!, tokensFile!!)
                    synchronized(initLock) {
                        recognizerFallback = fbRec
                        modelNameFallback = "AI4Bharat IndicConformer Single int8 (${modelFileFallback!!.length() / 1024 / 1024}MB)"
                    }
                    Log.i(TAG, "[BOL_KISAN] ASR_MODEL_LOADED: Fallback Recognizer Ready ($modelNameFallback)")
                } catch (e: Throwable) {
                    Log.e(TAG, "[BOL_KISAN] Failed to build Fallback recognizer: ${e.message}", e)
                    modelNameFallback = "ERROR: ${e.message}"
                }
            }

            Log.i(TAG, "[BOL_KISAN] Tri-lingual ASR initialization completed. Status: $loadedModelName")

        } catch (e: Throwable) {
            Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: Tri-lingual ASR engine initialization error: ${e.message}", e)
        }
    }

    private fun buildRecognizer(modelFile: File, tokens: File): OfflineRecognizer {
        // Load from filesystem using absolute path.
        // CRITICAL FIX: assetManager MUST be null when using filesystem paths!
        // Passing context.assets causes Sherpa-ONNX to fail file open and throw SIGABRT.
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = modelFile.absolutePath),
                tokens = tokens.absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "nemo_ctc"
            )
        )
        return OfflineRecognizer(null, config)
    }

    private fun getOrInitRecognizer(preferredLanguage: String): Pair<OfflineRecognizer?, String> {
        val lang = preferredLanguage.lowercase().trim()

        synchronized(initLock) {
            when (lang) {
                "en" -> {
                    // 1. Ready English recognizer
                    if (recognizerEn != null) return Pair(recognizerEn, modelNameEn)
                    // 2. Lazy load English recognizer
                    if (modelFileEn != null && tokensFileEn != null) {
                        try {
                            Log.i(TAG, "[BOL_KISAN] Lazy-initializing English IndicConformer on-demand...")
                            val r = buildRecognizer(modelFileEn!!, tokensFileEn!!)
                            recognizerEn = r
                            modelNameEn = "AI4Bharat IndicConformer English int8 (${modelFileEn!!.length() / 1024 / 1024}MB)"
                            return Pair(recognizerEn, modelNameEn)
                        } catch (e: Throwable) {
                            Log.e(TAG, "[BOL_KISAN] Lazy init English failed: ${e.message}", e)
                        }
                    }
                    // 3. Fallbacks
                    if (recognizerFallback != null) return Pair(recognizerFallback, modelNameFallback)
                    if (recognizerTe != null) return Pair(recognizerTe, modelNameTe)
                    if (recognizerHi != null) return Pair(recognizerHi, modelNameHi)
                }
                "hi" -> {
                    // 1. Ready Hindi recognizer
                    if (recognizerHi != null) return Pair(recognizerHi, modelNameHi)
                    // 2. Lazy load Hindi recognizer
                    if (modelFileHi != null && tokensFile != null) {
                        try {
                            Log.i(TAG, "[BOL_KISAN] Lazy-initializing Hindi IndicConformer on-demand...")
                            val r = buildRecognizer(modelFileHi!!, tokensFile!!)
                            recognizerHi = r
                            modelNameHi = "AI4Bharat IndicConformer Hindi int8 (${modelFileHi!!.length() / 1024 / 1024}MB)"
                            return Pair(recognizerHi, modelNameHi)
                        } catch (e: Throwable) {
                            Log.e(TAG, "[BOL_KISAN] Lazy init Hindi failed: ${e.message}", e)
                        }
                    }
                    // 3. Fallbacks
                    if (recognizerFallback != null) return Pair(recognizerFallback, modelNameFallback)
                    if (recognizerTe != null) return Pair(recognizerTe, modelNameTe)
                    if (recognizerEn != null) return Pair(recognizerEn, modelNameEn)
                }
                else -> { // "te" or default
                    // 1. Ready Telugu recognizer
                    if (recognizerTe != null) return Pair(recognizerTe, modelNameTe)
                    // 2. Lazy load Telugu recognizer
                    if (modelFileTe != null && tokensFile != null) {
                        try {
                            Log.i(TAG, "[BOL_KISAN] Lazy-initializing Telugu IndicConformer on-demand...")
                            val r = buildRecognizer(modelFileTe!!, tokensFile!!)
                            recognizerTe = r
                            modelNameTe = "AI4Bharat IndicConformer Telugu int8 (${modelFileTe!!.length() / 1024 / 1024}MB)"
                            return Pair(recognizerTe, modelNameTe)
                        } catch (e: Throwable) {
                            Log.e(TAG, "[BOL_KISAN] Lazy init Telugu failed: ${e.message}", e)
                        }
                    }
                    // 3. Fallbacks
                    if (recognizerFallback != null) return Pair(recognizerFallback, modelNameFallback)
                    if (recognizerHi != null) return Pair(recognizerHi, modelNameHi)
                    if (recognizerEn != null) return Pair(recognizerEn, modelNameEn)
                }
            }

            return Pair(null, "NO_RECOGNIZER_READY ($loadedModelName)")
        }
    }

    override fun isModelReady(): Boolean {
        return recognizerTe != null || recognizerHi != null || recognizerEn != null || recognizerFallback != null
    }

    fun isModelReady(preferredLanguage: String): Boolean {
        val lang = preferredLanguage.lowercase().trim()
        return when (lang) {
            "en" -> recognizerEn != null || (modelFileEn != null && tokensFileEn != null) || isModelReady()
            "hi" -> recognizerHi != null || (modelFileHi != null && tokensFile != null) || isModelReady()
            else -> recognizerTe != null || (modelFileTe != null && tokensFile != null) || isModelReady()
        }
    }

    override fun transcribe(audioBytes: ByteArray, preferredLanguage: String): AsrResult {
        return transcribe(audioBytes, preferredLanguage, null)
    }

    fun transcribe(audioBytes: ByteArray, preferredLanguage: String, interactionId: String? = null): AsrResult {
        val qId = interactionId ?: java.util.UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        val (activeRecognizer, modelUsed) = getOrInitRecognizer(preferredLanguage)
        if (activeRecognizer == null) {
            Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: ASR transcribe requested but model is not ready: $modelUsed (interactionId=$qId)")
            return AsrResult(
                transcript = "",
                detectedLanguage = preferredLanguage,
                confidence = 0.0f,
                modelUsed = modelUsed,
                latencyMs = System.currentTimeMillis() - startTime,
                isChemicalAmbiguous = false,
                isModelLoaded = false
            )
        }

        // Require at least 0.1s of 16kHz audio (1600 samples = 3200 bytes)
        if (audioBytes.size < 3200) {
            Log.w(TAG, "[BOL_KISAN] Audio buffer too short (${audioBytes.size} bytes), skipping decode (interactionId=$qId)")
            return AsrResult(
                transcript = "",
                detectedLanguage = preferredLanguage,
                confidence = 0.0f,
                modelUsed = modelUsed,
                latencyMs = System.currentTimeMillis() - startTime,
                isChemicalAmbiguous = false,
                isModelLoaded = true
            )
        }

        val sampleCount = audioBytes.size / 2
        Log.i(TAG, "[BOL_KISAN] ASR_STARTED: interactionId=$qId, TargetLang=$preferredLanguage, Model=$modelUsed, Samples=$sampleCount (~${sampleCount / 16}ms)")

        try {
            // Convert 16-bit little-endian PCM bytes to FloatArray samples in [-1.0, 1.0]
            val samples = FloatArray(sampleCount)
            val byteBuffer = java.nio.ByteBuffer.wrap(audioBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val shortBuffer = byteBuffer.asShortBuffer()

            var maxAbsPeak = 0.0f
            for (i in 0 until sampleCount) {
                val s = shortBuffer.get(i).toFloat() / 32768.0f
                samples[i] = s
                val absVal = kotlin.math.abs(s)
                if (absVal > maxAbsPeak) {
                    maxAbsPeak = absVal
                }
            }

            // Adaptive Gain Normalization:
            // If peak amplitude is low (farmer speaking softly or distance from microphone),
            // normalize gain up to target peak of 0.85 without exceeding a 4.5x boost ceiling.
            if (maxAbsPeak in 0.01f..0.60f) {
                val targetPeak = 0.85f
                val gainMultiplier = minOf(targetPeak / maxAbsPeak, 4.5f)
                for (i in samples.indices) {
                    samples[i] = (samples[i] * gainMultiplier).coerceIn(-1.0f, 1.0f)
                }
                Log.i(TAG, "[BOL_KISAN] AUDIO_GAIN_NORMALIZED: Applied boost factor ${"%.2f".format(gainMultiplier)}x (Peak: ${"%.3f".format(maxAbsPeak)} -> ${"%.3f".format(maxAbsPeak * gainMultiplier)})")
            } else if (maxAbsPeak > 0.60f) {
                Log.i(TAG, "[BOL_KISAN] AUDIO_LEVEL_OPTIMAL: Peak amplitude is ${"%.3f".format(maxAbsPeak)}, no gain boost needed")
            } else {
                Log.w(TAG, "[BOL_KISAN] AUDIO_LEVEL_LOW: Peak amplitude is extremely low (${"%.4f".format(maxAbsPeak)}), possible silence")
            }

            val rawText: String
            synchronized(recognitionLock) {
                val stream = activeRecognizer.createStream()
                try {
                    stream.acceptWaveform(samples, 16000)
                    activeRecognizer.decode(stream)
                    val result = activeRecognizer.getResult(stream)
                    rawText = result.text.trim()
                } finally {
                    stream.release()
                }
            }

            val latency = System.currentTimeMillis() - startTime

            // Trailing/pure punctuation & silence artifact sanitization:
            // Silence or breathing sometimes causes CTC decoders to produce lone punctuation (e.g. '.', '?', '-', ',').
            // Ensure any string lacking actual alphanumeric or Indic script characters is sanitized to empty string,
            // preventing spurious empty queries from reaching the RAG pipeline.
            val hasValidSpeechTokens = rawText.any { ch ->
                ch.isLetterOrDigit() || ch.code in 0x0C00..0x0C7F || ch.code in 0x0900..0x097F
            }

            val cleanText = if (hasValidSpeechTokens) {
                rawText.trim().trim('.', '?', ',', '!', '-', ';', ':', ' ', '।', '॥')
            } else {
                if (rawText.isNotEmpty()) {
                    Log.i(TAG, "[BOL_KISAN] SANITIZED_EMPTY_SPEECH: Discarded pure punctuation/silence artifact '$rawText' (interactionId=$qId)")
                }
                ""
            }

            // Determine language using LID engine
            val langResult = LanguageIdentificationEngine.detectLanguage(cleanText, preferredLanguage)

            Log.i(TAG, "[BOL_KISAN] ASR_COMPLETED: interactionId=$qId, RawTranscript='$rawText', Latency=${latency}ms, Model=$modelUsed")
            Log.i(TAG, "[BOL_KISAN] TRANSCRIPT_READY: interactionId=$qId, CleanTranscript='$cleanText', DetectedLang=${langResult.languageCode}, Confidence=${langResult.confidence}")

            return AsrResult(
                transcript = cleanText,
                detectedLanguage = langResult.languageCode,
                confidence = langResult.confidence,
                modelUsed = modelUsed,
                latencyMs = latency,
                isChemicalAmbiguous = false,
                isModelLoaded = true
            )

        } catch (e: Throwable) {
            Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: ASR inference failed: ${e.message} (interactionId=$qId)", e)
            return AsrResult(
                transcript = "",
                detectedLanguage = preferredLanguage,
                confidence = 0.0f,
                modelUsed = "ERROR: ${e.message}",
                latencyMs = System.currentTimeMillis() - startTime,
                isChemicalAmbiguous = false,
                isModelLoaded = false
            )
        }
    }

    override fun transcribeWavFile(wavFile: File, preferredLanguage: String): AsrResult {
        if (!wavFile.exists()) {
            Log.e(TAG, "[BOL_KISAN] WAV file not found: ${wavFile.absolutePath}")
            return AsrResult("", preferredLanguage, 0f, "FILE_NOT_FOUND", 0L, false, false)
        }
        val bytes = wavFile.readBytes()
        // Locate 'data' chunk in WAV header if present, else fallback to standard 44 bytes
        var dataOffset = -1
        for (i in 0 until bytes.size - 8) {
            if (bytes[i] == 'd'.code.toByte() &&
                bytes[i + 1] == 'a'.code.toByte() &&
                bytes[i + 2] == 't'.code.toByte() &&
                bytes[i + 3] == 'a'.code.toByte()
            ) {
                dataOffset = i + 8 // skip 'data' (4 bytes) + data length (4 bytes)
                break
            }
        }
        val pcmBytes = if (dataOffset in 0 until bytes.size) {
            bytes.copyOfRange(dataOffset, bytes.size)
        } else if (bytes.size > 44) {
            bytes.copyOfRange(44, bytes.size)
        } else {
            bytes
        }
        return transcribe(pcmBytes, preferredLanguage)
    }

    private fun findInStorage(vararg relativePaths: String): File? {
        val baseDirs = listOfNotNull(
            File(context.filesDir, "models"),
            context.getExternalFilesDir(null)?.let { File(it, "sherpa_onnx_models") },
            File("/sdcard/Android/data/${context.packageName}/files/sherpa_onnx_models")
        )
        for (baseDir in baseDirs) {
            for (rel in relativePaths) {
                val f = File(baseDir, rel)
                if (f.exists() && f.length() > 100000) {
                    Log.i(TAG, "[BOL_KISAN] Found model on filesystem: ${f.absolutePath} (${f.length() / 1024 / 1024} MB)")
                    return f
                }
            }
        }
        return null
    }

    private fun extractAssetIfPresent(assetSubPath: String, destFile: File): File? {
        try {
            val parentPath = if (assetSubPath.contains("/")) assetSubPath.substringBeforeLast("/") else ""
            val fileName = if (assetSubPath.contains("/")) assetSubPath.substringAfterLast("/") else assetSubPath
            val assetList = context.assets.list(parentPath) ?: emptyArray()
            if (assetList.contains(fileName)) {
                val parentDir = destFile.parentFile ?: File(context.filesDir, "models")
                if (!parentDir.exists()) {
                    parentDir.mkdirs()
                }
                if (!destFile.exists() || destFile.length() < 100000) {
                    Log.i(TAG, "[BOL_KISAN] Extracting asset $assetSubPath to ${destFile.absolutePath}...")
                    val tmp = File(parentDir, "${destFile.name}.tmp")
                    context.assets.open(assetSubPath).use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (tmp.exists() && tmp.length() > 100000) {
                        tmp.renameTo(destFile)
                        Log.i(TAG, "[BOL_KISAN] Successfully extracted: ${destFile.absolutePath} (${destFile.length() / 1024 / 1024} MB)")
                    } else {
                        tmp.delete()
                    }
                }
                if (destFile.exists() && destFile.length() > 100000) {
                    return destFile
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[BOL_KISAN] Error checking/extracting APK asset $assetSubPath: ${e.message}")
        }
        return null
    }

    private fun resolveTokensFile(fileName: String, internalModelsDir: File): File? {
        val baseDirs = listOfNotNull(
            File(context.filesDir, "models"),
            context.getExternalFilesDir(null)?.let { File(it, "sherpa_onnx_models") },
            File("/sdcard/Android/data/${context.packageName}/files/sherpa_onnx_models")
        )
        for (baseDir in baseDirs) {
            val f = File(baseDir, fileName)
            if (f.exists() && f.length() > 500) {
                Log.i(TAG, "[BOL_KISAN] Found $fileName on filesystem: ${f.absolutePath}")
                return f
            }
        }
        val dest = File(internalModelsDir, fileName)
        try {
            val assetList = context.assets.list("models") ?: emptyArray()
            if (assetList.contains(fileName)) {
                if (!dest.exists() || dest.length() < 500) {
                    Log.i(TAG, "[BOL_KISAN] Extracting $fileName from APK assets to ${dest.absolutePath}...")
                    context.assets.open("models/$fileName").use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                if (dest.exists() && dest.length() > 500) {
                    Log.i(TAG, "[BOL_KISAN] Successfully extracted $fileName: ${dest.absolutePath}")
                    return dest
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[BOL_KISAN] Error extracting $fileName: ${e.message}")
        }
        return null
    }

    fun release() {
        try {
            recognizerTe?.release()
        } catch (ignored: Throwable) {}
        recognizerTe = null

        try {
            recognizerHi?.release()
        } catch (ignored: Throwable) {}
        recognizerHi = null

        try {
            recognizerEn?.release()
        } catch (ignored: Throwable) {}
        recognizerEn = null

        try {
            recognizerFallback?.release()
        } catch (ignored: Throwable) {}
        recognizerFallback = null
        Log.i(TAG, "[BOL_KISAN] ASR recognizers released")
    }
}
