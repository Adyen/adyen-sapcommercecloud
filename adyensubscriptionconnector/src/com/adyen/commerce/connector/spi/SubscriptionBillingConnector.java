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

import java.util.List;

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
import com.adyen.commerce.connector.exception.CapabilityUnsupportedException;

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
	 * enforce that it equals {@code BaseStore.adyenMerchantAccount}.
	 *
	 * <p><b>External connectors must return their real gateway merchant account.</b> A blank answer is
	 * treated as "not configured" and rejected: only {@code ADYEN_NATIVE} is exempt, because it is
	 * the one path with no external gateway to bind. This deliberately does not let an incompletely
	 * configured gateway switch the check off by returning nothing.</p>
	 *
	 * @return the configured Adyen merchant account; {@code null} only for {@code ADYEN_NATIVE}, where
	 *         there is genuinely nothing to bind
	 */
	String configuredAdyenMerchantAccount();

	// --- Customer lifecycle ---

	/**
	 * Create-or-find the customer on the platform. Idempotent: repeated calls for the same customer must
	 * return the same reference rather than create duplicates.
	 *
	 * @param request the normalized customer data ({@code customerId} == Adyen {@code shopperReference})
	 * @return the external customer reference
	 * @throws BillingException if the platform call fails (retryable or terminal)
	 */
	BillingCustomerRef ensureCustomer(CustomerSyncRequest request) throws BillingException;

	// --- Payment method: import the Adyen token ---

	/**
	 * Import the Adyen-vaulted token as a stored payment method on the platform. The platform must be
	 * connected to the same Adyen merchant account the token was minted under.
	 *
	 * @param request the customer reference plus the {@code AdyenTokenHandle} and processing model
	 * @return the external payment-method reference
	 * @throws BillingException if the import or token validation fails
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

	/**
	 * Create a subscription on the platform. Idempotent on {@code request.idempotencyKey()}.
	 *
	 * @param request the customer/payment-method/plan references plus cycle, start date and metadata
	 * @return the external subscription reference
	 * @throws BillingException if creation fails
	 */
	BillingSubscriptionRef createSubscription(SubscriptionCreateRequest request) throws BillingException;

	/**
	 * Update an existing subscription (plan, quantity, price). Null request fields are left unchanged.
	 *
	 * @throws BillingException if the update fails
	 */
	void updateSubscription(SubscriptionUpdateRequest request) throws BillingException;

	/**
	 * Cancel a subscription, immediately or at the end of the current period per the request.
	 *
	 * @throws BillingException if cancellation fails
	 */
	void cancelSubscription(SubscriptionCancelRequest request) throws BillingException;

	/**
	 * Pause a subscription. Capability-gated: only meaningful when {@code capabilities().supportsPause()}.
	 * The default implementation rejects pause with {@link CapabilityUnsupportedException}; connectors on
	 * platforms that support pausing must override this <em>and</em> advertise {@code supportsPause() == true}.
	 *
	 * @throws CapabilityUnsupportedException if the platform does not support pausing (the default behavior)
	 * @throws BillingException               if the platform call fails
	 */
	default void pauseSubscription(final SubscriptionPauseRequest request) throws BillingException
	{
		throw new CapabilityUnsupportedException("Connector " + platform() + " does not support pausing subscriptions");
	}

	// --- Inbound sync ---

	/**
	 * Verify the webhook signature (connector-owned) and normalize it into a vendor-neutral event.
	 */
	NormalizedBillingEvent parseWebhook(RawWebhook raw) throws BillingException;

	/**
	 * Resolve which subscriptions an event applies to, for platforms whose webhooks do not name one.
	 * Called <em>only</em> when {@link NormalizedBillingEvent#externalSubscriptionId()} is absent — an
	 * invoice- or payment-shaped event, say — so a connector whose events always carry their
	 * subscription id needs nothing here. Returning more than one id is legitimate: one invoice can
	 * cover several subscriptions, and the event then applies to each.
	 *
	 * <p>The dispatcher calls this <em>after</em> claiming the event id for deduplication, so a
	 * redelivery cannot repeat whatever remote lookup this performs.
	 *
	 * @return the external subscription ids this event applies to; empty if none could be resolved
	 */
	default List<String> resolveSubscriptionIds(final NormalizedBillingEvent event) throws BillingException
	{
		return List.of();
	}
}
