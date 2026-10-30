package com.dxn.stockpoint.data.model

import com.google.gson.annotations.SerializedName

data class Product(
    val id: Int = 0,
    val sku: String = "",
    val name: String = "",
    val category: String = "",
    @SerializedName("retail_price") val retailPrice: Double = 0.0,
    @SerializedName("member_price") val memberPrice: Double = 0.0,
    @SerializedName("pv_points") val pvPoints: Double = 0.0,
    @SerializedName("stock_qty") val stockQty: Int = 0
)

data class ProductResponse(
    val success: Boolean,
    val products: List<Product>
)

data class AddProductRequest(
    val sku: String,
    val name: String,
    val category: String,
    @SerializedName("retail_price") val retailPrice: Double,
    @SerializedName("member_price") val memberPrice: Double,
    @SerializedName("pv_points") val pvPoints: Double,
    @SerializedName("stock_qty") val stockQty: Int
)
