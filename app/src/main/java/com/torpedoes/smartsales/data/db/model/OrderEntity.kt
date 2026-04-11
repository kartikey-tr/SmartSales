package com.torpedoes.smartsales.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerName : String,
    val items        : String,   // JSON string of items
    val total        : Double,
    val status       : String = "Pending",  // Pending / Completed / Cancelled
    val date         : Long = System.currentTimeMillis()
)