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
package com.adyen.commerce.connector.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.BillingCustomerRef;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.CancelReason;
import com.adyen.commerce.connector.dto.ConnectorCapabilities;
import com.adyen.commerce.connector.dto.CustomerSyncRequest;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.dto.RecurringProcessingModel;
import com.adyen.commerce.connector.dto.SubscriptionCancelRequest;
import com.adyen.commerce.connector.dto.SubscriptionCreateRequest;
import com.adyen.commerce.connector.dto.TokenImportRequest;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.event.SubscriptionActivatedEvent;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.model.BillingCustomerRefModel;
import com.adyen.commerce.connector.model.BillingPaymentMethodRefModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.service.SubscriptionBillingService;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.connector.token.AdyenTokenHandleFactory;
import com.adyen.commerce.connector.validation.ConnectorMerchantAccountValidator;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

/**
 * Default orchestration. Holds no per-platform logic: every platform difference is delegated to the
 * active {@link SubscriptionBillingConnector}; the core only branches on advertised
 * {@link ConnectorCapabilities}.
 */
public class DefaultSubscriptionBillingService implements SubscriptionBillingService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionBillingService.class);

	private static final String STATUS_ACTIVE = "ACTIVE";
	private static final String STATUS_CANCELLED = "CANCELLED";

	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private ConnectorMerchantAccountValidator merchantAccountValidator;
	private AdyenTokenHandleFactory tokenHandleFactory;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private EventService eventService;
	private Clock clock = Clock.systemUTC();

	@Override
	public BillingSubscriptionRefModel activateSubscription(final AbstractOrderModel order, final ProductModel subProduct)
			throws BillingException
	{
		final SubscriptionBillingConnector connector = connectorRegistry.getActiveConnector(order.getStore());

		// Design R2: the connector's Adyen account must match the store's, else the token is uncharged.
		merchantAccountValidator.validate(connector, order.getStore());

		// Idempotency (seed for design P4.4): never create a second subscription for the same order/platform.
		final Optional<BillingSubscriptionRefModel> existing = findSubscriptionRef(order, connector.platform());
		if (existing.isPresent())
		{
			LOG.info("Subscription already active for order '{}' on platform {} — returning existing ref",
					order.getCode(), connector.platform());
			return existing.get();
		}

		final AdyenTokenHandle token = tokenHandleFactory.create(order);
		final ConnectorCapabilities caps = connector.capabilities();

		// Design P1.7: branch on capabilities, not platform identity.
		if (caps.requiresNetworkTransactionId() && !token.hasNetworkTransactionId())
		{
			throw new PreconditionFailedException("Connector " + connector.platform()
					+ " requires a network transaction id (NTID) but the captured Adyen token has none");
		}

		final CustomerModel customer = (CustomerModel) order.getUser();
		final String idempotencyKey = order.getCode();

		final BillingCustomerRef customerRef = connector.ensureCustomer(buildCustomerSyncRequest(customer));
		persistCustomerRef(customer, customerRef);

		final BillingPaymentMethodRef paymentMethodRef = connector
				.importAdyenToken(new TokenImportRequest(customerRef, token, RecurringProcessingModel.SUBSCRIPTION));
		persistPaymentMethodRef(order.getPaymentInfo(), paymentMethodRef);

		final PlanRef plan = connector.resolvePlan(new PlanResolutionRequest(subProduct.getCode(), Map.of()));

		final Instant startDate = caps.supportsImmediateStart() ? clock.instant() : null;

		final SubscriptionCreateRequest createRequest = new SubscriptionCreateRequest(customerRef, paymentMethodRef, plan,
				1, null, null, startDate, buildMetadata(order, subProduct), idempotencyKey);

		final BillingSubscriptionRef subscriptionRef = connector.createSubscription(createRequest);

		final BillingSubscriptionRefModel model = persistSubscriptionRef(order, customer, subscriptionRef, customerRef,
				paymentMethodRef, plan, idempotencyKey);

		publishActivated(model);
		return model;
	}

	@Override
	public void cancel(final BillingSubscriptionRefModel subscription, final CancelReason reason) throws BillingException
	{
		final SubscriptionBillingConnector connector = connectorRegistry.getConnector(subscription.getPlatform());
		final BillingSubscriptionRef ref = new BillingSubscriptionRef(subscription.getPlatform(),
				subscription.getExternalSubscriptionId());
		connector.cancelSubscription(new SubscriptionCancelRequest(ref, reason, false, subscription.getIdempotencyKey()));
		subscription.setStatus(STATUS_CANCELLED);
		modelService.save(subscription);
	}

	protected CustomerSyncRequest buildCustomerSyncRequest(final CustomerModel customer)
	{
		return new CustomerSyncRequest(customer.getCustomerID(), customer.getUid(), customer.getName(), null, Map.of());
	}

	protected Map<String, String> buildMetadata(final AbstractOrderModel order, final ProductModel subProduct)
	{
		final Map<String, String> metadata = new HashMap<>();
		metadata.put("sapOrderCode", order.getCode());
		metadata.put("sapProductCode", subProduct.getCode());
		return metadata;
	}

	protected BillingCustomerRefModel persistCustomerRef(final CustomerModel customer, final BillingCustomerRef ref)
	{
		final BillingCustomerRefModel model = customer.getBillingCustomerRefs().stream()
				.filter(r -> ref.platform().equals(r.getPlatform())).findFirst()
				.orElseGet(() -> modelService.create(BillingCustomerRefModel.class));
		model.setPlatform(ref.platform());
		model.setExternalId(ref.externalId());
		model.setCustomer(customer);
		modelService.save(model);
		return model;
	}

	protected BillingPaymentMethodRefModel persistPaymentMethodRef(final PaymentInfoModel paymentInfo,
			final BillingPaymentMethodRef ref)
	{
		final BillingPaymentMethodRefModel model = paymentInfo.getBillingPaymentMethodRefs().stream()
				.filter(r -> ref.platform().equals(r.getPlatform())).findFirst()
				.orElseGet(() -> modelService.create(BillingPaymentMethodRefModel.class));
		model.setPlatform(ref.platform());
		model.setExternalId(ref.externalId());
		model.setPaymentInfo(paymentInfo);
		modelService.save(model);
		return model;
	}

	protected BillingSubscriptionRefModel persistSubscriptionRef(final AbstractOrderModel order,
			final CustomerModel customer, final BillingSubscriptionRef subscriptionRef, final BillingCustomerRef customerRef,
			final BillingPaymentMethodRef paymentMethodRef, final PlanRef plan, final String idempotencyKey)
	{
		final BillingSubscriptionRefModel model = modelService.create(BillingSubscriptionRefModel.class);
		model.setPlatform(subscriptionRef.platform());
		model.setExternalSubscriptionId(subscriptionRef.externalId());
		model.setExternalCustomerId(customerRef.externalId());
		model.setExternalPaymentMethodId(paymentMethodRef.externalId());
		model.setPlanCode(plan == null ? null : plan.planId());
		model.setQuantity(Integer.valueOf(1));
		model.setCurrencyIsoCode(order.getCurrency() == null ? null : order.getCurrency().getIsocode());
		model.setIdempotencyKey(idempotencyKey);
		model.setOrder(order);
		model.setCustomer(customer);
		model.setStatus(STATUS_ACTIVE);
		modelService.save(model);
		return model;
	}

	protected Optional<BillingSubscriptionRefModel> findSubscriptionRef(final AbstractOrderModel order,
			final BillingPlatform platform)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingSubscriptionRef} WHERE {order} = ?order AND {platform} = ?platform");
		query.addQueryParameter("order", order);
		query.addQueryParameter("platform", platform);
		final List<BillingSubscriptionRefModel> result = flexibleSearchService
				.<BillingSubscriptionRefModel> search(query).getResult();
		return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
	}

	protected void publishActivated(final BillingSubscriptionRefModel model)
	{
		if (eventService != null)
		{
			eventService.publishEvent(new SubscriptionActivatedEvent(model));
		}
	}

	public void setConnectorRegistry(final SubscriptionBillingConnectorRegistry connectorRegistry)
	{
		this.connectorRegistry = connectorRegistry;
	}

	public void setMerchantAccountValidator(final ConnectorMerchantAccountValidator merchantAccountValidator)
	{
		this.merchantAccountValidator = merchantAccountValidator;
	}

	public void setTokenHandleFactory(final AdyenTokenHandleFactory tokenHandleFactory)
	{
		this.tokenHandleFactory = tokenHandleFactory;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setEventService(final EventService eventService)
	{
		this.eventService = eventService;
	}

	public void setClock(final Clock clock)
	{
		this.clock = clock;
	}
}
