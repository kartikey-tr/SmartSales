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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created — Hilt injection complete")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WHATSAPP_PACKAGE) return

        val extras = sbn.notification.extras ?: return

        // Use getCharSequence to handle SpannableString on Android 15
        val title       = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val messageText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()  ?: return

        Log.d(TAG, "WhatsApp notification received — from: $title | message: $messageText")

        // Skip WhatsApp system notifications
        if (title.equals("WhatsApp", ignoreCase = true)) {
            Log.d(TAG, "Skipping WhatsApp system notification")
            return
        }

        // Skip group chats — EXTRA_SUMMARY_TEXT is only set for group notifications
        val isGroupChat = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT) != null
        if (isGroupChat) {
            Log.d(TAG, "Skipping group chat notification from: $title")
            return
        }

        // Skip bundle/summary notifications (e.g. "3 messages from 2 chats")
        if (title.contains("messages from", ignoreCase = true)) {
            Log.d(TAG, "Skipping summary notification: $title")
            return
        }

        // Try to extract a 10-digit phone from the title (works when contact isn't saved)
        val phone = title.filter { it.isDigit() }.let {
            if (it.length >= 10) it.takeLast(10) else null
        }

        scope.launch {
            Log.d(TAG, "Sending to Gemini parser: $messageText")
            val parsed = geminiOrderParser.parseOrder(messageText)

            if (parsed == null) {
                Log.d(TAG, "Gemini returned null — not an order or parse failed. Skipping.")
                return@launch
            }
            Log.d(TAG, "Parsed order: items=${parsed.items}, total=${parsed.total}, credit=${parsed.isCreditSale}")

            // Find or create customer
            val allCustomers     = customerDao.getAllCustomers().first()
            val existingCustomer = allCustomers.find { customer ->
                phone != null &&
                        customer.phone.filter { it.isDigit() }.takeLast(10) == phone
            }

            val customerName = existingCustomer?.name ?: run {
                val newName = phone ?: title
                customerDao.insertCustomer(
                    CustomerEntity(name = newName, phone = phone ?: title)
                )
                Log.d(TAG, "Created new customer: $newName")
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
            Log.d(TAG, "Order inserted successfully — customer: $customerName | items: $fullItems")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        Log.d(TAG, "Service destroyed — coroutine job cancelled")
    }
}