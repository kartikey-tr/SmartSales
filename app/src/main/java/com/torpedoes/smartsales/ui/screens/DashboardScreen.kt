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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torpedoes.smartsales.data.db.model.CustomerEntity
import com.torpedoes.smartsales.data.db.model.ProductEntity
import com.torpedoes.smartsales.data.db.model.SaleEntity
import com.torpedoes.smartsales.ui.theme.*
import com.torpedoes.smartsales.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    onNavigateToOrders: () -> Unit = {},
    viewModel         : DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
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
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint     = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Pending Credits",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = OnSurfaceLight
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFFFC107).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "${uiState.unpaidCredit.size}",
                                modifier   = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize   = 11.sp,
                                color      = Color(0xFFFFC107),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                items(uiState.unpaidCredit) { sale ->
                    CreditSaleCard(
                        sale          = sale,
                        onMarkPaid    = { viewModel.markCreditPaid(sale) },
                        onDelete      = { viewModel.deleteSale(sale) }
                    )
                }
            }

            // Recent Sales
            if (uiState.recentSales.isNotEmpty()) {
                item {
                    Text(
                        "Recent Sales",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = OnSurfaceLight
                    )
                }
                items(uiState.recentSales) { sale ->
                    SaleCard(
                        sale     = sale,
                        onDelete = { viewModel.deleteSale(sale) }
                    )
                }
            }
        }

        if (uiState.isAddSaleOpen) {
            AddSaleDialog(
                products     = uiState.products,
                customers    = uiState.customers,
                stockWarning = uiState.stockWarning,
                errorMessage = uiState.errorMessage,
                onDismiss    = { viewModel.closeAddSale() },
                onStockCheck = { productId, qty -> viewModel.checkStock(productId, qty) },
                onConfirm    = { itemName, qty, price, customerName, productId, isCredit, saveCustomer, phone ->
                    viewModel.addSale(itemName, qty, price, customerName, productId, isCredit, saveCustomer, phone)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Sale card with delete
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
                            Text(
                                "Credit",
                                modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize   = 10.sp,
                                color      = Color(0xFFFFC107),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(sale.customerName, fontSize = 12.sp, color = OnSurfaceMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("₹%.2f".format(sale.total), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BrandOrange)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (showConfirm) {
        DeleteConfirmDialog(
            message   = "Delete this sale entry?",
            onConfirm = { onDelete(); showConfirm = false },
            onDismiss = { showConfirm = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Credit sale card with mark paid + delete
// ─────────────────────────────────────────────────────────────

@Composable
private fun CreditSaleCard(sale: SaleEntity, onMarkPaid: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val daysSince = ((System.currentTimeMillis() - sale.date) / (1000 * 60 * 60 * 24)).toInt()

    Card(
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(
            containerColor = Color(0xFFFFC107).copy(alpha = 0.06f)
        ),
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
                            onClick      = onMarkPaid,
                            shape        = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border       = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50))
                        ) {
                            Text("Mark Paid", fontSize = 11.sp, color = Color(0xFF4CAF50))
                        }
                        IconButton(
                            onClick  = { showDeleteConfirm = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            message   = "Delete this credit entry? This will also update the customer's credit score.",
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
        confirmButton  = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape   = RoundedCornerShape(10.dp)
            ) { Text("Delete", color = OnSurfaceLight) }
        },
        dismissButton  = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurfaceMuted) }
        }
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
// Add Sale Dialog
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSaleDialog(
    products    : List<ProductEntity>,
    customers   : List<CustomerEntity>,
    stockWarning: String?,
    errorMessage: String?,
    onDismiss   : () -> Unit,
    onStockCheck: (Int?, String) -> Unit,
    onConfirm   : (String, String, String, String, Int?, Boolean, Boolean, String) -> Unit
) {
    var selectedProduct      by remember { mutableStateOf<ProductEntity?>(null) }
    var itemName             by remember { mutableStateOf("") }
    var itemDropdownOpen     by remember { mutableStateOf(false) }
    var isCustomItem         by remember { mutableStateOf(false) }

    var selectedCustomer     by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerName         by remember { mutableStateOf("") }
    var customerDropdownOpen by remember { mutableStateOf(false) }
    var isNewCustomer        by remember { mutableStateOf(false) }
    var saveCustomer         by remember { mutableStateOf(false) }
    var customerPhone        by remember { mutableStateOf("") }

    var quantity             by remember { mutableStateOf("") }
    var price                by remember { mutableStateOf("") }
    var isCreditSale         by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceMid,
        title  = { Text("Add Sale", color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
        text   = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ── Item Dropdown ──────────────────────────────────────────
                Text("Item", fontSize = 12.sp, color = OnSurfaceMuted)
                ExposedDropdownMenuBox(
                    expanded         = itemDropdownOpen,
                    onExpandedChange = { itemDropdownOpen = it }
                ) {
                    OutlinedTextField(
                        value         = if (isCustomItem) itemName else (selectedProduct?.name ?: ""),
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Select Item") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = itemDropdownOpen) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth(),
                        colors        = dialogFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded         = itemDropdownOpen,
                        onDismissRequest = { itemDropdownOpen = false }
                    ) {
                        products.forEach { product ->
                            DropdownMenuItem(
                                text    = {
                                    Column {
                                        Text(product.name, color = OnSurfaceLight)
                                        Text(
                                            "₹%.2f  •  Stock: ${product.stock}".format(product.price),
                                            fontSize = 11.sp,
                                            color    = if (product.stock <= 5) ErrorRed else OnSurfaceMuted
                                        )
                                    }
                                },
                                onClick = {
                                    selectedProduct  = product
                                    itemName         = product.name
                                    price            = product.price.toString()
                                    isCustomItem     = false
                                    itemDropdownOpen = false
                                    onStockCheck(product.id, quantity)
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text    = { Text("Custom Item…", color = BrandOrange) },
                            onClick = {
                                selectedProduct  = null
                                itemName         = ""
                                price            = ""
                                isCustomItem     = true
                                itemDropdownOpen = false
                                onStockCheck(null, quantity)
                            }
                        )
                    }
                }

                if (isCustomItem) {
                    DialogField(value = itemName, label = "Item Name", onChange = { itemName = it })
                }

                // ── Quantity & Price ───────────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DialogField(
                        value        = quantity,
                        label        = "Qty",
                        onChange     = {
                            quantity = it
                            onStockCheck(selectedProduct?.id, it)
                        },
                        keyboardType = KeyboardType.Number,
                        modifier     = Modifier.weight(1f)
                    )
                    DialogField(
                        value        = price,
                        label        = "Price (₹)",
                        onChange     = { price = it },
                        keyboardType = KeyboardType.Decimal,
                        modifier     = Modifier.weight(1f)
                    )
                }

                // Stock warning
                stockWarning?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFC107).copy(alpha = 0.12f)
                    ) {
                        Text(
                            it,
                            modifier   = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize   = 12.sp,
                            color      = Color(0xFFFFC107),
                            lineHeight = 18.sp
                        )
                    }
                }

                if (selectedProduct != null) {
                    Text(
                        "Price auto-filled from inventory. Edit if needed.",
                        fontSize = 11.sp,
                        color    = OnSurfaceMuted
                    )
                }

                // ── Credit Sale Toggle ─────────────────────────────────────
                Row(
                    modifier          = Modifier.fillMaxWidth(),
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
                            checkedThumbColor  = OnSurfaceLight,
                            checkedTrackColor  = BrandOrange,
                            uncheckedThumbColor = OnSurfaceMuted,
                            uncheckedTrackColor = OnSurfaceMuted.copy(alpha = 0.3f)
                        )
                    )
                }

                // ── Customer Dropdown ──────────────────────────────────────
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
                        colors        = dialogFieldColors()
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
                            onClick = {
                                selectedCustomer     = null
                                customerName         = ""
                                isNewCustomer        = true
                                customerDropdownOpen = false
                            }
                        )
                    }
                }

                if (isNewCustomer) {
                    DialogField(value = customerName, label = "Customer Name", onChange = { customerName = it })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked         = saveCustomer,
                            onCheckedChange = { saveCustomer = it },
                            colors          = CheckboxDefaults.colors(checkedColor = BrandOrange)
                        )
                        Text("Save to customer list", fontSize = 13.sp, color = OnSurfaceLight)
                    }
                    if (saveCustomer) {
                        DialogField(
                            value        = customerPhone,
                            label        = "Phone Number",
                            onChange     = { customerPhone = it },
                            keyboardType = KeyboardType.Phone
                        )
                    }
                }

                errorMessage?.let {
                    Text(it, color = ErrorRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalCustomer = if (isNewCustomer) customerName else (selectedCustomer?.name ?: "")
                    val finalItem     = if (isCustomItem) itemName else (selectedProduct?.name ?: itemName)
                    onConfirm(
                        finalItem, quantity, price, finalCustomer,
                        selectedProduct?.id, isCreditSale,
                        saveCustomer && isNewCustomer, customerPhone
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                shape  = RoundedCornerShape(12.dp)
            ) { Text("Add Sale", color = OnSurfaceLight) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurfaceMuted) }
        }
    )
}

@Composable
private fun DialogField(
    value       : String,
    label       : String,
    onChange    : (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier    : Modifier = Modifier
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onChange,
        label           = { Text(label) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier        = modifier.fillMaxWidth(),
        colors          = dialogFieldColors()
    )
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = BrandOrange,
    unfocusedBorderColor = OnSurfaceMuted,
    focusedLabelColor    = BrandOrange,
    unfocusedLabelColor  = OnSurfaceMuted,
    focusedTextColor     = OnSurfaceLight,
    unfocusedTextColor   = OnSurfaceLight,
    cursorColor          = BrandOrange
)