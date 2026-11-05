package com.bolkisan.app.data.packs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bolkisan.app.data.local.AppDatabase
import com.bolkisan.app.data.local.daos.ChunkWithDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Metadata for installed modular crop knowledge packs
 */
data class InstalledPackInfo(
    val cropTag: String,
    val version: String,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val lastModified: Long,
    val documentCount: Int = 0,
    val chunkCount: Int = 0
)

/**
 * KnowledgePackManager coordinates modular, client-side agricultural knowledge packs.
 *
 * Architecture Principles:
 * 1. Zero APK Bloat: Speech models occupy ~560 MB of weights. All supplemental crop databases
 *    and regional agronomic guides are stored externally in `context.getExternalFilesDir("knowledge_packs")`
 *    and NEVER bundled into the APK assets.
 * 2. Modular Extension: Core knowledge (Cotton, Paddy, Chilli, Groundnut, Red Gram + CIBRC Safety)
 *    lives in the primary Room DB (`bol_kisan_offline_room.db`). Extra crops (e.g. Maize, Sugarcane, Soybean)
 *    are packaged as standalone SQLite databases (`knowledge_pack_<crop>_<version>.db`).
 * 3. Federated Querying: Queries are executed across installed packs using isolated read-only SQLite
 *    access or dynamic SQLite ATTACH DATABASE without locking the primary Room database.
 */
class KnowledgePackManager(
    private val context: Context? = null,
    private val appDatabase: AppDatabase? = null,
    private val customDirectory: File? = null
) {

    private val TAG = "BolKisanPacks"

    val packDirectory: File
        get() {
            if (customDirectory != null) {
                if (!customDirectory.exists()) customDirectory.mkdirs()
                return customDirectory
            }
            val ctx = context ?: throw IllegalStateException("Context required when customDirectory is null")
            val externalDir = ctx.getExternalFilesDir("knowledge_packs")
            val dir = externalDir ?: File(ctx.filesDir, "knowledge_packs")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Lists all installed knowledge packs in the external files directory.
     */
    suspend fun listInstalledPacks(): List<InstalledPackInfo> = withContext(Dispatchers.IO) {
        val packFiles = packDirectory.listFiles { _, name ->
            name.startsWith("knowledge_pack_") && name.endsWith(".db")
        } ?: emptyArray()

        packFiles.map { file ->
            val nameWithoutExt = file.nameWithoutExtension
            val parts = nameWithoutExt.split("_")
            val crop = if (parts.size >= 3) parts[2].replaceFirstChar { it.uppercase() } else "Unknown"
            val version = if (parts.size >= 4) parts[3] else "v1"

            var docCount = 0
            var chunkCount = 0

            try {
                SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("SELECT COUNT(*) FROM local_documents", null).use { cursor ->
                        if (cursor.moveToFirst()) docCount = cursor.getInt(0)
                    }
                    db.rawQuery("SELECT COUNT(*) FROM local_chunks", null).use { cursor ->
                        if (cursor.moveToFirst()) chunkCount = cursor.getInt(0)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read counts for pack ${file.name}: ${e.message}")
            }

            InstalledPackInfo(
                cropTag = crop,
                version = version,
                fileName = file.name,
                filePath = file.absolutePath,
                fileSizeBytes = file.length(),
                lastModified = file.lastModified(),
                documentCount = docCount,
                chunkCount = chunkCount
            )
        }
    }

    /**
     * Searches across all installed knowledge pack databases for matching document chunks.
     */
    suspend fun searchPackChunks(keyword: String, crop: String? = null): List<ChunkWithDoc> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ChunkWithDoc>()
        val packFiles = packDirectory.listFiles { _, name ->
            name.startsWith("knowledge_pack_") && name.endsWith(".db")
        } ?: return@withContext emptyList()

        for (file in packFiles) {
            try {
                SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    val query = """
                        SELECT c.id, c.documentId, c.pageNumber, c.chunkIndex, c.content, c.cropTag, c.topicTag,
                               d.title as docTitle, d.issuingAuthority as issuingAuthority
                        FROM local_chunks c
                        JOIN local_documents d ON c.documentId = d.id
                        WHERE c.content LIKE '%' || ? || '%'
                        LIMIT 5
                    """.trimIndent()

                    db.rawQuery(query, arrayOf(keyword)).use { cursor ->
                        val idIdx = cursor.getColumnIndex("id")
                        val docIdIdx = cursor.getColumnIndex("documentId")
                        val pageIdx = cursor.getColumnIndex("pageNumber")
                        val chunkIdx = cursor.getColumnIndex("chunkIndex")
                        val contentIdx = cursor.getColumnIndex("content")
                        val cropTagIdx = cursor.getColumnIndex("cropTag")
                        val topicTagIdx = cursor.getColumnIndex("topicTag")
                        val docTitleIdx = cursor.getColumnIndex("docTitle")
                        val issuingAuthorityIdx = cursor.getColumnIndex("issuingAuthority")

                        while (cursor.moveToNext()) {
                            results.add(
                                ChunkWithDoc(
                                    id = if (idIdx >= 0) cursor.getString(idIdx) else "",
                                    documentId = if (docIdIdx >= 0) cursor.getString(docIdIdx) else "",
                                    pageNumber = if (pageIdx >= 0) cursor.getInt(pageIdx) else 1,
                                    chunkIndex = if (chunkIdx >= 0) cursor.getInt(chunkIdx) else 0,
                                    content = if (contentIdx >= 0) cursor.getString(contentIdx) else "",
                                    cropTag = if (cropTagIdx >= 0) cursor.getString(cropTagIdx) else (crop ?: "General"),
                                    topicTag = if (topicTagIdx >= 0) cursor.getString(topicTagIdx) else "Agronomy",
                                    docTitle = if (docTitleIdx >= 0) cursor.getString(docTitleIdx) else "Official Agricultural Advisory",
                                    issuingAuthority = if (issuingAuthorityIdx >= 0) cursor.getString(issuingAuthorityIdx) else "Agricultural Extension"
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query knowledge pack ${file.name}: ${e.message}")
            }
        }

        results
    }

    /**
     * Dynamically attaches a knowledge pack to an open SupportSQLiteDatabase using SQLite ATTACH DATABASE.
     */
    fun attachPackToDatabase(db: SupportSQLiteDatabase, packFile: File, alias: String): Boolean {
        return try {
            db.execSQL("ATTACH DATABASE ? AS ?", arrayOf(packFile.absolutePath, alias))
            Log.i(TAG, "[BOL_KISAN] ATTACH_DATABASE_SUCCESS: ${packFile.name} as $alias")
            true
        } catch (e: Exception) {
            Log.e(TAG, "[BOL_KISAN] ATTACH_DATABASE_ERROR: ${packFile.name}: ${e.message}", e)
            false
        }
    }

    /**
     * Detaches an attached knowledge pack from an open SupportSQLiteDatabase.
     */
    fun detachPackFromDatabase(db: SupportSQLiteDatabase, alias: String): Boolean {
        return try {
            db.execSQL("DETACH DATABASE ?", arrayOf(alias))
            Log.i(TAG, "[BOL_KISAN] DETACH_DATABASE_SUCCESS: $alias")
            true
        } catch (e: Exception) {
            Log.e(TAG, "[BOL_KISAN] DETACH_DATABASE_ERROR: $alias: ${e.message}", e)
            false
        }
    }

    /**
     * Installs or updates a knowledge pack from an input stream (e.g., downloaded from server or transferred peer-to-peer).
     */
    suspend fun installPack(
        inputStream: InputStream,
        crop: String,
        version: String
    ): File = withContext(Dispatchers.IO) {
        val safeCrop = crop.lowercase().trim()
        val safeVersion = version.lowercase().trim()
        val targetFile = File(packDirectory, "knowledge_pack_${safeCrop}_${safeVersion}.db")

        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        Log.i(TAG, "[BOL_KISAN] Installed modular knowledge pack: ${targetFile.name} (${targetFile.length()} bytes)")
        targetFile
    }

    /**
     * Deletes a knowledge pack to free up device storage.
     */
    suspend fun deletePack(crop: String): Boolean = withContext(Dispatchers.IO) {
        val safeCrop = crop.lowercase().trim()
        val packFiles = packDirectory.listFiles { _, name ->
            name.startsWith("knowledge_pack_${safeCrop}_") && name.endsWith(".db")
        } ?: return@withContext false

        var deletedAll = true
        for (file in packFiles) {
            if (!file.delete()) {
                deletedAll = false
            }
        }
        deletedAll
    }

    /**
     * Computes the total storage footprint of installed knowledge packs in bytes.
     */
    suspend fun getTotalPackStorageBytes(): Long = withContext(Dispatchers.IO) {
        val files = packDirectory.listFiles() ?: return@withContext 0L
        files.sumOf { it.length() }
    }
}
