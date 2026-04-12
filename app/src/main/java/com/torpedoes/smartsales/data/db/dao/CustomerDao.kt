package com.torpedoes.smartsales.data.db.dao

import androidx.room.*
import com.torpedoes.smartsales.data.db.model.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY name ASC")
    suspend fun getAllCustomersDirect(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getCustomerByName(name: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Int): CustomerEntity?

    @Query("SELECT * FROM customers WHERE replace(replace(replace(phone,' ',''),'-',''),'+','') LIKE '%' || replace(replace(replace(:digits,' ',''),'-',''),'+','') ORDER BY id DESC LIMIT 1")
    suspend fun getCustomerByPhoneDigits(digits: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)
}