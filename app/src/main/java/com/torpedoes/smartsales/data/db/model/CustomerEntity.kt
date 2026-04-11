package com.torpedoes.smartsales.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id           : Int    = 0,
    val name            : String,
    val phone           : String,
    val email           : String = "",
    val address         : String = "",
    val totalSpent      : Double = 0.0,
    val totalCredit     : Double = 0.0,   // total credit ever taken
    val totalCreditPaid : Double = 0.0,   // total credit repaid
    val creditScore     : Int    = 100,   // 0–100
    val avgRepayDays    : Int    = 0      // average days to repay
)