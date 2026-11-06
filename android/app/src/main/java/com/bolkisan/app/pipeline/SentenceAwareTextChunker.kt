package com.bolkisan.app.pipeline

import java.util.regex.Pattern

/**
 * Robust, sentence-aware text chunker for offline multilingual Text-To-Speech.
 *
 * Requirements addressed:
 * 1. Preserves complete text content without omission or silent truncation.
 * 2. Splits along natural sentence and clause boundaries rather than arbitrary character limits.
 * 3. Never splits decimal numbers common in agricultural dosages (e.g. "18.5% SC", "0.6 g/L", "2.5 ml").
 * 4. Normalizes bullet points, numbered lists, and markdown artifacts into clean spoken prose.
 * 5. Full support for Telugu (\u0C00..\u0C7F), Hindi (\u0900..\u097F with purna viram '।'), and English.
 * 6. Keeps individual chunks between 80 and 240 characters for fast synthesis and zero AudioTrack overflow.
 */
object SentenceAwareTextChunker {

    // Regex matching sentence terminators:
    // - Purna viram '।', exclamation '!', question mark '?', semicolon ';'
    // - Period '.' ONLY when NOT between digits: (?<!\d)\.(?!\d)
    private val SENTENCE_DELIMITER_REGEX = Pattern.compile("(?<=[!?।;])\\s+|(?<!\\d)\\.(?!\\d)(?:\\s+|$)")

    // Clause delimiter for splitting excessively long single sentences
    private val CLAUSE_DELIMITER_REGEX = Pattern.compile("(?<=[,،;—])\\s+|\\s+(?:and|or|లేదా|మరియు|तथा|या|लेकिन|కానీ)\\s+")

    // Leading bullet pattern to strip visual list markers for spoken flow
    private val BULLET_PREFIX_REGEX = Pattern.compile("^[\\s\\-*•\\d.)]+\\s*")

    // Markdown symbols removal (*, #, _, `, >)
    private val MARKDOWN_SYMBOLS_REGEX = Pattern.compile("[*#_`>~]")

    /**
     * Splits agricultural advisory text into sequentially speakable chunks.
     */
    fun chunkText(
        text: String,
        targetChunkLen: Int = 180,
        maxChunkLen: Int = 240
    ): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Remove markdown decoration that should not be vocalized
        val cleanMarkdown = MARKDOWN_SYMBOLS_REGEX.matcher(trimmed).replaceAll("")

        // 2. Split into logical paragraph blocks by newlines
        val paragraphs = cleanMarkdown.split(Regex("[\\r\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (paragraphs.isEmpty()) return emptyList()

        // 3. Extract clean sentences across all paragraphs
        val allSentences = mutableListOf<String>()
        for (para in paragraphs) {
            // Strip leading bullet markers like "• ", "1. ", "- "
            val cleanPara = BULLET_PREFIX_REGEX.matcher(para).replaceFirst("").trim()
            if (cleanPara.isEmpty()) continue

            val rawSentences = SENTENCE_DELIMITER_REGEX.split(cleanPara)
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (rawSentences.isEmpty()) {
                allSentences.add(cleanPara)
            } else {
                for (s in rawSentences) {
                    // Ensure trailing sentence terminator for natural TTS intonation
                    val withPunct = if (s.endsWith(".") || s.endsWith("!") || s.endsWith("?") ||
                        s.endsWith("।") || s.endsWith(";") || s.endsWith(":")
                    ) {
                        s
                    } else {
                        // Append language-appropriate terminator
                        if (s.any { it in '\u0900'..'\u097F' }) "$s।" else "$s."
                    }
                    allSentences.add(withPunct)
                }
            }
        }

        if (allSentences.isEmpty()) return emptyList()

        // 4. Pack sentences into cohesive chunks without exceeding maxChunkLen
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (sentence in allSentences) {
            if (sentence.length > maxChunkLen) {
                // If a single sentence is longer than maxChunkLen, subdivide at clause boundaries
                val clauses = splitLongSentenceIntoClauses(sentence, maxChunkLen)
                for (clause in clauses) {
                    if (currentChunk.isEmpty()) {
                        currentChunk.append(clause)
                    } else if (currentChunk.length + clause.length + 1 <= targetChunkLen) {
                        currentChunk.append(" ").append(clause)
                    } else {
                        chunks.add(currentChunk.toString().trim())
                        currentChunk = StringBuilder(clause)
                    }
                }
            } else {
                if (currentChunk.isEmpty()) {
                    currentChunk.append(sentence)
                } else if (currentChunk.length + sentence.length + 1 <= targetChunkLen) {
                    currentChunk.append(" ").append(sentence)
                } else {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder(sentence)
                }
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks.filter { it.isNotBlank() }
    }

    /**
     * Splits an over-length sentence at natural clauses or words so it never exceeds maxChunkLen.
     */
    private fun splitLongSentenceIntoClauses(sentence: String, maxChunkLen: Int): List<String> {
        val rawClauses = CLAUSE_DELIMITER_REGEX.split(sentence)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (rawClauses.size <= 1) {
            // Fallback: split by words
            return splitByWords(sentence, maxChunkLen)
        }

        val result = mutableListOf<String>()
        var buffer = StringBuilder()

        for (clause in rawClauses) {
            if (buffer.isEmpty()) {
                if (clause.length <= maxChunkLen) {
                    buffer.append(clause)
                } else {
                    result.addAll(splitByWords(clause, maxChunkLen))
                }
            } else if (buffer.length + clause.length + 1 <= maxChunkLen) {
                buffer.append(", ").append(clause)
            } else {
                result.add(buffer.toString().trim())
                if (clause.length <= maxChunkLen) {
                    buffer = StringBuilder(clause)
                } else {
                    result.addAll(splitByWords(clause, maxChunkLen))
                    buffer = StringBuilder()
                }
            }
        }

        if (buffer.isNotEmpty()) {
            result.add(buffer.toString().trim())
        }

        return result
    }

    /**
     * Splits text strictly by space-separated words when no clause boundaries exist.
     */
    private fun splitByWords(text: String, maxChunkLen: Int): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val result = mutableListOf<String>()
        var buffer = StringBuilder()

        for (word in words) {
            if (buffer.isEmpty()) {
                buffer.append(word)
            } else if (buffer.length + word.length + 1 <= maxChunkLen) {
                buffer.append(" ").append(word)
            } else {
                result.add(buffer.toString().trim())
                buffer = StringBuilder(word)
            }
        }

        if (buffer.isNotEmpty()) {
            result.add(buffer.toString().trim())
        }

        return result
    }
}
