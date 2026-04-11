package com.torpedoes.smartsales.ui.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torpedoes.smartsales.data.db.model.OrderEntity
import com.torpedoes.smartsales.ui.theme.*
import com.torpedoes.smartsales.ui.viewmodel.OrdersViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OrdersScreen(viewModel: OrdersViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Orders", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
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
                            onStatusChange = { status -> viewModel.updateStatus(order, status) },
                            onDelete       = { viewModel.deleteOrder(order) }
                        )
                    }
                }
            }
        }

        if (uiState.isAddOpen) {
            AddOrderDialog(
                errorMessage = uiState.errorMessage,
                onDismiss    = { viewModel.closeAdd() },
                onConfirm    = { customer, items, total -> viewModel.addOrder(customer, items, total) }
            )
        }
    }
}

@Composable
private fun OrderCard(order: OrderEntity, onStatusChange: (String) -> Unit, onDelete: () -> Unit) {
    val statusColor = when (order.status) {
        "Completed"  -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        "Cancelled"  -> ErrorRed
        else         -> BrandOrange
    }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceMid),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(order.customerName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                        Text(
                            order.status,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            color    = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = OnSurfaceMuted, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Mark Completed") }, onClick = { onStatusChange("Completed"); showMenu = false })
                            DropdownMenuItem(text = { Text("Mark Cancelled")  }, onClick = { onStatusChange("Cancelled");  showMenu = false })
                            DropdownMenuItem(text = { Text("Delete", color = ErrorRed) }, onClick = { onDelete(); showMenu = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(order.items, fontSize = 12.sp, color = OnSurfaceMuted, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("₹%.2f".format(order.total), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BrandOrange)
                Text(
                    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(order.date)),
                    fontSize = 11.sp, color = OnSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun AddOrderDialog(
    errorMessage: String?,
    onDismiss   : () -> Unit,
    onConfirm   : (String, String, String) -> Unit
) {
    var customer by remember { mutableStateOf("") }
    var items    by remember { mutableStateOf("") }
    var total    by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceMid,
        title  = { Text("New Order", color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogField(value = customer, label = "Customer Name", onChange = { customer = it })
                DialogField(value = items,    label = "Items (e.g. 2x Rice, 1x Oil)", onChange = { items = it })
                DialogField(value = total,    label = "Total Amount (₹)", onChange = { total = it }, keyboardType = KeyboardType.Decimal)
                errorMessage?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(customer, items, total) },
                colors  = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                shape   = RoundedCornerShape(12.dp)
            ) { Text("Add", color = OnSurfaceLight) }
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
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label) },
        singleLine    = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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