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

    @Query("SELECT * FROM sales WHERE isCreditSale = 1 AND creditPaid = 0 ORDER BY date DESC")
    fun getUnpaidCreditSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE customerName = :customerName AND isCreditSale = 1 AND creditPaid = 0")
    suspend fun getUnpaidCreditByCustomer(customerName: String): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE customerName = :customerName AND isCreditSale = 1 AND creditPaid = 1")
    suspend fun getPaidCreditByCustomer(customerName: String): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSaleById(id: Int): SaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity)

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Delete
    suspend fun deleteSale(sale: SaleEntity)
}