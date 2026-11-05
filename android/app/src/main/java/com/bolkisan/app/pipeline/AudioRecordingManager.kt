package com.bolkisan.app.pipeline

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

class AudioRecordingManager {

    private val TAG = "BolKisanAudio"
    val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val bufferSize = maxOf(minBuffer * 2, 4096)

    @Volatile
    private var isRecording = false
    private var audioRecord: AudioRecord? = null

    // Adaptive VAD Parameters
    val baseMinVadThreshold = 220.0
    var currentNoiseFloorRms = 100.0
        private set
    var currentAdaptiveVadThreshold = 250.0
        private set

    val vadEnergyThreshold: Double
        get() = currentAdaptiveVadThreshold

    var lastRecordingPeak: Int = 0
        private set
    var lastRecordingRms: Double = 0.0
        private set

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord? {
        val audioSources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        for (source in audioSources) {
            try {
                val record = AudioRecord(
                    source,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    val srcName = if (source == MediaRecorder.AudioSource.VOICE_RECOGNITION) "VOICE_RECOGNITION" else "MIC"
                    Log.i(TAG, "[BOL_KISAN] AudioRecord initialized successfully with audioSource=$srcName ($source)")
                    return record
                } else {
                    record.release()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "[BOL_KISAN] AudioRecord init attempt with audioSource=$source failed: ${e.message}")
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    suspend fun startRecording(
        maxDurationMs: Long = 8000L,
        silenceTimeoutMs: Long = 1800L,
        interactionId: String? = null,
        onAudioChunk: (ShortArray, Boolean) -> Unit = { _, _ -> }
    ): ByteArray = withContext(Dispatchers.IO) {
        val qId = interactionId ?: java.util.UUID.randomUUID().toString()
        val outputStream = ByteArrayOutputStream()
        val buffer = ShortArray(bufferSize / 2)
        val startTime = System.currentTimeMillis()

        var hasSpoken = false
        var lastVoiceTime = System.currentTimeMillis()
        var noiseFloorRms = 120.0 // Baseline initial estimate
        var framesProcessed = 0

        try {
            audioRecord = createAudioRecord()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: AudioRecord failed to initialize. State: ${audioRecord?.state} (interactionId=$qId)")
                return@withContext ByteArray(0)
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.i(TAG, "[BOL_KISAN] MIC_STARTED: interactionId=$qId, SampleRate=16000Hz, Channels=Mono, Format=PCM_16BIT, AudioSource=${audioRecord?.audioSource}")

            while (isRecording && currentCoroutineContext().isActive) {
                val now = System.currentTimeMillis()

                // Check max duration limit (8 seconds default)
                if (now - startTime > maxDurationMs) {
                    Log.i(TAG, "[BOL_KISAN] RECORDING_MAX_TIMEOUT: Reached ${maxDurationMs}ms ceiling, stopping")
                    break
                }

                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readCount > 0) {
                    framesProcessed++
                    val rms = calculateRMS(buffer, readCount)

                    // Dynamic noise floor estimation:
                    // Continuously track ambient background noise floor using an exponential moving average
                    // when speech has not been detected or during initial frames
                    if (!hasSpoken) {
                        noiseFloorRms = if (framesProcessed <= 3) {
                            maxOf(40.0, rms)
                        } else {
                            (0.85 * noiseFloorRms) + (0.15 * minOf(rms, noiseFloorRms * 1.5))
                        }
                    }
                    currentNoiseFloorRms = noiseFloorRms

                    // Adaptive VAD threshold:
                    // Scale threshold dynamically above ambient noise floor with a calibrated SNR margin
                    val adaptiveThreshold = maxOf(baseMinVadThreshold, (noiseFloorRms * 2.2) + 80.0)
                    currentAdaptiveVadThreshold = adaptiveThreshold

                    val isVoice = rms > adaptiveThreshold

                    if (isVoice) {
                        hasSpoken = true
                        lastVoiceTime = now
                    }

                    onAudioChunk(buffer.copyOf(readCount), isVoice)

                    // Write 16-bit little-endian PCM bytes
                    for (i in 0 until readCount) {
                        val sample = buffer[i].toInt()
                        outputStream.write(sample and 0xFF)
                        outputStream.write((sample shr 8) and 0xFF)
                    }

                    // End-of-speech silence detection:
                    // If user has started speaking and then paused for silenceTimeoutMs, stop recording automatically
                    if (hasSpoken && (now - lastVoiceTime > silenceTimeoutMs)) {
                        Log.i(TAG, "[BOL_KISAN] RECORDING_VAD_SILENCE: End of speech detected (${silenceTimeoutMs}ms silence, noiseFloor=${noiseFloorRms.toInt()}, threshold=${adaptiveThreshold.toInt()}), stopping")
                        break
                    }
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: Audio recording failed: ${e.message}", e)
        } finally {
            stopRecording()
        }

        val recordedBytes = outputStream.toByteArray()
        val durationMs = System.currentTimeMillis() - startTime
        val normalizedBytes = normalizePcm16(recordedBytes)
        Log.i(TAG, "[BOL_KISAN] AUDIO_CAPTURED: interactionId=$qId, Duration=${durationMs}ms, Size=${normalizedBytes.size} bytes (${normalizedBytes.size / 2} samples), Peak=$lastRecordingPeak, RMS=${String.format(java.util.Locale.US, "%.1f", lastRecordingRms)}")
        normalizedBytes
    }

    fun normalizePcm16(pcmBytes: ByteArray, targetPeak: Short = 26000): ByteArray {
        if (pcmBytes.size < 2) return pcmBytes
        var currentPeak = 0
        val sampleCount = pcmBytes.size / 2
        var sumSquares = 0.0

        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = ((high shl 8) or low).toShort()
            val absSample = kotlin.math.abs(sample.toInt())
            if (absSample > currentPeak) currentPeak = absSample
            sumSquares += sample.toDouble() * sample.toDouble()
        }

        lastRecordingPeak = currentPeak
        lastRecordingRms = if (sampleCount > 0) sqrt(sumSquares / sampleCount) else 0.0

        if (currentPeak < 400 || currentPeak >= targetPeak) {
            return pcmBytes // Audio is either silent or already adequately amplified
        }

        val gain = minOf(6.0f, targetPeak.toFloat() / currentPeak.toFloat())
        val normalized = ByteArray(pcmBytes.size)
        var newPeak = 0
        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = ((high shl 8) or low).toShort()
            val boosted = (sample * gain).toInt().coerceIn(-32768, 32767).toShort()
            val absB = kotlin.math.abs(boosted.toInt())
            if (absB > newPeak) newPeak = absB
            normalized[i * 2] = (boosted.toInt() and 0xFF).toByte()
            normalized[i * 2 + 1] = ((boosted.toInt() shr 8) and 0xFF).toByte()
        }
        lastRecordingPeak = newPeak
        Log.i(TAG, "[BOL_KISAN] AUDIO_NORMALIZED: OriginalPeak=$currentPeak -> NewPeak=$newPeak, GainApplied=${String.format(java.util.Locale.US, "%.2f", gain)}x")
        return normalized
    }

    fun stopRecording() {
        isRecording = false
        try {
            val record = audioRecord
            if (record != null) {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "[BOL_KISAN] PIPELINE_ERROR: Error stopping AudioRecord: ${e.message}", e)
        } finally {
            audioRecord = null
        }
    }

    fun isCurrentlyRecording(): Boolean = isRecording

    private fun calculateRMS(buffer: ShortArray, readCount: Int): Double {
        if (readCount <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until readCount) {
            sum += buffer[i].toDouble() * buffer[i].toDouble()
        }
        return sqrt(sum / readCount)
    }
}
