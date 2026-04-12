package com.torpedoes.smartsales.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torpedoes.smartsales.data.db.dao.CustomerDao
import com.torpedoes.smartsales.data.db.dao.OrderDao
import com.torpedoes.smartsales.data.db.dao.ProductDao
import com.torpedoes.smartsales.data.db.dao.SaleDao
import com.torpedoes.smartsales.data.db.model.CustomerEntity
import com.torpedoes.smartsales.data.db.model.OrderEntity
import com.torpedoes.smartsales.data.db.model.ProductEntity
import com.torpedoes.smartsales.data.db.model.SaleEntity
import com.torpedoes.smartsales.util.CreditScoreCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

// Represents one line item the shopkeeper links to an inventory product
data class LinkedItem(
    val productId   : Int,
    val productName : String,
    val qty         : Int,
    val pricePerUnit: Double
) {
    val lineTotal: Double get() = qty * pricePerUnit
}

data class OrdersUiState(
    val orders          : List<OrderEntity>    = emptyList(),
    val products        : List<ProductEntity>  = emptyList(),
    val customers       : List<CustomerEntity> = emptyList(),
    val isAddOpen       : Boolean              = false,
    // Which order the "Fulfill" sheet is open for
    val fulfillOrderId  : Int?                 = null,
    val errorMessage    : String?              = null
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderDao   : OrderDao,
    private val productDao : ProductDao,
    private val customerDao: CustomerDao,
    private val saleDao    : SaleDao
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

    // ── Fulfill sheet ─────────────────────────────────────────────────────────

    fun openFulfill(orderId: Int) = _uiState.update { it.copy(fulfillOrderId = orderId) }
    fun closeFulfill()            = _uiState.update { it.copy(fulfillOrderId = null) }

    /**
     * Called when the shopkeeper confirms the "Fulfill Order" sheet.
     * - Inserts any new custom products into inventory first
     * - Saves linked items as JSON on the order (resolving custom product IDs after insert)
     * - Updates total to the sum of linked line items
     * - Deducts stock from each matched existing product
     */
    fun fulfillOrder(
        order       : OrderEntity,
        linkedItems : List<LinkedItem>,
        newProducts : List<Pair<String, Double>> // name → price for custom items
    ) {
        if (linkedItems.isEmpty()) return

        viewModelScope.launch {
            // 1. Insert custom products and get their real IDs
            val customIdMap = mutableMapOf<String, Int>() // name → new product id
            for ((name, price) in newProducts) {
                val trimmed = name.trim()
                if (trimmed.isBlank()) continue
                // Check if already exists (user might have typed an existing name)
                val existing = _uiState.value.products.find {
                    it.name.equals(trimmed, ignoreCase = true)
                }
                if (existing != null) {
                    customIdMap[trimmed] = existing.id
                } else {
                    // Insert with stock = 0; will be updated below when we deduct
                    productDao.insertProduct(
                        ProductEntity(name = trimmed, price = price, stock = 0)
                    )
                    // Room doesn't return the id from insertProduct easily without Flow—
                    // query it back by name right after
                    // Give the Flow a moment to emit then grab from state, or re-query:
                    val inserted = productDao.getProductByName(trimmed)
                    if (inserted != null) customIdMap[trimmed] = inserted.id
                }
            }

            // 2. Resolve -1 ids in linkedItems to real ids
            val resolvedItems = linkedItems.map { li ->
                if (li.productId == -1) {
                    val realId = customIdMap[li.productName.trim()] ?: -1
                    li.copy(productId = realId)
                } else li
            }

            // 3. Build JSON
            val arr = JSONArray()
            resolvedItems.forEach { li ->
                arr.put(JSONObject().apply {
                    put("productId",    li.productId)
                    put("productName",  li.productName)
                    put("qty",          li.qty)
                    put("pricePerUnit", li.pricePerUnit)
                })
            }

            val newTotal = resolvedItems.sumOf { it.lineTotal }

            orderDao.updateOrder(
                order.copy(
                    linkedItemsJson = arr.toString(),
                    total           = newTotal,
                    creditAmount    = if (order.isCreditSale) newTotal else order.creditAmount
                )
            )

            // 4. Deduct stock from existing products (custom ones start at 0, go negative → coerce to 0)
            resolvedItems.forEach { li ->
                if (li.productId == -1) return@forEach
                val product = _uiState.value.products.find { it.id == li.productId }
                    ?: productDao.getProductByName(li.productName) // fallback for freshly inserted
                if (product != null) {
                    val newStock = (product.stock - li.qty).coerceAtLeast(0)
                    productDao.updateProduct(product.copy(stock = newStock))
                }
            }

            closeFulfill()
        }
    }

    // ── Parse helpers ─────────────────────────────────────────────────────────

    fun parseLinkedItems(json: String): List<LinkedItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                LinkedItem(
                    productId    = obj.getInt("productId"),
                    productName  = obj.getString("productName"),
                    qty          = obj.getInt("qty"),
                    pricePerUnit = obj.getDouble("pricePerUnit")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // ── Add order (manual) ────────────────────────────────────────────────────

    fun addOrder(
        customerName : String,
        items        : String,
        total        : String,
        isCreditSale : Boolean,
        saveCustomer : Boolean,
        customerPhone: String
    ) {
        val t = total.toDoubleOrNull() ?: 0.0
        when {
            customerName.isBlank() -> { _uiState.update { it.copy(errorMessage = "Please enter a customer name.") };  return }
            items.isBlank()        -> { _uiState.update { it.copy(errorMessage = "Please describe the items.") };     return }
            saveCustomer && customerPhone.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Enter phone number to save customer.") }
                return
            }
        }

        viewModelScope.launch {
            orderDao.insertOrder(
                OrderEntity(
                    customerName = customerName.trim(),
                    items        = items.trim(),
                    total        = t,
                    isCreditSale = isCreditSale,
                    creditAmount = if (isCreditSale) t else 0.0
                )
            )

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
            closeAdd()
        }
    }

    // ── Status / credit ───────────────────────────────────────────────────────

    fun updateStatus(order: OrderEntity, status: String) {
        viewModelScope.launch {
            val updated = order.copy(status = status)
            orderDao.updateOrder(updated)

            if (status == "Completed" && !order.convertedToSale) {
                saleDao.insertSale(
                    SaleEntity(
                        itemName     = order.items,
                        quantity     = 1,
                        pricePerUnit = order.total,
                        total        = order.total,
                        customerName = order.customerName,
                        isCreditSale = order.isCreditSale,
                        creditAmount = order.creditAmount,
                        creditPaid   = order.creditPaid,
                        creditPaidDate = order.creditPaidDate
                    )
                )
                orderDao.updateOrder(updated.copy(convertedToSale = true))
                if (order.isCreditSale) recalculateCustomerCredit(order.customerName)
            }
        }
    }

    fun markCreditPaid(order: OrderEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            orderDao.updateOrder(order.copy(creditPaid = true, creditPaidDate = now))
            if (order.convertedToSale) {
                val allSales = saleDao.getUnpaidCreditByCustomer(order.customerName)
                allSales.filter { it.total == order.total }.forEach { sale ->
                    saleDao.updateSale(sale.copy(creditPaid = true, creditPaidDate = now))
                }
            }
            recalculateCustomerCredit(order.customerName)
        }
    }

    fun deleteOrder(order: OrderEntity) {
        viewModelScope.launch {
            orderDao.deleteOrder(order)
            if (order.isCreditSale) recalculateCustomerCredit(order.customerName)
        }
    }

    fun updateGracePeriod(customer: CustomerEntity, type: String, customDays: Int = 0) {
        viewModelScope.launch {
            customerDao.updateCustomer(
                customer.copy(gracePeriodType = type, gracePeriodDays = if (type == "Custom") customDays else 0)
            )
            recalculateCustomerCredit(customer.name)
        }
    }

    private suspend fun recalculateCustomerCredit(customerName: String) {
        val customer     = customerDao.getCustomerByName(customerName) ?: return
        val paidSales    = saleDao.getPaidCreditByCustomer(customerName)
        val unpaidSales  = saleDao.getUnpaidCreditByCustomer(customerName)
        val paidOrders   = orderDao.getPaidCreditOrdersByCustomer(customerName)
        val unpaidOrders = orderDao.getUnpaidCreditOrdersByCustomer(customerName)

        val (score, avgDays) = CreditScoreCalculator.calculate(
            paidSales, unpaidSales, paidOrders, unpaidOrders, customer
        )

        val totalCredit     = (paidSales + unpaidSales).sumOf { it.creditAmount } +
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