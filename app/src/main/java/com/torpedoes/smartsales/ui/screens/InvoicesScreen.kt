package com.torpedoes.smartsales.ui.screens.invoices

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torpedoes.smartsales.data.db.model.SaleEntity
import com.torpedoes.smartsales.ui.theme.*
import com.torpedoes.smartsales.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun InvoicesScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Sales Report",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceLight,
            modifier = Modifier.padding(16.dp)
        )

        // Summary banner
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BrandOrange),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Today's Revenue", fontSize = 12.sp, color = OnSurfaceLight.copy(alpha = 0.8f))
                    Text("₹%.2f".format(uiState.todayRevenue), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Transactions", fontSize = 12.sp, color = OnSurfaceLight.copy(alpha = 0.8f))
                    Text("${uiState.todaySaleCount}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("All Sales", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))

        if (uiState.recentSales.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No sales recorded yet.", color = OnSurfaceMuted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.recentSales) { sale ->
                    SaleReportCard(sale)
                }
            }
        }
    }
}

@Composable
private fun SaleReportCard(sale: SaleEntity) {
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
                Text(sale.itemName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                Text("${sale.quantity} × ₹%.2f".format(sale.pricePerUnit), fontSize = 12.sp, color = OnSurfaceMuted)
                Text(sale.customerName, fontSize = 12.sp, color = OnSurfaceMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹%.2f".format(sale.total), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BrandOrange)
                Text(
                    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(sale.date)),
                    fontSize = 11.sp, color = OnSurfaceMuted
                )
            }
        }
    }
}