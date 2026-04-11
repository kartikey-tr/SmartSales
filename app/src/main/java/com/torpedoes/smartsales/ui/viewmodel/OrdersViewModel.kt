package com.torpedoes.smartsales.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torpedoes.smartsales.data.db.dao.CustomerDao
import com.torpedoes.smartsales.data.db.dao.OrderDao
import com.torpedoes.smartsales.data.db.dao.ProductDao
import com.torpedoes.smartsales.data.db.model.CustomerEntity
import com.torpedoes.smartsales.data.db.model.OrderEntity
import com.torpedoes.smartsales.data.db.model.ProductEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrdersUiState(
    val orders      : List<OrderEntity> = emptyList(),
    val products    : List<ProductEntity> = emptyList(),
    val customers   : List<CustomerEntity> = emptyList(),
    val isAddOpen   : Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderDao   : OrderDao,
    private val productDao : ProductDao,
    private val customerDao: CustomerDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            orderDao.getAllOrders().collect { orders ->
                _uiState.update { it.copy(orders = orders) }
            }
        }
        viewModelScope.launch {
            productDao.getAllProducts().collect { products ->
                _uiState.update { it.copy(products = products) }
            }
        }
        viewModelScope.launch {
            customerDao.getAllCustomers().collect { customers ->
                _uiState.update { it.copy(customers = customers) }
            }
        }
    }

    fun openAdd()    = _uiState.update { it.copy(isAddOpen = true,  errorMessage = null) }
    fun closeAdd()   = _uiState.update { it.copy(isAddOpen = false, errorMessage = null) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun addOrder(
        customerName : String,
        items        : String,
        total        : String,
        saveCustomer : Boolean,
        customerPhone: String
    ) {
        val t = total.toDoubleOrNull()
        when {
            customerName.isBlank() -> { _uiState.update { it.copy(errorMessage = "Please enter a customer name.") }; return }
            items.isBlank()        -> { _uiState.update { it.copy(errorMessage = "Please describe the items.") };    return }
            t == null              -> { _uiState.update { it.copy(errorMessage = "Please enter a valid total.") };   return }
            saveCustomer && customerPhone.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Please enter phone number to save customer.") }
                return
            }
        }

        viewModelScope.launch {
            orderDao.insertOrder(
                OrderEntity(
                    customerName = customerName.trim(),
                    items        = items.trim(),
                    total        = t!!
                )
            )

            if (saveCustomer) {
                val alreadyExists = _uiState.value.customers.any {
                    it.name.equals(customerName.trim(), ignoreCase = true)
                }
                if (!alreadyExists) {
                    customerDao.insertCustomer(
                        CustomerEntity(
                            name  = customerName.trim(),
                            phone = customerPhone.trim()
                        )
                    )
                }
            }

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