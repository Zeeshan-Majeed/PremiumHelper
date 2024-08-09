package com.pro.helper.pro_xutils

import com.android.billingclient.api.Purchase
import com.pro.helper.pro_xutils.errorsEnum.PremiumErrors

interface PremiumEventListeners {
    fun onProductsPurchased(purchases: List<Purchase?>)
    fun onPurchaseAcknowledged(purchase: Purchase)
    fun onPurchaseConsumed(purchase: Purchase)
    fun onBillingError(error: PremiumErrors)
}