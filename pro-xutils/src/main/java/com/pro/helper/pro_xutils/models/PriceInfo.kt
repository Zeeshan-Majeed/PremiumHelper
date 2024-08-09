package com.pro.helper.pro_xutils.models

import androidx.annotation.Keep
import com.android.billingclient.api.ProductDetails

@Keep
data class PriceInfo(
    var subsKey: String = "",
    var productBasePlanKey: String = "",
    var productOfferKey: String = "",
    var title: String = "",
    var type: String = "",
    var duration: String = "",
    var price: String = "",
    var productCompleteInfo: ProductDetails? = null
)
