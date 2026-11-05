package com.bolkisan.app.pipeline

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class OfflineTextToSpeechEngine(
    context: Context,
    private val onInitComplete: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private val TAG = "BolKisanTTS"
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady = false
    @Volatile
    private var isManualStop = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            Log.i(TAG, "[BOL_KISAN] TTS_INITIALIZED: Engine bound successfully")
            onInitComplete(true)
        } else {
            isReady = false
            Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: TTS initialization failed with status: $status")
            onInitComplete(false)
        }
    }

    fun speak(text: String, languageCode: String, onDone: () -> Unit) {
        speak(text, languageCode, null, onDone)
    }

    fun speak(text: String, languageCode: String, interactionId: String? = null, onDone: () -> Unit = {}) {
        val qId = interactionId ?: java.util.UUID.randomUUID().toString()

        if (!isReady || tts == null) {
            Log.w(TAG, "[BOL_KISAN] PIPELINE_ERROR: TTS not ready or null, skipping speech synthesis (interactionId=$qId)")
            onDone()
            return
        }

        if (text.isBlank()) {
            onDone()
            return
        }

        val (primaryLocale, fallbackLocale) = when (languageCode) {
            "en" -> Pair(Locale("en", "IN"), Locale.US)
            "hi" -> Pair(Locale("hi", "IN"), Locale.US)
            else -> Pair(Locale("te", "IN"), Locale("hi", "IN"))
        }

        val avail = tts?.isLanguageAvailable(primaryLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (avail >= TextToSpeech.LANG_AVAILABLE) {
            val setRes = tts?.setLanguage(primaryLocale)
            Log.i(TAG, "[BOL_KISAN] TTS selected primary locale: $primaryLocale (avail=$avail, setRes=$setRes)")
        } else {
            val fallbackAvail = tts?.isLanguageAvailable(fallbackLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (fallbackAvail >= TextToSpeech.LANG_AVAILABLE) {
                val setRes = tts?.setLanguage(fallbackLocale)
                Log.w(TAG, "[BOL_KISAN] Locale $primaryLocale unavailable ($avail), falling back to $fallbackLocale (setRes=$setRes)")
            } else if (languageCode == "en") {
                tts?.setLanguage(Locale.ENGLISH)
                Log.w(TAG, "[BOL_KISAN] Fallback to Locale.ENGLISH for English speech synthesis")
            } else {
                tts?.setLanguage(Locale.getDefault())
                Log.w(TAG, "[BOL_KISAN] Both locales unavailable, using default: ${Locale.getDefault()}")
            }
        }

        // Set optimal pitch and rate for natural advisory flow
        tts?.setPitch(1.0f)
        tts?.setSpeechRate(0.95f)

        isManualStop = false
        val utteranceId = "bol_kisan_utterance_${System.currentTimeMillis()}"
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        Log.i(TAG, "[BOL_KISAN] TTS_STARTED: interactionId=$qId, Utterance=$utteranceId, Lang=$languageCode, Length=${text.length}")

        val hasCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
        fun safeOnDone(reason: String) {
            if (hasCompleted.compareAndSet(false, true)) {
                Log.i(TAG, "[BOL_KISAN] TTS_COMPLETED: interactionId=$qId, Reason=$reason, Utterance=$utteranceId")
                mainHandler.post { onDone() }
            }
        }

        // Automatic watchdog timeout so voiceState never hangs even if Android TTS drops the callback
        val timeoutMs = maxOf(5000L, (text.length * 100L) + 3000L)
        val timeoutRunnable = Runnable {
            Log.w(TAG, "[BOL_KISAN] TTS_TIMEOUT_FALLBACK: Utterance $utteranceId (interactionId=$qId) timed out after ${timeoutMs}ms, triggering onDone")
            safeOnDone("TIMEOUT_FALLBACK")
        }
        mainHandler.postDelayed(timeoutRunnable, timeoutMs)

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                Log.i(TAG, "[BOL_KISAN] SPEECH_PLAYBACK_STARTED: Utterance $id (interactionId=$qId)")
            }

            override fun onDone(id: String?) {
                if (id == utteranceId && !isManualStop) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    safeOnDone("ON_DONE")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && !isManualStop && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
                    Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: Speech playback error on utterance $id (interactionId=$qId)")
                    mainHandler.removeCallbacks(timeoutRunnable)
                    safeOnDone("ON_ERROR")
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && !isManualStop) {
                    Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: Speech playback error code $errorCode on utterance $id (interactionId=$qId)")
                    mainHandler.removeCallbacks(timeoutRunnable)
                    safeOnDone("ON_ERROR_CODE_$errorCode")
                }
            }
        })

        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: speak() call returned error code $result (interactionId=$qId)")
            mainHandler.removeCallbacks(timeoutRunnable)
            safeOnDone("SPEAK_CALL_FAILED_$result")
        }
    }

    fun stop() {
        isManualStop = true
        tts?.stop()
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isReady = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS: ${e.message}", e)
        }
    }
}
