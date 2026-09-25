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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.context.SubscriptionStoreContext;
import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.BillingAddress;
import com.adyen.commerce.connector.dto.BillingCustomerRef;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.CancellationTiming;
import com.adyen.commerce.connector.dto.ConnectorCapabilities;
import com.adyen.commerce.connector.dto.CustomerSyncRequest;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.dto.RecurringProcessingModel;
import com.adyen.commerce.connector.dto.SubscriptionCancelRequest;
import com.adyen.commerce.connector.dto.PaymentMethodChangeOutcome;
import com.adyen.commerce.connector.dto.PaymentMethodChangeRequest;
import com.adyen.commerce.connector.dto.PaymentMethodChangeSupport;
import com.adyen.commerce.connector.dto.PaymentMethodChoice;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentPage;
import com.adyen.commerce.connector.dto.PaymentMethodSource;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;
import com.adyen.commerce.connector.exception.CapabilityUnsupportedException;
import com.adyen.commerce.connector.dto.SubscriptionCancellation;
import com.adyen.commerce.connector.dto.SubscriptionCreateRequest;
import com.adyen.commerce.connector.dto.TokenImportRequest;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.event.SubscriptionActivatedEvent;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.model.BillingCustomerRefModel;
import com.adyen.commerce.connector.model.BillingPaymentMethodRefModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.reconciliation.SubscriptionReconciliationService;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.service.SubscriptionBillingService;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.connector.token.AdyenTokenHandleFactory;
import com.adyen.commerce.connector.validation.ConnectorMerchantAccountValidator;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.exceptions.ModelSavingException;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Default orchestration. Platform differences live in the {@link SubscriptionBillingConnector}; the core only
 * branches on its {@link ConnectorCapabilities}. Operations on an existing subscription run in the store of its
 * originating order, never in the caller's.
 */
public class DefaultSubscriptionBillingService implements SubscriptionBillingService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultSubscriptionBillingService.class);

	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private ConnectorMerchantAccountValidator merchantAccountValidator;
	private AdyenTokenHandleFactory tokenHandleFactory;
	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private EventService eventService;
	private Clock clock = Clock.systemUTC();
	private SubscriptionReconciliationService reconciliationService;
	private SubscriptionStoreContext storeContext;

	@Override
	public BillingSubscriptionRefModel activateSubscription(final AbstractOrderModel order, final ProductModel subProduct)
			throws BillingException
	{
		validateActivationInputs(order, subProduct);
		final BaseStoreModel store = order.getStore();
		final CustomerModel customer = asCustomer(order);

		final SubscriptionBillingConnector connector = connectorRegistry.getActiveConnector(store);

		// The connector's Adyen account must match the store's, else the token cannot be charged.
		merchantAccountValidator.validate(connector, store);

		final Optional<BillingSubscriptionRefModel> existing = findSubscriptionRef(order, connector.platform());
		if (existing.isPresent())
		{
			LOG.info("Subscription already active for order '{}' on platform {} — returning existing ref",
					order.getCode(), connector.platform());
			return existing.get();
		}

		final AdyenTokenHandle token = tokenHandleFactory.create(order);
		final ConnectorCapabilities caps = connector.capabilities();

		if (caps.requiresNetworkTransactionId() && !token.hasNetworkTransactionId())
		{
			throw new PreconditionFailedException("Connector " + connector.platform()
					+ " requires a network transaction id (NTID) but the captured Adyen token has none");
		}

		final String idempotencyKey = idempotencyKeyFor(order);

		final BillingCustomerRef customerRef = connector.ensureCustomer(buildCustomerSyncRequest(customer));
		persistCustomerRef(customer, customerRef);

		final BillingPaymentMethodRef paymentMethodRef = connector
				.importAdyenToken(new TokenImportRequest(customerRef, token, RecurringProcessingModel.SUBSCRIPTION,
						buildBillingAddress(order)));
		persistPaymentMethodRef(order.getPaymentInfo(), paymentMethodRef);

		final PlanRef plan = connector.resolvePlan(new PlanResolutionRequest(subProduct.getCode(), store.getUid(), Map.of()));

		final Instant startDate = caps.supportsImmediateStart() ? clock.instant() : null;
		final String currencyIsoCode = order.getCurrency() == null ? null : order.getCurrency().getIsocode();

		final SubscriptionCreateRequest createRequest = new SubscriptionCreateRequest(customerRef, paymentMethodRef, plan,
				1, null, currencyIsoCode, null, startDate, buildMetadata(order, subProduct), idempotencyKey);

		final BillingSubscriptionRef subscriptionRef = connector.createSubscription(createRequest);

		final BillingSubscriptionRefModel model;
		try
		{
			model = persistSubscriptionRef(order, customer, subscriptionRef, customerRef, paymentMethodRef, plan,
					idempotencyKey, subProduct);
		}
		catch (final ModelSavingException e)
		{
			// Lost the race on the unique (order, platform) index. Both threads sent the same idempotency key, so
			// the platform returned one subscription to both; any other save failure stays a failure.
			final Optional<BillingSubscriptionRefModel> winner = findSubscriptionRef(order, connector.platform());
			if (winner.isEmpty())
			{
				throw e;
			}
			LOG.info("Subscription for order '{}' on platform {} was persisted by another thread; returning its "
					+ "reference {}", order.getCode(), connector.platform(), winner.get().getExternalSubscriptionId());
			return winner.get();
		}

		// No immediate read-back: webhooks and the reconciliation sweep promote PENDING to the platform's state.
		publishActivated(model);
		LOG.info(
				"Created subscription {} for order '{}' on platform {} with local status {}",
				subscriptionRef.externalId(),
				order.getCode(),
				connector.platform(),
				model.getStatus());
		return model;
	}

	@Override
	public String idempotencyKeyFor(final AbstractOrderModel order)
	{
		// Stable across redeliveries and retries, so the platform recognises a repeated request.
		return order == null ? null : order.getCode();
	}

	@Override
	public List<PlatformPaymentMethod> listPaymentMethods(final BillingSubscriptionRefModel subscription)
			throws BillingException
	{
		if (subscription == null || StringUtils.isBlank(subscription.getExternalCustomerId()))
		{
			return List.of();
		}
		return storeContext.callInStoreOf(subscription, store -> {
			final SubscriptionBillingConnector connector = connectorRegistry.getConnector(subscription.getPlatform());
			// Nothing to list when a platform payment method would not be accepted back.
			if (!connector.capabilities().paymentMethodChange().accepts(PaymentMethodSource.ALREADY_ON_PLATFORM))
			{
				return List.of();
			}
			return connector.listPaymentMethods(
					new BillingCustomerRef(subscription.getPlatform(), subscription.getExternalCustomerId()));
		});
	}

	@Override
	public Optional<PaymentMethodEnrollmentPage> paymentMethodEnrollmentPage(
			final BillingSubscriptionRefModel subscription) throws BillingException
	{
		if (subscription == null || StringUtils.isBlank(subscription.getExternalCustomerId()))
		{
			return Optional.empty();
		}
		return storeContext.callInStoreOf(subscription, store -> {
			final SubscriptionBillingConnector connector = connectorRegistry.getConnector(subscription.getPlatform());
			if (!connector.capabilities().paymentMethodEnrollment().isOffered())
			{
				return Optional.empty();
			}
			merchantAccountValidator.validate(connector, store);
			return connector.paymentMethodEnrollmentPage(
					new BillingCustomerRef(subscription.getPlatform(), subscription.getExternalCustomerId()));
		});
	}

	@Override
	public PaymentMethodChangeOutcome changePaymentMethod(final BillingSubscriptionRefModel subscription,
			final PaymentMethodChoice choice) throws BillingException
	{
		if (subscription == null || choice == null)
		{
			throw new PreconditionFailedException(
					"Cannot change a payment method without both a subscription reference and a choice");
		}
		if (StringUtils.isBlank(subscription.getExternalCustomerId()))
		{
			throw new PreconditionFailedException("Subscription " + subscription.getExternalSubscriptionId()
					+ " has no external customer id; there is nothing on the platform to attach a payment "
					+ "method to");
		}

		return storeContext.callInStoreOf(subscription, store -> changePaymentMethodInStore(subscription, choice, store));
	}

	protected PaymentMethodChangeOutcome changePaymentMethodInStore(final BillingSubscriptionRefModel subscription,
			final PaymentMethodChoice choice, final BaseStoreModel store) throws BillingException
	{
		final SubscriptionBillingConnector connector = connectorRegistry.getConnector(subscription.getPlatform());
		final PaymentMethodChangeSupport declared = connector.capabilities().paymentMethodChange();
		if (!declared.isSupported())
		{
			throw new CapabilityUnsupportedException("Connector " + connector.platform()
					+ " does not support changing the payment method of an existing subscription");
		}
		if (!declared.accepts(choice.source()))
		{
			throw new CapabilityUnsupportedException("Connector " + connector.platform()
					+ " does not accept a payment method of kind " + choice.source());
		}
		merchantAccountValidator.validate(connector, store);

		final PaymentMethodChangeOutcome outcome = connector.changePaymentMethod(new PaymentMethodChangeRequest(
				new BillingCustomerRef(subscription.getPlatform(), subscription.getExternalCustomerId()),
				new BillingSubscriptionRef(subscription.getPlatform(), subscription.getExternalSubscriptionId()),
				choice,
				paymentMethodKey(subscription, choice)));

		if (outcome.appliedScope() != declared.scope())
		{
			// Not fatal: the change happened, but the shopper is told what was applied, not what was declared.
			LOG.warn("Connector {} declares payment-method changes as {} but reported {} for subscription {}; "
					+ "the shopper is being told what actually happened, not what was advertised",
					connector.platform(), declared.scope(), outcome.appliedScope(),
					subscription.getExternalSubscriptionId());
		}
		recordPaymentMethod(subscription, outcome);
		return outcome;
	}

	/**
	 * The store owning the subscription, from its originating order: a customer is global across stores, so the
	 * session may belong to another one.
	 */
	protected BaseStoreModel storeOf(final BillingSubscriptionRefModel subscription)
	{
		return storeContext.storeOf(subscription);
	}

	/** Derived from, not equal to, the subscription's key, which belongs to its creation. */
	protected String paymentMethodKey(final BillingSubscriptionRefModel subscription,
			final PaymentMethodChoice choice)
	{
		if (StringUtils.isBlank(subscription.getIdempotencyKey()))
		{
			return null;
		}
		final String chosen = switch (choice)
		{
			case PaymentMethodChoice.AdyenVaultedToken vaulted -> vaulted.token().storedPaymentMethodId();
			case PaymentMethodChoice.AlreadyOnPlatform onPlatform -> onPlatform.platformPaymentMethodId();
		};
		// Unique per action: Recurly replays the first response for a repeated key, so A -> B -> A -> B would be
		// answered with a stored 200 for a change never made. Safe, as this call moves no money.
		return subscription.getIdempotencyKey() + "/payment-method/" + chosen + "/" + UUID.randomUUID();
	}

	/** Records the billed payment method on this row only; reconciliation updates any others the platform moved. */
	protected void recordPaymentMethod(final BillingSubscriptionRefModel subscription,
			final PaymentMethodChangeOutcome outcome)
	{
		try
		{
			subscription.setExternalPaymentMethodId(outcome.paymentMethod().externalId());
			modelService.save(subscription);
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Payment method for subscription {} was changed on platform {}, but the local reference "
					+ "could not be updated", subscription.getExternalSubscriptionId(),
					subscription.getPlatform(), e);
		}
	}

	@Override
	public void cancel(final BillingSubscriptionRefModel subscription, final SubscriptionCancellation cancellation)
			throws BillingException
	{
		if (subscription == null)
		{
			throw new PreconditionFailedException("Cannot cancel a null subscription reference");
		}
		// Not defaulted: an assumed immediate cancellation would forfeit a period the shopper has paid for.
		if (cancellation == null)
		{
			throw new PreconditionFailedException("Cannot cancel subscription "
					+ subscription.getExternalSubscriptionId() + " without a reason and a cancellation timing");
		}
		storeContext.callInStoreOf(subscription, store -> {
			cancelInStore(subscription, cancellation, store);
			return null;
		});
	}

	/**
	 * Routes on the subscription's own platform, not the store's active one, so a store that migrated can still
	 * cancel what it created before.
	 */
	protected void cancelInStore(final BillingSubscriptionRefModel subscription,
			final SubscriptionCancellation cancellation, final BaseStoreModel store) throws BillingException
	{
		final SubscriptionBillingConnector connector = connectorRegistry.getConnector(subscription.getPlatform());
		warnOnMerchantAccountMismatch(connector, store, subscription);
		final BillingSubscriptionRef ref = new BillingSubscriptionRef(subscription.getPlatform(),
				subscription.getExternalSubscriptionId());
		connector.cancelSubscription(
				new SubscriptionCancelRequest(
						ref,
						cancellation.reason(),
						cancellation.timing(),
						cancellationKey(subscription.getIdempotencyKey(), cancellation.timing())));

		recordScheduledCancellation(subscription, cancellation.timing());

		try
		{
			reconciliationService.reconcile(subscription);
		}
		catch (final BillingException | RuntimeException e)
		{
			// The cancellation already happened; a failure here only leaves the local projection stale.
			LOG.warn(
					"Subscription {} was cancelled on platform {}, but its authoritative state "
							+ "could not be fetched immediately; reconciliation sweep will retry",
					subscription.getExternalSubscriptionId(),
					subscription.getPlatform(),
					e);

			flagForReconciliationSweep(subscription);
		}
	}

	/**
	 * Merchant-account check for a cancellation, reported but not enforced: stopping a subscription charges
	 * nothing, and refusing it would keep billing the shopper.
	 */
	protected void warnOnMerchantAccountMismatch(final SubscriptionBillingConnector connector,
			final BaseStoreModel store, final BillingSubscriptionRefModel subscription)
	{
		try
		{
			merchantAccountValidator.validate(connector, store);
		}
		catch (final PreconditionFailedException e)
		{
			LOG.warn("Cancelling subscription {} on platform {} although its merchant account check fails: {}",
					subscription.getExternalSubscriptionId(), subscription.getPlatform(), e.getMessage());
		}
	}

	/**
	 * Keyed by timing as well: Recurly replays the first response for a repeated key, so an escalation from
	 * end-of-period to immediate would never reach it. A blank key stays blank rather than becoming shared.
	 */
	protected String cancellationKey(final String idempotencyKey, final CancellationTiming timing)
	{
		return StringUtils.isBlank(idempotencyKey)
				? idempotencyKey
				: idempotencyKey + "/" + timing.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Marks an end-of-period cancellation locally before the read-back, which may fail. The status after an
	 * immediate cancellation is left to the platform.
	 */
	protected void recordScheduledCancellation(final BillingSubscriptionRefModel subscription,
			final CancellationTiming timing)
	{
		if (timing != CancellationTiming.AT_PERIOD_END)
		{
			return;
		}
		try
		{
			subscription.setCancelAtPeriodEnd(Boolean.TRUE);
			modelService.save(subscription);
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Could not record the scheduled cancellation of subscription {} on platform {} locally; "
					+ "reconciliation is what puts it right", subscription.getExternalSubscriptionId(),
					subscription.getPlatform(), e);
		}
	}

	/** Clears the sync watermark so the next sweep picks this reference up; failing to do so is not fatal. */
	protected void flagForReconciliationSweep(final BillingSubscriptionRefModel subscription)
	{
		try
		{
			subscription.setLastSyncedAt(null);
			modelService.save(subscription);
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Could not flag subscription {} on platform {} for the reconciliation sweep; it will be "
					+ "picked up once its last sync goes stale instead", subscription.getExternalSubscriptionId(),
					subscription.getPlatform(), e);
		}
	}

	protected void validateActivationInputs(final AbstractOrderModel order, final ProductModel subProduct)
			throws PreconditionFailedException
	{
		if (order == null)
		{
			throw new PreconditionFailedException("Cannot activate a subscription without an order");
		}
		if (subProduct == null)
		{
			throw new PreconditionFailedException("Cannot activate a subscription without a subscription product");
		}
		if (order.getStore() == null)
		{
			throw new PreconditionFailedException(
					"Order '" + order.getCode() + "' has no base store; cannot resolve a billing connector");
		}
	}

	protected CustomerModel asCustomer(final AbstractOrderModel order) throws PreconditionFailedException
	{
		if (order.getUser() instanceof CustomerModel customer)
		{
			return customer;
		}
		throw new PreconditionFailedException("Order '" + order.getCode()
				+ "' is not owned by a customer; a subscription requires a Customer for the shopperReference");
	}

	protected CustomerSyncRequest buildCustomerSyncRequest(final CustomerModel customer)
	{
		return new CustomerSyncRequest(customer.getCustomerID(), customer.getUid(), customer.getName(), null, Map.of());
	}

	protected BillingAddress buildBillingAddress(final AbstractOrderModel order)
	{
		final AddressModel paymentAddress = order.getPaymentAddress();
		final AddressModel address = paymentAddress != null ? paymentAddress : order.getDeliveryAddress();
		if (address == null)
		{
			return null;
		}

		// Checkout copies the delivery address to the payment address when no billing address is given, so
		// it counts as the cardholder's only when it differs from the delivery address.
		final boolean confirmed = paymentAddress != null && !sameAddress(paymentAddress, order.getDeliveryAddress());
		if (!confirmed)
		{
			LOG.info("Order '{}' has no billing address distinct from its delivery address; passing the address "
					+ "on unconfirmed so connectors do not treat it as the cardholder's identity", order.getCode());
		}

		final String country = address.getCountry() == null ? null : address.getCountry().getIsocode();
		final String region = address.getRegion() == null ? null : address.getRegion().getIsocodeShort();
		return new BillingAddress(address.getFirstname(), address.getLastname(),
				joinStreet(address.getStreetname(), address.getStreetnumber()), null, address.getTown(), region,
				address.getPostalcode(), country, address.getPhone1(), confirmed);
	}

	/** By value: some checkout paths clone the address. */
	protected boolean sameAddress(final AddressModel left, final AddressModel right)
	{
		if (left == right)
		{
			return true;
		}
		if (right == null)
		{
			return false;
		}
		return Objects.equals(left.getFirstname(), right.getFirstname())
				&& Objects.equals(left.getLastname(), right.getLastname())
				&& Objects.equals(left.getStreetname(), right.getStreetname())
				&& Objects.equals(left.getStreetnumber(), right.getStreetnumber())
				&& Objects.equals(left.getPostalcode(), right.getPostalcode())
				&& Objects.equals(left.getTown(), right.getTown())
				&& Objects.equals(left.getCountry(), right.getCountry());
	}

	protected String joinStreet(final String streetName, final String streetNumber)
	{
		if (streetName == null || streetName.isBlank())
		{
			return streetNumber;
		}
		if (streetNumber == null || streetNumber.isBlank())
		{
			return streetName;
		}
		return streetName + " " + streetNumber;
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
			final BillingPaymentMethodRef paymentMethodRef, final PlanRef plan, final String idempotencyKey,
			final ProductModel subProduct)
	{
		final BillingSubscriptionRefModel model = modelService.create(BillingSubscriptionRefModel.class);
		model.setPlatform(subscriptionRef.platform());
		model.setExternalSubscriptionId(subscriptionRef.externalId());
		model.setExternalCustomerId(customerRef.externalId());
		model.setExternalPaymentMethodId(paymentMethodRef.externalId());
		model.setPlanCode(plan == null ? null : plan.planId());
		model.setQuantity(1);
		model.setCurrencyIsoCode(order.getCurrency() == null ? null : order.getCurrency().getIsocode());
		model.setIdempotencyKey(idempotencyKey);
		// Minted here, not lazily on read, where two concurrent reads would mint two values.
		model.setCode(UUID.randomUUID().toString());
		model.setProductCode(subProduct == null ? null : subProduct.getCode());
		model.setOrder(order);
		model.setCustomer(customer);
		model.setStatus(NormalizedSubscriptionStatus.PENDING.name());
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

	public void setReconciliationService(
			final SubscriptionReconciliationService reconciliationService)
	{
		this.reconciliationService = reconciliationService;
	}

	public void setStoreContext(final SubscriptionStoreContext storeContext)
	{
		this.storeContext = storeContext;
	}
}
