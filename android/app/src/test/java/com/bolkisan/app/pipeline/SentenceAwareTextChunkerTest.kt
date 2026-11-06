package com.bolkisan.app.pipeline

import org.junit.Assert.*
import org.junit.Test

class SentenceAwareTextChunkerTest {

    @Test
    fun testShortAnswer() {
        val input = "Wear gloves while spraying neem oil."
        val chunks = SentenceAwareTextChunker.chunkText(input)
        assertEquals(1, chunks.size)
        assertEquals("Wear gloves while spraying neem oil.", chunks[0])
    }

    @Test
    fun testMediumAnswerTwoSentences() {
        val input = "Apply balanced fertilizer for cotton. Water the crop at regular weekly intervals."
        val chunks = SentenceAwareTextChunker.chunkText(input)
        assertTrue(chunks.isNotEmpty())
        val combined = chunks.joinToString(" ")
        assertTrue(combined.contains("Apply balanced fertilizer"))
        assertTrue(combined.contains("weekly intervals."))
    }

    @Test
    fun testLongAnswerWithMultipleParagraphs() {
        val input = """
            Cotton pink bollworm requires integrated pest management.
            
            Install 8 to 10 pheromone traps per acre across the field.
            
            If damage exceeds economic threshold, apply Chlorantraniliprole 18.5% SC at 60 ml per acre.
        """.trimIndent()
        val chunks = SentenceAwareTextChunker.chunkText(input, maxChunkLen = 200)
        assertTrue(chunks.size >= 2)
        for (chunk in chunks) {
            assertTrue("Chunk length ${chunk.length} should be <= 240", chunk.length <= 240)
        }
        val combined = chunks.joinToString(" ")
        assertTrue(combined.contains("18.5% SC"))
    }

    @Test
    fun testVeryLongAnswerTenSentences() {
        val sentences = (1..10).map {
            "Sentence number $it explains an essential agronomic practice for managing crop health and disease resistance."
        }.joinToString(" ")

        val chunks = SentenceAwareTextChunker.chunkText(sentences, targetChunkLen = 180, maxChunkLen = 240)
        assertTrue("Should produce multiple chunks for 10 sentences", chunks.size in 5..10)
        for (chunk in chunks) {
            assertTrue("Chunk length ${chunk.length} should be <= 240", chunk.length <= 240)
        }
        val combined = chunks.joinToString(" ")
        for (i in 1..10) {
            assertTrue("Must contain sentence $i", combined.contains("Sentence number $i"))
        }
    }

    @Test
    fun testAnswerWithBulletPointsAndMarkdown() {
        val input = """
            # Cotton Pest Management:
            • **1.** Install 8 pheromone traps per acre.
            • **2.** Spray Neem oil 10,000 PPM (5 ml/L) in early vegetative stage.
            • **3.** Spray Emamectin Benzoate 5% SG at 88-100 g/acre.
            • **4.** Never use Monocrotophos or Paraquat.
        """.trimIndent()

        val chunks = SentenceAwareTextChunker.chunkText(input, maxChunkLen = 220)
        assertTrue(chunks.size >= 2)
        val combined = chunks.joinToString(" ")
        assertFalse("Markdown asterisks should be stripped", combined.contains("**"))
        assertFalse("Markdown hash should be stripped", combined.contains("#"))
        assertTrue("Content must be preserved", combined.contains("pheromone traps"))
        assertTrue("Content must be preserved", combined.contains("Neem oil 10,000 PPM"))
        assertTrue("Content must be preserved", combined.contains("5% SG"))
        assertTrue("Banned chemical warning preserved", combined.contains("Monocrotophos"))
    }

    @Test
    fun testAgriculturalTerminologyWithDecimals() {
        val input = "Apply Chlorantraniliprole 18.5% SC at 60 ml/acre or Tricyclazole 75% WP at 0.6 g/L in 200 L water."
        val chunks = SentenceAwareTextChunker.chunkText(input)
        assertEquals(1, chunks.size)
        // Decimals like 18.5% and 0.6 g/L must not be broken
        assertTrue("Decimal 18.5% preserved", chunks[0].contains("18.5%"))
        assertTrue("Decimal 0.6 g/L preserved", chunks[0].contains("0.6 g/L"))
    }

    @Test
    fun testTeluguLongResponse() {
        val teluguInput = """
            పత్తి పంటలో గులాబీ రంగు కాయతొలుచు పురుగు నివారణకు ఎకరానికి 8 లింగాకర్షక బుట్టలను అమర్చాలి.
            వేప నూనె 10,000 పీపీఎం 5 మిల్లీ లీటర్లు లీటరు నీటికి కలిపి పిచికారీ చేయాలి.
            పురుగు తీవ్రత ఎక్కువగా ఉన్నప్పుడు క్లోరాంట్రానిలిప్రోల్ 18.5% ఎస్.సి 60 మి.లీ లేదా ఇమామెక్టిన్ బెంజోయేట్ 5% ఎస్.జి 80 గ్రాములు ఎకరానికి 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి.
            మోనోక్రోటోఫాస్ వంటి నిషేధిత పురుగుమందులను ఎట్టి పరిస్థితుల్లోనూ వాడవద్దు.
        """.trimIndent()

        val chunks = SentenceAwareTextChunker.chunkText(teluguInput, targetChunkLen = 160, maxChunkLen = 220)
        assertTrue("Telugu text must yield multiple chunks", chunks.size >= 2)
        for (chunk in chunks) {
            assertTrue("Chunk length ${chunk.length} should be <= 240", chunk.length <= 240)
            assertTrue("Chunk must contain Telugu text", chunk.any { it in '\u0C00'..'\u0C7F' })
        }
        val combined = chunks.joinToString(" ")
        assertTrue(combined.contains("లింగాకర్షక బుట్టలను"))
        assertTrue(combined.contains("18.5% ఎస్.సి"))
        assertTrue(combined.contains("నిషేధిత"))
    }

    @Test
    fun testHindiLongResponseWithPurnaViram() {
        val hindiInput = """
            कपास में गुलाबी सुंडी के नियंत्रण के लिए प्रति एकड़ 8 फेरोमोन ट्रैप लगाएं।
            जैविक नियंत्रण के लिए नीम तेल 10,000 पीपीएम (5 मिली प्रति लीटर पानी) का छिड़काव करें।
            आर्थिक क्षति स्तर पार होने पर क्लोरेंट्रानिलिप्रोल 18.5% एससी 60 मिली प्रति एकड़ अथवा इमामेक्टिन बेंजोएट 5% एसजी 80-100 ग्राम प्रति एकड़ 200 लीटर पानी में मिलाकर छिड़काव करें।
            मोनोक्रोटोफॉस और पैराक्वाट जैसे प्रतिबंधित रसायनों का कदापि प्रयोग न करें।
        """.trimIndent()

        val chunks = SentenceAwareTextChunker.chunkText(hindiInput, targetChunkLen = 160, maxChunkLen = 220)
        assertTrue("Hindi text must yield multiple chunks", chunks.size >= 2)
        for (chunk in chunks) {
            assertTrue("Chunk length ${chunk.length} should be <= 240", chunk.length <= 240)
            assertTrue("Chunk must contain Hindi Devanagari text", chunk.any { it in '\u0900'..'\u097F' })
        }
        val combined = chunks.joinToString(" ")
        assertTrue(combined.contains("फेरोमोन ट्रैप"))
        assertTrue(combined.contains("18.5% एससी"))
        assertTrue(combined.contains("प्रतिबंधित रसायनों"))
    }

    @Test
    fun testEmptyAndWhitespaceInput() {
        val emptyChunks = SentenceAwareTextChunker.chunkText("")
        assertTrue(emptyChunks.isEmpty())
        val whitespaceChunks = SentenceAwareTextChunker.chunkText("   \n\n\t   ")
        assertTrue(whitespaceChunks.isEmpty())
    }
}
