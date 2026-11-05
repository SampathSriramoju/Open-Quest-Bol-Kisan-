package com.bolkisan.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bolkisan.app.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class BolKisanSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Configurable backend sync URL (Render / Supabase / Local Host)
    private val backendUrl = "https://bol-kisan-backend.onrender.com/api/v1/sync/batch"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getInstance(applicationContext)
        val interactionDao = database.interactionDao()
        val syncQueueDao = database.syncQueueDao()

        val pendingInteractions = interactionDao.getPendingUnsyncedInteractions()
        if (pendingInteractions.isEmpty()) {
            return@withContext Result.success()
        }

        try {
            val syncPayload = JSONObject()
            syncPayload.put("device_fingerprint", "android_${android.os.Build.FINGERPRINT.hashCode()}")
            syncPayload.put("client_sync_id", UUID.randomUUID().toString()) // Idempotency key

            val interactionsArray = JSONArray()
            pendingInteractions.forEach { inter ->
                val item = JSONObject()
                item.put("client_interaction_id", inter.id)
                item.put("session_id", inter.sessionId)
                item.put("client_timestamp", inter.timestamp)
                item.put("detected_language", inter.detectedLanguage)
                item.put("raw_transcript", inter.rawTranscript)
                item.put("response_text", inter.responseText)
                item.put("response_scenario", inter.responseScenario)
                item.put("asr_model_used", "sherpa-onnx-indic-conformer-offline")
                item.put("chemical_flag", inter.isChemicalFlagged)
                interactionsArray.put(item)
            }
            syncPayload.put("interactions", interactionsArray)
            syncPayload.put("escalations", JSONArray())

            val body = syncPayload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(backendUrl)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                // Acknowledged by server: safely mark as synced locally
                val syncedIds = pendingInteractions.map { it.id }
                interactionDao.markAsSynced(syncedIds)
                Result.success()
            } else {
                // Server unavailable or returned 5xx; keep records locally for next retry
                Result.retry()
            }
        } catch (e: Exception) {
            // Offline or network error: never delete records, retry when network returns
            Result.retry()
        }
    }
}
