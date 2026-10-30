package com.dxn.stockpoint

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.dxn.stockpoint.data.model.AddProductRequest
import com.dxn.stockpoint.ui.screens.*
import com.dxn.stockpoint.ui.theme.DXNStockpointTheme
import com.dxn.stockpoint.viewmodel.StockpointViewModel

sealed class Screen {
    object Catalog : Screen()
    object Cart : Screen()
    object Admin : Screen()
    object Settings : Screen()
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
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Catalog) }

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

    LaunchedEffect(orderResult) {
        orderResult?.data?.let { result ->
            Toast.makeText(context, "✅ Order ${result.orderNumber} placed!", Toast.LENGTH_LONG).show()
            viewModel.clearOrderResult()
        }
    }

    when (currentScreen) {
        Screen.Catalog -> CatalogScreen(
            uiState = productsState.copy(data = filteredProducts),
            cartItemCount = viewModel.cartItemCount,
            searchQuery = searchQuery,
            selectedCategory = selectedCategory,
            onSearchChanged = viewModel::setSearch,
            onCategorySelected = viewModel::setCategory,
            onAddToCart = { product ->
                viewModel.addToCart(product)
                Toast.makeText(context, "${product.name} added to cart", Toast.LENGTH_SHORT).show()
            },
            onCartClick = { currentScreen = Screen.Cart },
            onRefresh = viewModel::loadProducts,
            onSettingsClick = { currentScreen = Screen.Settings },
            onAdminClick = {
                viewModel.loadDashboard()
                viewModel.loadOrders()
                currentScreen = Screen.Admin
            }
        )

        Screen.Cart -> CartScreen(
            cartItems = cart,
            waPhoneNumber = waPhone,
            isOrderLoading = orderResult?.isLoading == true,
            orderError = orderResult?.error,
            onBack = { currentScreen = Screen.Catalog },
            onIncrease = { viewModel.addToCart(it.product) },
            onDecrease = { viewModel.removeFromCart(it.product) },
            onDelete = { viewModel.deleteFromCart(it.product) },
            onPlaceOrder = { name, isMember, memberId ->
                viewModel.placeOrder(name, isMember, memberId)
            },
            onOrderErrorDismiss = viewModel::clearOrderResult
        )

        Screen.Admin -> AdminScreen(
            dashboardState = dashboardState,
            productsState = productsState,
            ordersState = ordersState,
            onBack = { currentScreen = Screen.Catalog },
            onRefresh = {
                viewModel.loadProducts()
                viewModel.loadDashboard()
            },
            onUpdateStock = { productId, qty ->
                viewModel.updateStock(productId, qty) { success ->
                    val msg = if (success) "Stock updated!" else "Update failed"
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
                    val msg = if (success) "Product added!" else "Failed to add product"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onLoadOrders = viewModel::loadOrders
        )

        Screen.Settings -> SettingsScreen(
            currentServerUrl = serverUrl,
            currentWaPhone = waPhone,
            onBack = { currentScreen = Screen.Catalog },
            onSaveServerUrl = viewModel::saveServerUrl,
            onSaveWaPhone = viewModel::setWaPhoneNumber
        )
    }
}
