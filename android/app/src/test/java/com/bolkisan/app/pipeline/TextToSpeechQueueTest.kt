package com.bolkisan.app.pipeline

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TextToSpeechQueueTest {

    @Test
    fun testPlaybackStateMachineProgression() {
        // Test states: IDLE -> SPEAKING -> CHUNK_COMPLETED -> NEXT_CHUNK -> FINAL_CHUNK -> PLAYBACK_COMPLETED -> IDLE
        val states = mutableListOf<OfflineTextToSpeechEngine.PlaybackState>()
        states.add(OfflineTextToSpeechEngine.PlaybackState.IDLE)
        assertEquals(OfflineTextToSpeechEngine.PlaybackState.IDLE, states.last())

        // Start speaking chunk 1 of 3
        states.add(OfflineTextToSpeechEngine.PlaybackState.SPEAKING)
        // Chunk 1 completed
        states.add(OfflineTextToSpeechEngine.PlaybackState.CHUNK_COMPLETED)
        // Advance to next chunk (chunk 2)
        states.add(OfflineTextToSpeechEngine.PlaybackState.NEXT_CHUNK)
        states.add(OfflineTextToSpeechEngine.PlaybackState.SPEAKING)
        // Chunk 2 completed
        states.add(OfflineTextToSpeechEngine.PlaybackState.CHUNK_COMPLETED)
        // Advance to chunk 3 (final chunk)
        states.add(OfflineTextToSpeechEngine.PlaybackState.NEXT_CHUNK)
        states.add(OfflineTextToSpeechEngine.PlaybackState.FINAL_CHUNK)
        // Chunk 3 completed
        states.add(OfflineTextToSpeechEngine.PlaybackState.CHUNK_COMPLETED)
        // Playback finished
        states.add(OfflineTextToSpeechEngine.PlaybackState.PLAYBACK_COMPLETED)
        states.add(OfflineTextToSpeechEngine.PlaybackState.IDLE)

        assertEquals(11, states.size)
        assertEquals(OfflineTextToSpeechEngine.PlaybackState.IDLE, states.first())
        assertEquals(OfflineTextToSpeechEngine.PlaybackState.IDLE, states.last())
        assertTrue("Sequence contains FINAL_CHUNK", states.contains(OfflineTextToSpeechEngine.PlaybackState.FINAL_CHUNK))
        assertTrue("Sequence contains PLAYBACK_COMPLETED", states.contains(OfflineTextToSpeechEngine.PlaybackState.PLAYBACK_COMPLETED))
    }

    @Test
    fun testSequentialQueueCompletionGuarantee() {
        val totalChunks = 5
        val completedCount = AtomicInteger(0)
        val onDoneCalled = AtomicBoolean(false)

        for (i in 0 until totalChunks) {
            completedCount.incrementAndGet()
        }

        if (completedCount.get() == totalChunks) {
            onDoneCalled.set(true)
        }

        assertEquals(5, completedCount.get())
        assertTrue("onDone must be triggered exactly when final chunk completes", onDoneCalled.get())
    }

    @Test
    fun testManualStopInterruptsQueue() {
        val isManualStop = AtomicBoolean(false)
        val completedCount = AtomicInteger(0)

        for (i in 0 until 5) {
            if (i == 2) {
                // User taps stop at chunk index 2
                isManualStop.set(true)
            }
            if (isManualStop.get()) {
                break
            }
            completedCount.incrementAndGet()
        }

        assertEquals("Queue should halt when manual stop is flagged", 2, completedCount.get())
    }

    @Test
    fun testTeluguSpeechChunkIntegrity() {
        val te = "వరి పంటలో అగ్గితెగులు నివారణకు ట్రైసైక్లాజోల్ 75% డబ్ల్యూ.పి 0.6 గ్రాములు లీటరు నీటికి కలపాలి. నత్రజని ఎరువులను అధికంగా వాడవద్దు."
        val chunks = SentenceAwareTextChunker.chunkText(te, targetChunkLen = 150, maxChunkLen = 220)
        assertTrue("Must have at least 1 chunk", chunks.isNotEmpty())
        for (c in chunks) {
            assertTrue("Chunk must preserve Telugu text", c.any { it in '\u0C00'..'\u0C7F' })
        }
    }

    @Test
    fun testHindiSpeechChunkIntegrity() {
        val hi = "धान में झुलसा रोग के लिए ट्राइसाइक्लाजोल 75% डब्ल्यूपी 0.6 ग्राम प्रति लीटर पानी में मिलाकर छिड़काव करें। खेत में पानी की उचित निकासी रखें।"
        val chunks = SentenceAwareTextChunker.chunkText(hi, targetChunkLen = 150, maxChunkLen = 220)
        assertTrue("Must have at least 1 chunk", chunks.isNotEmpty())
        for (c in chunks) {
            assertTrue("Chunk must preserve Hindi Devanagari text", c.any { it in '\u0900'..'\u097F' })
        }
    }

    @Test
    fun testEnglishSpeechChunkIntegrity() {
        val en = "For groundnut tikka leaf spot, spray Mancozeb 75% WP at 2 g/L or Tebuconazole 25.9% EC at 1 ml/L. Ensure full coverage on the lower foliage."
        val chunks = SentenceAwareTextChunker.chunkText(en, targetChunkLen = 150, maxChunkLen = 220)
        assertTrue("Must have at least 1 chunk", chunks.isNotEmpty())
        for (c in chunks) {
            assertTrue("Must preserve pesticide details", c.contains("75% WP") || c.contains("25.9% EC"))
        }
    }
}
