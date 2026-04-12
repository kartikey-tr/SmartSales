package com.torpedoes.smartsales.data.remote

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedOrder(
    val items      : String,
    val total      : Double,
    val isCreditSale: Boolean,
    val note       : String = ""
)

@Singleton
class GeminiOrderParser @Inject constructor() {

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey    = com.torpedoes.smartsales.BuildConfig.GEMINI_API_KEY
    )

    /**
     * Takes a raw WhatsApp message (Hinglish/informal) and returns a structured ParsedOrder.
     * Returns null if the message doesn't look like an order.
     */
    suspend fun parseOrder(rawMessage: String): ParsedOrder? {
        val prompt = """
            You are a smart order parser for a small shop management app in India.
            A customer sent this WhatsApp message to the shopkeeper:

            "$rawMessage"

            Your job:
            1. Determine if this message is a shop order. If it is NOT an order (e.g. it's a greeting, question, or unrelated message), respond with exactly: NOT_AN_ORDER
            2. If it IS an order, extract the details and respond ONLY with a valid JSON object in this exact format (no markdown, no explanation):
            {
              "items": "formatted list of items e.g. 2x Rice (5kg), 1x Mustard Oil",
              "total": 0,
              "isCreditSale": false,
              "note": "any special instruction from the message"
            }

            Rules:
            - Convert Hinglish/informal language to clean English item names
            - If total/amount is mentioned, use it. Otherwise set total to 0 (shopkeeper will fill it)
            - If message mentions "udhaar", "credit", "baad mein dunga", "later", set isCreditSale to true
            - Keep items concise and professional
        """.trimIndent()

        return try {
            val response = model.generateContent(content { text(prompt) })
            val text     = response.text?.trim() ?: return null

            if (text == "NOT_AN_ORDER") return null

            val clean = text
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(clean)
            ParsedOrder(
                items       = json.getString("items"),
                total       = json.optDouble("total", 0.0),
                isCreditSale = json.optBoolean("isCreditSale", false),
                note        = json.optString("note", "")
            )
        } catch (e: Exception) {
            null
        }
    }
}