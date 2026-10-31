package com.dxn.stockpoint.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Small durable key/value store. Survives process death, so the server URL the
 * shopkeeper typed once is never asked for again.
 */
object Prefs {

    private const val FILE = "dxn_stockpoint_v2"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_TOKEN = "token"
    private const val KEY_TOKEN_EXP = "token_expires_at"
    private const val KEY_WA_PHONE = "wa_phone"
    private const val KEY_TIER = "tier"
    private const val KEY_REV = "last_rev"
    private const val KEY_CART = "cart_snapshot"

    /** Emulator loopback. A real phone points this at the InfinityFree domain. */
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8099/"
    const val DEFAULT_WA_PHONE = "8801987851131"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    private fun p(): SharedPreferences =
        prefs ?: throw IllegalStateException("Prefs.init(context) must be called from Application/Activity")

    var baseUrl: String
        get() = p().getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = p().edit().putString(KEY_BASE_URL, value).apply()

    var token: String?
        get() = p().getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) = p().edit().putString(KEY_TOKEN, value).apply()

    var tokenExpiresAt: String
        get() = p().getString(KEY_TOKEN_EXP, "") ?: ""
        set(value) = p().edit().putString(KEY_TOKEN_EXP, value).apply()

    var waPhone: String
        get() = p().getString(KEY_WA_PHONE, DEFAULT_WA_PHONE) ?: DEFAULT_WA_PHONE
        set(value) = p().edit().putString(KEY_WA_PHONE, value).apply()

    /** "member" or "retail" — the last pricing tier the shopkeeper used. */
    var tier: String
        get() = p().getString(KEY_TIER, "retail") ?: "retail"
        set(value) = p().edit().putString(KEY_TIER, value).apply()

    /** Highest server revision this device has fully applied. */
    var lastRev: Int
        get() = p().getInt(KEY_REV, 0)
        set(value) = p().edit().putInt(KEY_REV, value).apply()

    /** Serialised cart so a half-built sale survives the app being killed. */
    var cartSnapshot: String
        get() = p().getString(KEY_CART, "[]") ?: "[]"
        set(value) = p().edit().putString(KEY_CART, value).apply()

    val isAdmin: Boolean get() = token != null

    fun clearSession() {
        p().edit().remove(KEY_TOKEN).remove(KEY_TOKEN_EXP).apply()
    }

    /** Wipes everything except the server URL, so re-onboarding is instant. */
    fun resetDevice() {
        val base = baseUrl
        p().edit().clear().apply()
        baseUrl = base
    }
}
