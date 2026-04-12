package com.torpedoes.smartsales.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id              : Int    = 0,
    val name             : String,
    val phone            : String,
    val email            : String = "",
    val address          : String = "",
    val totalSpent       : Double = 0.0,
    val totalCredit      : Double = 0.0,
    val totalCreditPaid  : Double = 0.0,
    val creditScore      : Int    = 100,
    val avgRepayDays     : Int    = 0,
    val gracePeriodType  : String = "None",       // None / Weekly / Monthly / Custom
    val gracePeriodDays  : Int    = 0             // used when type = Custom
)