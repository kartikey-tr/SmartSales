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
    val todayRevenue  : Double               = 0.0,
    val todaySaleCount: Int                  = 0,
    val recentSales   : List<SaleEntity>     = emptyList(),
    val allSales      : List<SaleEntity>     = emptyList(),
    val unpaidCredit  : List<SaleEntity>     = emptyList(),
    val products      : List<ProductEntity>  = emptyList(),
    val customers     : List<CustomerEntity> = emptyList(),
    val isAddSaleOpen : Boolean              = false,
    val errorMessage  : String?              = null
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

    fun openAddSale()  = _uiState.update { it.copy(isAddSaleOpen = true,  errorMessage = null) }
    fun closeAddSale() = _uiState.update { it.copy(isAddSaleOpen = false, errorMessage = null) }
    fun clearError()   = _uiState.update { it.copy(errorMessage = null) }

    /**
     * Add one or more sale line-items in a single transaction.
     * Each LinkedItem maps to one SaleEntity row and updates inventory accordingly.
     * Custom items (productId == -1) are added to inventory automatically.
     */
    fun addSale(
        linkedItems  : List<LinkedItem>,
        newProducts  : List<Pair<String, Double>>,   // custom items to insert in inventory
        customerName : String,
        isCreditSale : Boolean,
        saveCustomer : Boolean,
        customerPhone: String
    ) {
        when {
            customerName.isBlank() -> { _uiState.update { it.copy(errorMessage = "Please enter a customer name.") }; return }
            linkedItems.isEmpty()  -> { _uiState.update { it.copy(errorMessage = "Please add at least one item.") }; return }
            saveCustomer && customerPhone.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Enter phone number to save customer.") }; return
            }
        }

        viewModelScope.launch {
            // 1. Insert custom products into inventory first
            val customIdMap = mutableMapOf<String, Int>()
            for ((name, price) in newProducts) {
                val trimmed  = name.trim()
                if (trimmed.isBlank()) continue
                val existing = _uiState.value.products.find { it.name.equals(trimmed, ignoreCase = true) }
                if (existing != null) {
                    customIdMap[trimmed] = existing.id
                } else {
                    productDao.insertProduct(ProductEntity(name = trimmed, price = price, stock = 0))
                    val inserted = productDao.getProductByName(trimmed)
                    if (inserted != null) customIdMap[trimmed] = inserted.id
                }
            }

            // 2. Resolve placeholder ids
            val resolved = linkedItems.map { li ->
                if (li.productId == -1) li.copy(productId = customIdMap[li.productName.trim()] ?: -1) else li
            }

            val grandTotal = resolved.sumOf { it.lineTotal }
            val customer   = customerName.trim()

            // 3. Insert one SaleEntity per line item
            resolved.forEach { li ->
                saleDao.insertSale(
                    SaleEntity(
                        itemName     = li.productName,
                        quantity     = li.qty,
                        pricePerUnit = li.pricePerUnit,
                        total        = li.lineTotal,
                        customerName = customer,
                        isCreditSale = isCreditSale,
                        creditPaid   = false,
                        creditAmount = if (isCreditSale) li.lineTotal else 0.0
                    )
                )
            }

            // 4. Deduct stock for known products
            resolved.forEach { li ->
                if (li.productId == -1) return@forEach
                val product = _uiState.value.products.find { it.id == li.productId }
                    ?: productDao.getProductByName(li.productName)
                product?.let {
                    productDao.updateProduct(it.copy(stock = (it.stock - li.qty).coerceAtLeast(0)))
                }
            }

            // 5. Save new customer if requested
            if (saveCustomer) {
                val exists = _uiState.value.customers.any { it.name.equals(customer, ignoreCase = true) }
                if (!exists) {
                    customerDao.insertCustomer(CustomerEntity(name = customer, phone = customerPhone.trim()))
                }
            }

            // 6. Recalculate credit if needed
            if (isCreditSale) recalculateCustomerCredit(customer)

            _uiState.update { it.copy(isAddSaleOpen = false, errorMessage = null) }
        }
    }

    fun deleteSale(sale: SaleEntity) {
        viewModelScope.launch {
            saleDao.deleteSale(sale)
            val product = _uiState.value.products.find { it.name.equals(sale.itemName, ignoreCase = true) }
            product?.let { productDao.updateProduct(it.copy(stock = it.stock + sale.quantity)) }
            if (sale.isCreditSale) recalculateCustomerCredit(sale.customerName)
        }
    }

    fun markCreditPaid(sale: SaleEntity) {
        viewModelScope.launch {
            saleDao.updateSale(sale.copy(creditPaid = true, creditPaidDate = System.currentTimeMillis()))
            recalculateCustomerCredit(sale.customerName)
        }
    }

    private suspend fun recalculateCustomerCredit(customerName: String) {
        val customer     = customerDao.getCustomerByName(customerName) ?: return
        val paidSales    = saleDao.getPaidCreditByCustomer(customerName)
        val unpaidSales  = saleDao.getUnpaidCreditByCustomer(customerName)
        val paidOrders   = orderDao.getPaidCreditOrdersByCustomer(customerName)
        val unpaidOrders = orderDao.getUnpaidCreditOrdersByCustomer(customerName)

        val (score, avgDays) = CreditScoreCalculator.calculate(paidSales, unpaidSales, paidOrders, unpaidOrders, customer)

        val totalCredit     = (paidSales + unpaidSales).sumOf { it.creditAmount } + (paidOrders + unpaidOrders).sumOf { it.creditAmount }
        val totalCreditPaid = paidSales.sumOf { it.creditAmount } + paidOrders.sumOf { it.creditAmount }

        customerDao.updateCustomer(
            customer.copy(totalCredit = totalCredit, totalCreditPaid = totalCreditPaid, creditScore = score, avgRepayDays = avgDays)
        )
    }
}