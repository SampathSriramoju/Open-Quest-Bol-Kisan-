package com.bolkisan.app.data.local.daos

import androidx.room.*
import com.bolkisan.app.data.local.entities.DocumentChunkEntity
import com.bolkisan.app.data.local.entities.DocumentEntity

data class ChunkWithDoc(
    val id: String,
    val documentId: String,
    val pageNumber: Int,
    val chunkIndex: Int,
    val content: String,
    val cropTag: String,
    val topicTag: String,
    val docTitle: String,
    val issuingAuthority: String
)

@Dao
interface DocumentDao {
    @Query("""
        SELECT c.*, d.title as docTitle, d.issuingAuthority as issuingAuthority
        FROM local_chunks c
        JOIN local_documents d ON c.documentId = d.id
        WHERE c.content LIKE '%' || :keyword || '%'
        LIMIT 5
    """)
    suspend fun searchChunksByKeyword(keyword: String): List<ChunkWithDoc>

    @Query("""
        SELECT c.*, d.title as docTitle, d.issuingAuthority as issuingAuthority
        FROM local_chunks c
        JOIN local_documents d ON c.documentId = d.id
        WHERE c.cropTag = :crop
        LIMIT 10
    """)
    suspend fun getChunksByCrop(crop: String): List<ChunkWithDoc>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(docs: List<DocumentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>)

    @Query("SELECT COUNT(*) FROM local_documents")
    suspend fun getDocumentCount(): Int
}
