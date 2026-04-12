package com.torpedoes.smartsales.data.remote

import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedOrder(
    val items        : String,
    val total        : Double,
    val isCreditSale : Boolean,
    val note         : String = ""
)

@Singleton
class GeminiOrderParser @Inject constructor() {

    private val TAG    = "GroqOrderParser"
    private val client = OkHttpClient()
    private val apiKey = com.torpedoes.smartsales.BuildConfig.GROQ_API_KEY

    // Groq's OpenAI-compatible endpoint — llama-3.1-8b-instant is free & fast
    private val endpoint = "https://api.groq.com/openai/v1/chat/completions"
    private val model    = "llama-3.1-8b-instant"

    suspend fun parseOrder(rawMessage: String): ParsedOrder? {
        val systemPrompt = """
            You are a smart order parser for a small shop management app in India.
            When given a WhatsApp message from a customer, you must:
            1. If it is NOT an order (greeting, question, unrelated message), respond with exactly: NOT_AN_ORDER
            2. If it IS an order, respond ONLY with a valid JSON object — no markdown, no explanation:
            {
              "items": "formatted list e.g. 2x Rice (5kg), 1x Mustard Oil",
              "total": 0,
              "isCreditSale": false,
              "note": "any special instruction"
            }
            Rules:
            - Convert Hinglish/informal language to clean English item names
            - Short messages like "2x rice" or "2kg rice" ARE valid orders
            - If total/amount is mentioned, use it. Otherwise set total to 0
            - If message mentions "udhaar", "credit", "baad mein dunga", "later", set isCreditSale to true
            - Return ONLY the raw JSON, no code fences
        """.trimIndent()

        return tryWithRetry(rawMessage, systemPrompt, maxRetries = 3)
    }

    private suspend fun tryWithRetry(
        rawMessage  : String,
        systemPrompt: String,
        maxRetries  : Int
    ): ParsedOrder? {
        var lastError: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                val requestBody = JSONObject().apply {
                    put("model", model)
                    put("max_tokens", 256)
                    put("temperature", 0)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", rawMessage)
                        })
                    })
                }.toString()

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                Log.d(TAG, "Groq call attempt $attempt/$maxRetries for: $rawMessage")

                val response     = client.newCall(request).execute()
                val responseBody = response.body?.string()

                // Handle rate limit — Groq returns 429 with Retry-After header
                if (response.code == 429) {
                    val retryAfterMs = (response.header("retry-after")?.toDoubleOrNull()
                        ?: (attempt * 15.0)) * 1_000L
                    Log.w(TAG, "429 from Groq — waiting ${retryAfterMs}ms (attempt $attempt)")
                    delay(retryAfterMs.toLong())
                    continue
                }

                if (!response.isSuccessful || responseBody == null) {
                    Log.e(TAG, "Groq error ${response.code}: $responseBody")
                    return null
                }

                // Parse OpenAI-compatible response format
                val text = JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()

                Log.d(TAG, "Groq response: $text")

                if (text.equals("NOT_AN_ORDER", ignoreCase = true)) return null

                val clean = text
                    .replace(Regex("```json\\s*"), "")
                    .replace(Regex("```\\s*"), "")
                    .trim()

                val jsonStart = clean.indexOf('{')
                val jsonEnd   = clean.lastIndexOf('}')
                if (jsonStart == -1 || jsonEnd == -1) {
                    Log.w(TAG, "No JSON in response: $clean")
                    return null
                }

                val json = JSONObject(clean.substring(jsonStart, jsonEnd + 1))
                return ParsedOrder(
                    items        = json.getString("items"),
                    total        = json.optDouble("total", 0.0),
                    isCreditSale = json.optBoolean("isCreditSale", false),
                    note         = json.optString("note", "")
                ).also { Log.d(TAG, "Parsed: $it") }

            } catch (e: Exception) {
                lastError = e
                Log.e(TAG, "Attempt $attempt failed", e)
                if (attempt < maxRetries) delay(attempt * 2_000L)
            }
        }

        Log.e(TAG, "All $maxRetries attempts failed", lastError)
        return null
    }
}