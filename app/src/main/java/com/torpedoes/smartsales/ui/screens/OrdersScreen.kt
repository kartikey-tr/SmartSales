package com.torpedoes.smartsales.ui.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.torpedoes.smartsales.data.db.model.OrderEntity
import com.torpedoes.smartsales.data.db.model.ProductEntity
import com.torpedoes.smartsales.ui.theme.*
import com.torpedoes.smartsales.ui.viewmodel.LinkedItem
import com.torpedoes.smartsales.ui.viewmodel.OrdersViewModel
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────
// Screen root
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    triggerOpenAdd: Boolean         = false,
    onAddOpened   : () -> Unit      = {},
    viewModel     : OrdersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(triggerOpenAdd) {
        if (triggerOpenAdd) { viewModel.openAdd(); onAddOpened() }
    }

    val fulfillOrder = uiState.fulfillOrderId?.let { id -> uiState.orders.find { it.id == id } }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Row(
                modifier              = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text("Orders", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
                    val autoCount = uiState.orders.count { it.isAutoOrder && it.status == "Pending" }
                    if (autoCount > 0) {
                        Text(
                            "$autoCount new WhatsApp order${if (autoCount > 1) "s" else ""}",
                            fontSize = 12.sp, color = Color(0xFF4CAF50)
                        )
                    }
                }
                IconButton(onClick = { viewModel.openAdd() }) {
                    Icon(Icons.Default.Add, contentDescription = "New Order", tint = BrandOrange)
                }
            }

            if (uiState.orders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ShoppingCart, null, tint = OnSurfaceMuted, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No orders yet", color = OnSurfaceMuted, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.openAdd() },
                            colors  = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                            shape   = RoundedCornerShape(12.dp)
                        ) { Text("New Order", color = OnSurfaceLight) }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.orders, key = { it.id }) { order ->
                        OrderCard(
                            order               = order,
                            customers           = uiState.customers,
                            linkedItems         = viewModel.parseLinkedItems(order.linkedItemsJson),
                            onStatusChange      = { status -> viewModel.updateStatus(order, status) },
                            onMarkCreditPaid    = { viewModel.markCreditPaid(order) },
                            onDelete            = { viewModel.deleteOrder(order) },
                            onFulfill           = { viewModel.openFulfill(order.id) },
                            onGracePeriodUpdate = { customer, type, days ->
                                viewModel.updateGracePeriod(customer, type, days)
                            }
                        )
                    }
                }
            }
        }

        if (uiState.isAddOpen) {
            AddOrderDialog(
                customers    = uiState.customers,
                errorMessage = uiState.errorMessage,
                onDismiss    = { viewModel.closeAdd() },
                onConfirm    = { customer, items, total, isCredit, saveCustomer, phone ->
                    viewModel.addOrder(customer, items, total, isCredit, saveCustomer, phone)
                }
            )
        }
    }

    // Fulfill sheet (outside Box to overlay correctly)
    if (fulfillOrder != null) {
        FulfillOrderSheet(
            order        = fulfillOrder,
            products     = uiState.products,
            initialItems = viewModel.parseLinkedItems(fulfillOrder.linkedItemsJson),
            onDismiss    = { viewModel.closeFulfill() },
            onConfirm    = { linkedItems, newProducts -> viewModel.fulfillOrder(fulfillOrder, linkedItems, newProducts) }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Order Card
// ─────────────────────────────────────────────────────────────

@Composable
private fun OrderCard(
    order              : OrderEntity,
    customers          : List<CustomerEntity>,
    linkedItems        : List<LinkedItem>,
    onStatusChange     : (String) -> Unit,
    onMarkCreditPaid   : () -> Unit,
    onDelete           : () -> Unit,
    onFulfill          : () -> Unit,
    onGracePeriodUpdate: (CustomerEntity, String, Int) -> Unit
) {
    val statusColor     = when (order.status) { "Completed" -> Color(0xFF4CAF50); "Cancelled" -> ErrorRed; else -> BrandOrange }
    var showMenu          by remember { mutableStateOf(false) }
    var showGraceDialog   by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val matchedCustomer   = customers.find { it.name.equals(order.customerName, ignoreCase = true) }
    val isFulfilled       = linkedItems.isNotEmpty()

    Card(
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = SurfaceMid),
        modifier = Modifier.fillMaxWidth().then(
            if (order.isAutoOrder && order.status == "Pending")
                Modifier.border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            else Modifier
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Top row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(order.customerName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                    if (order.isAutoOrder) { Spacer(Modifier.width(6.dp)); SmallChip("WhatsApp", Color(0xFF4CAF50)) }
                    if (order.isCreditSale) {
                        Spacer(Modifier.width(4.dp))
                        SmallChip(
                            if (order.creditPaid) "Credit ✓" else "Credit",
                            if (order.creditPaid) Color(0xFF4CAF50) else Color(0xFFFFC107)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                        Text(order.status, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = OnSurfaceMuted, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (order.status == "Pending") {
                                DropdownMenuItem(text = { Text("Mark Completed") }, onClick = { onStatusChange("Completed"); showMenu = false })
                                DropdownMenuItem(text = { Text("Mark Cancelled") }, onClick = { onStatusChange("Cancelled"); showMenu = false })
                            }
                            if (order.isCreditSale && !order.creditPaid && order.status == "Completed") {
                                DropdownMenuItem(text = { Text("Mark Credit Paid", color = Color(0xFF4CAF50)) }, onClick = { onMarkCreditPaid(); showMenu = false })
                            }
                            if (matchedCustomer != null) {
                                DropdownMenuItem(text = { Text("Set Grace Period") }, onClick = { showGraceDialog = true; showMenu = false })
                            }
                            DropdownMenuItem(text = { Text("Delete", color = ErrorRed) }, onClick = { showDeleteConfirm = true; showMenu = false })
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Items area
            if (isFulfilled) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceCard.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                        linkedItems.forEachIndexed { i, li ->
                            if (i > 0) HorizontalDivider(color = OnSurfaceMuted.copy(alpha = 0.1f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${li.qty}× ${li.productName}", fontSize = 12.sp, color = OnSurfaceLight, modifier = Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("₹${li.pricePerUnit.toInt()}/unit", fontSize = 10.sp, color = OnSurfaceMuted)
                                    Text("₹${li.lineTotal.toInt()}", fontSize = 12.sp, color = BrandOrange, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Text(order.items, fontSize = 12.sp, color = OnSurfaceMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
            }

            // Bottom row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    val displayTotal = if (isFulfilled) linkedItems.sumOf { it.lineTotal } else order.total
                    Text(
                        if (displayTotal > 0) "₹%.0f".format(displayTotal) else "Price pending",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color    = if (displayTotal > 0) BrandOrange else OnSurfaceMuted
                    )
                    if (order.isCreditSale && !order.creditPaid) {
                        Text("Credit due: ₹%.0f".format(order.creditAmount), fontSize = 11.sp, color = Color(0xFFFFC107))
                    }
                    Text(
                        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(order.date)),
                        fontSize = 11.sp, color = OnSurfaceMuted
                    )
                }

                if (order.status == "Pending") {
                    FilledTonalButton(
                        onClick        = onFulfill,
                        colors         = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isFulfilled) Color(0xFF4CAF50).copy(alpha = 0.15f) else BrandOrange.copy(alpha = 0.15f),
                            contentColor   = if (isFulfilled) Color(0xFF4CAF50) else BrandOrange
                        ),
                        shape          = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(if (isFulfilled) Icons.Default.Edit else Icons.Default.Inventory2, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isFulfilled) "Edit Items" else "Add Items", fontSize = 12.sp)
                    }
                }
            }

            if (matchedCustomer != null && matchedCustomer.gracePeriodType != "None") {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Grace: ${matchedCustomer.gracePeriodType}" + if (matchedCustomer.gracePeriodType == "Custom") " (${matchedCustomer.gracePeriodDays} days)" else "",
                    fontSize = 10.sp, color = Color(0xFF4CAF50)
                )
            }
        }
    }

    if (showGraceDialog && matchedCustomer != null) {
        GracePeriodDialog(
            customer  = matchedCustomer,
            onDismiss = { showGraceDialog = false },
            onConfirm = { type, days -> onGracePeriodUpdate(matchedCustomer, type, days); showGraceDialog = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = SurfaceMid,
            title            = { Text("Delete Order?", color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
            text             = { Text("This will remove the order permanently.", color = OnSurfaceMuted, fontSize = 13.sp) },
            confirmButton    = {
                Button(onClick = { onDelete(); showDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), shape = RoundedCornerShape(10.dp)) {
                    Text("Delete", color = OnSurfaceLight)
                }
            },
            dismissButton    = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = OnSurfaceMuted) } }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Fulfill Order Bottom Sheet
// ─────────────────────────────────────────────────────────────

private data class LineRow(
    val id          : Long             = System.nanoTime(),
    val product     : ProductEntity?   = null,
    val qty         : String           = "1",
    val pricePerUnit: String           = "",
    // When the user picks "Custom product", product stays null and this holds the typed name
    val isCustom    : Boolean          = false,
    val customName  : String           = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FulfillOrderSheet(
    order       : OrderEntity,
    products    : List<ProductEntity>,
    initialItems: List<LinkedItem>,
    onDismiss   : () -> Unit,
    // newProducts = list of (name, price) for custom items to insert into inventory
    onConfirm   : (List<LinkedItem>, List<Pair<String, Double>>) -> Unit
) {
    var rows by remember {
        mutableStateOf(
            if (initialItems.isNotEmpty()) {
                initialItems.map { li ->
                    LineRow(
                        product      = products.find { it.id == li.productId },
                        qty          = li.qty.toString(),
                        pricePerUnit = li.pricePerUnit.toInt().toString()
                    )
                }
            } else { listOf(LineRow()) }
        )
    }

    var error by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = SurfaceMid,
        dragHandle       = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(OnSurfaceMuted.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            Text("Add Items to Order", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
            Surface(shape = RoundedCornerShape(8.dp), color = SurfaceCard.copy(alpha = 0.6f)) {
                Text(
                    "\"${order.items.take(80)}${if (order.items.length > 80) "…" else ""}\"",
                    modifier  = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize  = 12.sp, color = OnSurfaceMuted,
                    maxLines  = 3, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "Pick products from inventory · set qty · price auto-fills from inventory (you can edit it).",
                fontSize = 11.sp, color = OnSurfaceMuted, lineHeight = 16.sp
            )

            HorizontalDivider(color = OnSurfaceMuted.copy(alpha = 0.15f))

            // Line item rows
            rows.forEachIndexed { index, row ->
                LineItemRow(
                    row              = row,
                    products         = products,
                    showDelete       = rows.size > 1,
                    onProductSelected = { product ->
                        rows = rows.toMutableList().also {
                            // id == 0 is our sentinel for "clear custom, reopen picker"
                            if (product.id == 0) {
                                it[index] = row.copy(isCustom = false, product = null, customName = "", pricePerUnit = "")
                            } else {
                                it[index] = row.copy(product = product, pricePerUnit = product.price.toInt().toString(), isCustom = false, customName = "")
                            }
                        }
                    },
                    onQtyChange      = { qty   -> rows = rows.toMutableList().also { it[index] = row.copy(qty = qty) } },
                    onPriceChange    = { price -> rows = rows.toMutableList().also { it[index] = row.copy(pricePerUnit = price) } },
                    onCustomToggled  = {
                        rows = rows.toMutableList().also {
                            it[index] = row.copy(isCustom = true, product = null, customName = "", pricePerUnit = "")
                        }
                    },
                    onCustomNameChange = { name -> rows = rows.toMutableList().also { it[index] = row.copy(customName = name) } },
                    onDelete         = { rows  = rows.toMutableList().also { it.removeAt(index) } }
                )
            }

            // Add row
            TextButton(onClick = { rows = rows + LineRow() }, modifier = Modifier.padding(vertical = 0.dp)) {
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
                    Text("Order Total", fontSize = 14.sp, color = OnSurfaceMuted)
                    Text("₹%.0f".format(runningTotal), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = BrandOrange)
                }
            }

            error?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }

            // Confirm
            Button(
                onClick = {
                    val built        = mutableListOf<LinkedItem>()
                    val newProducts  = mutableListOf<Pair<String, Double>>() // name → price for inventory insert

                    for (row in rows) {
                        val qty   = row.qty.toIntOrNull()?.takeIf { it > 0 } ?: continue
                        val price = row.pricePerUnit.toDoubleOrNull()?.takeIf { it >= 0 } ?: continue

                        if (row.isCustom) {
                            val name = row.customName.trim()
                            if (name.isBlank()) continue
                            // Placeholder id = -1; ViewModel will resolve after insertion
                            built.add(LinkedItem(-1, name, qty, price))
                            newProducts.add(Pair(name, price))
                        } else {
                            val p = row.product ?: continue
                            built.add(LinkedItem(p.id, p.name, qty, price))
                        }
                    }

                    if (built.isEmpty()) error = "Please select or name at least one product with a valid quantity."
                    else { error = null; onConfirm(built, newProducts) }
                },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Inventory2, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Confirm & Update Inventory", color = OnSurfaceLight, fontWeight = FontWeight.SemiBold)
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = OnSurfaceMuted)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Single line-item row
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineItemRow(
    row                : LineRow,
    products           : List<ProductEntity>,
    showDelete         : Boolean,
    onProductSelected  : (ProductEntity) -> Unit,
    onQtyChange        : (String) -> Unit,
    onPriceChange      : (String) -> Unit,
    onCustomToggled    : () -> Unit,
    onCustomNameChange : (String) -> Unit,
    onDelete           : () -> Unit
) {
    var dropdownOpen by remember { mutableStateOf(false) }
    var search       by remember { mutableStateOf("") }

    val filteredProducts = remember(search, products) {
        if (search.isBlank()) products
        else products.filter { it.name.contains(search, ignoreCase = true) }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Product picker + delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExposedDropdownMenuBox(
                    expanded         = dropdownOpen,
                    onExpandedChange = { if (!row.isCustom) dropdownOpen = it },
                    modifier         = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value         = when {
                            row.isCustom    -> "Custom product"
                            row.product != null -> row.product.name
                            else            -> ""
                        },
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Product", fontSize = 12.sp) },
                        placeholder   = { Text("Select from inventory", fontSize = 12.sp, color = OnSurfaceMuted) },
                        trailingIcon  = {
                            if (row.isCustom) {
                                // Show a clear icon to go back to picker
                                IconButton(onClick = {
                                    onProductSelected(ProductEntity(0, "", 0.0, 0)) // reset via parent clearing isCustom
                                    dropdownOpen = false
                                }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, null, tint = OnSurfaceMuted, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOpen)
                            }
                        },
                        modifier      = Modifier.menuAnchor().fillMaxWidth(),
                        colors        = if (row.isCustom)
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = BrandAmber,
                                unfocusedBorderColor = BrandAmber.copy(alpha = 0.5f),
                                focusedTextColor     = BrandAmber,
                                unfocusedTextColor   = BrandAmber,
                                focusedLabelColor    = BrandAmber,
                                unfocusedLabelColor  = BrandAmber.copy(alpha = 0.7f),
                                cursorColor          = BrandAmber
                            )
                        else dialogFieldColors(),
                        singleLine    = true
                    )
                    if (!row.isCustom) {
                        ExposedDropdownMenu(
                            expanded         = dropdownOpen,
                            onDismissRequest = { dropdownOpen = false; search = "" }
                        ) {
                            // Search box
                            OutlinedTextField(
                                value         = search,
                                onValueChange = { search = it },
                                placeholder   = { Text("Search…", fontSize = 12.sp) },
                                modifier      = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                singleLine    = true,
                                colors        = dialogFieldColors()
                            )
                            // Inventory products
                            if (filteredProducts.isEmpty()) {
                                DropdownMenuItem(text = { Text("No products found", color = OnSurfaceMuted, fontSize = 12.sp) }, onClick = {})
                            } else {
                                filteredProducts.forEach { product ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(product.name, color = OnSurfaceLight, fontSize = 13.sp)
                                                    Text("Stock: ${product.stock}", color = OnSurfaceMuted, fontSize = 11.sp)
                                                }
                                                Text("₹${product.price.toInt()}", color = BrandOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        },
                                        onClick = {
                                            onProductSelected(product)
                                            dropdownOpen = false
                                            search = ""
                                        }
                                    )
                                }
                            }
                            // Divider + custom option at bottom
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = OnSurfaceMuted.copy(alpha = 0.2f))
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AddCircleOutline, null, tint = BrandAmber, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Custom product…", color = BrandAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                },
                                onClick = {
                                    onCustomToggled()
                                    dropdownOpen = false
                                    search = ""
                                }
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

            // Custom product name field (only when isCustom)
            if (row.isCustom) {
                OutlinedTextField(
                    value         = row.customName,
                    onValueChange = onCustomNameChange,
                    label         = { Text("Product name", fontSize = 12.sp) },
                    placeholder   = { Text("e.g. Mustard Oil 1L", fontSize = 12.sp, color = OnSurfaceMuted) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = BrandAmber,
                        unfocusedBorderColor = BrandAmber.copy(alpha = 0.5f),
                        focusedLabelColor    = BrandAmber,
                        unfocusedLabelColor  = BrandAmber.copy(alpha = 0.7f),
                        focusedTextColor     = OnSurfaceLight,
                        unfocusedTextColor   = OnSurfaceLight,
                        cursorColor          = BrandAmber
                    )
                )
                // Hint
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = BrandAmber.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Will be added to your inventory automatically", fontSize = 10.sp, color = BrandAmber.copy(alpha = 0.7f))
                }
            }

            // Qty + Price per unit
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value           = row.qty,
                    onValueChange   = onQtyChange,
                    label           = { Text("Qty", fontSize = 12.sp) },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier        = Modifier.weight(1f),
                    colors          = dialogFieldColors()
                )
                OutlinedTextField(
                    value           = row.pricePerUnit,
                    onValueChange   = onPriceChange,
                    label           = { Text("Price/unit (₹)", fontSize = 12.sp) },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier        = Modifier.weight(2f),
                    colors          = dialogFieldColors()
                )
            }

            // Line total + stock warning
            val lineTotal = (row.qty.toIntOrNull() ?: 0) * (row.pricePerUnit.toDoubleOrNull() ?: 0.0)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                when {
                    row.isCustom -> {
                        SmallChip("New", BrandAmber)
                    }
                    row.product != null -> {
                        val reqQty = row.qty.toIntOrNull() ?: 0
                        if (reqQty > row.product.stock) {
                            Text("⚠ Only ${row.product.stock} in stock", fontSize = 11.sp, color = Color(0xFFFFC107))
                        } else {
                            Text("Stock: ${row.product.stock}", fontSize = 11.sp, color = OnSurfaceMuted)
                        }
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

// ─────────────────────────────────────────────────────────────
// Small reusable chip
// ─────────────────────────────────────────────────────────────

@Composable
private fun SmallChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────
// Grace Period Dialog
// ─────────────────────────────────────────────────────────────

@Composable
private fun GracePeriodDialog(
    customer : CustomerEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var selectedType by remember { mutableStateOf(customer.gracePeriodType) }
    var customDays   by remember { mutableStateOf(customer.gracePeriodDays.toString()) }
    val options      = listOf("None", "Weekly", "Monthly", "Custom")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceMid,
        title            = { Text("Grace Period — ${customer.name}", color = OnSurfaceLight, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set how long this customer has to repay credit before their score is affected.", fontSize = 12.sp, color = OnSurfaceMuted, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
                options.forEach { option ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedType == option, onClick = { selectedType = option }, colors = RadioButtonDefaults.colors(selectedColor = BrandOrange))
                        Text(when (option) { "Weekly" -> "Weekly (7 days)"; "Monthly" -> "Monthly (30 days)"; "Custom" -> "Custom"; else -> "None" }, fontSize = 13.sp, color = OnSurfaceLight)
                    }
                }
                if (selectedType == "Custom") {
                    OutlinedTextField(
                        value           = customDays,
                        onValueChange   = { customDays = it },
                        label           = { Text("Days") },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier        = Modifier.fillMaxWidth(),
                        colors          = dialogFieldColors()
                    )
                }
            }
        },
        confirmButton    = {
            Button(
                onClick = { onConfirm(selectedType, if (selectedType == "Custom") customDays.toIntOrNull() ?: 0 else 0) },
                colors  = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                shape   = RoundedCornerShape(12.dp)
            ) { Text("Save", color = OnSurfaceLight) }
        },
        dismissButton    = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurfaceMuted) } }
    )
}

// ─────────────────────────────────────────────────────────────
// Add Order Dialog (unchanged from before)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddOrderDialog(
    customers   : List<CustomerEntity>,
    errorMessage: String?,
    onDismiss   : () -> Unit,
    onConfirm   : (String, String, String, Boolean, Boolean, String) -> Unit
) {
    var selectedCustomer     by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerName         by remember { mutableStateOf("") }
    var customerDropdownOpen by remember { mutableStateOf(false) }
    var isNewCustomer        by remember { mutableStateOf(false) }
    var saveCustomer         by remember { mutableStateOf(false) }
    var customerPhone        by remember { mutableStateOf("") }
    var items                by remember { mutableStateOf("") }
    var total                by remember { mutableStateOf("") }
    var isCreditSale         by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceMid,
        title            = { Text("New Order", color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
        text             = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Customer", fontSize = 12.sp, color = OnSurfaceMuted)
                ExposedDropdownMenuBox(expanded = customerDropdownOpen, onExpandedChange = { customerDropdownOpen = it }) {
                    OutlinedTextField(
                        value         = if (isNewCustomer) customerName else (selectedCustomer?.name ?: ""),
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Select Customer") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerDropdownOpen) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth(),
                        colors        = dialogFieldColors()
                    )
                    ExposedDropdownMenu(expanded = customerDropdownOpen, onDismissRequest = { customerDropdownOpen = false }) {
                        customers.forEach { customer ->
                            DropdownMenuItem(
                                text    = { Column { Text(customer.name, color = OnSurfaceLight); Text(customer.phone, fontSize = 11.sp, color = OnSurfaceMuted) } },
                                onClick = { selectedCustomer = customer; customerName = customer.name; isNewCustomer = false; saveCustomer = false; customerPhone = ""; customerDropdownOpen = false }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("New Customer…", color = BrandOrange) }, onClick = { selectedCustomer = null; customerName = ""; isNewCustomer = true; customerDropdownOpen = false })
                    }
                }

                if (isNewCustomer) {
                    OutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("Customer Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = dialogFieldColors())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveCustomer, onCheckedChange = { saveCustomer = it }, colors = CheckboxDefaults.colors(checkedColor = BrandOrange))
                        Text("Save to customer list", fontSize = 13.sp, color = OnSurfaceLight)
                    }
                    if (saveCustomer) {
                        OutlinedTextField(value = customerPhone, onValueChange = { customerPhone = it }, label = { Text("Phone Number") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), colors = dialogFieldColors())
                    }
                }

                OutlinedTextField(value = items, onValueChange = { items = it }, label = { Text("Items (e.g. 2 Rice, 1 Oil)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = dialogFieldColors())
                OutlinedTextField(value = total, onValueChange = { total = it }, label = { Text("Total Amount ₹ (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), colors = dialogFieldColors())

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Credit Order", fontSize = 13.sp, color = OnSurfaceLight, fontWeight = FontWeight.SemiBold); Text("Customer pays later", fontSize = 11.sp, color = OnSurfaceMuted) }
                    Switch(checked = isCreditSale, onCheckedChange = { isCreditSale = it }, colors = SwitchDefaults.colors(checkedThumbColor = OnSurfaceLight, checkedTrackColor = BrandOrange, uncheckedThumbColor = OnSurfaceMuted, uncheckedTrackColor = OnSurfaceMuted.copy(alpha = 0.3f)))
                }

                errorMessage?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (isNewCustomer) customerName else (selectedCustomer?.name ?: "")
                    onConfirm(finalName, items, total.ifBlank { "0" }, isCreditSale, saveCustomer && isNewCustomer, customerPhone)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                shape  = RoundedCornerShape(12.dp)
            ) { Text("Add Order", color = OnSurfaceLight) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurfaceMuted) } }
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