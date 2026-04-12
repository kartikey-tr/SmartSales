package com.torpedoes.smartsales.data.remote

import android.util.Log
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

    private val TAG    = "GeminiOrderParser"
    private val client = OkHttpClient()
    private val apiKey = com.torpedoes.smartsales.BuildConfig.GEMINI_API_KEY

    // Direct REST endpoint — no SDK, no model name guessing
    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

    suspend fun parseOrder(rawMessage: String): ParsedOrder? {
        val prompt = """
            You are a smart order parser for a small shop management app in India.
            A customer sent this WhatsApp message to the shopkeeper:

            "$rawMessage"

            Your job:
            1. Determine if this message is a shop order. If it is NOT an order (e.g. it's a greeting, question, or unrelated message), respond with exactly: NOT_AN_ORDER
            2. If it IS an order, extract the details and respond ONLY with a valid JSON object in this exact format (no markdown, no explanation, no code fences):
            {
              "items": "formatted list of items e.g. 2x Rice (5kg), 1x Mustard Oil",
              "total": 0,
              "isCreditSale": false,
              "note": "any special instruction from the message"
            }

            Rules:
            - Convert Hinglish/informal language to clean English item names
            - Short messages like "2x rice" or "2kg rice" ARE valid orders — treat them as orders
            - If total/amount is mentioned, use it. Otherwise set total to 0
            - If message mentions "udhaar", "credit", "baad mein dunga", "later", set isCreditSale to true
            - Keep items concise and professional
            - Return ONLY the raw JSON object, no markdown fences, no extra text
        """.trimIndent()

        return try {
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }.toString()

            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "Calling Gemini REST API for: $rawMessage")

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "HTTP ${response.code} — body: $responseBody")

            if (!response.isSuccessful || responseBody == null) {
                Log.e(TAG, "Gemini API error ${response.code}: $responseBody")
                return null
            }

            // Extract text from response
            val text = JSONObject(responseBody)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            Log.d(TAG, "Gemini response text: $text")

            if (text.equals("NOT_AN_ORDER", ignoreCase = true)) return null

            val clean = text
                .replace(Regex("```json\\s*"), "")
                .replace(Regex("```\\s*"), "")
                .trim()

            val jsonStart = clean.indexOf('{')
            val jsonEnd   = clean.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1) {
                Log.w(TAG, "No JSON found in: $clean")
                return null
            }

            val json = JSONObject(clean.substring(jsonStart, jsonEnd + 1))
            ParsedOrder(
                items        = json.getString("items"),
                total        = json.optDouble("total", 0.0),
                isCreditSale = json.optBoolean("isCreditSale", false),
                note         = json.optString("note", "")
            ).also { Log.d(TAG, "Parsed successfully: $it") }

        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing order: $rawMessage", e)
            null
        }
    }
}