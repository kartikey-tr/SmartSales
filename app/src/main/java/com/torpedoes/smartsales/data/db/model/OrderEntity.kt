package com.torpedoes.smartsales.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id             : Int     = 0,
    val customerName    : String,
    val items           : String,
    val total           : Double,
    val status          : String  = "Pending",    // Pending / Completed / Cancelled
    val date            : Long    = System.currentTimeMillis(),
    val isCreditSale    : Boolean = false,
    val creditPaid      : Boolean = false,
    val creditPaidDate  : Long?   = null,
    val creditAmount    : Double  = 0.0,
    val convertedToSale : Boolean = false,        // true once a SaleEntity is created
    val isAutoOrder     : Boolean = false         // true if came from WhatsApp notification
)