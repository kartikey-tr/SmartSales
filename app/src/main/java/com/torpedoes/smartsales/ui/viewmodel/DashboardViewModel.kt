package com.torpedoes.smartsales.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torpedoes.smartsales.data.db.dao.SaleDao
import com.torpedoes.smartsales.data.db.model.SaleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val todayRevenue  : Double = 0.0,
    val todaySaleCount: Int    = 0,
    val recentSales   : List<SaleEntity> = emptyList(),
    val isAddSaleOpen : Boolean = false,
    val errorMessage  : String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val saleDao: SaleDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        viewModelScope.launch {
            saleDao.getTodayRevenue(startOfDay).collect { revenue ->
                _uiState.update { it.copy(todayRevenue = revenue ?: 0.0) }
            }
        }
        viewModelScope.launch {
            saleDao.getTodaySales(startOfDay).collect { sales ->
                _uiState.update { it.copy(todaySaleCount = sales.size, recentSales = sales.take(5)) }
            }
        }
    }

    fun openAddSale()  = _uiState.update { it.copy(isAddSaleOpen = true) }
    fun closeAddSale() = _uiState.update { it.copy(isAddSaleOpen = false) }

    fun addSale(itemName: String, quantity: String, price: String, customerName: String) {
        val qty   = quantity.toIntOrNull()
        val ppu   = price.toDoubleOrNull()
        if (itemName.isBlank() || qty == null || ppu == null || customerName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill all fields correctly.") }
            return
        }
        viewModelScope.launch {
            saleDao.insertSale(
                SaleEntity(
                    itemName     = itemName.trim(),
                    quantity     = qty,
                    pricePerUnit = ppu,
                    total        = qty * ppu,
                    customerName = customerName.trim()
                )
            )
            _uiState.update { it.copy(isAddSaleOpen = false, errorMessage = null) }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}