package com.dxn.stockpoint.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dxn.stockpoint.data.api.RetrofitClient
import com.dxn.stockpoint.data.model.*
import com.dxn.stockpoint.data.repository.Result
import com.dxn.stockpoint.data.repository.StockpointRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState<T>(
    val data: T? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class StockpointViewModel : ViewModel() {

    private val repository = StockpointRepository()

    // ── Products ─────────────────────────────────────────────────────────────
    private val _products = MutableStateFlow(UiState<List<Product>>(isLoading = true))
    val products: StateFlow<UiState<List<Product>>> = _products.asStateFlow()

    // ── Cart ─────────────────────────────────────────────────────────────────
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    val cartItemCount: Int get() = _cart.value.sumOf { it.quantity }

    // ── Orders ────────────────────────────────────────────────────────────────
    private val _orders = MutableStateFlow(UiState<List<Order>>())
    val orders: StateFlow<UiState<List<Order>>> = _orders.asStateFlow()

    // ── Dashboard ─────────────────────────────────────────────────────────────
    private val _dashboard = MutableStateFlow(UiState<DashboardData>(isLoading = true))
    val dashboard: StateFlow<UiState<DashboardData>> = _dashboard.asStateFlow()

    // ── Order Result ──────────────────────────────────────────────────────────
    private val _orderResult = MutableStateFlow<UiState<PlaceOrderResponse>?>(null)
    val orderResult: StateFlow<UiState<PlaceOrderResponse>?> = _orderResult.asStateFlow()

    // ── Search & Filter ───────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // ── Settings ──────────────────────────────────────────────────────────────
    private val _serverUrl = MutableStateFlow(RetrofitClient.getBaseUrl())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _waPhoneNumber = MutableStateFlow("8801987851131")
    val waPhoneNumber: StateFlow<String> = _waPhoneNumber.asStateFlow()

    init {
        loadProducts()
    }

    // ── Product Actions ───────────────────────────────────────────────────────

    fun loadProducts() {
        viewModelScope.launch {
            _products.value = UiState(isLoading = true)
            when (val r = repository.getProducts()) {
                is Result.Success -> _products.value = UiState(data = r.data)
                is Result.Error   -> _products.value = UiState(error = r.message)
            }
        }
    }

    fun setSearch(query: String) { _searchQuery.value = query }
    fun setCategory(cat: String) { _selectedCategory.value = cat }

    fun filteredProducts(): List<Product> {
        val all = _products.value.data ?: return emptyList()
        val q = _searchQuery.value.lowercase()
        val cat = _selectedCategory.value
        return all.filter { p ->
            (cat == "All" || p.category.equals(cat, ignoreCase = true)) &&
            (q.isEmpty() || p.name.lowercase().contains(q) || p.category.lowercase().contains(q))
        }
    }

    // ── Cart Actions ──────────────────────────────────────────────────────────

    fun addToCart(product: Product) {
        val current = _cart.value.toMutableList()
        val existing = current.find { it.product.id == product.id }
        if (existing != null) {
            val idx = current.indexOf(existing)
            current[idx] = existing.copy(quantity = existing.quantity + 1)
        } else {
            current.add(CartItem(product, 1))
        }
        _cart.value = current
    }

    fun removeFromCart(product: Product) {
        val current = _cart.value.toMutableList()
        val existing = current.find { it.product.id == product.id }
        if (existing != null) {
            if (existing.quantity > 1) {
                val idx = current.indexOf(existing)
                current[idx] = existing.copy(quantity = existing.quantity - 1)
            } else {
                current.remove(existing)
            }
        }
        _cart.value = current
    }

    fun deleteFromCart(product: Product) {
        _cart.value = _cart.value.filter { it.product.id != product.id }
    }

    fun clearCart() { _cart.value = emptyList() }

    fun cartTotal(isMember: Boolean): Double =
        _cart.value.sumOf { if (isMember) it.subtotalMember else it.subtotalRetail }

    fun cartTotalPV(): Double = _cart.value.sumOf { it.subtotalPV }

    // ── Order Actions ─────────────────────────────────────────────────────────

    fun placeOrder(customerName: String, isMember: Boolean, dxnMemberId: String?) {
        viewModelScope.launch {
            _orderResult.value = UiState(isLoading = true)
            val request = PlaceOrderRequest(
                customerName = customerName,
                items = _cart.value.map { OrderItem(it.product.id, it.quantity) },
                isMember = isMember,
                dxnMemberId = dxnMemberId
            )
            when (val r = repository.placeOrder(request)) {
                is Result.Success -> {
                    _orderResult.value = UiState(data = r.data)
                    clearCart()
                }
                is Result.Error -> _orderResult.value = UiState(error = r.message)
            }
        }
    }

    fun clearOrderResult() { _orderResult.value = null }

    fun loadOrders() {
        viewModelScope.launch {
            _orders.value = UiState(isLoading = true)
            when (val r = repository.getOrders()) {
                is Result.Success -> _orders.value = UiState(data = r.data)
                is Result.Error   -> _orders.value = UiState(error = r.message)
            }
        }
    }

    // ── Dashboard Actions ─────────────────────────────────────────────────────

    fun loadDashboard() {
        viewModelScope.launch {
            _dashboard.value = UiState(isLoading = true)
            when (val r = repository.getDashboard()) {
                is Result.Success -> _dashboard.value = UiState(data = r.data)
                is Result.Error   -> _dashboard.value = UiState(error = r.message)
            }
        }
    }

    fun updateStock(productId: Int, qty: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            when (repository.updateStock(productId, qty)) {
                is Result.Success -> { onDone(true); loadProducts(); loadDashboard() }
                is Result.Error   -> onDone(false)
            }
        }
    }

    fun addProduct(request: AddProductRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            when (repository.addProduct(request)) {
                is Result.Success -> { onDone(true); loadProducts() }
                is Result.Error   -> onDone(false)
            }
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun saveServerUrl(url: String) {
        RetrofitClient.setBaseUrl(url)
        _serverUrl.value = RetrofitClient.getBaseUrl()
        loadProducts()
    }

    fun setWaPhoneNumber(phone: String) { _waPhoneNumber.value = phone }
}

