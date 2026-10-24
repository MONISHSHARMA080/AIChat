package com.agentic.aichat.billing

import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Thin, idempotent wrapper around the RevenueCat SDK. Use [Purchases.sharedInstance]
 * (configured synchronously in [com.agentic.aichat.AiChatApplication]) for anything
 * not exposed here.
 */
object BillingManager {

	/**
	 * Flow of the current Pro entitlement state.
	 *
	 * Starts with the latest CustomerInfo and re-emits whenever the SDK updates it
	 * (launch cache refresh, purchase, restore).
	 *
	 * Note: the SDK exposes a single UpdatedCustomerInfoListener per Purchases
	 * instance; collecting this flow takes it over and clears it on cancellation.
	 */
	fun observeProEntitlement(): Flow<Boolean> = callbackFlow {
		Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
			trySend(customerInfo.hasProEntitlement())
		}
		launch {
			try {
				send(Purchases.sharedInstance.awaitCustomerInfo().hasProEntitlement())
			} catch (_: PurchasesException) {
				// Keep reporting the latest state received through the listener.
			}
		}
		awaitClose {
			Purchases.sharedInstance.updatedCustomerInfoListener = null
		}
	}

	/**
	 * Latest customer info (cached when fresh, fetched otherwise).
	 *
	 * @throws PurchasesException if the request fails.
	 */
	suspend fun getCustomerInfo(): CustomerInfo =
		Purchases.sharedInstance.awaitCustomerInfo()

	/**
	 * Restores purchases made elsewhere (other devices / store account).
	 *
	 * @throws PurchasesException if the restore fails.
	 */
	suspend fun restorePurchases(): CustomerInfo =
		Purchases.sharedInstance.awaitRestore()

	private fun CustomerInfo?.hasProEntitlement(): Boolean {
		return this?.entitlements?.get(RevenueCatConfig.ENTITLEMENT_ID)?.isActive == true
	}
}
