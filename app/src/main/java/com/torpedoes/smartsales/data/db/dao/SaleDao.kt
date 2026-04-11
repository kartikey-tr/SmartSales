package com.torpedoes.smartsales.data.db.dao

import androidx.room.*
import com.torpedoes.smartsales.data.db.model.SaleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY date DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE date >= :startOfDay ORDER BY date DESC")
    fun getTodaySales(startOfDay: Long): Flow<List<SaleEntity>>

    @Query("SELECT SUM(total) FROM sales WHERE date >= :startOfDay")
    fun getTodayRevenue(startOfDay: Long): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity)

    @Delete
    suspend fun deleteSale(sale: SaleEntity)
}