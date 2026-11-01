package com.dxn.stockpoint.ui.components

import androidx.annotation.DrawableRes
import com.dxn.stockpoint.R

/**
 * Bundled product photography for the seeded DXN catalogue. Keeping the
 * photos inside the APK means the catalogue looks right on the very first run
 * and stays usable with no connection; remote `image_url` values are used for
 * products the shopkeeper adds later.
 */
object ProductArtwork {

    @DrawableRes
    fun forSku(sku: String?): Int? = when (sku?.trim()?.uppercase()) {
        "DXN-LZC-3IN1-20" -> R.drawable.p_dxn_lzc_3in1_20
        "DXN-LZC-2IN1-20" -> R.drawable.p_dxn_lzc_2in1_20
        "DXN-ZHI-MOCHA-20" -> R.drawable.p_dxn_zhi_mocha_20
        "DXN-LZC-LITE-20" -> R.drawable.p_dxn_lzc_lite_20
        "DXN-RGGL-90" -> R.drawable.p_dxn_rggl_90
        "DXN-MORINZHI-500" -> R.drawable.p_dxn_morinzhi_500
        "DXN-SPIRULINA-120" -> R.drawable.p_dxn_spirulina_120
        "DXN-ANDRO-90" -> R.drawable.p_dxn_andro_90
        "DXN-LZC-TEA-25" -> R.drawable.p_dxn_lzc_tea_25
        "DXN-TOOTHPASTE-120" -> R.drawable.p_dxn_toothpaste_120
        "DXN-SOAP-GANO" -> R.drawable.p_dxn_soap_gano
        "DXN-SHAMPOO-GANO" -> R.drawable.p_dxn_shampoo_gano
        else -> null
    }
}
