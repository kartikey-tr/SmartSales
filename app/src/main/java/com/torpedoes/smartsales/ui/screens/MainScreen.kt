package com.torpedoes.smartsales.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.torpedoes.smartsales.ui.screens.customers.CustomersScreen
import com.torpedoes.smartsales.ui.screens.dashboard.DashboardScreen
import com.torpedoes.smartsales.ui.screens.inventory.InventoryScreen
import com.torpedoes.smartsales.ui.screens.invoices.InvoicesScreen
import com.torpedoes.smartsales.ui.screens.orders.OrdersScreen
import com.torpedoes.smartsales.ui.theme.*

private sealed class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : BottomTab("dashboard", "Home",      Icons.Default.Home)
    object Inventory : BottomTab("inventory", "Inventory", Icons.Default.Inventory2)
    object Orders    : BottomTab("orders",    "Orders",    Icons.Default.ShoppingCart)
    object Customers : BottomTab("customers", "Customers", Icons.Default.People)
    object Invoices  : BottomTab("invoices",  "Reports",   Icons.Default.Receipt)
}

@Composable
fun MainScreen() {
    val tabs = listOf(
        BottomTab.Dashboard,
        BottomTab.Inventory,
        BottomTab.Orders,
        BottomTab.Customers,
        BottomTab.Invoices
    )
    val navController = rememberNavController()
    val backStack     by navController.currentBackStackEntryAsState()
    val currentRoute  = backStack?.destination?.route

    var openOrdersDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = SurfaceMid) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick  = {
                            navController.navigate(tab.route) {
                                popUpTo(BottomTab.Dashboard.route) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon  = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = BrandOrange,
                            selectedTextColor   = BrandOrange,
                            unselectedIconColor = OnSurfaceMuted,
                            unselectedTextColor = OnSurfaceMuted,
                            indicatorColor      = BrandOrange.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = BottomTab.Dashboard.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(BottomTab.Dashboard.route) {
                DashboardScreen(
                    onNavigateToOrders = {
                        openOrdersDialog = true
                        navController.navigate(BottomTab.Orders.route) {
                            popUpTo(BottomTab.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }
            composable(BottomTab.Inventory.route) { InventoryScreen() }
            composable(BottomTab.Orders.route) {
                OrdersScreen(
                    triggerOpenAdd = openOrdersDialog,
                    onAddOpened    = { openOrdersDialog = false }
                )
            }
            composable(BottomTab.Customers.route) { CustomersScreen() }
            composable(BottomTab.Invoices.route)  { InvoicesScreen() }
        }
    }
}