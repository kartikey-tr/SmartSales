package com.torpedoes.smartsales.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.torpedoes.smartsales.data.db.dao.CustomerDao
import com.torpedoes.smartsales.data.db.dao.OrderDao
import com.torpedoes.smartsales.data.db.model.CustomerEntity
import com.torpedoes.smartsales.data.db.model.OrderEntity
import com.torpedoes.smartsales.data.remote.GeminiOrderParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WhatsAppNotificationService : NotificationListenerService() {

    @Inject lateinit var orderDao         : OrderDao
    @Inject lateinit var customerDao      : CustomerDao
    @Inject lateinit var geminiOrderParser: GeminiOrderParser

    private val WHATSAPP_PACKAGE = "com.whatsapp"

    private val job   = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WHATSAPP_PACKAGE) return

        val extras      = sbn.notification.extras ?: return
        val title       = extras.getString(Notification.EXTRA_TITLE) ?: return
        val messageText = extras.getString(Notification.EXTRA_TEXT)  ?: return

        // Skip group chats and WhatsApp system notifications
        if (title.contains("messages from", ignoreCase = true) ||
            title.equals("WhatsApp", ignoreCase = true)) return

        val phone = title.filter { it.isDigit() }.let {
            if (it.length >= 10) it.takeLast(10) else null
        }

        scope.launch {
            val parsed = geminiOrderParser.parseOrder(messageText) ?: return@launch

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
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}