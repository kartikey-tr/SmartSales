package com.torpedoes.smartsales.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id             : Int     = 0,
    val customerName    : String,
    val items           : String,           // raw text from WhatsApp / manual entry
    val total           : Double,
    val status          : String  = "Pending",
    val date            : Long    = System.currentTimeMillis(),
    val isCreditSale    : Boolean = false,
    val creditPaid      : Boolean = false,
    val creditPaidDate  : Long?   = null,
    val creditAmount    : Double  = 0.0,
    val convertedToSale : Boolean = false,
    val isAutoOrder     : Boolean = false,

    // NEW: JSON array of fulfilled line items, set when shopkeeper maps items to inventory.
    // Format: [{"productId":1,"productName":"Rice","qty":2,"pricePerUnit":60.0}, ...]
    // Empty string = not yet fulfilled / no inventory linked
    val linkedItemsJson : String  = ""
)