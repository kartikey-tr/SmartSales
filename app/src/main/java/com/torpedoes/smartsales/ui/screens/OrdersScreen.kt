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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torpedoes.smartsales.data.db.model.CustomerEntity
import com.torpedoes.smartsales.data.db.model.OrderEntity
import com.torpedoes.smartsales.ui.theme.*
import com.torpedoes.smartsales.ui.viewmodel.OrdersViewModel
import java.text.SimpleDateFormat
import java.util.*

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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Orders", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
                    val autoCount = uiState.orders.count { it.isAutoOrder && it.status == "Pending" }
                    if (autoCount > 0) {
                        Text(
                            "$autoCount new WhatsApp order${if (autoCount > 1) "s" else ""}",
                            fontSize = 12.sp,
                            color    = Color(0xFF4CAF50)
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
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(64.dp))
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.orders, key = { it.id }) { order ->
                        OrderCard(
                            order          = order,
                            customers      = uiState.customers,
                            onStatusChange = { status -> viewModel.updateStatus(order, status) },
                            onMarkCreditPaid = { viewModel.markCreditPaid(order) },
                            onDelete       = { viewModel.deleteOrder(order) },
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
}

// ─────────────────────────────────────────────────────────────
// Order Card
// ─────────────────────────────────────────────────────────────

@Composable
private fun OrderCard(
    order              : OrderEntity,
    customers          : List<CustomerEntity>,
    onStatusChange     : (String) -> Unit,
    onMarkCreditPaid   : () -> Unit,
    onDelete           : () -> Unit,
    onGracePeriodUpdate: (CustomerEntity, String, Int) -> Unit
) {
    val statusColor = when (order.status) {
        "Completed" -> Color(0xFF4CAF50)
        "Cancelled" -> ErrorRed
        else        -> BrandOrange
    }
    var showMenu          by remember { mutableStateOf(false) }
    var showGraceDialog   by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val matchedCustomer = customers.find {
        it.name.equals(order.customerName, ignoreCase = true)
    }

    Card(
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = SurfaceMid),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (order.isAutoOrder && order.status == "Pending")
                    Modifier.border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(order.customerName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                        if (order.isAutoOrder) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "WhatsApp",
                                    modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize   = 9.sp,
                                    color      = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (order.isCreditSale) {
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFC107).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    if (order.creditPaid) "Credit ✓" else "Credit",
                                    modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize   = 9.sp,
                                    color      = if (order.creditPaid) Color(0xFF4CAF50) else Color(0xFFFFC107),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                        Text(
                            order.status,
                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize   = 11.sp,
                            color      = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = OnSurfaceMuted, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded         = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (order.status == "Pending") {
                                DropdownMenuItem(
                                    text    = { Text("Mark Completed") },
                                    onClick = { onStatusChange("Completed"); showMenu = false }
                                )
                                DropdownMenuItem(
                                    text    = { Text("Mark Cancelled") },
                                    onClick = { onStatusChange("Cancelled"); showMenu = false }
                                )
                            }
                            if (order.isCreditSale && !order.creditPaid && order.status == "Completed") {
                                DropdownMenuItem(
                                    text    = { Text("Mark Credit Paid", color = Color(0xFF4CAF50)) },
                                    onClick = { onMarkCreditPaid(); showMenu = false }
                                )
                            }
                            if (matchedCustomer != null) {
                                DropdownMenuItem(
                                    text    = { Text("Set Grace Period") },
                                    onClick = { showGraceDialog = true; showMenu = false }
                                )
                            }
                            DropdownMenuItem(
                                text    = { Text("Delete", color = ErrorRed) },
                                onClick = { showDeleteConfirm = true; showMenu = false }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(order.items, fontSize = 12.sp, color = OnSurfaceMuted, maxLines = 3)
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("₹%.2f".format(order.total), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BrandOrange)
                    if (order.isCreditSale && !order.creditPaid) {
                        Text(
                            "Credit due: ₹%.2f".format(order.creditAmount),
                            fontSize = 11.sp,
                            color    = Color(0xFFFFC107)
                        )
                    }
                }
                Text(
                    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(order.date)),
                    fontSize = 11.sp,
                    color    = OnSurfaceMuted
                )
            }

            // Grace period display
            if (matchedCustomer != null && matchedCustomer.gracePeriodType != "None") {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Grace: ${matchedCustomer.gracePeriodType}" +
                            if (matchedCustomer.gracePeriodType == "Custom")
                                " (${matchedCustomer.gracePeriodDays} days)"
                            else "",
                    fontSize = 10.sp,
                    color    = Color(0xFF4CAF50)
                )
            }
        }
    }

    // Grace period dialog
    if (showGraceDialog && matchedCustomer != null) {
        GracePeriodDialog(
            customer  = matchedCustomer,
            onDismiss = { showGraceDialog = false },
            onConfirm = { type, days ->
                onGracePeriodUpdate(matchedCustomer, type, days)
                showGraceDialog = false
            }
        )
    }

    // Delete confirm
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = SurfaceMid,
            title  = { Text("Delete Order?", color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
            text   = { Text("This will remove the order permanently.", color = OnSurfaceMuted, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDeleteConfirm = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape   = RoundedCornerShape(10.dp)
                ) { Text("Delete", color = OnSurfaceLight) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = OnSurfaceMuted) }
            }
        )
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
        title  = {
            Text(
                "Grace Period — ${customer.name}",
                color      = OnSurfaceLight,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp
            )
        },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Set how long this customer has to repay credit before their score is affected.",
                    fontSize    = 12.sp,
                    color       = OnSurfaceMuted,
                    lineHeight  = 18.sp
                )
                Spacer(Modifier.height(4.dp))
                options.forEach { option ->
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == option,
                            onClick  = { selectedType = option },
                            colors   = RadioButtonDefaults.colors(selectedColor = BrandOrange)
                        )
                        Text(
                            when (option) {
                                "Weekly"  -> "Weekly (7 days)"
                                "Monthly" -> "Monthly (30 days)"
                                "Custom"  -> "Custom"
                                else      -> "None (score affected immediately)"
                            },
                            fontSize = 13.sp,
                            color    = OnSurfaceLight
                        )
                    }
                }
                if (selectedType == "Custom") {
                    OutlinedTextField(
                        value         = customDays,
                        onValueChange = { customDays = it },
                        label         = { Text("Days") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = BrandOrange,
                            unfocusedBorderColor = OnSurfaceMuted,
                            focusedLabelColor    = BrandOrange,
                            unfocusedLabelColor  = OnSurfaceMuted,
                            focusedTextColor     = OnSurfaceLight,
                            unfocusedTextColor   = OnSurfaceLight,
                            cursorColor          = BrandOrange
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = if (selectedType == "Custom") customDays.toIntOrNull() ?: 0 else 0
                    onConfirm(selectedType, days)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                shape  = RoundedCornerShape(12.dp)
            ) { Text("Save", color = OnSurfaceLight) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurfaceMuted) }
        }
    )
}

// ─────────────────────────────────────────────────────────────
// Add Order Dialog
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
        title  = { Text("New Order", color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
        text   = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Customer Dropdown
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

                DialogField(value = items, label = "Items (e.g. 2x Rice, 1x Oil)", onChange = { items = it })
                DialogField(
                    value        = total,
                    label        = "Total Amount (₹)",
                    onChange     = { total = it },
                    keyboardType = KeyboardType.Decimal
                )

                // Credit Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Credit Order", fontSize = 13.sp, color = OnSurfaceLight, fontWeight = FontWeight.SemiBold)
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

                errorMessage?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (isNewCustomer) customerName else (selectedCustomer?.name ?: "")
                    onConfirm(finalName, items, total, isCreditSale, saveCustomer && isNewCustomer, customerPhone)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                shape  = RoundedCornerShape(12.dp)
            ) { Text("Add Order", color = OnSurfaceLight) }
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