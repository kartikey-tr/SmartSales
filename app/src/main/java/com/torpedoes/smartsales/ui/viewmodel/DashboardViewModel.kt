package com.torpedoes.smartsales.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torpedoes.smartsales.data.db.dao.CustomerDao
import com.torpedoes.smartsales.data.db.dao.OrderDao
import com.torpedoes.smartsales.data.db.dao.ProductDao
import com.torpedoes.smartsales.data.db.dao.SaleDao
import com.torpedoes.smartsales.data.db.model.CustomerEntity
import com.torpedoes.smartsales.data.db.model.ProductEntity
import com.torpedoes.smartsales.data.db.model.SaleEntity
import com.torpedoes.smartsales.util.CreditScoreCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val todayRevenue  : Double = 0.0,
    val todaySaleCount: Int = 0,
    val recentSales   : List<SaleEntity> = emptyList(),
    val allSales      : List<SaleEntity> = emptyList(),
    val unpaidCredit  : List<SaleEntity> = emptyList(),
    val products      : List<ProductEntity> = emptyList(),
    val customers     : List<CustomerEntity> = emptyList(),
    val isAddSaleOpen : Boolean = false,
    val stockWarning  : String? = null,
    val errorMessage  : String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val saleDao    : SaleDao,
    private val productDao : ProductDao,
    private val customerDao: CustomerDao,
    private val orderDao   : OrderDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
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
        viewModelScope.launch {
            saleDao.getAllSales().collect { sales ->
                _uiState.update { it.copy(allSales = sales) }
            }
        }
        viewModelScope.launch {
            saleDao.getUnpaidCreditSales().collect { sales ->
                _uiState.update { it.copy(unpaidCredit = sales) }
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

    fun openAddSale()  = _uiState.update { it.copy(isAddSaleOpen = true,  errorMessage = null, stockWarning = null) }
    fun closeAddSale() = _uiState.update { it.copy(isAddSaleOpen = false, errorMessage = null, stockWarning = null) }
    fun clearError()   = _uiState.update { it.copy(errorMessage = null) }

    fun checkStock(productId: Int?, quantity: String) {
        if (productId == null) { _uiState.update { it.copy(stockWarning = null) }; return }
        val qty     = quantity.toIntOrNull() ?: return
        val product = _uiState.value.products.find { it.id == productId } ?: return
        _uiState.update {
            it.copy(
                stockWarning = if (qty > product.stock)
                    "⚠ Only ${product.stock} units in stock. Proceeding will result in negative stock."
                else null
            )
        }
    }

    fun addSale(
        itemName     : String,
        quantity     : String,
        price        : String,
        customerName : String,
        productId    : Int?,
        isCreditSale : Boolean,
        saveCustomer : Boolean,
        customerPhone: String
    ) {
        val qty = quantity.toIntOrNull()
        val ppu = price.toDoubleOrNull()
        when {
            itemName.isBlank()     -> { _uiState.update { it.copy(errorMessage = "Please enter an item name.") };          return }
            customerName.isBlank() -> { _uiState.update { it.copy(errorMessage = "Please enter a customer name.") };       return }
            qty == null || qty < 1 -> { _uiState.update { it.copy(errorMessage = "Please enter a valid quantity.") };      return }
            ppu == null || ppu < 0 -> { _uiState.update { it.copy(errorMessage = "Please enter a valid price.") };        return }
            saveCustomer && customerPhone.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Enter phone number to save customer.") }
                return
            }
        }

        val total = qty!! * ppu!!

        viewModelScope.launch {
            // 1. Insert sale
            saleDao.insertSale(
                SaleEntity(
                    itemName     = itemName.trim(),
                    quantity     = qty,
                    pricePerUnit = ppu,
                    total        = total,
                    customerName = customerName.trim(),
                    isCreditSale = isCreditSale,
                    creditPaid   = false,
                    creditAmount = if (isCreditSale) total else 0.0
                )
            )

            // 2. Decrement stock
            if (productId != null) {
                val product = _uiState.value.products.find { it.id == productId }
                product?.let {
                    productDao.updateProduct(it.copy(stock = (it.stock - qty).coerceAtLeast(0)))
                }
            }

            // 3. Save new customer if requested
            if (saveCustomer) {
                val exists = _uiState.value.customers.any {
                    it.name.equals(customerName.trim(), ignoreCase = true)
                }
                if (!exists) {
                    customerDao.insertCustomer(
                        CustomerEntity(name = customerName.trim(), phone = customerPhone.trim())
                    )
                }
            }

            // 4. Update customer credit if credit sale
            if (isCreditSale) {
                recalculateCustomerCredit(customerName.trim())
            }

            _uiState.update { it.copy(isAddSaleOpen = false, errorMessage = null, stockWarning = null) }
        }
    }

    fun deleteSale(sale: SaleEntity) {
        viewModelScope.launch {
            saleDao.deleteSale(sale)
            // Restore stock — best effort match by name
            val product = _uiState.value.products.find {
                it.name.equals(sale.itemName, ignoreCase = true)
            }
            product?.let {
                productDao.updateProduct(it.copy(stock = it.stock + sale.quantity))
            }
            if (sale.isCreditSale) {
                recalculateCustomerCredit(sale.customerName)
            }
        }
    }

    fun markCreditPaid(sale: SaleEntity) {
        viewModelScope.launch {
            saleDao.updateSale(
                sale.copy(
                    creditPaid     = true,
                    creditPaidDate = System.currentTimeMillis()
                )
            )
            recalculateCustomerCredit(sale.customerName)
        }
    }

    private suspend fun recalculateCustomerCredit(customerName: String) {
        val customer = customerDao.getCustomerByName(customerName) ?: return

        val paidSales    = saleDao.getPaidCreditByCustomer(customerName)
        val unpaidSales  = saleDao.getUnpaidCreditByCustomer(customerName)
        val paidOrders   = orderDao.getPaidCreditOrdersByCustomer(customerName)
        val unpaidOrders = orderDao.getUnpaidCreditOrdersByCustomer(customerName)

        val (score, avgDays) = CreditScoreCalculator.calculate(
            paidSales    = paidSales,
            unpaidSales  = unpaidSales,
            paidOrders   = paidOrders,
            unpaidOrders = unpaidOrders,
            customer     = customer
        )

        val totalCredit = (paidSales + unpaidSales).sumOf { it.creditAmount } +
                (paidOrders + unpaidOrders).sumOf { it.creditAmount }
        val totalCreditPaid = paidSales.sumOf { it.creditAmount } +
                paidOrders.sumOf { it.creditAmount }

        customerDao.updateCustomer(
            customer.copy(
                totalCredit     = totalCredit,
                totalCreditPaid = totalCreditPaid,
                creditScore     = score,
                avgRepayDays    = avgDays
            )
        )
    }
}