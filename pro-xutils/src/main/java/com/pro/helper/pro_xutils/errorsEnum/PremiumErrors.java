package com.pro.helper.pro_xutils.errorsEnum;

import androidx.annotation.Keep;

@Keep
public enum PremiumErrors {
    DEVELOPER_ERROR,

    CLIENT_NOT_READY,

    CLIENT_DISCONNECTED,
    PRODUCT_NOT_EXIST,
    OFFER_NOT_EXIST,
    BILLING_ERROR,
    USER_CANCELED,
    SERVICE_UNAVAILABLE,
    BILLING_UNAVAILABLE,
    ITEM_UNAVAILABLE,
    ERROR,
    ITEM_ALREADY_OWNED,
    ITEM_NOT_OWNED,
    SERVICE_DISCONNECTED,
    ACKNOWLEDGE_ERROR,
    ACKNOWLEDGE_WARNING,
    OLD_PURCHASE_TOKEN_NOT_FOUND, INVALID_PRODUCT_TYPE_SET,
    CONSUME_ERROR
}
