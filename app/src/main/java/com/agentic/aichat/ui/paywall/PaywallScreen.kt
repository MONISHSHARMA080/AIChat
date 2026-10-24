package com.agentic.aichat.ui.paywall

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Paywall / Upgrade screen (pushed over chat or settings).
 *
 * Blank on purpose. Planned content:
 * - RevenueCat offering (packages + prices) from [com.agentic.aichat.data.billing.BillingRepository].
 * - Purchase + restore purchase actions against the "pro" entitlement.
 * - On success: notify the backend (quota state is decided server-side per request)
 *   and close via [onClose].
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: implement paywall UI (PaywallViewModel is currently empty).
}
