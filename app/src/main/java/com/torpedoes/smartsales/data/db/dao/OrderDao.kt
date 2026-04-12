package com.torpedoes.smartsales.data.db.dao

import androidx.room.*
import com.torpedoes.smartsales.data.db.model.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Query("SELECT * FROM orders ORDER BY date DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE status = 'Completed' AND convertedToSale = 0")
    suspend fun getCompletedNotConverted(): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE isCreditSale = 1 AND creditPaid = 0 AND status != 'Cancelled' ORDER BY date DESC")
    fun getUnpaidCreditOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE customerName = :customerName AND isCreditSale = 1 AND creditPaid = 1")
    suspend fun getPaidCreditOrdersByCustomer(customerName: String): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE customerName = :customerName AND isCreditSale = 1 AND creditPaid = 0 AND status != 'Cancelled'")
    suspend fun getUnpaidCreditOrdersByCustomer(customerName: String): List<OrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Delete
    suspend fun deleteOrder(order: OrderEntity)
}