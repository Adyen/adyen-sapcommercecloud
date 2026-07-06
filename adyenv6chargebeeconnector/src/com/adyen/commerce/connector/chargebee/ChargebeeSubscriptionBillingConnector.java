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
package com.adyen.commerce.connector.chargebee;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.chargebee.client.ChargebeeApiClient;
import com.adyen.commerce.connector.chargebee.client.ChargebeeSubscriptionParams;
import com.adyen.commerce.connector.chargebee.config.ChargebeeConfigService;
import com.adyen.commerce.connector.chargebee.plan.ChargebeePlanResolver;
import com.adyen.commerce.connector.dto.AdyenTokenHandle;
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
import com.adyen.commerce.connector.dto.SubscriptionUpdateRequest;
import com.adyen.commerce.connector.dto.TokenImportRequest;
import com.adyen.commerce.connector.dto.TokenImportStyle;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.TerminalBillingException;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

/**
 * Chargebee adapter of the {@link SubscriptionBillingConnector} SPI (ADR-001 Option A: Adyen keeps
 * processing recurring payments; Chargebee only orchestrates billing).
 *
 * <p>This first cut covers the outbound lifecycle (customer, token import, plan resolution,
 * subscription create/update/cancel). Pause is not supported ({@code supportsPause=false}, SPI default
 * rejects it) and inbound webhook handling is deferred (see {@link #parseWebhook}, task P2.4).</p>
 */
public class ChargebeeSubscriptionBillingConnector implements SubscriptionBillingConnector
{
	private static final ConnectorCapabilities CAPABILITIES = new ConnectorCapabilities(
			false, // requiresNetworkTransactionId — the Adyen plugin never captures an NTID, and Chargebee import does not need one
			true,  // supportsImmediateStart — subscription_for_items can start immediately
			false, // supportsPause — deferred to a later increment (SPI default rejects pause)
			true,  // requiresPreConfiguredPlan — the item price must already exist in the Chargebee catalog
			true,  // liveTokenValidationOnImport — create_using_permanent_token makes a live retrieval call to Adyen
			TokenImportStyle.SLASH_JOINED); // reference_id = shopperReference/recurringDetailReference

	private ChargebeeApiClient apiClient;
	private ChargebeeConfigService configService;
	private ChargebeePlanResolver planResolver;

	@Override
	public BillingPlatform platform()
	{
		return BillingPlatform.CHARGEBEE;
	}

	@Override
	public ConnectorCapabilities capabilities()
	{
		return CAPABILITIES;
	}

	@Override
	public String configuredAdyenMerchantAccount()
	{
		return configService.getConfiguredAdyenMerchantAccount();
	}

	@Override
	public BillingCustomerRef ensureCustomer(final CustomerSyncRequest request) throws BillingException
	{
		final String customerId = apiClient.ensureCustomer(request.customerId(), request.email(), request.firstName(),
				request.lastName());
		return new BillingCustomerRef(BillingPlatform.CHARGEBEE, customerId);
	}

	@Override
	public BillingPaymentMethodRef importAdyenToken(final TokenImportRequest request) throws BillingException
	{
		final AdyenTokenHandle token = request.token();
		verifyMerchantAccount(token);
		final String paymentSourceId = apiClient.importPermanentToken(request.customer().externalId(),
				buildReferenceId(token), token.cardMetadata());
		return new BillingPaymentMethodRef(BillingPlatform.CHARGEBEE, paymentSourceId);
	}

	@Override
	public PlanRef resolvePlan(final PlanResolutionRequest request) throws BillingException
	{
		return planResolver.resolve(request);
	}

	@Override
	public BillingSubscriptionRef createSubscription(final SubscriptionCreateRequest request) throws BillingException
	{
		final Long startEpochSeconds = request.startDate() == null ? null : request.startDate().getEpochSecond();
		final ChargebeeSubscriptionParams params = new ChargebeeSubscriptionParams(request.customer().externalId(),
				itemPriceId(request.plan()), request.quantity(), startEpochSeconds, request.idempotencyKey(),
				request.metadata());
		final String subscriptionId = apiClient.createSubscription(params);
		return new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, subscriptionId);
	}

	@Override
	public void updateSubscription(final SubscriptionUpdateRequest request) throws BillingException
	{
		final String itemPriceId = request.plan() == null ? null : itemPriceId(request.plan());
		apiClient.updateSubscription(request.subscription().externalId(), itemPriceId, request.quantity());
	}

	@Override
	public void cancelSubscription(final SubscriptionCancelRequest request) throws BillingException
	{
		apiClient.cancelSubscription(request.subscription().externalId(), request.atPeriodEnd());
	}

	// pauseSubscription is intentionally NOT overridden: supportsPause=false, so the SPI default
	// throws CapabilityUnsupportedException. Chargebee pause/resume will be added in a later increment.

	@Override
	public NormalizedBillingEvent parseWebhook(final RawWebhook raw) throws BillingException
	{
		throw new TerminalBillingException("Chargebee webhook handling is not yet implemented (planned in task P2.4)");
	}

	/**
	 * Defensive R2 guard (the core validator already checks this pre-activation): the token must be
	 * charged by a Chargebee gateway bound to the same Adyen merchant account it was minted under.
	 */
	protected void verifyMerchantAccount(final AdyenTokenHandle token) throws PreconditionFailedException
	{
		final String configured = configService.getConfiguredAdyenMerchantAccount();
		// Chargebee is an external gateway: a blank merchant account is a misconfiguration, not an
		// exemption. Fail closed so R2 cannot be silently bypassed (the core validator skips on null).
		if (StringUtils.isBlank(configured))
		{
			throw new PreconditionFailedException("Chargebee connector has no configured Adyen merchant account "
					+ "(chargebee.adyenMerchantAccount); refusing to import a token without the R2 guarantee");
		}
		if (!configured.equals(token.merchantAccount()))
		{
			throw new PreconditionFailedException("Chargebee connector is bound to Adyen merchant account '" + configured
					+ "' but the token was minted under '" + token.merchantAccount() + "'");
		}
	}

	/**
	 * Build the Chargebee {@code reference_id}: {@code shopperReference/recurringDetailReference}
	 * (shopper first, slash-joined; {@code storedPaymentMethodId == recurringDetailReference}).
	 */
	protected String buildReferenceId(final AdyenTokenHandle token)
	{
		return token.shopperReference() + "/" + token.storedPaymentMethodId();
	}

	protected String itemPriceId(final PlanRef plan)
	{
		// resolvePlan maps the sendable Chargebee item price id into PlanRef.planId (priceId is the
		// optional separate price id, not what subscription_items[item_price_id] expects).
		return plan.planId();
	}

	public void setApiClient(final ChargebeeApiClient apiClient)
	{
		this.apiClient = apiClient;
	}

	public void setConfigService(final ChargebeeConfigService configService)
	{
		this.configService = configService;
	}

	public void setPlanResolver(final ChargebeePlanResolver planResolver)
	{
		this.planResolver = planResolver;
	}
}
