package com.torpedoes.smartsales.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torpedoes.smartsales.data.db.dao.ProductDao
import com.torpedoes.smartsales.data.db.model.ProductEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryUiState(
    val products      : List<ProductEntity> = emptyList(),
    val isAddOpen     : Boolean = false,
    val editingProduct: ProductEntity? = null,
    val errorMessage  : String? = null
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val productDao: ProductDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            productDao.getAllProducts().collect { products ->
                _uiState.update { it.copy(products = products) }
            }
        }
    }

    fun openAdd()                           = _uiState.update { it.copy(isAddOpen = true, editingProduct = null) }
    fun openEdit(product: ProductEntity)    = _uiState.update { it.copy(editingProduct = product, isAddOpen = true) }
    fun closeDialog()                       = _uiState.update { it.copy(isAddOpen = false, editingProduct = null, errorMessage = null) }
    fun clearError()                        = _uiState.update { it.copy(errorMessage = null) }

    fun saveProduct(name: String, price: String, stock: String, category: String) {
        val p = price.toDoubleOrNull()
        val s = stock.toIntOrNull()
        if (name.isBlank() || p == null || s == null) {
            _uiState.update { it.copy(errorMessage = "Please fill all fields correctly.") }
            return
        }
        viewModelScope.launch {
            val existing = _uiState.value.editingProduct
            if (existing != null) {
                productDao.updateProduct(existing.copy(name = name.trim(), price = p, stock = s, category = category.trim()))
            } else {
                productDao.insertProduct(ProductEntity(name = name.trim(), price = p, stock = s, category = category.trim()))
            }
            closeDialog()
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch { productDao.deleteProduct(product) }
    }
}