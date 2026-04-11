package com.torpedoes.smartsales.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name     : String,
    val price    : Double,
    val stock    : Int,
    val category : String = ""
)