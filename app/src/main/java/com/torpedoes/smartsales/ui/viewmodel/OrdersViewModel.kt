package com.torpedoes.smartsales.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torpedoes.smartsales.data.db.dao.OrderDao
import com.torpedoes.smartsales.data.db.model.OrderEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrdersUiState(
    val orders      : List<OrderEntity> = emptyList(),
    val isAddOpen   : Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderDao: OrderDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            orderDao.getAllOrders().collect { orders ->
                _uiState.update { it.copy(orders = orders) }
            }
        }
    }

    fun openAdd()     = _uiState.update { it.copy(isAddOpen = true) }
    fun closeAdd()    = _uiState.update { it.copy(isAddOpen = false, errorMessage = null) }
    fun clearError()  = _uiState.update { it.copy(errorMessage = null) }

    fun addOrder(customerName: String, items: String, total: String) {
        val t = total.toDoubleOrNull()
        if (customerName.isBlank() || items.isBlank() || t == null) {
            _uiState.update { it.copy(errorMessage = "Please fill all fields correctly.") }
            return
        }
        viewModelScope.launch {
            orderDao.insertOrder(OrderEntity(customerName = customerName.trim(), items = items.trim(), total = t))
            closeAdd()
        }
    }

    fun updateStatus(order: OrderEntity, status: String) {
        viewModelScope.launch { orderDao.updateOrder(order.copy(status = status)) }
    }

    fun deleteOrder(order: OrderEntity) {
        viewModelScope.launch { orderDao.deleteOrder(order) }
    }
}