package com.torpedoes.smartsales.ui.screens.customers

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torpedoes.smartsales.data.db.model.CustomerEntity
import com.torpedoes.smartsales.ui.theme.*
import com.torpedoes.smartsales.ui.viewmodel.CustomersViewModel
import com.torpedoes.smartsales.util.CreditScoreCalculator

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
                            customer          = customer,
                            onEdit            = { viewModel.openEdit(customer) },
                            onDelete          = { viewModel.deleteCustomer(customer) },
                            onGracePeriodSave = { type, days -> viewModel.updateGracePeriod(customer, type, days) }
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
private fun CustomerCard(
    customer         : CustomerEntity,
    onEdit           : () -> Unit,
    onDelete         : () -> Unit,
    onGracePeriodSave: (String, Int) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showGraceDialog   by remember { mutableStateOf(false) }
    val hasCredit         = customer.totalCredit > 0.0
    val scoreColor        = CreditScoreCalculator.scoreColor(customer.creditScore)
    val scoreLabel        = CreditScoreCalculator.scoreLabel(customer.creditScore)
    val outstanding       = customer.totalCredit - customer.totalCreditPaid

    Card(
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = SurfaceMid),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape    = RoundedCornerShape(50),
                        color    = BrandOrange.copy(alpha = 0.15f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                customer.name.first().uppercase(),
                                fontSize   = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color      = BrandOrange
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
                    IconButton(onClick = onEdit)                      { Icon(Icons.Default.Edit,   contentDescription = "Edit",   tint = OnSurfaceMuted, modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed,       modifier = Modifier.size(20.dp)) }
                }
            }

            // Grace period row — always visible
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = when (customer.gracePeriodType) {
                            "Weekly"  -> "Grace: Weekly (7 days)"
                            "Monthly" -> "Grace: Monthly (30 days)"
                            "Custom"  -> "Grace: ${customer.gracePeriodDays} days"
                            else      -> "No grace period"
                        },
                        fontSize = 12.sp,
                        color    = if (customer.gracePeriodType == "None") OnSurfaceMuted else Color(0xFF4CAF50)
                    )
                }
                TextButton(
                    onClick        = { showGraceDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (customer.gracePeriodType == "None") "Set" else "Edit",
                        fontSize = 11.sp,
                        color    = BrandOrange
                    )
                }
            }

            // Credit section
            if (hasCredit) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = OnSurfaceMuted.copy(alpha = 0.15f))
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress    = { customer.creditScore / 100f },
                                modifier    = Modifier.size(52.dp),
                                color       = scoreColor,
                                trackColor  = scoreColor.copy(alpha = 0.15f),
                                strokeWidth = 5.dp
                            )
                            Text(
                                "${customer.creditScore}",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color      = scoreColor
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(scoreLabel,     fontSize = 10.sp, color = scoreColor,    fontWeight = FontWeight.SemiBold)
                        Text("Credit Score", fontSize = 9.sp,  color = OnSurfaceMuted)
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CreditStat(label = "Total Credit", value = "₹%.2f".format(customer.totalCredit))
                        CreditStat(label = "Repaid",       value = "₹%.2f".format(customer.totalCreditPaid))
                        CreditStat(
                            label      = "Outstanding",
                            value      = "₹%.2f".format(outstanding),
                            valueColor = if (outstanding > 0) ErrorRed else Color(0xFF4CAF50)
                        )
                        if (customer.avgRepayDays > 0)
                            CreditStat(label = "Avg Repay", value = "${customer.avgRepayDays} days")
                    }
                }
            }
        }
    }

    if (showGraceDialog) {
        GracePeriodDialog(
            customer  = customer,
            onDismiss = { showGraceDialog = false },
            onConfirm = { type, days -> onGracePeriodSave(type, days); showGraceDialog = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = SurfaceMid,
            title  = { Text("Delete Customer?", color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
            text   = { Text("This will remove ${customer.name} from your customer list.", color = OnSurfaceMuted, fontSize = 13.sp) },
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
        title            = {
            Text(
                "Grace Period — ${customer.name}",
                color      = OnSurfaceLight,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp
            )
        },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "How long does this customer have to repay credit before their score is affected?",
                    fontSize   = 12.sp,
                    color      = OnSurfaceMuted,
                    lineHeight = 18.sp
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
                                "Custom"  -> "Custom number of days"
                                else      -> "None (score affected immediately)"
                            },
                            fontSize = 13.sp,
                            color    = OnSurfaceLight
                        )
                    }
                }
                if (selectedType == "Custom") {
                    OutlinedTextField(
                        value           = customDays,
                        onValueChange   = { customDays = it },
                        label           = { Text("Number of days") },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier        = Modifier.fillMaxWidth(),
                        colors          = OutlinedTextFieldDefaults.colors(
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
        confirmButton    = {
            Button(
                onClick = {
                    val days = if (selectedType == "Custom") customDays.toIntOrNull() ?: 0 else 0
                    onConfirm(selectedType, days)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
                shape  = RoundedCornerShape(12.dp)
            ) { Text("Save", color = OnSurfaceLight) }
        },
        dismissButton    = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurfaceMuted) } }
    )
}

@Composable
private fun CreditStat(
    label     : String,
    value     : String,
    valueColor: Color = OnSurfaceLight
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = OnSurfaceMuted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
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
                DialogField(value = name,    label = "Full Name",          onChange = { name = it })
                DialogField(value = phone,   label = "Phone Number",       onChange = { phone = it },   keyboardType = KeyboardType.Phone)
                DialogField(value = email,   label = "Email (optional)",   onChange = { email = it },   keyboardType = KeyboardType.Email)
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
        value           = value,
        onValueChange   = onChange,
        label           = { Text(label) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier        = Modifier.fillMaxWidth(),
        colors          = OutlinedTextFieldDefaults.colors(
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