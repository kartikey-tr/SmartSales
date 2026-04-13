package com.torpedoes.smartsales.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torpedoes.smartsales.data.db.dao.CustomerDao
import com.torpedoes.smartsales.data.db.model.CustomerEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomersUiState(
    val customers      : List<CustomerEntity> = emptyList(),
    val isAddOpen      : Boolean              = false,
    val editingCustomer: CustomerEntity?      = null,
    val errorMessage   : String?              = null
)

@HiltViewModel
class CustomersViewModel @Inject constructor(
    private val customerDao: CustomerDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomersUiState())
    val uiState: StateFlow<CustomersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            customerDao.getAllCustomers().collect { customers ->
                _uiState.update { it.copy(customers = customers) }
            }
        }
    }

    fun openAdd()                            = _uiState.update { it.copy(isAddOpen = true, editingCustomer = null) }
    fun openEdit(customer: CustomerEntity)   = _uiState.update { it.copy(editingCustomer = customer, isAddOpen = true) }
    fun closeDialog()                        = _uiState.update { it.copy(isAddOpen = false, editingCustomer = null, errorMessage = null) }
    fun clearError()                         = _uiState.update { it.copy(errorMessage = null) }

    fun saveCustomer(name: String, phone: String, email: String, address: String) {
        if (name.isBlank() || phone.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Name and phone are required.") }
            return
        }
        viewModelScope.launch {
            val existing = _uiState.value.editingCustomer
            if (existing != null) {
                customerDao.updateCustomer(
                    existing.copy(
                        name    = name.trim(),
                        phone   = phone.trim(),
                        email   = email.trim(),
                        address = address.trim()
                    )
                )
            } else {
                customerDao.insertCustomer(
                    CustomerEntity(
                        name    = name.trim(),
                        phone   = phone.trim(),
                        email   = email.trim(),
                        address = address.trim()
                    )
                )
            }
            closeDialog()
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch { customerDao.deleteCustomer(customer) }
    }

    /**
     * Updates grace period for a customer directly from the Customers screen.
     * Does NOT recalculate credit score here — score is recalculated lazily when
     * the next credit event (sale/order/payment) occurs for this customer.
     * If you want immediate score refresh, wire in SaleDao + OrderDao here.
     */
    fun updateGracePeriod(customer: CustomerEntity, type: String, customDays: Int = 0) {
        viewModelScope.launch {
            customerDao.updateCustomer(
                customer.copy(
                    gracePeriodType = type,
                    gracePeriodDays = if (type == "Custom") customDays.coerceAtLeast(0) else 0
                )
            )
        }
    }
}