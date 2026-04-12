package com.torpedoes.smartsales.data.db.dao

import androidx.room.*
import com.torpedoes.smartsales.data.db.model.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsDirect(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE stock <= 5 ORDER BY stock ASC")
    fun getLowStockProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getProductByName(name: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Int): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)
}