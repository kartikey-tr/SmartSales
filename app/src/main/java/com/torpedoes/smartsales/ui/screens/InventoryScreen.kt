package com.torpedoes.smartsales.ui.screens.inventory

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
import com.torpedoes.smartsales.data.db.model.ProductEntity
import com.torpedoes.smartsales.ui.theme.*
import com.torpedoes.smartsales.ui.viewmodel.InventoryViewModel

@Composable
fun InventoryScreen(viewModel: InventoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Inventory", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
                IconButton(
                    onClick = { viewModel.openAdd() },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Product", tint = BrandOrange)
                }
            }

            if (uiState.products.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No products yet", color = OnSurfaceMuted, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.openAdd() },
                            colors  = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                            shape   = RoundedCornerShape(12.dp)
                        ) { Text("Add Product", color = OnSurfaceLight) }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.products, key = { it.id }) { product ->
                        ProductCard(
                            product  = product,
                            onEdit   = { viewModel.openEdit(product) },
                            onDelete = { viewModel.deleteProduct(product) }
                        )
                    }
                }
            }
        }

        if (uiState.isAddOpen) {
            ProductDialog(
                existing     = uiState.editingProduct,
                errorMessage = uiState.errorMessage,
                onDismiss    = { viewModel.closeDialog() },
                onConfirm    = { name, price, stock, category ->
                    viewModel.saveProduct(name, price, stock, category)
                }
            )
        }
    }
}

@Composable
private fun ProductCard(product: ProductEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                if (product.category.isNotBlank())
                    Text(product.category, fontSize = 12.sp, color = OnSurfaceMuted)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("₹%.2f".format(product.price), fontSize = 13.sp, color = BrandOrange, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Stock: ${product.stock}",
                        fontSize = 13.sp,
                        color = if (product.stock <= 5) ErrorRed else OnSurfaceMuted
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit)   { Icon(Icons.Default.Edit,   contentDescription = "Edit",   tint = OnSurfaceMuted, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed,       modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun ProductDialog(
    existing    : ProductEntity?,
    errorMessage: String?,
    onDismiss   : () -> Unit,
    onConfirm   : (String, String, String, String) -> Unit
) {
    var name     by remember { mutableStateOf(existing?.name     ?: "") }
    var price    by remember { mutableStateOf(existing?.price?.toString() ?: "") }
    var stock    by remember { mutableStateOf(existing?.stock?.toString() ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceMid,
        title  = { Text(if (existing != null) "Edit Product" else "Add Product", color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogField(value = name,     label = "Product Name", onChange = { name = it })
                DialogField(value = category, label = "Category (optional)", onChange = { category = it })
                DialogField(value = price,    label = "Price (₹)",    onChange = { price = it },    keyboardType = KeyboardType.Decimal)
                DialogField(value = stock,    label = "Stock Count",  onChange = { stock = it },    keyboardType = KeyboardType.Number)
                errorMessage?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, price, stock, category) },
                colors  = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                shape   = RoundedCornerShape(12.dp)
            ) { Text(if (existing != null) "Update" else "Add", color = OnSurfaceLight) }
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