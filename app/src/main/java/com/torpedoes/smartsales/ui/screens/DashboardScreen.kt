package com.torpedoes.smartsales.ui.screens.dashboard

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
import com.torpedoes.smartsales.ui.theme.*
import com.torpedoes.smartsales.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "SmartSales",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandOrange
                )
                Text(
                    text = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date()),
                    fontSize = 13.sp,
                    color = OnSurfaceMuted
                )
            }

            // Today's Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceMid),
                    elevation = CardDefaults.cardElevation(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Today's Summary", fontSize = 14.sp, color = OnSurfaceMuted)
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Revenue", fontSize = 12.sp, color = OnSurfaceMuted)
                                Text(
                                    "₹%.2f".format(uiState.todayRevenue),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandOrange
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Sales", fontSize = 12.sp, color = OnSurfaceMuted)
                                Text(
                                    "${uiState.todaySaleCount}",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceLight
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickActionButton(
                        label = "Add Sale",
                        icon  = Icons.Default.Add,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.openAddSale() }
                    )
                    QuickActionButton(
                        label = "New Order",
                        icon  = Icons.Default.ShoppingCart,
                        modifier = Modifier.weight(1f),
                        onClick = {}
                    )
                }
            }

            // Recent Sales
            if (uiState.recentSales.isNotEmpty()) {
                item {
                    Text("Recent Sales", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                }
                items(uiState.recentSales) { sale ->
                    Card(
                        shape  = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceMid),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(sale.itemName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                                Text(sale.customerName, fontSize = 12.sp, color = OnSurfaceMuted)
                            }
                            Text(
                                "₹%.2f".format(sale.total),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandOrange
                            )
                        }
                    }
                }
            }
        }

        // Add Sale Dialog
        if (uiState.isAddSaleOpen) {
            AddSaleDialog(
                errorMessage = uiState.errorMessage,
                onDismiss    = { viewModel.closeAddSale() },
                onConfirm    = { item, qty, price, customer ->
                    viewModel.addSale(item, qty, price, customer)
                }
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    label   : String,
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick : () -> Unit
) {
    Card(
        onClick   = onClick,
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = BrandOrange),
        modifier  = modifier.height(72.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = OnSurfaceLight, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
        }
    }
}

@Composable
private fun AddSaleDialog(
    errorMessage: String?,
    onDismiss   : () -> Unit,
    onConfirm   : (String, String, String, String) -> Unit
) {
    var item     by remember { mutableStateOf("") }
    var qty      by remember { mutableStateOf("") }
    var price    by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceMid,
        title  = { Text("Add Sale", color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogField(value = item,     label = "Item Name",    onChange = { item = it })
                DialogField(value = customer, label = "Customer Name",onChange = { customer = it })
                DialogField(value = qty,      label = "Quantity",     onChange = { qty = it },   keyboardType = KeyboardType.Number)
                DialogField(value = price,    label = "Price per Unit (₹)", onChange = { price = it }, keyboardType = KeyboardType.Decimal)
                errorMessage?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(item, qty, price, customer) },
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
    value        : String,
    label        : String,
    onChange     : (String) -> Unit,
    keyboardType : KeyboardType = KeyboardType.Text
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