package com.murmur.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*

object BillingHelper {

    private const val PRODUCT_ID = "pro_upgrade1"   // matches Play Console
    private lateinit var billingClient: BillingClient

    // Call this once on app start (e.g., in MainActivity.onCreate)
    fun init(context: Context) {
        billingClient = BillingClient.newBuilder(context)
            .enablePendingPurchases()
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    handlePurchases(context, purchases)
                }
            }
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Restore existing entitlement (user re-installs, new device, etc.)
                    queryAndRestore(context)
                }
            }
            override fun onBillingServiceDisconnected() { /* Play will try to reconnect */ }
        })
    }

    private fun queryAndRestore(context: Context) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { _, purchaseList ->
            val ownsPro = purchaseList.any { it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            Upgrade.setPro(context, ownsPro)
        }
    }

    private fun handlePurchases(context: Context, purchases: List<Purchase>) {
        purchases.forEach { p ->
            if (p.products.contains(PRODUCT_ID) && p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                // Acknowledge if needed (required for one‑time products)
                if (!p.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(p.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(ackParams) { ackResult ->
                        if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Upgrade.setPro(context, true)
                        }
                    }
                } else {
                    Upgrade.setPro(context, true)
                }
            }
        }
    }

    // We’ll wire this in the next step (launch the purchase flow)
    fun buyPro(activity: Activity, onError: (String) -> Unit) {
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || detailsList.isEmpty()) {
                onError("Product not available.")
                return@queryProductDetailsAsync
            }
            val pd = detailsList.first()
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(pd)
                            .build()
                    )
                )
                .build()
            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    fun queryProductDetails(productIds: List<String>) {
        // TODO: real implementation in the next step
    }}
