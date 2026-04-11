package com.torpedoes.smartsales.ui.screens.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.torpedoes.smartsales.data.db.model.CustomerEntity
import com.torpedoes.smartsales.ui.theme.*
import com.torpedoes.smartsales.ui.viewmodel.CustomersViewModel
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun CustomersScreen(viewModel: CustomersViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Customers", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
                IconButton(onClick = { viewModel.openAdd() }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer", tint = BrandOrange)
                }
            }

            if (uiState.customers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.People, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No customers yet", color = OnSurfaceMuted, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.openAdd() },
                            colors  = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                            shape   = RoundedCornerShape(12.dp)
                        ) { Text("Add Customer", color = OnSurfaceLight) }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.customers, key = { it.id }) { customer ->
                        CustomerCard(
                            customer = customer,
                            onEdit   = { viewModel.openEdit(customer) },
                            onDelete = { viewModel.deleteCustomer(customer) }
                        )
                    }
                }
            }
        }

        if (uiState.isAddOpen) {
            CustomerDialog(
                existing     = uiState.editingCustomer,
                errorMessage = uiState.errorMessage,
                onDismiss    = { viewModel.closeDialog() },
                onConfirm    = { name, phone, email, address ->
                    viewModel.saveCustomer(name, phone, email, address)
                }
            )
        }
    }
}

@Composable
private fun CustomerCard(customer: CustomerEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = BrandOrange.copy(alpha = 0.15f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            customer.name.first().uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandOrange
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(customer.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                    Text(customer.phone, fontSize = 12.sp, color = OnSurfaceMuted)
                    if (customer.email.isNotBlank())
                        Text(customer.email, fontSize = 12.sp, color = OnSurfaceMuted)
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
private fun CustomerDialog(
    existing    : CustomerEntity?,
    errorMessage: String?,
    onDismiss   : () -> Unit,
    onConfirm   : (String, String, String, String) -> Unit
) {
    var name    by remember { mutableStateOf(existing?.name    ?: "") }
    var phone   by remember { mutableStateOf(existing?.phone   ?: "") }
    var email   by remember { mutableStateOf(existing?.email   ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceMid,
        title  = { Text(if (existing != null) "Edit Customer" else "Add Customer", color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogField(value = name,    label = "Full Name",         onChange = { name = it })
                DialogField(value = phone,   label = "Phone Number",      onChange = { phone = it },  keyboardType = KeyboardType.Phone)
                DialogField(value = email,   label = "Email (optional)",  onChange = { email = it },  keyboardType = KeyboardType.Email)
                DialogField(value = address, label = "Address (optional)", onChange = { address = it })
                errorMessage?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, phone, email, address) },
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