package com.pro.helper.pro_xutils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.pro.helper.pro_xutils.errorsEnum.PremiumErrors
import com.pro.helper.pro_xutils.models.PriceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PremiumHelper(private val context: Context) {

    private val TAG = "PremiumHelper"

    companion object {
        private var isClientReady = false
        private var billingClient: BillingClient? = null
        private var premiumEventListener: PremiumEventListeners? = null
        private var premiumClientListener: PremiumClientListeners? = null
        private var purchasesUpdatedListener: PurchasesUpdatedListener? = null

        private val subKeys by lazy { mutableListOf<String>() }
        private val inAppKeys by lazy { mutableListOf<String>() }
        private val consumeAbleKeys by lazy { mutableListOf<String>() }
        private val allProducts by lazy { mutableListOf<ProductDetails>() }
        private val purchasedSubsProductList by lazy { mutableListOf<Purchase>() }
        private val purchasedInAppProductList by lazy { mutableListOf<Purchase>() }

        private var enableLog = false
    }

    fun initialize() {
        if (billingClient == null) {
            isClientReady = false
            premiumLog("Setup new billing client")
            purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        purchases?.let {
                            for (purchase in it) {
                                premiumLog("purchases --> $purchase")
                                CoroutineScope(Dispatchers.IO).launch {
                                    handlePurchase(purchase)
                                }
                            }
                            premiumEventListener?.onProductsPurchased(purchasedSubsProductList)
                        }
                    }

                    BillingClient.BillingResponseCode.USER_CANCELED -> {
                        premiumLog("User pressed back or canceled a dialog." + " Response code: " + billingResult.responseCode)
                        premiumEventListener?.onBillingError(PremiumErrors.USER_CANCELED)
                    }

                    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                        premiumLog("Network connection is down." + " Response code: " + billingResult.responseCode)
                        premiumEventListener?.onBillingError(PremiumErrors.SERVICE_UNAVAILABLE)

                    }

                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                        premiumLog("Billing API version is not supported for the type requested." + " Response code: " + billingResult.responseCode)
                        premiumEventListener?.onBillingError(PremiumErrors.BILLING_UNAVAILABLE)

                    }

                    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                        premiumLog("Requested product is not available for purchase." + " Response code: " + billingResult.responseCode)
                        premiumEventListener?.onBillingError(PremiumErrors.ITEM_UNAVAILABLE)

                    }

                    BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                        premiumLog("Invalid arguments provided to the API." + " Response code: " + billingResult.responseCode)
                        premiumEventListener?.onBillingError(PremiumErrors.DEVELOPER_ERROR)

                    }

                    BillingClient.BillingResponseCode.ERROR -> {
                        premiumLog("Fatal error during the API action." + " Response code: " + billingResult.responseCode)
                        premiumEventListener?.onBillingError(PremiumErrors.ERROR)
                    }

                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                        premiumLog("Failure to purchase since item is already owned." + " Response code: " + billingResult.responseCode)
                        premiumEventListener?.onBillingError(PremiumErrors.ITEM_ALREADY_OWNED)
                    }

                    BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {
                        premiumLog("Failure to consume since item is not owned." + " Response code: " + billingResult.responseCode)
                        premiumEventListener?.onBillingError(PremiumErrors.ITEM_NOT_OWNED)
                    }

                    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> {
                        premiumLog("Initialization error: service disconnected/timeout. Trying to reconnect...")
                        premiumEventListener?.onBillingError(PremiumErrors.SERVICE_DISCONNECTED)
                    }

                    else -> {
                        premiumLog("Initialization error: ")
                        premiumEventListener?.onBillingError(PremiumErrors.ERROR)
                    }
                }
            }
            billingClient = BillingClient.newBuilder(context)
                .setListener(purchasesUpdatedListener!!)
                .enablePendingPurchases().build()
            startConnection()
        } else {
            premiumClientListener?.onClientAllReadyConnected()
        }
    }

    private fun startConnection() {

        premiumLog("Connect start with Google Play")
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    premiumLog("Connected to Google Play")
                    isClientReady = true

                    CoroutineScope(Dispatchers.Main).launch {
                        // Define CompletableDeferred for each async task
                        val subsDeferred = CompletableDeferred<Unit>()
                        val inAppDeferred = CompletableDeferred<Unit>()
                        val purchasesDeferred = CompletableDeferred<Unit>()

                        // Fetch subscriptions
                        withContext(Dispatchers.IO) {
                            if (subKeys.isNotEmpty()) {
                                fetchAvailableAllSubsProducts(subKeys, subsDeferred)
                            } else {
                                subsDeferred.complete(Unit)
                            }
                        }

                        // Fetch in-app products
                        withContext(Dispatchers.IO) {
                            if (inAppKeys.isNotEmpty()) {
                                fetchAvailableAllInAppProducts(inAppKeys, inAppDeferred)
                            } else {
                                inAppDeferred.complete(Unit)
                            }
                        }

                        // Fetch active purchases
                        withContext(Dispatchers.IO) {
                            fetchActivePurchases(purchasesDeferred)
                        }

                        // Await all CompletableDeferred to complete
                        awaitAll(subsDeferred, inAppDeferred, purchasesDeferred)

                        // Notify the listener on the Main thread
                        withContext(Dispatchers.Main) {
                            premiumLog("Billing client is ready")
                            premiumClientListener?.onClientReady()
                        }
                    }

                } else if (billingResult.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
                    premiumClientListener?.onPurchasesUpdated()
                }
            }

            override fun onBillingServiceDisconnected() {
                premiumLog("Fail to connect with Google Play")
                isClientReady = false

                // callback with Main thread because billing throw it in IO thread
                CoroutineScope(Dispatchers.Main).launch {
                    premiumClientListener?.onClientInitError()
                }
            }
        })
    }

    private fun fetchAvailableAllSubsProducts(productListKeys: MutableList<String>, subsDeferred: CompletableDeferred<Unit>) {
        // Early return if billing client is null
        val client = billingClient ?: run {
            premiumLog("Billing client null while fetching All Subscription Products")
            premiumEventListener?.onBillingError(PremiumErrors.SERVICE_DISCONNECTED)
            subsDeferred.complete(Unit)
            return
        }

        // Create a list of QueryProductDetailsParams.Product from the productListKeys
        val productList = productListKeys.map {
            premiumLog("Subscription key: $it")
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        // Build the QueryProductDetailsParams
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        // Query product details asynchronously
        client.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {

                productDetailsList.forEach { productDetails ->
                    premiumLog("Subscription product details: $productDetails")
                    allProducts.add(productDetails)
                }
            } else {
                premiumLog("Failed to retrieve SUBS prices: ${billingResult.debugMessage}")
            }

            subsDeferred.complete(Unit)

        }
    }

    fun subscribe(activity: Activity, productId: String, offerId: String = "") {
        if (billingClient != null) {
            val productInfo = getProductDetail(productId, offerId, BillingClient.ProductType.SUBS)
            if (productInfo != null) {
                val productDetailsParamsList = ArrayList<BillingFlowParams.ProductDetailsParams>()
                if (productInfo.productType == BillingClient.ProductType.SUBS && productInfo.subscriptionOfferDetails != null) {
                    val offerToken =
                        getOfferToken(productInfo.subscriptionOfferDetails, productId, offerId)
                    if (offerToken.trim { it <= ' ' } != "") {
                        productDetailsParamsList.add(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productInfo).setOfferToken(offerToken).build()
                        )
                    } else {
                        premiumEventListener?.onBillingError(PremiumErrors.OFFER_NOT_EXIST)
                        premiumLog("The offer id: $productId doesn't seem to exist on Play Console")
                        return
                    }
                } else {
                    productDetailsParamsList.add(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productInfo).build())
                }
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList).build()
                billingClient!!.launchBillingFlow(activity, billingFlowParams)
            } else {
                premiumEventListener?.onBillingError(PremiumErrors.PRODUCT_NOT_EXIST)
                premiumLog("Billing client can not launch billing flow because product details are missing")
            }
        } else {
            premiumLog("Billing client null while purchases")
            premiumEventListener?.onBillingError(PremiumErrors.SERVICE_DISCONNECTED)
        }
    }

    private fun upgradeOrDowngradeSubscription(activity: Activity, updateProductId: String, updateOfferId: String, oldProductID: String, policy: Int) {

        if (billingClient != null) {
            val productInfo =
                getProductDetail(updateProductId, updateOfferId, BillingClient.ProductType.SUBS)
            if (productInfo != null) {
                val oldToken = getOldPurchaseToken(oldProductID)
                if (oldToken.trim().isNotEmpty()) {
                    val productDetailsParamsList =
                        ArrayList<BillingFlowParams.ProductDetailsParams>()
                    if (productInfo.productType == BillingClient.ProductType.SUBS && productInfo.subscriptionOfferDetails != null) {
                        val offerToken = getOfferToken(
                            productInfo.subscriptionOfferDetails, updateProductId, updateOfferId
                        )
                        if (offerToken.trim { it <= ' ' } != "") {
                            productDetailsParamsList.add(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productInfo).setOfferToken(offerToken)
                                    .build()
                            )
                        } else {
                            premiumEventListener?.onBillingError(PremiumErrors.OFFER_NOT_EXIST)
                            premiumLog("The offer id: $updateProductId doesn't seem to exist on Play Console")
                            return
                        }
                    } else {
                        productDetailsParamsList.add(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productInfo).build()
                        )
                    }
                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(productDetailsParamsList)
                        .setSubscriptionUpdateParams(
                            BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                                .setOldPurchaseToken(oldToken)
                                .setSubscriptionReplacementMode(policy)
                                .build()
                        ).build()
                    billingClient!!.launchBillingFlow(activity, billingFlowParams)
                } else {
                    premiumLog("old purchase token not found")
                    premiumEventListener?.onBillingError(PremiumErrors.OLD_PURCHASE_TOKEN_NOT_FOUND)

                }
            } else {
                premiumLog("Billing client can not launch billing flow because product details are missing while update")
                premiumEventListener?.onBillingError(PremiumErrors.PRODUCT_NOT_EXIST)
            }
        } else {
            premiumLog("Billing client null while Update subs")
            premiumEventListener?.onBillingError(PremiumErrors.SERVICE_DISCONNECTED)
        }
    }

    private fun getOldPurchaseToken(basePlanKey: String): String {
        // Find the product that matches the subscription and base plan key
        val matchingProduct = allProducts.firstOrNull { product ->
            product.productType == BillingClient.ProductType.SUBS && product.subscriptionOfferDetails?.any { it.basePlanId == basePlanKey } == true
        }

        // If a matching product is found, find the corresponding purchase token
        matchingProduct?.let { product ->
            val matchingPurchase = purchasedSubsProductList.firstOrNull { purchase ->
                purchase.products.firstOrNull() == product.productId
            }
            return matchingPurchase?.purchaseToken ?: ""
        }

        // Return empty string if no matching product or purchase is found
        return ""
    }

    private fun getOfferToken(offerList: List<ProductDetails.SubscriptionOfferDetails>?, productId: String, offerId: String): String {
        for (product in offerList!!) {
            if (product.offerId != null && product.offerId == offerId && product.basePlanId == productId) {
                return product.offerToken
            } else if (offerId.trim { it <= ' ' } == "" && product.basePlanId == productId && product.offerId == null) {
                // case when no offer in base plan
                return product.offerToken
            }
        }
        premiumLog("No Offer find")
        return ""
    }

    fun setSubKeys(keysList: MutableList<String>): PremiumHelper {
        subKeys.addAll(keysList)
        return this
    }

    fun isSubsPremiumUser(): Boolean {
        return purchasedSubsProductList.isNotEmpty()
    }

    fun isSubsPremiumUserByBasePlanKey(basePlanKey: String): Boolean {
        val isPremiumUser = allProducts.any { product ->
            product.productType == BillingClient.ProductType.SUBS &&
                    product.subscriptionOfferDetails?.any { it.basePlanId == basePlanKey } == true &&
                    purchasedSubsProductList.any { it.products.firstOrNull() == product.productId }
        }

        if (!isPremiumUser) {
            premiumEventListener?.onBillingError(PremiumErrors.PRODUCT_NOT_EXIST)
        }

        return isPremiumUser
    }

    fun isSubsPremiumUserBySubIDKey(subId: String): Boolean {
        return purchasedSubsProductList.any { it.products.first() == subId }
    }

    fun areSubscriptionsSupported(): Boolean {
        return if (billingClient != null) {
            val responseCode =
                billingClient!!.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
            responseCode.responseCode == BillingClient.BillingResponseCode.OK
        } else {
            premiumLog("billing client null while check subscription support ")
            premiumEventListener?.onBillingError(PremiumErrors.BILLING_UNAVAILABLE)

            false
        }
    }

    fun unsubscribe(activity: Activity, SubId: String) {
        try {
            val subscriptionUrl =
                "http://play.google.com/store/account/subscriptions?package=" + activity.packageName + "&sku=" + SubId
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse(subscriptionUrl)
            activity.startActivity(intent)
            activity.finish()
        } catch (e: Exception) {
            premiumLog("Handling subscription cancellation: error while trying to unsubscribe")
            e.printStackTrace()
        }
    }

    //////////////////////////////////////////////////// In-App /////////////////////////////////////////////////////////////

    fun buyInApp(activity: Activity, productId: String, isPersonalizedOffer: Boolean = false) {
        val client = billingClient ?: run {
            premiumLog("Error: Billing client is null.")
            premiumEventListener?.onBillingError(PremiumErrors.SERVICE_DISCONNECTED)
            return
        }

        val productInfo = getProductDetail(productId, "", BillingClient.ProductType.INAPP)
        if (productInfo != null) {
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productInfo)
                    .build()
            )
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .setIsOfferPersonalized(isPersonalizedOffer)
                .build()

            client.launchBillingFlow(activity, billingFlowParams)
            premiumLog("Initiating purchase for IN-APP product: $productId")
        } else {
            premiumLog("Error: IN-APP product details missing for product ID: $productId")
            premiumEventListener?.onBillingError(PremiumErrors.PRODUCT_NOT_EXIST)
        }
    }


    private fun fetchAvailableAllInAppProducts(productListKeys: MutableList<String>, inAppDeferred: CompletableDeferred<Unit>) {
        // Early return if billing client is null
        val client = billingClient ?: run {
            premiumLog("Billing client null while fetching All In-App Products")
            premiumEventListener?.onBillingError(PremiumErrors.SERVICE_DISCONNECTED)
            inAppDeferred.complete(Unit)
            return
        }

        val productList = productListKeys.map {
            premiumLog("In-App key: $it")
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList.forEach { productDetails ->
                    premiumLog("In-app product details: $productDetails")
                    allProducts.add(productDetails)
                }
            } else {
                premiumLog("Failed to retrieve In-APP prices: ${billingResult.debugMessage}")
            }
            inAppDeferred.complete(Unit)
        }
    }

    fun isInAppPremiumUser(): Boolean {
        return purchasedInAppProductList.isNotEmpty()
    }

    fun isInAppPremiumUserByInAppKey(inAppKey: String): Boolean {
        return purchasedInAppProductList.any { purchase ->
            purchase.products.any { it == inAppKey }
        }
    }

    fun setInAppKeys(keysList: MutableList<String>): PremiumHelper {
        inAppKeys.addAll(keysList)
        return this
    }

    fun setConsumableKeys(keysList: MutableList<String>): PremiumHelper {
        consumeAbleKeys.addAll(keysList)
        return this
    }

    ///////////////////////////////////////////////// Common ////////////////////////////////////////////////////////////

    fun getAllProductPrices(): MutableList<PriceInfo> {
        val priceList = mutableListOf<PriceInfo>()

        // Place try catch because billing internal class throw null pointer some time on ProductType
        try {
            allProducts.forEach {

                if (it.productType == BillingClient.ProductType.INAPP) {
                    val productPrice = PriceInfo()
                    productPrice.title = it.title
                    productPrice.type = it.productType
                    productPrice.subsKey = it.productId
                    productPrice.productBasePlanKey = ""
                    productPrice.productOfferKey = ""
                    productPrice.price = it.oneTimePurchaseOfferDetails?.formattedPrice.toString()
                    productPrice.duration = "lifeTime"
                    priceList.add(productPrice)
                } else {
                    it.subscriptionOfferDetails?.forEach { subIt ->
                        val productPrice = PriceInfo()
                        productPrice.title = it.title
                        productPrice.type = it.productType
                        productPrice.subsKey = it.productId
                        productPrice.productBasePlanKey = subIt.basePlanId
                        productPrice.productOfferKey = subIt.offerId.toString()
                        productPrice.price =
                            subIt.pricingPhases.pricingPhaseList.first().formattedPrice
                        productPrice.duration =
                            subIt.pricingPhases.pricingPhaseList.first().billingPeriod
                        priceList.add(productPrice)
                    }

                }
            }
        } catch (e: java.lang.Exception) {
            return mutableListOf()
        } catch (e: Exception) {
            return mutableListOf()
        }

        return priceList
    }

    fun getProductPriceByKey(basePlanKey: String, offerKey: String): PriceInfo? {
        // Place try catch because billing internal class throw null pointer some time on ProductType
        try {
            allProducts.forEach {
                if (it.productType == BillingClient.ProductType.SUBS) {
                    it.subscriptionOfferDetails?.forEach { subIt ->
                        if (offerKey.trim().isNotEmpty()) {
                            if (subIt.basePlanId == basePlanKey && subIt.offerId == offerKey) {
                                val productPrice = PriceInfo()
                                productPrice.title = it.title
                                productPrice.type = it.productType
                                productPrice.subsKey = it.productId
                                productPrice.productBasePlanKey = subIt.basePlanId
                                productPrice.productOfferKey = subIt.offerId.toString()
                                productPrice.price = subIt.pricingPhases.pricingPhaseList.first().formattedPrice
                                productPrice.duration = subIt.pricingPhases.pricingPhaseList.first().billingPeriod
                                return productPrice
                            }
                        } else {
                            if (subIt.basePlanId == basePlanKey && subIt.offerId == null) {
                                val productPrice = PriceInfo()
                                productPrice.title = it.title
                                productPrice.type = it.productType
                                productPrice.subsKey = it.productId
                                productPrice.productBasePlanKey = subIt.basePlanId
                                productPrice.productOfferKey = subIt.offerId.toString()
                                productPrice.price = subIt.pricingPhases.pricingPhaseList.first().formattedPrice
                                productPrice.duration = subIt.pricingPhases.pricingPhaseList.first().billingPeriod
                                return productPrice
                            }
                        }
                    }
                }

            }
        } catch (e: java.lang.Exception) {
            ///leave blank because below code auto handle this
        } catch (e: Exception) {
            ///leave blank because below code auto handle this
        }
        premiumLog("SUBS Product Price not found because product is missing")
        premiumEventListener?.onBillingError(PremiumErrors.PRODUCT_NOT_EXIST)
        return null
    }

    fun getProductPriceByKey(productKey: String): PriceInfo? {
        // Place try catch because billing internal class throw null pointer some time on ProductType
        try {
            allProducts.forEach {
                if (it.productType == BillingClient.ProductType.INAPP) {
                    if (it.productId == productKey) {
                        val productPrice = PriceInfo()
                        productPrice.title = it.title
                        productPrice.type = it.productType
                        productPrice.subsKey = it.productId
                        productPrice.productBasePlanKey = ""
                        productPrice.productOfferKey = ""
                        productPrice.price =
                            it.oneTimePurchaseOfferDetails?.formattedPrice.toString()
                        productPrice.duration = "lifeTime"
                        return productPrice
                    }
                }

            }
        } catch (e: java.lang.Exception) {
            ///leave blank because below code auto handle this
        } catch (e: Exception) {
            ///leave blank because below code auto handle this
        }
        premiumLog("IN-APP Product Price not found because product is missing")
        premiumEventListener?.onBillingError(PremiumErrors.PRODUCT_NOT_EXIST)
        return null
    }

    private fun handlePurchase(purchase: Purchase) {
        // Ensure billingClient is not null
        val billingClient = billingClient ?: run {
            premiumLog("Billing client is null while handling purchases")
            premiumEventListener?.onBillingError(PremiumErrors.SERVICE_DISCONNECTED)
            return
        }

        // Get the product type of the purchase
        val productType = getProductType(purchase.products.first())

        // Handle non-purchased states
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            premiumLog("No item purchased: ${purchase.packageName}")
            if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                premiumLog("Purchase is pending, cannot acknowledge until purchased")
                premiumEventListener?.onBillingError(PremiumErrors.ACKNOWLEDGE_WARNING)
            }
            return
        }

        // Handle purchase acknowledgment
        if (!purchase.isAcknowledged) {
            acknowledgePurchase(billingClient, purchase, productType)
        } else {
            premiumLog("Item already acknowledged")
            purchasedSubsProductList.add(purchase)
            premiumClientListener?.onPurchasesUpdated()
        }

        // Handle consumable purchases
        if (consumeAbleKeys.contains(purchase.products.first())) {
            consumePurchase(billingClient, purchase)
        } else {
            premiumLog("This purchase is not consumable")
        }
    }

    // Helper function to acknowledge a purchase
    private fun acknowledgePurchase(billingClient: BillingClient, purchase: Purchase, productType: String) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) {
            if (it.responseCode == BillingClient.BillingResponseCode.OK) {
                premiumLog("$productType item acknowledged")
                // Add purchase to the appropriate list
                if (productType.trim().isNotEmpty()) {
                    if (productType == BillingClient.ProductType.INAPP) {
                        purchasedInAppProductList.add(purchase)
                    } else {
                        purchasedSubsProductList.add(purchase)
                    }
                    premiumClientListener?.onPurchasesUpdated()
                } else {
                    premiumLog("Product type not found while handling purchase")
                }
                premiumEventListener?.onPurchaseAcknowledged(purchase)
            } else {
                premiumLog("Acknowledge error: ${it.debugMessage} (code: ${it.responseCode})")
                premiumEventListener?.onBillingError(PremiumErrors.ACKNOWLEDGE_ERROR)
            }
        }
    }

    // Helper function to consume a purchase
    private fun consumePurchase(billingClient: BillingClient, purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient.consumeAsync(consumeParams) { result, _ ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                premiumLog("Purchase consumed")
                premiumEventListener?.onPurchaseConsumed(purchase)
            } else {
                premiumLog("Failed to consume purchase: ${result.debugMessage} (code: ${result.responseCode})")
                premiumEventListener?.onBillingError(PremiumErrors.CONSUME_ERROR)
            }
        }
    }

    fun fetchActivePurchases(purchasesDeferred: CompletableDeferred<Unit> = CompletableDeferred()) {
        fetchAndUpdateActivePurchases(purchasesDeferred)
//        fetchActiveInAppPurchasesHistory()
    }

    private fun fetchAndUpdateActivePurchases(purchasesDeferred: CompletableDeferred<Unit>) {
        val billingClient = billingClient
        if (billingClient == null) {
            premiumLog("Billing client is null while fetching active purchases")
            premiumEventListener?.onBillingError(PremiumErrors.SERVICE_DISCONNECTED)
            purchasesDeferred.complete(Unit)
            return
        }

        val scope = CoroutineScope(Dispatchers.IO)

        fun handleBillingResult(billingResult: BillingResult, purchases: List<Purchase>, productType: String, purchasesDeferred: CompletableDeferred<Unit>) {
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val activePurchases = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                premiumLog("$productType purchases found: ${activePurchases.size}")

                if (activePurchases.isEmpty()) {
                    premiumClientListener?.onPurchasesUpdated()
                    purchasesDeferred.complete(Unit)
                    return
                }

                scope.launch {
                    activePurchases.forEach { purchase ->
                        premiumLog("$productType purchase: ${purchase.products.first()}")
                        handlePurchase(purchase)
                    }
                    purchasesDeferred.complete(Unit)
                }
            } else {
                premiumLog("No $productType purchases found")
            }
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { billingResult, purchases ->
            handleBillingResult(billingResult, purchases, "SUBS", purchasesDeferred)
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { billingResult, purchases ->
            handleBillingResult(billingResult, purchases, "IN-APP", purchasesDeferred)
        }
    }

    fun getProductDetail(productKey: String, offerKey: String = "", productType: String): ProductDetails? {

        val offerKeyNormalized = offerKey.trim().takeIf { it.isNotEmpty() } ?: "null"

        if (allProducts.isEmpty()) {
            premiumEventListener?.onBillingError(PremiumErrors.PRODUCT_NOT_EXIST)
            return null
        }

        val product = allProducts.find { product ->
            when (productType) {
                BillingClient.ProductType.INAPP -> {
                    if (product.productId == productKey) {
                        premiumLog("In App product detail: title: ${product.title} price: ${product.oneTimePurchaseOfferDetails?.formattedPrice}")
                        true
                    } else {
                        false
                    }
                }

                BillingClient.ProductType.SUBS -> {
                    product.subscriptionOfferDetails?.any { subDetails ->
                        val isMatchingBasePlan = subDetails.basePlanId.equals(productKey, true)
                        val isMatchingOfferId = subDetails.offerId.toString().equals(offerKeyNormalized, true)
                        if (isMatchingBasePlan && isMatchingOfferId) {
                            premiumLog("Subscription product detail: basePlanId: ${subDetails.basePlanId} offerId: ${subDetails.offerId}")
                        }
                        isMatchingBasePlan && isMatchingOfferId
                    } ?: false
                }

                else -> false
            }
        }

        if (product == null) {
            premiumEventListener?.onBillingError(PremiumErrors.PRODUCT_NOT_EXIST)
        }

        return product
    }

    private fun getProductType(productKey: String): String {
        allProducts.forEach { productDetail ->
            if (productDetail.productType == BillingClient.ProductType.INAPP) {
                if (productDetail.productId == productKey) {
                    return productDetail.productType
                }
            } else {
                productDetail.subscriptionOfferDetails?.forEach {
                    if (it.basePlanId == productKey) {
                        return productDetail.productType
                    }
                }
            }
        }
        return ""
    }

    fun isClientReady(): Boolean {
        return isClientReady
    }

    fun enableLogging(isEnableLog: Boolean = true): PremiumHelper {
        enableLog = isEnableLog
        return this
    }

    private fun premiumLog(message: String) {
        if (enableLog) {
            Log.d(TAG, message)
        }
    }

    fun release() {
        if (billingClient != null && billingClient!!.isReady) {
            premiumLog("BillingHelper instance release: ending connection...")
            billingClient?.endConnection()
        }
    }

    fun setBillingEventListener(billingEventListeners: PremiumEventListeners?): PremiumHelper {
        premiumEventListener = billingEventListeners
        return this
    }

    fun setBillingClientListener(billingClientListeners: PremiumClientListeners?): PremiumHelper {
        premiumClientListener = billingClientListeners
        return this
    }
}