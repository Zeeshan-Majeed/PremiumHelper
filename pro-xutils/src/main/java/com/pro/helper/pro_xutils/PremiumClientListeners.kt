package com.pro.helper.pro_xutils

interface PremiumClientListeners {
    fun onPurchasesUpdated()
    fun onClientReady()
    fun onClientAllReadyConnected(){}
    fun onClientInitError()
}