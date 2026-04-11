package com.torpedoes.smartsales.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name    : String,
    val phone   : String,
    val email   : String = "",
    val address : String = "",
    val totalSpent: Double = 0.0
)