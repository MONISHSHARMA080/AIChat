package com.agentic.aichat.data.billing

import android.app.Activity
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.GetVirtualCurrenciesCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.virtualcurrencies.VirtualCurrencies
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Singleton
class BillingRepository @Inject constructor() {
	suspend fun getBalance(): Result<Int> {
		Purchases.sharedInstance.invalidateVirtualCurrenciesCache()
		return suspendCancellableCoroutine { continuation ->
			Purchases.sharedInstance.getVirtualCurrencies(object : GetVirtualCurrenciesCallback {
				override fun onReceived(virtualCurrencies: VirtualCurrencies) {
					if (continuation.isActive) {
						val coinBalance = virtualCurrencies.all["COIN"]?.balance
							?: virtualCurrencies.all.values.firstOrNull()?.balance
							?: 0
						continuation.resume(Result.success(coinBalance))
					}
				}

				override fun onError(error: PurchasesError) {
					if (continuation.isActive) continuation.resume(Result.failure(Exception(error.message)))
				}
			})
		}
	}

	suspend fun getCurrentOffering(): Result<Offering?> = suspendCancellableCoroutine { continuation ->
		Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
			override fun onReceived(offerings: Offerings) {
				if (continuation.isActive) continuation.resume(Result.success(offerings.current))
			}

			override fun onError(error: PurchasesError) {
				if (continuation.isActive) continuation.resume(Result.failure(Exception(error.message)))
			}
		})
	}

	suspend fun purchase(
		activity: Activity,
		pkg: Package,
	): Result<CustomerInfo> = suspendCancellableCoroutine { continuation ->
		val params = PurchaseParams.Builder(activity, pkg).build()
		Purchases.sharedInstance.purchase(params, object : PurchaseCallback {
			override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
				if (continuation.isActive) continuation.resume(Result.success(customerInfo))
			}

			override fun onError(error: PurchasesError, userCancelled: Boolean) {
				if (continuation.isActive) continuation.resume(Result.failure(Exception(error.message)))
			}
		})
	}

	suspend fun restore(): Result<CustomerInfo> = suspendCancellableCoroutine { continuation ->
		Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
			override fun onReceived(customerInfo: CustomerInfo) {
				if (continuation.isActive) continuation.resume(Result.success(customerInfo))
			}

			override fun onError(error: PurchasesError) {
				if (continuation.isActive) continuation.resume(Result.failure(Exception(error.message)))
			}
		})
	}
}
