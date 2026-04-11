package com.torpedoes.smartsales.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id          : Int     = 0,
    val itemName        : String,
    val quantity        : Int,
    val pricePerUnit    : Double,
    val total           : Double,
    val customerName    : String,
    val date            : Long    = System.currentTimeMillis(),
    val isCreditSale    : Boolean = false,
    val creditPaid      : Boolean = false,
    val creditPaidDate  : Long?   = null,
    val creditAmount    : Double  = 0.0
)