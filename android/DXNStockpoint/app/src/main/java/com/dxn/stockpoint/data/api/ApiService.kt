package com.dxn.stockpoint.data.api

import com.dxn.stockpoint.data.model.*
import retrofit2.http.*

interface ApiService {

    @GET("api/v1/products")
    suspend fun getProducts(): ProductResponse

    @POST("api/v1/products")
    suspend fun addProduct(@Body request: AddProductRequest): GenericResponse

    @PUT("api/v1/products/{id}")
    suspend fun updateProduct(@Path("id") id: Int, @Body request: AddProductRequest): GenericResponse

    @POST("api/v1/orders")
    suspend fun placeOrder(@Body request: PlaceOrderRequest): PlaceOrderResponse

    @GET("api/v1/orders")
    suspend fun getOrders(): OrdersResponse

    @POST("api/v1/stock/update")
    suspend fun updateStock(@Body request: StockUpdateRequest): GenericResponse

    @GET("api/v1/dashboard")
    suspend fun getDashboard(): DashboardResponse
}

