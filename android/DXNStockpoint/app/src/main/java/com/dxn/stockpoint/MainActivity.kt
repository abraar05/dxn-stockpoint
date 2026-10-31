package com.dxn.stockpoint

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dxn.stockpoint.data.model.AddProductRequest
import com.dxn.stockpoint.ui.screens.*
import com.dxn.stockpoint.ui.theme.DXNStockpointTheme
import com.dxn.stockpoint.ui.theme.EmeraldGreen
import com.dxn.stockpoint.ui.theme.ForestGreen
import com.dxn.stockpoint.viewmodel.StockpointViewModel

enum class NavItem(val title: String, val icon: ImageVector) {
    CATALOG("Catalog", Icons.Default.Storefront),
    CART("Cart", Icons.Default.ShoppingCart),
    ADMIN("Admin POS", Icons.Default.AdminPanelSettings),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: StockpointViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DXNStockpointTheme {
                DXNStockpointApp(viewModel)
            }
        }
    }
}

@Composable
fun DXNStockpointApp(viewModel: StockpointViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(NavItem.CATALOG) }

    val productsState by viewModel.products.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val ordersState by viewModel.orders.collectAsState()
    val dashboardState by viewModel.dashboard.collectAsState()
    val orderResult by viewModel.orderResult.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val waPhone by viewModel.waPhoneNumber.collectAsState()

    val filteredProducts = viewModel.filteredProducts()
    val cartCount = viewModel.cartItemCount

    LaunchedEffect(orderResult) {
        orderResult?.data?.let { result ->
            Toast.makeText(context, "✅ Order ${result.orderNumber} successfully recorded!", Toast.LENGTH_LONG).show()
            viewModel.clearOrderResult()
        }
        orderResult?.error?.let { err ->
            Toast.makeText(context, "⚠️ Error: $err", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavItem.values().forEach { item ->
                    val isSelected = currentTab == item
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            currentTab = item
                            if (item == NavItem.ADMIN) {
                                viewModel.loadDashboard()
                                viewModel.loadOrders()
                            }
                        },
                        icon = {
                            Box {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = if (isSelected) ForestGreen else Color.Gray
                                )
                                if (item == NavItem.CART && cartCount > 0) {
                                    Badge(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 10.dp, y = (-4).dp),
                                        containerColor = Color(0xFFD32F2F)
                                    ) {
                                        Text(
                                            text = if (cartCount > 99) "99+" else "$cartCount",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        },
                        label = {
                            Text(
                                text = item.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ForestGreen else Color.Gray
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = EmeraldGreen.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                NavItem.CATALOG -> CatalogScreen(
                    uiState = productsState.copy(data = filteredProducts),
                    cartItemCount = cartCount,
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory,
                    onSearchChanged = viewModel::setSearch,
                    onCategorySelected = viewModel::setCategory,
                    onAddToCart = { product ->
                        viewModel.addToCart(product)
                        Toast.makeText(context, "Added ${product.name}", Toast.LENGTH_SHORT).show()
                    },
                    onCartClick = { currentTab = NavItem.CART },
                    onRefresh = viewModel::loadProducts,
                    onSettingsClick = { currentTab = NavItem.SETTINGS },
                    onAdminClick = {
                        viewModel.loadDashboard()
                        viewModel.loadOrders()
                        currentTab = NavItem.ADMIN
                    }
                )

                NavItem.CART -> CartScreen(
                    cartItems = cart,
                    waPhoneNumber = waPhone,
                    isOrderLoading = orderResult?.isLoading == true,
                    orderError = orderResult?.error,
                    onBack = { currentTab = NavItem.CATALOG },
                    onIncrease = { viewModel.addToCart(it.product) },
                    onDecrease = { viewModel.removeFromCart(it.product) },
                    onDelete = { viewModel.deleteFromCart(it.product) },
                    onPlaceOrder = { name, isMember, memberId ->
                        viewModel.placeOrder(name, isMember, memberId)
                    },
                    onOrderErrorDismiss = viewModel::clearOrderResult
                )

                NavItem.ADMIN -> AdminScreen(
                    dashboardState = dashboardState,
                    productsState = productsState,
                    ordersState = ordersState,
                    onBack = { currentTab = NavItem.CATALOG },
                    onRefresh = {
                        viewModel.loadProducts()
                        viewModel.loadDashboard()
                    },
                    onUpdateStock = { productId, qty ->
                        viewModel.updateStock(productId, qty) { success ->
                            val msg = if (success) "Stock updated successfully!" else "Update failed"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAddProduct = { sku, name, category, retail, member, pv, stock ->
                        viewModel.addProduct(
                            AddProductRequest(
                                sku = sku,
                                name = name,
                                category = category,
                                retailPrice = retail,
                                memberPrice = member,
                                pvPoints = pv,
                                stockQty = stock
                            )
                        ) { success ->
                            val msg = if (success) "Product added to catalog!" else "Failed to add product"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLoadOrders = viewModel::loadOrders
                )

                NavItem.SETTINGS -> SettingsScreen(
                    currentServerUrl = serverUrl,
                    currentWaPhone = waPhone,
                    onBack = { currentTab = NavItem.CATALOG },
                    onSaveServerUrl = viewModel::saveServerUrl,
                    onSaveWaPhone = viewModel::setWaPhoneNumber
                )
            }
        }
    }
}
