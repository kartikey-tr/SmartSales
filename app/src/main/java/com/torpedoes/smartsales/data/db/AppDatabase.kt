package com.torpedoes.smartsales.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.torpedoes.smartsales.data.db.dao.*
import com.torpedoes.smartsales.data.db.model.*

@Database(
    entities = [SaleEntity::class, ProductEntity::class, OrderEntity::class, CustomerEntity::class],
    version  = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun saleDao()    : SaleDao
    abstract fun productDao() : ProductDao
    abstract fun orderDao()   : OrderDao
    abstract fun customerDao(): CustomerDao
}