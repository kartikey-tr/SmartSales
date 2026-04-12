package com.torpedoes.smartsales.service

import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ParsedVoiceSale(
    val customerName : String,
    val itemName     : String,
    val quantity     : Int,
    val pricePerUnit : Double,
    val isCreditSale : Boolean
)

object VoiceSaleParser {

    private const val TAG = "VoiceSaleParser"

    // Longer timeouts — Groq can be slow on first call
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL    = "llama-3.1-8b-instant"

    private val SYSTEM_PROMPT = """
        You are a bilingual sales entry parser for a small Indian shop (kirana/general store).
        The shopkeeper speaks in Hindi, Hinglish, or English and may mention multiple sales in one sentence.

        Parse the input and return ONLY a valid JSON array of sale objects. No markdown, no explanation, no preamble.
        Each object must have exactly these fields:
        {
          "customerName": "Properly capitalized customer name",
          "itemName": "English item name (capitalize first letter)",
          "quantity": <integer, minimum 1>,
          "pricePerUnit": <number, 0.0 if not mentioned>,
          "isCreditSale": <true if udhaar/credit/baad mein/later, else false>
        }

        Splitting rules:
        - "aur", "and", "bhi", "+" between sales → separate objects
        - "X ko Y Z diya/bechi/di" → customerName=X, itemName=Z, quantity=Y
        - "X ne Y Z li/kharida/liya" → customerName=X, itemName=Z, quantity=Y
        - Numbers like "ek"=1, "do"=2, "teen"=3, "char"=4, "paanch"=5, "das"=10, "bees"=20

        Hindi→English translations (examples):
        doodh→Milk, atta→Flour, chawal→Rice, tel→Oil, namak→Salt, shakkar/cheeni→Sugar,
        sabun→Soap, chai→Tea, biscuit→Biscuit, bread→Bread, egg/anda→Egg,
        bike→Bike, car→Car, mobile→Mobile, kapda→Cloth

        If customer name is unclear: use "Customer"
        If quantity is unclear: use 1
        Return ONLY the raw JSON array. Nothing else.
    """.trimIndent()

    suspend fun parse(voiceText: String): List<ParsedVoiceSale> {
        if (voiceText.isBlank()) return emptyList()

        for (attempt in 1..3) {
            try {
                val bodyJson = JSONObject().apply {
                    put("model", MODEL)
                    put("max_tokens", 512)
                    put("temperature", 0)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply { put("role", "system"); put("content", SYSTEM_PROMPT) })
                        put(JSONObject().apply { put("role", "user");   put("content", voiceText)    })
                    })
                }.toString()

                val request = Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("Authorization", "Bearer ${com.torpedoes.smartsales.BuildConfig.GROQ_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response     = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.code == 429) {
                    val wait = (response.header("retry-after")?.toLongOrNull() ?: (attempt * 15L)) * 1_000L
                    Log.w(TAG, "Rate limited, waiting ${wait}ms")
                    delay(wait)
                    continue
                }

                if (!response.isSuccessful || responseBody == null) {
                    Log.e(TAG, "HTTP ${response.code}: $responseBody")
                    return emptyList()
                }

                val raw = JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                    .replace(Regex("```json\\s*"), "")
                    .replace(Regex("```\\s*"), "")
                    .trim()

                Log.d(TAG, "Groq raw: $raw")

                val arrStart = raw.indexOf('[')
                val arrEnd   = raw.lastIndexOf(']')
                if (arrStart == -1 || arrEnd == -1) {
                    Log.w(TAG, "No JSON array in response")
                    return emptyList()
                }

                val arr    = JSONArray(raw.substring(arrStart, arrEnd + 1))
                val result = mutableListOf<ParsedVoiceSale>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    result.add(
                        ParsedVoiceSale(
                            customerName = obj.optString("customerName", "Customer").trim().ifBlank { "Customer" },
                            itemName     = obj.optString("itemName",     "Item").trim().ifBlank { "Item" },
                            quantity     = obj.optInt("quantity", 1).coerceAtLeast(1),
                            pricePerUnit = obj.optDouble("pricePerUnit", 0.0).coerceAtLeast(0.0),
                            isCreditSale = obj.optBoolean("isCreditSale", false)
                        )
                    )
                }
                Log.d(TAG, "Parsed ${result.size} sale(s): $result")
                return result

            } catch (e: Exception) {
                Log.e(TAG, "Attempt $attempt failed", e)
                if (attempt < 3) delay(attempt * 2_000L)
            }
        }
        return emptyList()
    }
}