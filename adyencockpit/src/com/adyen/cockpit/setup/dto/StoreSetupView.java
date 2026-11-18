package com.adyen.cockpit.setup.dto;

import java.util.List;

/**
 * Where one base store stands. Presence flags only: no credential leaves the server, not even masked.
 */
public record StoreSetupView(
		String uid,
		String name,
		boolean testMode,
		String merchantAccount,
		boolean hasManagementKey,
		boolean hasCheckoutKey,
		boolean hasClientKey,
		boolean hasWebhookCredentials,
		boolean hasHmacKey,
		List<SiteView> sites)
{
}
