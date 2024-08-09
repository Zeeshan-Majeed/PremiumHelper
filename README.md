# Premium Helper

Premium Helper is a simple, straight-forward implementation of the Android 6.0 In-app billing API
> Support both IN-App and Subscriptions.
> ### **Billing v7 subscription model:**
>
> 
## Getting Started

#### Dependencies

No extra dependencies required

## Step 1

Add maven repository in project level build.gradle or in latest project setting.gradle file

```kotlin 

    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
 
```  

## Step 2

Add Premium Helper dependencies in App level build.gradle.

 

## Step 3 (Setup)

Finally initialise Billing class and setup Subscription Ids

```kotlin 

    PremiumHelper(this)
    .setSubKeys(mutableListOf("Subs Key", "Subs Key 2"))
    .initialize()
 
```
if both subscription and In-App

```kotlin 

    PremiumHelper(this)
    .setSubKeys(mutableListOf("Subs Key", "Subs Key 2"))
    .setInAppKeys(mutableListOf("In-App Key"))
    .initialize() 
  
```
if consumable in-App
```kotlin 

    PremiumHelper(this)
    .setInAppKeys(mutableListOf("In-App Key, In-App consumable Key")) 
	.setConsumableKeys(mutableListOf("In-App consumable Key"))
    .initialize() 
 
```
**Note: you have add consumable key in both func ```setInAppKeys()``` and ```setConsumableKeys()```**

Call this in first stable activity or in App class

### Billing Client Listeners

```kotlin

    PremiumHelper(this)
    .setSubKeys(mutableListOf("Subs Key", "Subs Key 2"))
    .setInAppKeys(mutableListOf("In-App Key"))
    .enableLogging().setBillingClientListener(object : PremiumClientListener {
      override fun onPurchasesUpdated() {
        Log.i("premium", "onPurchasesUpdated: called when user latest premium status fetched ")
      }

      override fun onClientReady() {
        Log.i("premium", "onClientReady: Called when client ready after fetch products details and active product against user")
      }

      override fun onClientInitError() {
        Log.i("premium", "onClientInitError: Called when client fail to init")
      }

        })
    .initialize()


```

### Enable Logs

##### Only for debug

```kotlin

    PremiumHelper(this)
    .setSubKeys(mutableListOf("Subs Key", "Subs Key 2"))
    .setInAppKeys(mutableListOf("In-App Key"))
    .enableLogging(isEnableLog = true)
    .initialize()


```

### Buy In-App Product

Subscribe to a Subscription
```kotlin
    PremiumHelper(this).buyInApp(activity,"In-App Key",false)
```
```fasle```  value used for **isPersonalizedOffer** attribute:

If your app can be distributed to users in the European Union, use the **isPersonalizedOffer** value ```true``` to disclose to users that an item's price was personalized using automated decision-making.

**Note: it auto acknowledge the In-App and give callback when product acknowledged successfully.**
### Subscribe to a Subscription

Subscribe to a Subscription
```kotlin
    PremiumHelper(this).subscribe(activity, "Base Plan ID")
```
Subscribe to a offer
```kotlin
    PremiumHelper(this).subscribe(activity, "Base Plan ID","Offer ID")
```

**Note: it auto acknowledge the subscription and give callback when product acknowledged successfully.**

### Upgrade or Downgrade Subscription

 ```kotlin
    PremiumHelper(this).upgradeOrDowngradeSubscription(this, "New Base Plan ID", "New Offer Id (If offer )", "Old Base Plan ID", ProrationMode)

```
```ProrationMode``` is a setting in subscription billing systems that determines how proration is calculated when changes are made to a subscription plan. There are different proration modes, including:

```

  1. DEFERRED

    Replacement takes effect when the old plan expires, and the new price will be charged at the same time.

  2. IMMEDIATE_AND_CHARGE_FULL_PRICE

    Replacement takes effect immediately, and the user is charged full price of new plan and is given a full billing cycle of subscription, plus remaining prorated time from the old plan.

  3. IMMEDIATE_AND_CHARGE_PRORATED_PRICE

    Replacement takes effect immediately, and the billing cycle remains the same.

  4. IMMEDIATE_WITHOUT_PRORATION

    Replacement takes effect immediately, and the new price will be charged on next recurrence time.

  5. IMMEDIATE_WITH_TIME_PRORATION

    Replacement takes effect immediately, and the remaining time will be prorated and credited to the user.

  6. UNKNOWN_SUBSCRIPTION_UPGRADE_DOWNGRADE_POLICY
 

```
Example :

```kotlin
  PremiumHelper(this).upgradeOrDowngradeSubscription(this, "New Base Plan ID", "New Offer Id (If offer )", "Old Base Plan ID", BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_FULL_PRICE)
```

### Billing Listeners

Interface implementation to handle purchase results and errors.
 ```kotlin

      PremiumHelper(this).setBillingEventListener(object : PremiumEventListener {
            override fun onProductsPurchased(purchases: List<Purchase?>) {
			//call back when purchase occured 
            }

            override fun onPurchaseAcknowledged(purchase: Purchase) {
			 //call back when purchase occur and acknowledged 
            }
			
			 override fun onPurchaseConsumed(purchase: Purchase) {
			 //call back when purchase occur and consumed 
            }

            override fun onBillingError(error: PremiumErrors) {
                when (error) {
                    PremiumErrors.CLIENT_NOT_READY -> {

                    }
                    PremiumErrors.CLIENT_DISCONNECTED -> {

                    }
                    PremiumErrors.PRODUCT_NOT_EXIST -> {

                    }
                    PremiumErrors.BILLING_ERROR -> {

                    }
                    PremiumErrors.USER_CANCELED -> {

                    }
                    PremiumErrors.SERVICE_UNAVAILABLE -> {

                    }
                    PremiumErrors.BILLING_UNAVAILABLE -> {

                    }
                    PremiumErrors.ITEM_UNAVAILABLE -> {

                    }
                    PremiumErrors.DEVELOPER_ERROR -> {

                    }
                    PremiumErrors.ERROR -> {

                    }
                    PremiumErrors.ITEM_ALREADY_OWNED -> {

                    }
                    PremiumErrors.ITEM_NOT_OWNED -> {

                    }

                    PremiumErrors.SERVICE_DISCONNECTED -> {

                    }

                    PremiumErrors.ACKNOWLEDGE_ERROR -> {

                    }

                    PremiumErrors.ACKNOWLEDGE_WARNING -> {

                    }
                    
                    PremiumErrors.OLD_PURCHASE_TOKEN_NOT_FOUND -> {

                    }
					
                    PremiumErrors.CONSUME_ERROR -> {

                    }
                    else -> {

                    }
                }
            }
        })
 
```

## Step 4 (Product's Detail)

### Get Product price

Get all products prices list include both In-App and Subs

```kotlin
    PremiumHelper(this).getAllProductPrices()
```
Get In-App Product price


```kotlin
    PremiumHelper(this).getProductPriceByKey("In-App Key").price
```

Get specific subscription price (without offer)


```kotlin
    PremiumHelper(this).getProductPriceByKey("Base Plan ID","").price
```

Get specific subscription price (with offer)


```kotlin
    PremiumHelper(this).getProductPriceByKey("Base Plan ID","Offer ID").price
```

This method return ```ProductPriceInfo``` object that contain complete detail   about subscription. To get only price just call ```.Price```.

### Get Single Product Detail

For In-App Product

```kotlin
    PremiumHelper(this).getProductDetail("In-App Key","",BillingClient.ProductType.INAPP)
```
For Subs Product

```kotlin
    PremiumHelper(this).getProductDetail("Base Plan ID","Offer ID",BillingClient.ProductType.SUBS)
```

Above methods return ```ProductPriceInfo``` object that contain complete detail about Product.

## Step 5 (Check if any Product buy)

### Check In-App

For check if user buy any In-App Product

 ```kotlin
  PremiumHelper(this).isInAppPremiumUser()

 ``` 

For check specific In-App Product

``` kotlin
  PremiumHelper(this).isInAppPremiumUserByInAppKey("In-App Key")

 ```

### Check Subscription

For check if any subscription is subscribe

 ```kotlin
  PremiumHelper(this).isSubsPremiumUser()

 ``` 

For check if any specific subscription is subscribe (by Base Plan ID)

``` kotlin
  PremiumHelper(this).isSubsPremiumUserByBasePlanKey("Base Plan ID")

 ``` 
For check if any specific subscription is subscribe (by Subscription ID)

``` kotlin
  PremiumHelper(this).isSubsPremiumUserBySubIDKey("Subscription ID")

 ``` 

## Step 6 (Cancel any subscription)

### Cancel  Subscription
