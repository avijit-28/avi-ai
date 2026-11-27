package com.aviai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UiTranscriptTurn(
    val speaker: String,
    val text: String,
    val timestamp: String
)

data class UiCallSummary(
    val callerName: String?,
    val callPurpose: String,
    val callbackRequested: Boolean,
    val preferredCallbackTime: String?,
    val callbackNumber: String?,
    val urgency: String,
    val actionItems: List<String>,
    val fullSummary: String
)

data class UiCallRecord(
    val id: String,
    val callerNumber: String,
    val startTime: String,
    val durationSeconds: Int,
    val status: String,
    val summary: UiCallSummary?,
    val transcript: List<UiTranscriptTurn>,
    val audioUrl: String? = null
)

data class UiSettings(
    val ownerStatus: String = "Away",
    val customStatusNote: String = "",
    val assistantName: String = "Avi",
    val forwardingNumber: String = "+1 938 444 8902"
)

class CallRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchCalls(backendUrl: String): Result<List<UiCallRecord>> = withContext(Dispatchers.IO) {
        try {
            val url = if (backendUrl.endsWith("/")) "${backendUrl}api/v1/calls" else "$backendUrl/api/v1/calls"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: "[]"
            val jsonArray = JSONArray(body)
            val records = mutableListOf<UiCallRecord>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val summaryObj = obj.optJSONObject("summary")
                var summary: UiCallSummary? = null
                if (summaryObj != null) {
                    val actionsArr = summaryObj.optJSONArray("action_items") ?: JSONArray()
                    val actions = mutableListOf<String>()
                    for (j in 0 until actionsArr.length()) {
                        actions.add(actionsArr.getString(j))
                    }
                    summary = UiCallSummary(
                        callerName = if (summaryObj.isNull("caller_name")) null else summaryObj.optString("caller_name"),
                        callPurpose = summaryObj.optString("call_purpose", ""),
                        callbackRequested = summaryObj.optBoolean("callback_requested", false),
                        preferredCallbackTime = if (summaryObj.isNull("preferred_callback_time")) null else summaryObj.optString("preferred_callback_time"),
                        callbackNumber = if (summaryObj.isNull("callback_number")) null else summaryObj.optString("callback_number"),
                        urgency = summaryObj.optString("urgency", "Normal"),
                        actionItems = actions,
                        fullSummary = summaryObj.optString("full_summary", "")
                    )
                }

                val transcriptArr = obj.optJSONArray("transcript") ?: JSONArray()
                val transcript = mutableListOf<UiTranscriptTurn>()
                for (j in 0 until transcriptArr.length()) {
                    val turnObj = transcriptArr.getJSONObject(j)
                    transcript.add(UiTranscriptTurn(
                        speaker = turnObj.optString("speaker", "AI"),
                        text = turnObj.optString("text", ""),
                        timestamp = turnObj.optString("timestamp", "")
                    ))
                }

                val audioUrl = if (obj.isNull("audio_url")) null else obj.optString("audio_url")

                records.add(UiCallRecord(
                    id = obj.getString("id"),
                    callerNumber = obj.getString("caller_number"),
                    startTime = obj.getString("start_time"),
                    durationSeconds = obj.optInt("duration_seconds", 0),
                    status = obj.optString("status", "COMPLETED"),
                    summary = summary,
                    transcript = transcript,
                    audioUrl = audioUrl
                ))
            }

            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSettings(backendUrl: String): Result<UiSettings> = withContext(Dispatchers.IO) {
        try {
            val url = if (backendUrl.endsWith("/")) "${backendUrl}api/v1/settings" else "$backendUrl/api/v1/settings"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
            val body = response.body?.string() ?: "{}"
            val obj = JSONObject(body)
            Result.success(
                UiSettings(
                    ownerStatus = obj.optString("owner_status", "Away"),
                    customStatusNote = obj.optString("custom_status_note", ""),
                    assistantName = obj.optString("assistant_name", "Avi"),
                    forwardingNumber = obj.optString("forwarding_number", "+1 938 444 8902")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSettings(backendUrl: String, settings: UiSettings): Result<UiSettings> = withContext(Dispatchers.IO) {
        try {
            val url = if (backendUrl.endsWith("/")) "${backendUrl}api/v1/settings" else "$backendUrl/api/v1/settings"
            val json = JSONObject().apply {
                put("owner_status", settings.ownerStatus)
                put("custom_status_note", settings.customStatusNote)
                put("assistant_name", settings.assistantName)
                put("forwarding_number", settings.forwardingNumber)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
            Result.success(settings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
