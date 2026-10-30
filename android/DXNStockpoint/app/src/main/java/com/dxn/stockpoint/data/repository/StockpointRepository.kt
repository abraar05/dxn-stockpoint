package com.dxn.stockpoint.data.repository

import com.dxn.stockpoint.data.api.RetrofitClient
import com.dxn.stockpoint.data.model.*

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
}

class StockpointRepository {

    private val api get() = RetrofitClient.apiService

    suspend fun getProducts(): Result<List<Product>> = try {
        val response = api.getProducts()
        if (response.success) Result.Success(response.products)
        else Result.Error("Server returned success=false")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Unknown error", e)
    }

    suspend fun addProduct(request: AddProductRequest): Result<GenericResponse> = try {
        Result.Success(api.addProduct(request))
    } catch (e: Exception) {
        Result.Error(e.message ?: "Unknown error", e)
    }

    suspend fun placeOrder(request: PlaceOrderRequest): Result<PlaceOrderResponse> = try {
        val response = api.placeOrder(request)
        if (response.success) Result.Success(response)
        else Result.Error("Order failed")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Unknown error", e)
    }

    suspend fun getOrders(): Result<List<Order>> = try {
        val response = api.getOrders()
        if (response.success) Result.Success(response.orders)
        else Result.Error("Failed to fetch orders")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Unknown error", e)
    }

    suspend fun updateStock(productId: Int, newQty: Int): Result<GenericResponse> = try {
        Result.Success(api.updateStock(StockUpdateRequest(productId, newQty)))
    } catch (e: Exception) {
        Result.Error(e.message ?: "Unknown error", e)
    }

    suspend fun getDashboard(): Result<DashboardData> = try {
        val response = api.getDashboard()
        if (response.success) Result.Success(response.data)
        else Result.Error("Failed to fetch dashboard")
    } catch (e: Exception) {
        Result.Error(e.message ?: "Unknown error", e)
    }
}

