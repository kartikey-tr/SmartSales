package com.torpedoes.smartsales

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.torpedoes.smartsales.ui.navigation.AppNavGraph
import com.torpedoes.smartsales.ui.theme.BrandOrange
import com.torpedoes.smartsales.ui.theme.OnSurfaceLight
import com.torpedoes.smartsales.ui.theme.OnSurfaceMuted
import com.torpedoes.smartsales.ui.theme.SmartSalesTheme
import com.torpedoes.smartsales.ui.theme.SurfaceMid
import com.torpedoes.smartsales.util.NotificationPermissionHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartSalesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    var showPermissionDialog by remember {
                        mutableStateOf(
                            !NotificationPermissionHelper.isNotificationListenerEnabled(this@MainActivity)
                        )
                    }

                    AppNavGraph()

                    if (showPermissionDialog) {
                        AlertDialog(
                            onDismissRequest = { showPermissionDialog = false },
                            containerColor   = SurfaceMid,
                            title  = {
                                Text(
                                    "Enable WhatsApp Orders",
                                    color      = OnSurfaceLight,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text   = {
                                Text(
                                    "SmartSales can automatically capture WhatsApp orders and add them to your order list.\n\nGo to Settings → Notification Access → enable SmartSales.",
                                    color      = OnSurfaceMuted,
                                    fontSize   = androidx.compose.ui.unit.TextUnit.Unspecified
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showPermissionDialog = false
                                        NotificationPermissionHelper.openNotificationListenerSettings(this@MainActivity)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandOrange)
                                ) { Text("Open Settings", color = OnSurfaceLight) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPermissionDialog = false }) {
                                    Text("Not Now", color = OnSurfaceMuted)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}