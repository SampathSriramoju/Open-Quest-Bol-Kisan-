package com.bolkisan.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interactions")
data class InteractionEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val detectedLanguage: String, // "te" or "hi"
    val rawTranscript: String,
    val responseText: String,
    val responseScenario: String, // EVIDENCE_AVAILABLE, INCOMPLETE_EVIDENCE, EVIDENCE_UNAVAILABLE_REFUSAL, CHEMICAL_SAFETY_BLOCK
    val citationDoc: String? = null,
    val citationPage: Int = 1,
    val isChemicalFlagged: Boolean = false,
    val isSynced: Boolean = false
)
