package com.dxn.stockpoint.data.api

import com.dxn.stockpoint.data.model.*
import retrofit2.http.*

/**
 * Every route the PHP backend exposes. Paths are relative because the base URL
 * always ends in `/` (see [RetrofitClient.normalize]).
 */
interface ApiService {

    // ── Health / bootstrap ───────────────────────────────────────────────────
    @GET("api/v1/health")
    suspend fun health(): HealthResponse

    // ── Auth ────────────────────────────────────────────────────────────────
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("api/v1/auth/me")
    suspend fun me(): MeResponse

    @POST("api/v1/auth/pin")
    suspend fun changePin(@Body request: ChangePinRequest): GenericResponse

    // ── Catalog ─────────────────────────────────────────────────────────────
    @GET("api/v1/products")
    suspend fun getProducts(@Query("include_inactive") includeInactive: Int = 0): ProductResponse

    @POST("api/v1/products")
    suspend fun addProduct(@Body request: AddProductRequest): ProductEnvelope

    @PUT("api/v1/products/{id}")
    suspend fun updateProduct(@Path("id") id: Int, @Body request: AddProductRequest): ProductEnvelope

    // ── Stock ───────────────────────────────────────────────────────────────
    @POST("api/v1/stock/update")
    suspend fun updateStock(@Body request: StockUpdateRequest): ProductEnvelope

    @POST("api/v1/stock/adjust")
    suspend fun adjustStock(@Body request: StockAdjustRequest): ProductEnvelope

    @GET("api/v1/stock/movements")
    suspend fun movements(@Query("limit") limit: Int = 100): MovementsResponse

    // ── Orders ──────────────────────────────────────────────────────────────
    @POST("api/v1/orders")
    suspend fun placeOrder(@Body request: PlaceOrderRequest): PlaceOrderResponse

    @GET("api/v1/orders")
    suspend fun getOrders(
        @Query("limit") limit: Int = 100,
        @Query("status") status: String? = null,
        @Query("q") query: String? = null
    ): OrdersResponse

    @POST("api/v1/orders/{id}/status")
    suspend fun setOrderStatus(@Path("id") id: Int, @Body request: OrderStatusRequest): OrderEnvelope

    // ── Dashboard & settings ────────────────────────────────────────────────
    @GET("api/v1/dashboard")
    suspend fun getDashboard(): DashboardResponse

    @GET("api/v1/settings")
    suspend fun getSettings(): SettingsResponse

    @PUT("api/v1/settings")
    suspend fun saveSettings(@Body body: Map<String, String>): SettingsResponse

    // ── Sync ────────────────────────────────────────────────────────────────
    @GET("api/v1/sync")
    suspend fun sync(@Query("since") since: Int = 0): SyncResponse

    @GET("api/v1/sync/status")
    suspend fun syncStatus(): SyncStatusResponse
}
