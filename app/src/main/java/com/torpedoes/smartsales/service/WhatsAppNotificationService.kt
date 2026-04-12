package com.torpedoes.smartsales.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.torpedoes.smartsales.data.db.dao.CustomerDao
import com.torpedoes.smartsales.data.db.dao.OrderDao
import com.torpedoes.smartsales.data.db.model.CustomerEntity
import com.torpedoes.smartsales.data.db.model.OrderEntity
import com.torpedoes.smartsales.data.remote.GeminiOrderParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
class WhatsAppNotificationService : NotificationListenerService() {

    @Inject lateinit var orderDao         : OrderDao
    @Inject lateinit var customerDao      : CustomerDao
    @Inject lateinit var geminiOrderParser: GeminiOrderParser

    private val TAG              = "WA_OrderService"
    private val WHATSAPP_PACKAGE = "com.whatsapp"

    private val job   = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    // ── Rate limiting ─────────────────────────────────────────────────────────
    private val callTimestamps      = ArrayDeque<Long>()
    private val rateMutex           = Mutex()
    private val MAX_CALLS_PER_MINUTE = 10   // safely under Gemini free tier's 15 RPM

    // ── Deduplication ─────────────────────────────────────────────────────────
    private val recentMessages  = LinkedHashMap<String, Long>(16, 0.75f, true)
    private val DEDUP_WINDOW_MS = 5_000L

    // ── Local keyword pre-filter ──────────────────────────────────────────────
    private val ORDER_KEYWORDS = listOf(
        "kg", "gram", "gm", "litre", "liter", "lt", "packet", "pack", "bottle",
        "piece", "pcs", "box", "dozen", "bag", "order", "send", "bhejo", "chahiye",
        "de do", "dedo", "dena", "lena", "rice", "oil", "atta", "sugar",
        "salt", "dal", "sabji", "sabzi", "doodh", "milk", "bread", "biscuit",
        "soap", "shampoo", "tea", "chai", "coffee", "ghee", "maida",
        "udhaar", "credit", "baad mein", "baad me", "later", "kal dunga"
    )

    private fun looksLikeOrder(message: String): Boolean {
        val lower = message.lowercase().trim()
        if (lower.length <= 3) return false
        val quantityPattern = Regex("""(\d+)\s*(x|kg|gm|gram|g|lt|ltr|litre|liter|pc|pcs|piece|packet|pack|bottle|box|dozen|bag)\b""")
        if (quantityPattern.containsMatchIn(lower)) return true
        return ORDER_KEYWORDS.any { lower.contains(it) }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WHATSAPP_PACKAGE) return

        val extras = sbn.notification.extras ?: return
        val title       = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val messageText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()  ?: return

        Log.d(TAG, "WA notification — from: $title | msg: $messageText")

        if (title.equals("WhatsApp", ignoreCase = true)) return

        val isGroupChat = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT) != null
        if (isGroupChat) { Log.d(TAG, "Skipping group chat"); return }

        if (title.contains("messages from", ignoreCase = true)) return

        // LOCAL PRE-FILTER — skips ~80% of API calls
        if (!looksLikeOrder(messageText)) {
            Log.d(TAG, "Pre-filter: not an order, skipping Gemini")
            return
        }

        // DEDUPLICATION
        val dedupKey = "$title:${messageText.trim()}"
        val now      = System.currentTimeMillis()
        synchronized(recentMessages) {
            val lastSeen = recentMessages[dedupKey]
            if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) {
                Log.d(TAG, "Duplicate — skipping")
                return
            }
            recentMessages[dedupKey] = now
            if (recentMessages.size > 100) recentMessages.remove(recentMessages.keys.first())
        }

        val phone = title.filter { it.isDigit() }.let {
            if (it.length >= 10) it.takeLast(10) else null
        }

        scope.launch {
            // RATE LIMITING
            rateMutex.withLock {
                val windowStart = System.currentTimeMillis() - 60_000L
                while (callTimestamps.isNotEmpty() && callTimestamps.first() < windowStart) {
                    callTimestamps.removeFirst()
                }
                if (callTimestamps.size >= MAX_CALLS_PER_MINUTE) {
                    val waitMs = (callTimestamps.first() + 60_000L) - System.currentTimeMillis() + 500L
                    Log.w(TAG, "Rate limit reached — waiting ${waitMs}ms")
                    delay(waitMs.coerceAtLeast(1_000L))
                }
                callTimestamps.addLast(System.currentTimeMillis())
            }

            val parsed = geminiOrderParser.parseOrder(messageText)
            if (parsed == null) {
                Log.d(TAG, "Gemini: not an order. Skipping.")
                return@launch
            }

            // Match by last-10 phone digits OR by WhatsApp display name (title)
            val last10           = phone?.takeLast(10)
            val allCustomers     = customerDao.getAllCustomers().first()
            val existingCustomer = allCustomers.find { customer ->
                (last10 != null && customer.phone.filter { it.isDigit() }.takeLast(10) == last10)
                        || customer.name.equals(title.trim(), ignoreCase = true)
            }

            val customerName = existingCustomer?.name ?: run {
                // Prefer display name over raw digits
                val newName = if (title.any { it.isLetter() }) title.trim() else (phone ?: title.trim())
                customerDao.insertCustomer(CustomerEntity(name = newName, phone = phone ?: ""))
                Log.d(TAG, "New customer created: $newName")
                newName
            }

            val fullItems = if (parsed.note.isNotBlank())
                "${parsed.items} [Note: ${parsed.note}]"
            else
                parsed.items

            orderDao.insertOrder(
                OrderEntity(
                    customerName = customerName,
                    items        = fullItems,
                    total        = parsed.total,
                    isCreditSale = parsed.isCreditSale,
                    creditAmount = if (parsed.isCreditSale) parsed.total else 0.0,
                    isAutoOrder  = true
                )
            )
            Log.d(TAG, "Order inserted — $customerName | $fullItems")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        Log.d(TAG, "Service destroyed")
    }
}