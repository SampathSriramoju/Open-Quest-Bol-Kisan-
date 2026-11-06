package com.bolkisan.app.pipeline

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class OnlineAdvisoryResult(
    val query: String,
    val detectedLanguage: String,
    val scenario: String,
    val answer: String,
    val spokenAnswer: String,
    val citationDoc: String?,
    val citationPage: Int,
    val modelProvider: String?,
    val modelName: String?,
    val isFallback: Boolean,
    val latencyMs: Int
)

class OnlineAdvisoryApiClient {

    companion object {
        private const val TAG = "BolKisanOnlineApi"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // Shared HTTP client with connection pooling and fast timeouts
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(4, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    suspend fun fetchAdvisory(
        baseUrl: String,
        query: String,
        language: String,
        crop: String,
        region: String = "Telangana"
    ): Result<OnlineAdvisoryResult> = withContext(Dispatchers.IO) {
        try {
            val endpointUrl = "${baseUrl.trimEnd('/')}/api/v1/rag/query"
            Log.i(TAG, "[BOL_KISAN_ONLINE] Querying endpoint: $endpointUrl (Lang=$language, Crop=$crop)")

            val jsonBody = JSONObject().apply {
                put("query", query)
                put("language", language)
                put("crop", crop)
                put("region", region)
                put("mode", "online")
            }

            val requestBody = jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(endpointUrl)
                .post(requestBody)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "BolKisan-Android-App/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                val code = response.code
                Log.w(TAG, "[BOL_KISAN_ONLINE] Backend error HTTP $code: $responseBody")
                return@withContext Result.failure(IOException("Server error HTTP $code: $responseBody"))
            }

            val json = JSONObject(responseBody)
            val scenario = json.optString("scenario", "EVIDENCE_AVAILABLE")
            val answer = json.optString("answer", "")
            val spokenAnswer = json.optString("spoken_answer", answer)
            val detectedLang = json.optString("detected_language", language)
            val modelProvider = json.optString("model_provider", "gemini")
            val modelName = json.optString("model_name", "gemini-2.5-flash")
            val isFallback = json.optBoolean("is_fallback", false)
            val latencyMs = json.optInt("latency_ms", 0)

            // Parse first citation if present
            var citationDoc: String? = null
            var citationPage = 1
            val citationsArr = json.optJSONArray("citations")
            if (citationsArr != null && citationsArr.length() > 0) {
                val firstCit = citationsArr.getJSONObject(0)
                val authority = firstCit.optString("issuing_authority", "PJTSAU")
                val title = firstCit.optString("document_title", "Advisory")
                citationDoc = "$authority: $title"
                citationPage = firstCit.optInt("page_number", 1)
            }

            Log.i(TAG, "[BOL_KISAN_ONLINE] Received online advisory (Scenario=$scenario, Provider=$modelProvider, Fallback=$isFallback, Latency=${latencyMs}ms)")

            val result = OnlineAdvisoryResult(
                query = query,
                detectedLanguage = detectedLang,
                scenario = scenario,
                answer = answer,
                spokenAnswer = spokenAnswer,
                citationDoc = citationDoc,
                citationPage = citationPage,
                modelProvider = modelProvider,
                modelName = modelName,
                isFallback = isFallback,
                latencyMs = latencyMs
            )

            Result.success(result)
        } catch (e: Exception) {
            Log.w(TAG, "[BOL_KISAN_ONLINE] Network request failed: ${e.message}")
            Result.failure(e)
        }
    }
}
