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
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.dto.PaymentMethodChangeOutcome;
import com.adyen.commerce.connector.dto.PaymentMethodChangeRequest;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
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
 * Port (SPI) of the agnostic subscription billing connector, one implementation per billing platform
 * (Recurly, Chargebee, Zuora, ...), each living in its own extension and depending on this core rather
 * than the other way around.
 *
 * <p>No vendor type may appear in a signature; mutating calls are idempotent on a caller-supplied key;
 * every failure surfaces as a {@link BillingException} subtype, transient ones as
 * {@link com.adyen.commerce.connector.exception.RetryableBillingException}. Implementations receive an
 * {@link com.adyen.commerce.connector.dto.AdyenTokenHandle}, so a PAN never crosses this boundary.</p>
 */
public interface SubscriptionBillingConnector
{
	/**
	 * @return the platform this connector adapts, as the registry resolves it
	 */
	BillingPlatform platform();

	/**
	 * @return the capabilities the core branches on instead of hard-coding per-platform logic
	 */
	ConnectorCapabilities capabilities();

	/**
	 * The Adyen merchant account this connector's gateway is configured against; the core enforces that it
	 * equals {@code BaseStore.adyenMerchantAccount}. A blank answer counts as "not configured" and is
	 * rejected, so an incompletely configured gateway cannot switch the check off.
	 *
	 * @return the configured Adyen merchant account; {@code null} only for {@code ADYEN_NATIVE}, the one
	 *         path with no external gateway to bind
	 */
	String configuredAdyenMerchantAccount();

	// --- Customer lifecycle ---

	/**
	 * Create-or-find the customer on the platform; repeated calls for the same customer must return the
	 * same reference rather than create duplicates.
	 *
	 * @param request the normalized customer data ({@code customerId} == Adyen {@code shopperReference})
	 * @return the external customer reference
	 */
	BillingCustomerRef ensureCustomer(CustomerSyncRequest request) throws BillingException;

	// --- Payment method: import the Adyen token ---

	/**
	 * Import the Adyen-vaulted token as a stored payment method on the platform. The platform must be
	 * connected to the same Adyen merchant account the token was minted under.
	 *
	 * @param request the customer reference plus the {@code AdyenTokenHandle} and processing model
	 * @return the external payment-method reference
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
	 */
	BillingSubscriptionRef createSubscription(SubscriptionCreateRequest request) throws BillingException;

	/**
	 * Fetch the platform's current authoritative subscription state.
	 *
	 * @param subscription opaque platform subscription reference
	 * @return a normalized live snapshot
	 */
	NormalizedSubscription fetchSubscription(BillingSubscriptionRef subscription) throws BillingException;

	/**
	 * Update an existing subscription (plan, quantity, price). Null request fields are left unchanged.
	 */
	void updateSubscription(SubscriptionUpdateRequest request) throws BillingException;

	/**
	 * Cancel a subscription, immediately or at the end of the current period per the request.
	 */
	void cancelSubscription(SubscriptionCancelRequest request) throws BillingException;

	/**
	 * The payment methods this platform already holds for the customer, as it describes them.
	 *
	 * <p>The empty default is not a refusal: this runs while the page is being rendered, so a connector that
	 * cannot enumerate its methods simply offers no {@code ALREADY_ON_PLATFORM} choice. Only called for a
	 * connector whose capability names that source.</p>
	 */
	default List<PlatformPaymentMethod> listPaymentMethods(final BillingCustomerRef customer)
			throws BillingException
	{
		return List.of();
	}

	/**
	 * Point this subscription's future billing at the payment method the shopper chose. Capability-gated:
	 * meaningful only when {@code capabilities().paymentMethodChange().isSupported()}, and only for a source
	 * that capability names.
	 *
	 * <p>It changes which instrument the next billing event uses; it never charges, refunds, prorates or
	 * retries an outstanding invoice. Separate from {@link #importAdyenToken} because an import is not a
	 * replacement on every platform: Chargebee's replaces the primary payment source, while Recurly's adds a
	 * non-primary billing info or refuses to replace an existing one.</p>
	 *
	 * <p>A connector that overrides this must advertise a scope other than {@code NOT_SUPPORTED}, and the
	 * scope it returns in the outcome must be the scope it declared.</p>
	 *
	 * @return the payment-method reference now in force and the scope the change actually had, which the
	 *         caller uses to decide what the shopper is told
	 * @throws CapabilityUnsupportedException if this platform cannot do it (the default behaviour)
	 */
	default PaymentMethodChangeOutcome changePaymentMethod(final PaymentMethodChangeRequest request)
			throws BillingException
	{
		throw new CapabilityUnsupportedException("Connector " + platform()
				+ " does not support changing the payment method of an existing subscription");
	}

	/**
	 * Pause a subscription. A connector that overrides this must also advertise
	 * {@code capabilities().supportsPause() == true}.
	 *
	 * @throws CapabilityUnsupportedException if the platform does not support pausing (the default behaviour)
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
	 * Resolve which subscriptions an event applies to, for platforms whose webhooks do not name one. Called
	 * only when {@link NormalizedBillingEvent#externalSubscriptionId()} is absent, so a connector whose
	 * events always carry their subscription id needs nothing here. More than one id is legitimate: one
	 * invoice can cover several subscriptions.
	 *
	 * <p>The dispatcher calls this after claiming the event id for deduplication, so a redelivery cannot
	 * repeat whatever remote lookup this performs.</p>
	 *
	 * @return the external subscription ids this event applies to; empty if none could be resolved
	 */
	default List<String> resolveSubscriptionIds(final NormalizedBillingEvent event) throws BillingException
	{
		return List.of();
	}
}
