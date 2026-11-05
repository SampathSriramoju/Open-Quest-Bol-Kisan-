package com.bolkisan.app.data.local.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "local_documents")
data class DocumentEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val crop: String,
    val region: String,
    val language: String,
    val issuingAuthority: String,
    val publicationDate: String,
    val version: String,
    val sourceUrl: String = "",
    val verificationStatus: String = "VERIFIED_OFFICIAL",
    val approvalStatus: String = "APPROVED"
)

@Entity(tableName = "local_chunks")
data class DocumentChunkEntity(
    @PrimaryKey
    val id: String,
    val documentId: String,
    val pageNumber: Int,
    val chunkIndex: Int,
    val content: String,
    val cropTag: String,
    val topicTag: String
)

@Fts4(contentEntity = DocumentChunkEntity::class)
@Entity(tableName = "chunks_fts")
data class ChunkFtsEntity(
    val rowid: Int,
    val content: String,
    val cropTag: String
)
