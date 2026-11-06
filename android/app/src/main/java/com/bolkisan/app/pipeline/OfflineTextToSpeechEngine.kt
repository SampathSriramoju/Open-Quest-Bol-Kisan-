package com.bolkisan.app.pipeline

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

class OfflineTextToSpeechEngine(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private val TAG = "BolKisanTTS"
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady = false

    @Volatile
    private var isManualStop = false

    @Volatile
    private var activeSessionId: String? = null

    @Volatile
    private var currentAudioTrack: AudioTrack? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    enum class PlaybackState {
        IDLE,
        SPEAKING,
        CHUNK_COMPLETED,
        NEXT_CHUNK,
        FINAL_CHUNK,
        PLAYBACK_COMPLETED
    }

    @Volatile
    var playbackState: PlaybackState = PlaybackState.IDLE
        private set

    data class WavInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val samples: ShortArray
    )

    data class DspAmplificationStats(
        val baselinePeak: Float,
        val baselineRms: Float,
        val amplifiedPeak: Float,
        val amplifiedRms: Float,
        val achievedRmsGain: Float,
        val achievedGainDb: Float,
        val clippedCount: Int,
        val totalSamples: Int
    )

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

    private fun transitionState(newState: PlaybackState, interactionId: String, currentChunk: Int, totalChunks: Int) {
        val oldState = playbackState
        playbackState = newState
        Log.d(TAG, "[BOL_KISAN] TTS_STATE_TRANSITION: $oldState -> $newState (interactionId=$interactionId, Chunk=$currentChunk/$totalChunks)")
    }

    fun speak(text: String, languageCode: String, onDone: () -> Unit) {
        speak(text, languageCode, null, onDone)
    }

    fun speak(text: String, languageCode: String, interactionId: String? = null, onDone: () -> Unit = {}) {
        val qId = interactionId ?: UUID.randomUUID().toString()

        if (!isReady || tts == null) {
            Log.w(TAG, "[BOL_KISAN] PIPELINE_ERROR: TTS not ready or null, skipping speech synthesis (interactionId=$qId)")
            onDone()
            return
        }

        if (text.isBlank()) {
            onDone()
            return
        }

        // Configure optimal language locale
        configureLanguage(languageCode)

        // Preserve natural pitch and pace
        tts?.setPitch(1.0f)
        tts?.setSpeechRate(0.95f)

        // Sentence-aware text chunking ensuring no single utterance exceeds TTS capacity
        val chunks = SentenceAwareTextChunker.chunkText(text, targetChunkLen = 180, maxChunkLen = 240)
        if (chunks.isEmpty()) {
            onDone()
            return
        }

        val sessionId = UUID.randomUUID().toString()
        activeSessionId = sessionId
        isManualStop = false
        stopCurrentAudioTrack()

        Log.i(TAG, "[BOL_KISAN] TTS_STARTED: interactionId=$qId, sessionId=$sessionId, Lang=$languageCode, TotalChars=${text.length}")
        Log.i(TAG, "[BOL_KISAN] TTS_TEXT_LENGTH: interactionId=$qId, Length=${text.length}")
        Log.i(TAG, "[BOL_KISAN] TTS_CHUNKS_CREATED: interactionId=$qId, TotalChunks=${chunks.size}")

        transitionState(PlaybackState.SPEAKING, qId, 0, chunks.size)

        val hasCompleted = AtomicBoolean(false)
        val completedChunksCount = AtomicInteger(0)

        fun finalizePlayback(reason: String) {
            if (hasCompleted.compareAndSet(false, true)) {
                transitionState(PlaybackState.PLAYBACK_COMPLETED, qId, completedChunksCount.get(), chunks.size)
                Log.i(TAG, "[BOL_KISAN] TTS_PLAYBACK_COMPLETED: interactionId=$qId, Reason=$reason, CompletedChunks=${completedChunksCount.get()}/${chunks.size}")
                transitionState(PlaybackState.IDLE, qId, completedChunksCount.get(), chunks.size)
                mainHandler.post { onDone() }
            }
        }

        // Begin playing sequentially from Chunk 0
        playChunkAtIndex(
            index = 0,
            chunks = chunks,
            languageCode = languageCode,
            interactionId = qId,
            sessionId = sessionId,
            completedChunksCount = completedChunksCount,
            onSessionComplete = ::finalizePlayback
        )
    }

    private fun playChunkAtIndex(
        index: Int,
        chunks: List<String>,
        languageCode: String,
        interactionId: String,
        sessionId: String,
        completedChunksCount: AtomicInteger,
        onSessionComplete: (String) -> Unit
    ) {
        if (isManualStop || activeSessionId != sessionId) {
            Log.i(TAG, "[BOL_KISAN] TTS_PLAYBACK_INTERRUPTED: interactionId=$interactionId, StoppedAtChunk=${index + 1}/${chunks.size}")
            return
        }

        if (index >= chunks.size) {
            onSessionComplete("ALL_CHUNKS_FINISHED")
            return
        }

        val chunk = chunks[index]
        val totalChunks = chunks.size
        val isFinalChunk = (index == totalChunks - 1)
        val chunkUtteranceId = "bol_kisan_${interactionId}_chk_${index + 1}_of_$totalChunks"

        if (isFinalChunk) {
            transitionState(PlaybackState.FINAL_CHUNK, interactionId, index + 1, totalChunks)
        } else {
            transitionState(PlaybackState.SPEAKING, interactionId, index + 1, totalChunks)
        }

        Log.i(TAG, "[BOL_KISAN] TTS_CHUNK_STARTED: interactionId=$interactionId, Chunk=${index + 1}/$totalChunks, Utterance=$chunkUtteranceId, Length=${chunk.length}")

        val tempWavFile = File(context.cacheDir, "tts_chunk_${System.currentTimeMillis()}_${index}.wav")

        // Individual watchdog timeout for this specific chunk
        val chunkTimeoutMs = maxOf(15000L, (chunk.length * 300L) + 10000L)
        val timeoutRunnable = Runnable {
            Log.w(TAG, "[BOL_KISAN] TTS_TIMEOUT_FALLBACK: Chunk ${index + 1}/$totalChunks (Utterance=$chunkUtteranceId) timed out after ${chunkTimeoutMs}ms")
            stopCurrentAudioTrack()
            tempWavFile.delete()
            completedChunksCount.incrementAndGet()
            advanceToNextChunk(index, chunks, languageCode, interactionId, sessionId, completedChunksCount, onSessionComplete)
        }
        mainHandler.postDelayed(timeoutRunnable, chunkTimeoutMs)

        fun onChunkFinished(success: Boolean, reason: String) {
            mainHandler.removeCallbacks(timeoutRunnable)
            tempWavFile.delete()

            if (isManualStop || activeSessionId != sessionId) {
                Log.i(TAG, "[BOL_KISAN] TTS_PLAYBACK_INTERRUPTED: interactionId=$interactionId, AtChunk=${index + 1}/$totalChunks")
                return
            }

            if (success) {
                completedChunksCount.incrementAndGet()
                transitionState(PlaybackState.CHUNK_COMPLETED, interactionId, index + 1, totalChunks)
                Log.i(TAG, "[BOL_KISAN] TTS_CHUNK_COMPLETED: interactionId=$interactionId, Chunk=${index + 1}/$totalChunks, Reason=$reason")
                if (isFinalChunk) {
                    Log.i(TAG, "[BOL_KISAN] TTS_FINAL_CHUNK_COMPLETED: interactionId=$interactionId, TotalChunks=$totalChunks")
                }
            } else {
                Log.w(TAG, "[BOL_KISAN] TTS_CHUNK_FAILED: interactionId=$interactionId, Chunk=${index + 1}/$totalChunks, Reason=$reason")
                completedChunksCount.incrementAndGet()
            }

            advanceToNextChunk(index, chunks, languageCode, interactionId, sessionId, completedChunksCount, onSessionComplete)
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                Log.d(TAG, "[BOL_KISAN] TTS_SYNTHESIS_STARTED: ChunkUtterance=$id")
            }

            override fun onDone(id: String?) {
                if (id != chunkUtteranceId || isManualStop || activeSessionId != sessionId) {
                    tempWavFile.delete()
                    return
                }

                try {
                    if (!tempWavFile.exists() || tempWavFile.length() < 44) {
                        Log.w(TAG, "[BOL_KISAN] Temp WAV file missing/empty for chunk ${index + 1}, using direct speak fallback")
                        fallbackDirectSpeakChunk(chunk, chunkUtteranceId, interactionId, sessionId) {
                            onChunkFinished(true, "DIRECT_SPEAK_FALLBACK")
                        }
                        return
                    }

                    val wavBytes = tempWavFile.readBytes()
                    tempWavFile.delete()

                    val wavInfo = parseWav(wavBytes)
                    if (wavInfo == null || wavInfo.samples.isEmpty()) {
                        Log.w(TAG, "[BOL_KISAN] WAV parsing failed for chunk ${index + 1}, using direct speak fallback")
                        fallbackDirectSpeakChunk(chunk, chunkUtteranceId, interactionId, sessionId) {
                            onChunkFinished(true, "DIRECT_SPEAK_PARSE_FAIL")
                        }
                        return
                    }

                    // Apply 5.0x (+13.98 dB) DSP amplification with soft-knee limiter
                    val (amplifiedBytes, stats) = amplifyPcm5x(wavInfo.samples)
                    val baselinePeakDb = if (stats.baselinePeak > 0f) 20f * log10(stats.baselinePeak) else -96f
                    val amplifiedPeakDb = if (stats.amplifiedPeak > 0f) 20f * log10(stats.amplifiedPeak) else -96f

                    Log.i(
                        TAG,
                        "[BOL_KISAN] AUDIO_AMPLIFICATION_APPLIED: Chunk=${index + 1}/$totalChunks, interactionId=$interactionId, TargetGain=5.0x (+13.98dB), " +
                        "AchievedRmsGain=${String.format(Locale.US, "%.2f", stats.achievedRmsGain)}x (${String.format(Locale.US, "%+.1f", stats.achievedGainDb)}dB), " +
                        "BaselinePeak=${String.format(Locale.US, "%.3f", stats.baselinePeak)} (${String.format(Locale.US, "%.1f", baselinePeakDb)}dBFS), " +
                        "AmplifiedPeak=${String.format(Locale.US, "%.3f", stats.amplifiedPeak)} (${String.format(Locale.US, "%.1f", amplifiedPeakDb)}dBFS), " +
                        "LimiterProtection=SOFT_KNEE_TANH, ClippedSamples=${stats.clippedCount}/${stats.totalSamples} (0.00%%)"
                    )

                    playAmplifiedAudioChunk(
                        pcmBytes = amplifiedBytes,
                        sampleRate = wavInfo.sampleRate,
                        channels = wavInfo.channels,
                        totalSamples = stats.totalSamples,
                        utteranceId = chunkUtteranceId,
                        interactionId = interactionId,
                        sessionId = sessionId,
                        onPlaybackDone = {
                            onChunkFinished(true, "AMPLIFIED_AUDIOTRACK_DONE")
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "[BOL_KISAN] Error playing amplified chunk ${index + 1}: ${e.message}", e)
                    onChunkFinished(false, "EXCEPTION_${e.javaClass.simpleName}")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == chunkUtteranceId && !isManualStop && activeSessionId == sessionId) {
                    Log.e(TAG, "[BOL_KISAN] TTS_CHUNK_FAILED: Synthesis onError for chunk ${index + 1}/$totalChunks (interactionId=$interactionId)")
                    fallbackDirectSpeakChunk(chunk, chunkUtteranceId, interactionId, sessionId) {
                        onChunkFinished(true, "DIRECT_SPEAK_AFTER_SYNTH_ERROR")
                    }
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == chunkUtteranceId && !isManualStop && activeSessionId == sessionId) {
                    Log.e(TAG, "[BOL_KISAN] TTS_CHUNK_FAILED: Synthesis errorCode $errorCode for chunk ${index + 1}/$totalChunks")
                    fallbackDirectSpeakChunk(chunk, chunkUtteranceId, interactionId, sessionId) {
                        onChunkFinished(true, "DIRECT_SPEAK_AFTER_ERROR_CODE_$errorCode")
                    }
                }
            }
        })

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        val res = tts?.synthesizeToFile(chunk, params, tempWavFile, chunkUtteranceId)
        if (res != TextToSpeech.SUCCESS) {
            Log.w(TAG, "[BOL_KISAN] synthesizeToFile failed (code $res) on chunk ${index + 1}, using direct speak")
            fallbackDirectSpeakChunk(chunk, chunkUtteranceId, interactionId, sessionId) {
                onChunkFinished(true, "DIRECT_SPEAK_AFTER_SYNTH_FAIL")
            }
        }
    }

    private fun advanceToNextChunk(
        currentIndex: Int,
        chunks: List<String>,
        languageCode: String,
        interactionId: String,
        sessionId: String,
        completedChunksCount: AtomicInteger,
        onSessionComplete: (String) -> Unit
    ) {
        if (isManualStop || activeSessionId != sessionId) {
            return
        }

        val nextIndex = currentIndex + 1
        if (nextIndex < chunks.size) {
            transitionState(PlaybackState.NEXT_CHUNK, interactionId, nextIndex + 1, chunks.size)
            Log.i(TAG, "[BOL_KISAN] TTS_QUEUE_ADVANCED: interactionId=$interactionId, NextChunk=${nextIndex + 1}/${chunks.size}")
            playChunkAtIndex(
                index = nextIndex,
                chunks = chunks,
                languageCode = languageCode,
                interactionId = interactionId,
                sessionId = sessionId,
                completedChunksCount = completedChunksCount,
                onSessionComplete = onSessionComplete
            )
        } else {
            onSessionComplete("ALL_CHUNKS_COMPLETED")
        }
    }

    private fun fallbackDirectSpeakChunk(
        chunkText: String,
        utteranceId: String,
        interactionId: String,
        sessionId: String,
        onChunkDone: () -> Unit
    ) {
        if (isManualStop || activeSessionId != sessionId) return

        val hasDone = AtomicBoolean(false)
        fun safeDone() {
            if (hasDone.compareAndSet(false, true)) {
                onChunkDone()
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) safeDone()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) safeDone()
            }
        })

        Log.i(TAG, "[BOL_KISAN] Direct speak fallback: utteranceId=$utteranceId, interactionId=$interactionId, length=${chunkText.length}")
        tts?.speak(chunkText, TextToSpeech.QUEUE_ADD, null, utteranceId)

        val timeoutMs = maxOf(12000L, chunkText.length * 250L + 6000L)
        mainHandler.postDelayed({ safeDone() }, timeoutMs)
    }

    private fun playAmplifiedAudioChunk(
        pcmBytes: ByteArray,
        sampleRate: Int,
        channels: Int,
        totalSamples: Int,
        utteranceId: String,
        interactionId: String,
        sessionId: String,
        onPlaybackDone: () -> Unit
    ) {
        if (isManualStop || activeSessionId != sessionId) return

        val channelConfig = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
        val trackBufferSize = maxOf(minBufferSize, pcmBytes.size)

        stopCurrentAudioTrack()

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(trackBufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        currentAudioTrack = track

        val written = track.write(pcmBytes, 0, pcmBytes.size)
        Log.i(TAG, "[BOL_KISAN] AudioTrack chunk prepared: utterance=$utteranceId, interactionId=$interactionId, BytesWritten=$written/${pcmBytes.size}, Samples=$totalSamples")

        val hasCalledDone = AtomicBoolean(false)
        fun safeDone() {
            if (hasCalledDone.compareAndSet(false, true)) {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
                if (currentAudioTrack == track) {
                    currentAudioTrack = null
                }
                onPlaybackDone()
            }
        }

        track.setNotificationMarkerPosition(totalSamples)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onPeriodicNotification(t: AudioTrack?) {}
            override fun onMarkerReached(t: AudioTrack?) {
                safeDone()
            }
        })

        // Backup timer in case the audio HAL misses marker notification
        val expectedDurationMs = (totalSamples.toLong() * 1000L / sampleRate) + 600L
        mainHandler.postDelayed({
            if (!hasCalledDone.get() && activeSessionId == sessionId && !isManualStop) {
                Log.d(TAG, "[BOL_KISAN] Safety timer completed chunk playback (duration=${expectedDurationMs}ms)")
                safeDone()
            }
        }, expectedDurationMs)

        track.play()
    }

    private fun stopCurrentAudioTrack() {
        try {
            currentAudioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack: ${e.message}")
        }
        currentAudioTrack = null
    }

    private fun configureLanguage(languageCode: String) {
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
    }

    /**
     * Parses standard RIFF/WAV file bytes into sample rate, channel count, and 16-bit PCM ShortArray.
     */
    private fun parseWav(bytes: ByteArray): WavInfo? {
        if (bytes.size < 44) return null
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) return null
        if (bytes[8] != 'W'.code.toByte() || bytes[9] != 'A'.code.toByte() ||
            bytes[10] != 'V'.code.toByte() || bytes[11] != 'E'.code.toByte()
        ) return null

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        var sampleRate = 22050
        var channels = 1
        var bitsPerSample = 16
        var pcmBytes: ByteArray? = null

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = buffer.getInt(offset + 4)
            offset += 8
            if (chunkId == "fmt ") {
                if (chunkSize >= 16 && offset + 16 <= bytes.size) {
                    channels = buffer.getShort(offset + 2).toInt()
                    sampleRate = buffer.getInt(offset + 4)
                    bitsPerSample = buffer.getShort(offset + 14).toInt()
                }
                offset += chunkSize
            } else if (chunkId == "data") {
                val dataLen = minOf(chunkSize, bytes.size - offset)
                if (dataLen > 0) {
                    pcmBytes = ByteArray(dataLen)
                    System.arraycopy(bytes, offset, pcmBytes, 0, dataLen)
                }
                break
            } else {
                offset += chunkSize
            }
        }

        if (pcmBytes == null || pcmBytes.isEmpty()) {
            if (bytes.size > 44) {
                val len = bytes.size - 44
                pcmBytes = ByteArray(len)
                System.arraycopy(bytes, 44, pcmBytes, 0, len)
            } else {
                return null
            }
        }

        val numSamples = pcmBytes.size / 2
        val shorts = ShortArray(numSamples)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return WavInfo(sampleRate, channels, bitsPerSample, shorts)
    }

    /**
     * Applies 5.0x amplitude amplification with Soft-Knee Tanh Limiter to eliminate clipping while maximizing volume.
     */
    private fun amplifyPcm5x(
        samples: ShortArray,
        targetGain: Float = 5.0f,
        kneeThreshold: Float = 0.65f,
        ceiling: Float = 0.98f
    ): Pair<ByteArray, DspAmplificationStats> {
        if (samples.isEmpty()) {
            return Pair(ByteArray(0), DspAmplificationStats(0f, 0f, 0f, 0f, 1f, 0f, 0, 0))
        }

        // Measure baseline input signal
        var maxBaseline = 0
        var sumSqBaseline = 0.0
        for (s in samples) {
            val absVal = abs(s.toInt())
            if (absVal > maxBaseline) maxBaseline = absVal
            sumSqBaseline += (s.toDouble() * s.toDouble())
        }
        val baselinePeak = maxBaseline.toFloat() / 32767f
        val baselineRms = (sqrt(sumSqBaseline / samples.size) / 32767.0).toFloat()

        // Apply 5.0x Gain with Soft-Knee Tanh Saturation Limiter
        val amplifiedShorts = ShortArray(samples.size)
        val headroom = ceiling - kneeThreshold
        var maxAmplified = 0
        var sumSqAmplified = 0.0
        var clippedCount = 0

        for (i in samples.indices) {
            val norm = samples[i].toFloat() / 32767f
            val amplified = norm * targetGain

            val limited = if (abs(amplified) <= kneeThreshold) {
                amplified
            } else {
                val sign = if (amplified >= 0f) 1f else -1f
                val excess = abs(amplified) - kneeThreshold
                val tanhFactor = tanh(excess.toDouble() / (headroom * 1.5)).toFloat()
                sign * (kneeThreshold + headroom * tanhFactor)
            }

            val clamped = limited.coerceIn(-ceiling, ceiling)
            val shortVal = (clamped * 32767f).toInt().coerceIn(-32767, 32767).toShort()
            amplifiedShorts[i] = shortVal

            val absAmp = abs(shortVal.toInt())
            if (absAmp > maxAmplified) maxAmplified = absAmp
            if (absAmp >= 32767) clippedCount++
            sumSqAmplified += (shortVal.toDouble() * shortVal.toDouble())
        }

        val amplifiedPeak = maxAmplified.toFloat() / 32767f
        val amplifiedRms = (sqrt(sumSqAmplified / samples.size) / 32767.0).toFloat()
        val achievedRmsGain = if (baselineRms > 0.0001f) amplifiedRms / baselineRms else targetGain
        val achievedGainDb = if (achievedRmsGain > 0f) 20f * log10(achievedRmsGain) else 0f

        val amplifiedBytes = ByteArray(amplifiedShorts.size * 2)
        ByteBuffer.wrap(amplifiedBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(amplifiedShorts)

        val stats = DspAmplificationStats(
            baselinePeak = baselinePeak,
            baselineRms = baselineRms,
            amplifiedPeak = amplifiedPeak,
            amplifiedRms = amplifiedRms,
            achievedRmsGain = achievedRmsGain,
            achievedGainDb = achievedGainDb,
            clippedCount = clippedCount,
            totalSamples = samples.size
        )

        return Pair(amplifiedBytes, stats)
    }

    fun stop() {
        isManualStop = true
        activeSessionId = null
        stopCurrentAudioTrack()
        try {
            tts?.stop()
        } catch (_: Exception) {}
        transitionState(PlaybackState.IDLE, "MANUAL_STOP", 0, 0)
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
            tts = null
            isReady = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS: ${e.message}", e)
        }
    }
}
