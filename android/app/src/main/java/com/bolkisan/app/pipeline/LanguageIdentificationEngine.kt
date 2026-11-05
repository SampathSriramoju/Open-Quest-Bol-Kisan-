package com.bolkisan.app.pipeline

data class LanguageDetectionResult(
    val languageCode: String, // "te", "hi", "en"
    val displayName: String,
    val confidence: Float
)

typealias LanguageIdentificationResult = LanguageDetectionResult

class LanguageIdentificationEngine {

    companion object {
        private const val TELUGU_START = 0x0C00
        private const val TELUGU_END = 0x0C7F
        private const val DEVANAGARI_START = 0x0900
        private const val DEVANAGARI_END = 0x097F

        fun detectLanguage(input: String, fallback: String = "te"): LanguageDetectionResult {
            if (input.isBlank()) {
                val displayName = when (fallback) {
                    "te" -> "తెలుగు"
                    "hi" -> "हिन्दी"
                    else -> "English"
                }
                return LanguageDetectionResult(fallback, displayName, 0.5f)
            }

            var teluguChars = 0
            var hindiChars = 0
            var latinChars = 0

            for (ch in input) {
                val code = ch.code
                if (code in TELUGU_START..TELUGU_END) {
                    teluguChars++
                } else if (code in DEVANAGARI_START..DEVANAGARI_END) {
                    hindiChars++
                } else if ((ch in 'a'..'z') || (ch in 'A'..'Z')) {
                    latinChars++
                }
            }

            val totalIndic = teluguChars + hindiChars
            val totalAll = totalIndic + latinChars

            if (totalAll > 0) {
                if (latinChars > totalIndic) {
                    val conf = (latinChars.toFloat() / totalAll).coerceIn(0.7f, 0.98f)
                    return LanguageDetectionResult("en", "English", conf)
                } else if (teluguChars >= hindiChars && teluguChars > 0) {
                    val conf = (teluguChars.toFloat() / totalIndic).coerceIn(0.7f, 0.98f)
                    return LanguageDetectionResult("te", "తెలుగు (Telugu)", conf)
                } else if (hindiChars > 0) {
                    val conf = (hindiChars.toFloat() / totalIndic).coerceIn(0.7f, 0.98f)
                    return LanguageDetectionResult("hi", "हिन्दी (Hindi)", conf)
                }
            }

            // Phonetic Romanized checks
            val lower = input.lowercase()
            val teluguWords = listOf("namaskaram", "patti", "vari", "mirapa", "purugu", "mandhu", "aakulu", "pasupu", "chettu", "dhara", "mandi")
            val hindiWords = listOf("namaste", "dhan", "kapas", "mirch", "keet", "dawa", "kheton", "peele", "patte", "bhav", "mandi")
            val englishWords = listOf("what", "how", "spray", "cotton", "crop", "pest", "disease", "fertilizer", "price", "market", "problem", "control", "pink", "bollworm")

            val teScore = teluguWords.count { lower.contains(it) }
            val hiScore = hindiWords.count { lower.contains(it) }
            val enScore = englishWords.count { lower.contains(it) }

            return if (enScore > teScore && enScore > hiScore) {
                LanguageDetectionResult("en", "English", 0.90f)
            } else if (teScore > hiScore && teScore > enScore) {
                LanguageDetectionResult("te", "తెలుగు (Telugu)", 0.88f)
            } else if (hiScore > teScore && hiScore > enScore) {
                LanguageDetectionResult("hi", "हिन्दी (Hindi)", 0.88f)
            } else {
                val displayName = when (fallback) {
                    "te" -> "తెలుగు"
                    "hi" -> "हिन्दी"
                    else -> "English"
                }
                LanguageDetectionResult(fallback, displayName, 0.6f)
            }
        }
    }
}
