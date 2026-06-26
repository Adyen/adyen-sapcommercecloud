/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.connector.spi;

import com.adyen.commerce.connector.dto.BillingCustomerRef;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.ConnectorCapabilities;
import com.adyen.commerce.connector.dto.CustomerSyncRequest;
import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.dto.SubscriptionCancelRequest;
import com.adyen.commerce.connector.dto.SubscriptionCreateRequest;
import com.adyen.commerce.connector.dto.SubscriptionPauseRequest;
import com.adyen.commerce.connector.dto.SubscriptionUpdateRequest;
import com.adyen.commerce.connector.dto.TokenImportRequest;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;

/**
 * The port (SPI) of the agnostic subscription billing connector. One implementation per billing
 * platform (Recurly, Chargebee, Zuora, ...); the implementations live in their own extensions and
 * depend on this core, never the other way around (hexagonal / ports-and-adapters).
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li><b>Vendor-neutral.</b> No vendor type may appear in any signature; translation to/from the
 *       platform API happens entirely inside the implementation.</li>
 *   <li><b>Idempotent mutations.</b> Mutating calls carry a caller-supplied idempotency key; calling
 *       twice with the same key must not create duplicates.</li>
 *   <li><b>Normalized errors.</b> Every failure is surfaced as a {@link BillingException} subtype.
 *       Transient failures use {@link com.adyen.commerce.connector.exception.RetryableBillingException}.</li>
 *   <li><b>Token-only.</b> Implementations receive an
 *       {@link com.adyen.commerce.connector.dto.AdyenTokenHandle}; a PAN never crosses this boundary.</li>
 * </ul>
 */
public interface SubscriptionBillingConnector
{
	/**
	 * @return the platform this connector adapts. Used by the registry for resolution.
	 */
	BillingPlatform platform();

	/**
	 * @return the capabilities/constraints the core branches on instead of hard-coding per-platform logic.
	 */
	ConnectorCapabilities capabilities();

	/**
	 * The Adyen merchant account this connector's gateway is configured against, used by the core to
	 * enforce that it equals {@code BaseStore.adyenMerchantAccount} (design R2). Return {@code null}
	 * when not applicable (e.g. the Adyen-native connector, which has no external gateway binding).
	 *
	 * @return the configured Adyen merchant account, or {@code null}
	 */
	String configuredAdyenMerchantAccount();

	// --- Customer lifecycle ---

	/**
	 * Create-or-find the customer on the platform.
	 */
	BillingCustomerRef ensureCustomer(CustomerSyncRequest request) throws BillingException;

	// --- Payment method: import the Adyen token ---

	/**
	 * Import the Adyen-vaulted token as a stored payment method on the platform.
	 */
	BillingPaymentMethodRef importAdyenToken(TokenImportRequest request) throws BillingException;

	// --- Plan resolution ---

	/**
	 * Resolve a SAP subscription product code to a platform plan/price reference.
	 *
	 * @throws com.adyen.commerce.connector.exception.PlanNotMappedException if no mapping exists
	 */
	PlanRef resolvePlan(PlanResolutionRequest request) throws BillingException;

	// --- Subscription lifecycle ---

	BillingSubscriptionRef createSubscription(SubscriptionCreateRequest request) throws BillingException;

	void updateSubscription(SubscriptionUpdateRequest request) throws BillingException;

	void cancelSubscription(SubscriptionCancelRequest request) throws BillingException;

	/**
	 * Pause a subscription. Capability-gated: only callable when {@code capabilities().supportsPause()}.
	 *
	 * @throws com.adyen.commerce.connector.exception.CapabilityUnsupportedException if pause is unsupported
	 */
	void pauseSubscription(SubscriptionPauseRequest request) throws BillingException;

	// --- Inbound sync ---

	/**
	 * Verify the webhook signature (connector-owned) and normalize it into a vendor-neutral event.
	 */
	NormalizedBillingEvent parseWebhook(RawWebhook raw) throws BillingException;
}
