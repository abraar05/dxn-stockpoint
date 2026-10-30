package com.dxn.stockpoint.data.model

import com.google.gson.annotations.SerializedName

data class OrderItem(
    val productId: Int,
    val quantity: Int
)

data class PlaceOrderRequest(
    val customerName: String,
    val items: List<OrderItem>,
    val isMember: Boolean,
    val dxnMemberId: String? = null
)

data class PlaceOrderResponse(
    val success: Boolean,
    val orderNumber: String,
    val totalAmount: Double,
    val totalPV: Double
)

data class Order(
    val id: Int = 0,
    @SerializedName("order_number") val orderNumber: String = "",
    @SerializedName("customer_name") val customerName: String = "",
    @SerializedName("total_amount") val totalAmount: Double = 0.0,
    @SerializedName("total_pv") val totalPV: Double = 0.0,
    val status: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

data class OrdersResponse(
    val success: Boolean,
    val orders: List<Order>
)

data class StockUpdateRequest(
    val productId: Int,
    val newQuantity: Int
)

data class DashboardData(
    val totalOrders: Int,
    val totalRevenue: Double,
    val totalPV: Double,
    val lowStockProducts: List<Product>
)

data class DashboardResponse(
    val success: Boolean,
    val data: DashboardData
)

data class GenericResponse(
    val success: Boolean,
    val message: String
)
