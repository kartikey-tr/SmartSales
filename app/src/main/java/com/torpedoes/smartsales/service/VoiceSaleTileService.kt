package com.torpedoes.smartsales.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.room.Room
import com.torpedoes.smartsales.data.db.AppDatabase
import com.torpedoes.smartsales.data.db.model.CustomerEntity
import com.torpedoes.smartsales.data.db.model.ProductEntity
import com.torpedoes.smartsales.data.db.model.SaleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile for hands-free voice sale entry in Hindi/Hinglish.
 *
 * Tap once  → starts listening  (tile active, "Listening…")
 * Tap again → stops, parses via Groq, inserts sales + updates inventory
 */
class VoiceSaleTileService : TileService() {

    private val TAG             = "VoiceSaleTile"
    private val CHANNEL         = "voice_sale_channel"
    private val NOTIF_LISTENING = 7001
    private val NOTIF_RESULT    = 7002

    private val job         = SupervisorJob()
    private val scope       = CoroutineScope(Dispatchers.IO + job)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer : SpeechRecognizer? = null
    private var isListening       = false
    private var isProcessing      = false
    private val accumulatedText   = StringBuilder()
    private var hiINFailed        = false

    private val db by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "smartsales_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    // ── TileService lifecycle ─────────────────────────────────────────────────

    override fun onStartListening() { updateTile() }

    override fun onClick() {
        if (isListening) {
            isListening = false
            updateTile()
            mainHandler.post { stopRecognizer() }
        } else {
            if (isProcessing) return
            isListening = true
            updateTile()
            mainHandler.post { startListening() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        job.cancel()
    }

    // ── Tile UI ───────────────────────────────────────────────────────────────

    private fun updateTile() {
        val tile = qsTile ?: return
        when {
            isProcessing -> {
                tile.state    = Tile.STATE_UNAVAILABLE
                tile.label    = "Processing…"
                tile.subtitle = "Please wait"
            }
            isListening -> {
                tile.state    = Tile.STATE_ACTIVE
                tile.label    = "Listening…"
                tile.subtitle = "Tap to stop"
            }
            else -> {
                tile.state    = Tile.STATE_INACTIVE
                tile.label    = "Voice Sale"
                tile.subtitle = "Tap to record"
            }
        }
        tile.updateTile()
    }

    // ── Speech recognition — ALL must run on main thread ─────────────────────

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            postNotification("Voice Sale", "Speech recognition not available on this device.", isError = true)
            isListening = false
            updateTile()
            return
        }

        accumulatedText.clear()
        hiINFailed = false
        showListeningNotification()

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(buildListener())
        }
        beginSession()
    }

    private fun stopRecognizer() {
        speechRecognizer?.stopListening()
        mainHandler.postDelayed({
            if (!isProcessing) tryProcess()
        }, 1_500)
    }

    private fun beginSession() {
        val lang = if (hiINFailed) "en-IN" else "hi-IN"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,   RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,         lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,  false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,      1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,          500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
        }
        speechRecognizer?.startListening(intent)
        Log.d(TAG, "beginSession lang=$lang")
    }

    private fun buildListener() = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?)   { Log.d(TAG, "Ready") }
        override fun onBeginningOfSpeech()           { Log.d(TAG, "Speaking") }
        override fun onRmsChanged(v: Float)          {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech()                 { Log.d(TAG, "End of speech") }
        override fun onEvent(t: Int, p: Bundle?)     {}
        override fun onPartialResults(b: Bundle?)    {}

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                accumulatedText.append(text)
                Log.d(TAG, "Appended: \"$text\" | total: \"$accumulatedText\"")
            }
            if (isListening) {
                mainHandler.post { beginSession() }
            } else {
                tryProcess()
            }
        }

        override fun onError(error: Int) {
            Log.w(TAG, "Speech error $error (isListening=$isListening)")
            when {
                error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    Log.w(TAG, "hi-IN unavailable, falling back to en-IN")
                    hiINFailed = true
                    if (isListening) mainHandler.post { beginSession() }
                    else tryProcess()
                }
                isListening && error in listOf(
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                ) -> mainHandler.postDelayed({ beginSession() }, 200)

                !isListening -> tryProcess()

                else -> {
                    isListening = false
                    updateTile()
                    postNotification("Voice Sale Error", "Recognition error ($error). Tap tile to try again.", isError = true)
                }
            }
        }
    }

    // ── Process + insert ──────────────────────────────────────────────────────

    private fun tryProcess() {
        if (isProcessing) return
        isProcessing = true
        isListening  = false
        updateTile()

        val text = accumulatedText.toString().trim()
        accumulatedText.clear()

        if (text.isBlank()) {
            isProcessing = false
            updateTile()
            cancelListeningNotification()
            postNotification("Voice Sale", "No speech captured. Tap the tile and try again.", isError = true)
            return
        }

        Log.d(TAG, "Processing: \"$text\"")
        showProcessingNotification(text)

        scope.launch {
            try {
                processAndInsert(text)
            } finally {
                mainHandler.post {
                    isProcessing = false
                    updateTile()
                }
            }
        }
    }

    private suspend fun processAndInsert(text: String) {
        val parsed = VoiceSaleParser.parse(text)

        if (parsed.isEmpty()) {
            postNotification(
                "Voice Sale — Not Understood",
                "Heard: \"$text\"\n\nCould not extract any sales. Try again.",
                isError = true
            )
            return
        }

        withContext(Dispatchers.IO) {
            val saleDao     = db.saleDao()
            val productDao  = db.productDao()
            val customerDao = db.customerDao()

            val products  = productDao.getAllProductsDirect()
            val customers = customerDao.getAllCustomersDirect()

            for (sale in parsed) {
                // ── Product: match or create ──────────────────────────
                var product = products.find { it.name.equals(sale.itemName, ignoreCase = true) }
                if (product == null) {
                    val newId = productDao.insertProduct(
                        ProductEntity(name = sale.itemName, price = sale.pricePerUnit, stock = 0)
                    )
                    product = productDao.getProductById(newId.toInt())
                }

                // ── Customer: match or create ─────────────────────────
                val customer = customers.find { it.name.equals(sale.customerName, ignoreCase = true) }
                if (customer == null && sale.customerName != "Customer") {
                    customerDao.insertCustomer(
                        CustomerEntity(name = sale.customerName, phone = "")
                    )
                }

                // ── Stock deduction ───────────────────────────────────
                product?.let { p ->
                    productDao.updateProduct(
                        p.copy(stock = (p.stock - sale.quantity).coerceAtLeast(0))
                    )
                }

                val total = sale.quantity * sale.pricePerUnit

                // ── Insert sale ───────────────────────────────────────
                saleDao.insertSale(
                    SaleEntity(
                        itemName     = sale.itemName,
                        quantity     = sale.quantity,
                        pricePerUnit = sale.pricePerUnit,
                        total        = total,
                        customerName = sale.customerName,
                        isCreditSale = sale.isCreditSale,
                        creditPaid   = false,
                        creditAmount = if (sale.isCreditSale) total else 0.0
                    )
                )
                Log.d(TAG, "Inserted: ${sale.customerName} — ${sale.quantity}× ${sale.itemName} @ ₹${sale.pricePerUnit}")
            }
        }

        val summary = parsed.joinToString("\n") { s ->
            "• ${s.customerName}: ${s.quantity}× ${s.itemName}" +
                    (if (s.pricePerUnit > 0) " @ ₹${s.pricePerUnit.toInt()}" else "") +
                    (if (s.isCreditSale) " [Udhaar]" else "")
        }
        postNotification(
            title   = "🎙 Auto Entry: ${parsed.size} sale${if (parsed.size != 1) "s" else ""} added",
            message = summary,
            isError = false
        )
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Voice Sale Entry", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Status for voice sale entry" }
            )
        }
    }

    private fun showListeningNotification() {
        ensureChannel()
        val notif = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("🎙 Voice Sale — Listening…")
            .setContentText("Speak in Hindi or English. Tap tile again to stop & save.")
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_LISTENING, notif)
    }

    private fun showProcessingNotification(text: String) {
        ensureChannel()
        cancelListeningNotification()
        val preview = text.take(80) + if (text.length > 80) "…" else ""
        val notif = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("⏳ Voice Sale — Processing…")
            .setContentText("\"$preview\"")
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_RESULT, notif)
    }

    private fun postNotification(title: String, message: String, isError: Boolean) {
        ensureChannel()
        cancelListeningNotification()
        val icon = if (isError) android.R.drawable.ic_dialog_alert else android.R.drawable.ic_dialog_info
        val notif = Notification.Builder(this, CHANNEL)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_RESULT, notif)
    }

    private fun cancelListeningNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIF_LISTENING)
    }
}