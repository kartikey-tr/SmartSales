package com.torpedoes.smartsales.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torpedoes.smartsales.data.db.model.CustomerEntity
import com.torpedoes.smartsales.data.db.model.ProductEntity
import com.torpedoes.smartsales.data.db.model.SaleEntity
import com.torpedoes.smartsales.ui.theme.*
import com.torpedoes.smartsales.ui.viewmodel.DashboardViewModel
import com.torpedoes.smartsales.ui.viewmodel.LinkedItem
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────
// Screen root
// ─────────────────────────────────────────────────────────────

@Composable
fun DashboardScreen(
    onNavigateToOrders: () -> Unit = {},
    viewModel         : DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("SmartSales", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = BrandOrange)
                Text(
                    SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date()),
                    fontSize = 13.sp, color = OnSurfaceMuted
                )
            }

            // Today's Summary
            item {
                Card(
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = SurfaceMid),
                    elevation = CardDefaults.cardElevation(4.dp),
                    modifier  = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Today's Summary", fontSize = 14.sp, color = OnSurfaceMuted)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Revenue", fontSize = 12.sp, color = OnSurfaceMuted)
                                Text(
                                    "₹%.2f".format(uiState.todayRevenue),
                                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BrandOrange
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Sales", fontSize = 12.sp, color = OnSurfaceMuted)
                                Text(
                                    "${uiState.todaySaleCount}",
                                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = OnSurfaceLight
                                )
                            }
                        }
                    }
                }
            }

            // Quick Actions
            item {
                Text("Quick Actions", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionButton(
                        label    = "Add Sale",
                        icon     = Icons.Default.Add,
                        modifier = Modifier.weight(1f),
                        onClick  = { viewModel.openAddSale() }
                    )
                    QuickActionButton(
                        label    = "New Order",
                        icon     = Icons.Default.ShoppingCart,
                        modifier = Modifier.weight(1f),
                        onClick  = { onNavigateToOrders() }
                    )
                }
            }

            // Unpaid Credit Section
            if (uiState.unpaidCredit.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pending Credits", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(50), color = Color(0xFFFFC107).copy(alpha = 0.15f)) {
                            Text(
                                "${uiState.unpaidCredit.size}",
                                modifier   = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize   = 11.sp, color = Color(0xFFFFC107), fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                items(uiState.unpaidCredit) { sale ->
                    CreditSaleCard(
                        sale       = sale,
                        onMarkPaid = { viewModel.markCreditPaid(sale) },
                        onDelete   = { viewModel.deleteSale(sale) }
                    )
                }
            }

            // Recent Sales
            if (uiState.recentSales.isNotEmpty()) {
                item { Text("Recent Sales", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight) }
                items(uiState.recentSales) { sale ->
                    SaleCard(sale = sale, onDelete = { viewModel.deleteSale(sale) })
                }
            }
        }

        if (uiState.isAddSaleOpen) {
            AddSaleDialog(
                products     = uiState.products,
                customers    = uiState.customers,
                errorMessage = uiState.errorMessage,
                onDismiss    = { viewModel.closeAddSale() },
                onConfirm    = { linkedItems, newProducts, customerName, isCredit, saveCustomer, phone ->
                    viewModel.addSale(linkedItems, newProducts, customerName, isCredit, saveCustomer, phone)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Sale card
// ─────────────────────────────────────────────────────────────

@Composable
private fun SaleCard(sale: SaleEntity, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    Card(
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = SurfaceMid),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sale.itemName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                    if (sale.isCreditSale) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFFC107).copy(alpha = 0.15f)) {
                            Text("Credit", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = Color(0xFFFFC107), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(sale.customerName, fontSize = 12.sp, color = OnSurfaceMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("₹%.2f".format(sale.total), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BrandOrange)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    if (showConfirm) {
        DeleteConfirmDialog("Delete this sale entry?", onConfirm = { onDelete(); showConfirm = false }, onDismiss = { showConfirm = false })
    }
}

// ─────────────────────────────────────────────────────────────
// Credit sale card
// ─────────────────────────────────────────────────────────────

@Composable
private fun CreditSaleCard(sale: SaleEntity, onMarkPaid: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val daysSince = ((System.currentTimeMillis() - sale.date) / (1000 * 60 * 60 * 24)).toInt()
    Card(
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFC107).copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(sale.customerName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                    Text(sale.itemName, fontSize = 12.sp, color = OnSurfaceMuted)
                    Text(
                        "$daysSince day${if (daysSince != 1) "s" else ""} overdue",
                        fontSize = 11.sp,
                        color    = if (daysSince > 7) ErrorRed else Color(0xFFFFC107)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("₹%.2f".format(sale.creditAmount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFC107))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick        = onMarkPaid,
                            shape          = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border         = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50))
                        ) { Text("Mark Paid", fontSize = 11.sp, color = Color(0xFF4CAF50)) }
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            "Delete this credit entry? This will also update the customer's credit score.",
            onConfirm = { onDelete(); showDeleteConfirm = false },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Delete confirmation dialog
// ─────────────────────────────────────────────────────────────

@Composable
private fun DeleteConfirmDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceMid,
        title  = { Text("Are you sure?", color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
        text   = { Text(message, color = OnSurfaceMuted, fontSize = 13.sp) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), shape = RoundedCornerShape(10.dp)) {
                Text("Delete", color = OnSurfaceLight)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurfaceMuted) } }
    )
}

// ─────────────────────────────────────────────────────────────
// Quick action button
// ─────────────────────────────────────────────────────────────

@Composable
private fun QuickActionButton(
    label   : String,
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick : () -> Unit
) {
    Card(
        onClick  = onClick,
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = BrandOrange),
        modifier = modifier.height(72.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = OnSurfaceLight, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Line row state model (mirrors OrdersScreen)
// ─────────────────────────────────────────────────────────────

private data class SaleLineRow(
    val id          : Long           = System.nanoTime(),
    val product     : ProductEntity? = null,
    val qty         : String         = "1",
    val pricePerUnit: String         = "",
    val isCustom    : Boolean        = false,
    val customName  : String         = ""
)

// ─────────────────────────────────────────────────────────────
// Add Sale Dialog — multi-item, same picker as Orders
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSaleDialog(
    products    : List<ProductEntity>,
    customers   : List<CustomerEntity>,
    errorMessage: String?,
    onDismiss   : () -> Unit,
    onConfirm   : (List<LinkedItem>, List<Pair<String, Double>>, String, Boolean, Boolean, String) -> Unit
) {
    var selectedCustomer     by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerName         by remember { mutableStateOf("") }
    var customerDropdownOpen by remember { mutableStateOf(false) }
    var isNewCustomer        by remember { mutableStateOf(false) }
    var saveCustomer         by remember { mutableStateOf(false) }
    var customerPhone        by remember { mutableStateOf("") }
    var isCreditSale         by remember { mutableStateOf(false) }
    var rows                 by remember { mutableStateOf(listOf(SaleLineRow())) }
    var localError           by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceMid,
        title  = { Text("Add Sale", color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
        text   = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ── Item rows ─────────────────────────────────────────
                Text("Items", fontSize = 12.sp, color = OnSurfaceMuted)

                rows.forEachIndexed { index, row ->
                    SaleLineItemRow(
                        row               = row,
                        products          = products,
                        showDelete        = rows.size > 1,
                        onProductSelected = { product ->
                            rows = rows.toMutableList().also {
                                if (product.id == 0) {
                                    it[index] = row.copy(isCustom = false, product = null, customName = "", pricePerUnit = "")
                                } else {
                                    it[index] = row.copy(product = product, pricePerUnit = product.price.toInt().toString(), isCustom = false, customName = "")
                                }
                            }
                        },
                        onQtyChange        = { qty   -> rows = rows.toMutableList().also { it[index] = row.copy(qty = qty) } },
                        onPriceChange      = { price -> rows = rows.toMutableList().also { it[index] = row.copy(pricePerUnit = price) } },
                        onCustomToggled    = { rows = rows.toMutableList().also { it[index] = row.copy(isCustom = true, product = null, customName = "", pricePerUnit = "") } },
                        onCustomNameChange = { name -> rows = rows.toMutableList().also { it[index] = row.copy(customName = name) } },
                        onDelete           = { rows = rows.toMutableList().also { it.removeAt(index) } }
                    )
                }

                TextButton(onClick = { rows = rows + SaleLineRow() }) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp), tint = BrandOrange)
                    Spacer(Modifier.width(4.dp))
                    Text("Add another item", color = BrandOrange, fontSize = 13.sp)
                }

                // Running total
                val runningTotal = rows.sumOf { r ->
                    (r.qty.toIntOrNull() ?: 0) * (r.pricePerUnit.toDoubleOrNull() ?: 0.0)
                }
                if (runningTotal > 0) {
                    HorizontalDivider(color = OnSurfaceMuted.copy(alpha = 0.2f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sale Total", fontSize = 14.sp, color = OnSurfaceMuted)
                        Text("₹%.0f".format(runningTotal), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = BrandOrange)
                    }
                }

                HorizontalDivider(color = OnSurfaceMuted.copy(alpha = 0.15f))

                // ── Credit toggle ─────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Sell on Credit", fontSize = 13.sp, color = OnSurfaceLight, fontWeight = FontWeight.SemiBold)
                        Text("Customer pays later", fontSize = 11.sp, color = OnSurfaceMuted)
                    }
                    Switch(
                        checked         = isCreditSale,
                        onCheckedChange = { isCreditSale = it },
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = OnSurfaceLight,
                            checkedTrackColor   = BrandOrange,
                            uncheckedThumbColor = OnSurfaceMuted,
                            uncheckedTrackColor = OnSurfaceMuted.copy(alpha = 0.3f)
                        )
                    )
                }

                HorizontalDivider(color = OnSurfaceMuted.copy(alpha = 0.15f))

                // ── Customer picker ───────────────────────────────────
                Text("Customer", fontSize = 12.sp, color = OnSurfaceMuted)
                ExposedDropdownMenuBox(
                    expanded         = customerDropdownOpen,
                    onExpandedChange = { customerDropdownOpen = it }
                ) {
                    OutlinedTextField(
                        value         = if (isNewCustomer) customerName else (selectedCustomer?.name ?: ""),
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Select Customer") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerDropdownOpen) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth(),
                        colors        = saleDialogFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded         = customerDropdownOpen,
                        onDismissRequest = { customerDropdownOpen = false }
                    ) {
                        customers.forEach { customer ->
                            DropdownMenuItem(
                                text    = {
                                    Column {
                                        Text(customer.name, color = OnSurfaceLight)
                                        Text(customer.phone, fontSize = 11.sp, color = OnSurfaceMuted)
                                    }
                                },
                                onClick = {
                                    selectedCustomer     = customer
                                    customerName         = customer.name
                                    isNewCustomer        = false
                                    saveCustomer         = false
                                    customerPhone        = ""
                                    customerDropdownOpen = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text    = { Text("New Customer…", color = BrandOrange) },
                            onClick = { selectedCustomer = null; customerName = ""; isNewCustomer = true; customerDropdownOpen = false }
                        )
                    }
                }

                if (isNewCustomer) {
                    OutlinedTextField(
                        value         = customerName,
                        onValueChange = { customerName = it },
                        label         = { Text("Customer Name") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = saleDialogFieldColors()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveCustomer, onCheckedChange = { saveCustomer = it }, colors = CheckboxDefaults.colors(checkedColor = BrandOrange))
                        Text("Save to customer list", fontSize = 13.sp, color = OnSurfaceLight)
                    }
                    if (saveCustomer) {
                        OutlinedTextField(
                            value           = customerPhone,
                            onValueChange   = { customerPhone = it },
                            label           = { Text("Phone Number") },
                            singleLine      = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier        = Modifier.fillMaxWidth(),
                            colors          = saleDialogFieldColors()
                        )
                    }
                }

                (localError ?: errorMessage)?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalCustomer = if (isNewCustomer) customerName else (selectedCustomer?.name ?: "")
                    val built         = mutableListOf<LinkedItem>()
                    val newProducts   = mutableListOf<Pair<String, Double>>()

                    for (row in rows) {
                        val qty   = row.qty.toIntOrNull()?.takeIf { it > 0 } ?: continue
                        val price = row.pricePerUnit.toDoubleOrNull()?.takeIf { it >= 0 } ?: continue
                        if (row.isCustom) {
                            val name = row.customName.trim()
                            if (name.isBlank()) continue
                            built.add(LinkedItem(-1, name, qty, price))
                            newProducts.add(name to price)
                        } else {
                            val p = row.product ?: continue
                            built.add(LinkedItem(p.id, p.name, qty, price))
                        }
                    }

                    if (built.isEmpty()) { localError = "Please add at least one item with qty and price."; return@Button }
                    localError = null
                    onConfirm(built, newProducts, finalCustomer, isCreditSale, saveCustomer && isNewCustomer, customerPhone)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                shape  = RoundedCornerShape(12.dp)
            ) { Text("Add Sale", color = OnSurfaceLight) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurfaceMuted) } }
    )
}

// ─────────────────────────────────────────────────────────────
// Single line-item row for the sale dialog
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaleLineItemRow(
    row               : SaleLineRow,
    products          : List<ProductEntity>,
    showDelete        : Boolean,
    onProductSelected : (ProductEntity) -> Unit,
    onQtyChange       : (String) -> Unit,
    onPriceChange     : (String) -> Unit,
    onCustomToggled   : () -> Unit,
    onCustomNameChange: (String) -> Unit,
    onDelete          : () -> Unit
) {
    var dropdownOpen by remember { mutableStateOf(false) }
    var search       by remember { mutableStateOf("") }

    val filteredProducts = remember(search, products) {
        if (search.isBlank()) products else products.filter { it.name.contains(search, ignoreCase = true) }
    }

    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceCard.copy(alpha = 0.4f)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Product picker row
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExposedDropdownMenuBox(
                    expanded         = dropdownOpen,
                    onExpandedChange = { if (!row.isCustom) dropdownOpen = it },
                    modifier         = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value         = when {
                            row.isCustom        -> "Custom product"
                            row.product != null -> row.product.name
                            else                -> ""
                        },
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Product", fontSize = 12.sp) },
                        placeholder   = { Text("Select from inventory", fontSize = 12.sp, color = OnSurfaceMuted) },
                        trailingIcon  = {
                            if (row.isCustom) {
                                IconButton(onClick = { onProductSelected(ProductEntity(0, "", 0.0, 0)); dropdownOpen = false }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, null, tint = OnSurfaceMuted, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOpen)
                            }
                        },
                        modifier      = Modifier.menuAnchor().fillMaxWidth(),
                        colors        = if (row.isCustom)
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = BrandAmber, unfocusedBorderColor = BrandAmber.copy(alpha = 0.5f),
                                focusedTextColor     = BrandAmber, unfocusedTextColor   = BrandAmber,
                                focusedLabelColor    = BrandAmber, unfocusedLabelColor  = BrandAmber.copy(alpha = 0.7f),
                                cursorColor          = BrandAmber
                            )
                        else saleDialogFieldColors(),
                        singleLine    = true
                    )
                    if (!row.isCustom) {
                        ExposedDropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false; search = "" }) {
                            OutlinedTextField(
                                value         = search,
                                onValueChange = { search = it },
                                placeholder   = { Text("Search…", fontSize = 12.sp) },
                                modifier      = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                singleLine    = true,
                                colors        = saleDialogFieldColors()
                            )
                            if (filteredProducts.isEmpty()) {
                                DropdownMenuItem(text = { Text("No products found", color = OnSurfaceMuted, fontSize = 12.sp) }, onClick = {})
                            } else {
                                filteredProducts.forEach { product ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(product.name, color = OnSurfaceLight, fontSize = 13.sp)
                                                    Text(
                                                        "Stock: ${product.stock}",
                                                        color = if (product.stock <= 5) ErrorRed else OnSurfaceMuted,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                Text("₹${product.price.toInt()}", color = BrandOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        },
                                        onClick = { onProductSelected(product); dropdownOpen = false; search = "" }
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = OnSurfaceMuted.copy(alpha = 0.2f))
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AddCircleOutline, null, tint = BrandAmber, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Custom product…", color = BrandAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                },
                                onClick = { onCustomToggled(); dropdownOpen = false; search = "" }
                            )
                        }
                    }
                }
                if (showDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, null, tint = ErrorRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (row.isCustom) {
                OutlinedTextField(
                    value         = row.customName,
                    onValueChange = onCustomNameChange,
                    label         = { Text("Product name", fontSize = 12.sp) },
                    placeholder   = { Text("e.g. Mustard Oil 1L", fontSize = 12.sp, color = OnSurfaceMuted) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = BrandAmber, unfocusedBorderColor = BrandAmber.copy(alpha = 0.5f),
                        focusedLabelColor    = BrandAmber, unfocusedLabelColor  = BrandAmber.copy(alpha = 0.7f),
                        focusedTextColor     = OnSurfaceLight, unfocusedTextColor = OnSurfaceLight,
                        cursorColor          = BrandAmber
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = BrandAmber.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Will be added to your inventory automatically", fontSize = 10.sp, color = BrandAmber.copy(alpha = 0.7f))
                }
            }

            // Qty + Price
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value           = row.qty,
                    onValueChange   = onQtyChange,
                    label           = { Text("Qty", fontSize = 12.sp) },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier        = Modifier.weight(1f),
                    colors          = saleDialogFieldColors()
                )
                OutlinedTextField(
                    value           = row.pricePerUnit,
                    onValueChange   = onPriceChange,
                    label           = { Text("Price/unit (₹)", fontSize = 12.sp) },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier        = Modifier.weight(2f),
                    colors          = saleDialogFieldColors()
                )
            }

            // Line total + stock hint
            val lineTotal = (row.qty.toIntOrNull() ?: 0) * (row.pricePerUnit.toDoubleOrNull() ?: 0.0)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                when {
                    row.isCustom -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = BrandAmber.copy(alpha = 0.15f)) {
                            Text("New", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, color = BrandAmber, fontWeight = FontWeight.Bold)
                        }
                    }
                    row.product != null -> {
                        val reqQty = row.qty.toIntOrNull() ?: 0
                        Text(
                            if (reqQty > row.product.stock) "⚠ Only ${row.product.stock} in stock" else "Stock: ${row.product.stock}",
                            fontSize = 11.sp,
                            color    = if (reqQty > row.product.stock) Color(0xFFFFC107) else OnSurfaceMuted
                        )
                    }
                    else -> Spacer(Modifier.weight(1f))
                }
                if (lineTotal > 0) {
                    Text("= ₹%.0f".format(lineTotal), fontSize = 12.sp, color = BrandOrange, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun saleDialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = BrandOrange,
    unfocusedBorderColor = OnSurfaceMuted,
    focusedLabelColor    = BrandOrange,
    unfocusedLabelColor  = OnSurfaceMuted,
    focusedTextColor     = OnSurfaceLight,
    unfocusedTextColor   = OnSurfaceLight,
    cursorColor          = BrandOrange
)