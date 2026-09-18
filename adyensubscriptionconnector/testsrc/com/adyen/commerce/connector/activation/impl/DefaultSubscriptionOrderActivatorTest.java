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
package com.adyen.commerce.connector.activation.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.activation.BillingActivationAttemptService;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.product.impl.DefaultSubscriptionProductRule;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.service.SubscriptionBillingService;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.core.PK;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.servicelayer.exceptions.ModelSavingException;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * Unit test for {@link DefaultSubscriptionOrderActivator} — which orders activate a
 * subscription, which are left alone, and the guarantee that nothing ever escapes into the checkout.
 */
@UnitTest
public class DefaultSubscriptionOrderActivatorTest
{
	private static final String SUB_PRODUCT = "sub-product";
	private static final String SECOND_SUB_PRODUCT = "second-sub-product";
	private static final String PLAIN_PRODUCT = "plain-product";

	private static final PK STORE_PK = PK.fromLong(1001L);
	private static final PK OTHER_STORE_PK = PK.fromLong(2002L);

	@Mock
	private SubscriptionBillingService subscriptionBillingService;
	@Mock
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	@Mock
	private SubscriptionBillingConnector connector;
	@Mock
	private BillingActivationAttemptService attemptService;
	@Mock
	private SessionService sessionService;
	@Mock
	private BaseSiteService baseSiteService;
	@Mock
	private BaseStoreService baseStoreService;
	@Mock
	private OrderModel order;
	@Mock
	private BaseStoreModel store;
	@Mock
	private BillingActivationAttemptModel attempt;

	private DefaultSubscriptionOrderActivator activator;

	@Before
	public void setUp() throws Exception
	{
		MockitoAnnotations.openMocks(this);

		activator = new DefaultSubscriptionOrderActivator();
		activator.setSubscriptionBillingService(subscriptionBillingService);
		activator.setConnectorRegistry(connectorRegistry);
		// The real rule, shared in production with SubscriptionPaymentRequestDecorator; stubbing it out would
		// let this test and the decorator's drift apart.
		activator.setSubscriptionProductRule(new DefaultSubscriptionProductRule());
		activator.setAttemptService(attemptService);
		activator.setSessionService(sessionService);
		activator.setBaseSiteService(baseSiteService);
		activator.setBaseStoreService(baseStoreService);

		// The local view is the unit under test's own plumbing, not a collaborator to assert on: the body
		// runs inline so every test below exercises it.
		when(sessionService.executeInLocalView(any(SessionExecutionBody.class))).thenAnswer(invocation -> {
			invocation.<SessionExecutionBody> getArgument(0).execute();
			return null;
		});

		when(order.getCode()).thenReturn("order-1");
		when(order.getStore()).thenReturn(store);
		when(store.getUid()).thenReturn("electronics");
		when(store.getPk()).thenReturn(STORE_PK);
		when(store.getActiveBillingPlatform()).thenReturn(BillingPlatform.CHARGEBEE);
		when(baseStoreService.getCurrentBaseStore()).thenReturn(store);
		when(attemptService.begin(any(), any(), any(), any())).thenReturn(attempt);
		when(connectorRegistry.getActiveConnector(store)).thenReturn(connector);
		when(connector.platform()).thenReturn(BillingPlatform.CHARGEBEE);

		// Only the two subscription codes are mapped to a plan; anything else is an ordinary product.
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenAnswer(invocation -> {
			final PlanResolutionRequest request = invocation.getArgument(0);
			if (SUB_PRODUCT.equals(request.productCode()) || SECOND_SUB_PRODUCT.equals(request.productCode()))
			{
				return new PlanRef("plan-1", null);
			}
			throw new PlanNotMappedException("no mapping for " + request.productCode());
		});
	}

	@Test
	public void activatesForAnOrderCarryingASubscriptionProduct() throws Exception
	{
		givenEntries(product(SUB_PRODUCT));

		activator.activateFor(order);

		verify(subscriptionBillingService).activateSubscription(order, entryProduct(SUB_PRODUCT));
	}

	@Test
	public void journalsASuccessfulActivationAgainstTheKeyItSent() throws Exception
	{
		when(subscriptionBillingService.idempotencyKeyFor(order)).thenReturn("order-1");
		final BillingSubscriptionRefModel ref = mock(BillingSubscriptionRefModel.class);
		when(subscriptionBillingService.activateSubscription(any(), any())).thenReturn(ref);
		givenEntries(product(SUB_PRODUCT));

		activator.activateFor(order);

		verify(attemptService).begin(order, BillingPlatform.CHARGEBEE, SUB_PRODUCT, "order-1");
		verify(attemptService).succeeded(attempt, ref);
		verify(attemptService, never()).failed(any(), any());
	}

	/**
	 * A failure the checkout never sees still has to leave a record the retry policy can act on.
	 */
	@Test
	public void journalsAFailedActivationInsteadOfLosingIt() throws Exception
	{
		givenEntries(product(SUB_PRODUCT));
		final RetryableBillingException failure = new RetryableBillingException("Chargebee is down");
		doThrow(failure).when(subscriptionBillingService).activateSubscription(any(), any());

		activator.activateFor(order);

		verify(attemptService).failed(attempt, failure);
		verify(attemptService, never()).succeeded(any(), any());
	}

	/**
	 * A save failure that is not the (order, platform) race is an ordinary failure and belongs in the journal
	 * like any other.
	 */
	@Test
	public void journalsAModelSavingFailure() throws Exception
	{
		givenEntries(product(SUB_PRODUCT));
		final ModelSavingException failure = new ModelSavingException("could not save");
		doThrow(failure).when(subscriptionBillingService).activateSubscription(any(), any());

		activator.activateFor(order);

		verify(attemptService).failed(attempt, failure);
	}

	@Test
	public void activatesTheOrdersOwnBaseSiteBeforeTouchingAConnector() throws Exception
	{
		final BaseSiteModel site = mock(BaseSiteModel.class);
		when(order.getSite()).thenReturn(site);
		givenEntries(product(SUB_PRODUCT));

		activator.activateFor(order);

		verify(baseSiteService).setCurrentBaseSite(site, false);
		verify(subscriptionBillingService).activateSubscription(order, entryProduct(SUB_PRODUCT));
	}

	/**
	 * A base site listing several stores resolves to its first one. Activating anyway would have the connector
	 * read another store's credentials and bill a merchant account the shopper never saw, so this refuses, and
	 * refuses terminally because the cause is configuration.
	 */
	@Test
	public void refusesToActivateWhenTheSessionResolvesToADifferentStore() throws Exception
	{
		final BaseStoreModel otherStore = mock(BaseStoreModel.class);
		when(otherStore.getPk()).thenReturn(OTHER_STORE_PK);
		when(otherStore.getUid()).thenReturn("apparel");
		when(baseStoreService.getCurrentBaseStore()).thenReturn(otherStore);
		givenEntries(product(SUB_PRODUCT));

		activator.activateFor(order);

		verify(subscriptionBillingService, never()).activateSubscription(any(), any());
		verify(attemptService).failed(eq(attempt), any(PreconditionFailedException.class));
	}

	@Test
	public void refusesToActivateWhenNoStoreCanBeResolvedAtAll() throws Exception
	{
		when(baseStoreService.getCurrentBaseStore()).thenReturn(null);
		givenEntries(product(SUB_PRODUCT));

		activator.activateFor(order);

		verify(subscriptionBillingService, never()).activateSubscription(any(), any());
		verify(attemptService).failed(eq(attempt), any(PreconditionFailedException.class));
	}

	/**
	 * The journal must not become a second way for the activation path to throw into a checkout.
	 */
	@Test
	public void swallowsAFailureToWriteTheJournalItself() throws Exception
	{
		givenEntries(product(SUB_PRODUCT));
		doThrow(new RetryableBillingException("Chargebee is down")).when(subscriptionBillingService)
				.activateSubscription(any(), any());
		doThrow(new IllegalStateException("the database is gone")).when(attemptService).failed(any(), any());

		activator.activateFor(order);
	}

	@Test
	public void ignoresAnOrderOfOrdinaryProducts() throws Exception
	{
		givenEntries(product(PLAIN_PRODUCT), product("another-plain"));

		activator.activateFor(order);

		verifyNoInteractions(subscriptionBillingService);
	}

	/**
	 * Activation is idempotent on (order, platform), so only one subscription can exist per order: an order
	 * carrying several activates none, because neither the reference nor the journal has room to record the
	 * ones that would be dropped.
	 */
	@Test
	public void activatesNothingWhenAnOrderCarriesSeveralSubscriptionProducts() throws Exception
	{
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenReturn(new PlanRef("plan-1", null));
		givenEntries(product("sub-a"), product("sub-b"));

		activator.activateFor(order);

		verify(subscriptionBillingService, never()).activateSubscription(any(), any());
	}

	@Test
	public void countsARepeatedProductOnce() throws Exception
	{
		givenEntries(product(SUB_PRODUCT), product(SUB_PRODUCT));

		activator.activateFor(order);

		verify(subscriptionBillingService, times(1)).activateSubscription(order, entryProduct(SUB_PRODUCT));
	}

	/**
	 * Two different subscription products on one order activate none. Reference and journal are both keyed one
	 * row per order and platform, so activating the first would leave the shopper paying for two and holding
	 * one with nothing recording the second; refusing buys a record naming both.
	 */
	@Test
	public void refusesAnOrderCarryingTwoDifferentSubscriptionProducts() throws Exception
	{
		givenEntries(product(SUB_PRODUCT), product(SECOND_SUB_PRODUCT));

		activator.activateFor(order);

		verify(subscriptionBillingService, never()).activateSubscription(any(), any());
		verify(attemptService).failed(any(), isA(PreconditionFailedException.class));
	}

	/**
	 * A guest has no account, so the only place a subscription can be seen or stopped is closed to them while
	 * it goes on renewing — and registering later mints a different customer, so it never opens.
	 */
	@Test
	public void refusesToStartASubscriptionAGuestCouldNeverStop() throws Exception
	{
		givenEntries(product(SUB_PRODUCT));
		final CustomerModel guest = mock(CustomerModel.class);
		when(guest.getType()).thenReturn(CustomerType.GUEST);
		when(order.getUser()).thenReturn(guest);

		activator.activateFor(order);

		verify(subscriptionBillingService, never()).activateSubscription(any(), any());
		verify(attemptService).failed(any(), isA(PreconditionFailedException.class));
	}

	/**
	 * The guard reads the customer's type, not merely whether one is present: every activation has a
	 * customer, and refusing them all would stop the feature rather than the unmanageable case.
	 */
	@Test
	public void activatesNormallyForARegisteredShopper() throws Exception
	{
		givenEntries(product(SUB_PRODUCT));
		final CustomerModel registered = mock(CustomerModel.class);
		when(registered.getType()).thenReturn(CustomerType.REGISTERED);
		when(order.getUser()).thenReturn(registered);

		activator.activateFor(order);

		verify(subscriptionBillingService).activateSubscription(order, entryProduct(SUB_PRODUCT));
	}

	/**
	 * The refusal is journalled against the product it concerns, which is why the guard runs after the
	 * attempt is opened rather than before it.
	 */
	@Test
	public void journalsTheGuestRefusalAgainstTheProductThatWasSold() throws Exception
	{
		when(subscriptionBillingService.idempotencyKeyFor(order)).thenReturn("order-1");
		givenEntries(product(SUB_PRODUCT));
		final CustomerModel guest = mock(CustomerModel.class);
		when(guest.getType()).thenReturn(CustomerType.GUEST);
		when(order.getUser()).thenReturn(guest);

		activator.activateFor(order);

		verify(attemptService).begin(order, BillingPlatform.CHARGEBEE, SUB_PRODUCT, "order-1");
	}

	@Test
	public void doesNothingWhenTheStoreSellsNoSubscriptions() throws Exception
	{
		when(store.getActiveBillingPlatform()).thenReturn(null);
		givenEntries(product(SUB_PRODUCT));

		activator.activateFor(order);

		verify(connectorRegistry, never()).getActiveConnector(any());
		verifyNoInteractions(subscriptionBillingService);
	}

	@Test
	public void doesNothingForAnOrderWithoutAStore() throws Exception
	{
		when(order.getStore()).thenReturn(null);
		givenEntries(product(SUB_PRODUCT));

		activator.activateFor(order);

		verifyNoInteractions(subscriptionBillingService);
	}

	@Test
	public void doesNothingForANullOrder() throws Exception
	{
		activator.activateFor(null);

		verifyNoInteractions(subscriptionBillingService);
	}

	/**
	 * afterPlaceOrder runs after submitOrder and outside the strategy's try/finally, so anything escaping
	 * here would surface as a checkout failure for an order that is already placed and paid.
	 */
	@Test
	public void swallowsAnActivationFailureSoTheCheckoutStillSucceeds() throws Exception
	{
		givenEntries(product(SUB_PRODUCT));
		doThrow(new RetryableBillingException("Chargebee is down")).when(subscriptionBillingService)
				.activateSubscription(any(), any());

		activator.activateFor(order);
	}

	/**
	 * A store selecting a platform nothing answers for is a misconfiguration that stops every subscription in
	 * that store, so it is journalled rather than only logged — at the price of an ordinary order in such a
	 * store acquiring a record too.
	 */
	@Test
	public void swallowsAMissingConnectorSoTheCheckoutStillSucceeds() throws Exception
	{
		givenEntries(product(SUB_PRODUCT));
		final ConnectorNotConfiguredException failure = new ConnectorNotConfiguredException("no connector for CHARGEBEE");
		when(connectorRegistry.getActiveConnector(store)).thenThrow(failure);

		activator.activateFor(order);

		verify(subscriptionBillingService, never()).activateSubscription(any(), any());
		verify(attemptService).failed(attempt, failure);
	}

	@Test
	public void swallowsAnUnexpectedRuntimeFailure() throws Exception
	{
		givenEntries(product(SUB_PRODUCT));
		when(order.getEntries()).thenThrow(new IllegalStateException("boom"));

		activator.activateFor(order);
	}

	/**
	 * A resolver that breaks is not the same as a product that is not a subscription. Flattening the two would
	 * make the order look ordinary and return before {@code begin} is called, leaving no attempt row for
	 * {@code SubscriptionActivationRetryJob} to find on an order the shopper has paid for. It leaves a record,
	 * and the record does not take the checkout down with it.
	 */
	@Test
	public void journalsAResolverThatCannotAnswerInsteadOfCallingTheOrderOrdinary() throws Exception
	{
		when(subscriptionBillingService.idempotencyKeyFor(order)).thenReturn("order-1");
		when(connector.resolvePlan(any(PlanResolutionRequest.class)))
				.thenThrow(new RetryableBillingException("resolver exploded"));
		givenEntries(product(SUB_PRODUCT));

		activator.activateFor(order);

		// No product code on the row marks an order that could not be classified, as opposed to a row carrying
		// the product that was tried and the platform's own refusal.
		verify(attemptService).begin(order, BillingPlatform.CHARGEBEE, null, "order-1");
		verify(attemptService).failed(eq(attempt), any(SubscriptionProductUndecidableException.class));
		verify(subscriptionBillingService, never()).activateSubscription(any(), any());
	}

	/**
	 * An unchecked failure out of the resolver &mdash; FlexibleSearch throws unchecked &mdash; is the same
	 * unclassifiable case and gets the same durable record rather than being dropped per entry.
	 */
	@Test
	public void journalsAnUncheckedResolverFailureToo() throws Exception
	{
		when(connector.resolvePlan(any(PlanResolutionRequest.class)))
				.thenThrow(new IllegalStateException("FlexibleSearch is unhappy"));
		givenEntries(product(SUB_PRODUCT));

		activator.activateFor(order);

		verify(attemptService).failed(eq(attempt), any(SubscriptionProductUndecidableException.class));
		verify(subscriptionBillingService, never()).activateSubscription(any(), any());
	}

	/**
	 * One unclassifiable entry defers the whole order to the retry even though another entry resolved: only one
	 * subscription per order is activated, so going ahead would mean choosing a plan while unable to see one of
	 * the candidates.
	 */
	@Test
	public void defersAMixedOrderRatherThanActivatingTheEntryThatHappenedToResolve() throws Exception
	{
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenAnswer(invocation -> {
			final PlanResolutionRequest request = invocation.getArgument(0);
			if (SUB_PRODUCT.equals(request.productCode()))
			{
				return new PlanRef("plan-1", null);
			}
			throw new RetryableBillingException("resolver exploded for " + request.productCode());
		});
		givenEntries(product("unreadable"), product(SUB_PRODUCT));

		activator.activateFor(order);

		verify(subscriptionBillingService, never()).activateSubscription(any(), any());
		verify(attemptService).failed(eq(attempt), any(SubscriptionProductUndecidableException.class));
	}

	@Test
	public void skipsAnEntryWithoutAUsableProduct() throws Exception
	{
		final ProductModel blank = product("   ");
		final AbstractOrderEntryModel noProduct = mock(AbstractOrderEntryModel.class);
		final AbstractOrderEntryModel blankCode = mock(AbstractOrderEntryModel.class);
		when(blankCode.getProduct()).thenReturn(blank);
		when(order.getEntries()).thenReturn(List.of(noProduct, blankCode));

		activator.activateFor(order);

		verifyNoInteractions(subscriptionBillingService);
	}

	@Test
	public void aNullOrderDoesNothing() throws Exception
	{
		activator.activateFor(null);

		verify(connectorRegistry, never()).getActiveConnector(any());
		verifyNoInteractions(subscriptionBillingService);
	}

	@Test
	public void resolvesEachDistinctProductCodeOnlyOnce() throws Exception
	{
		givenEntries(product(SUB_PRODUCT), product(SUB_PRODUCT), product(PLAIN_PRODUCT));

		activator.activateFor(order);

		verify(connector, times(2)).resolvePlan(any(PlanResolutionRequest.class));
	}

	/**
	 * Built eagerly rather than in a stream mapped inside {@code when(...)}: stubbing one mock while another's
	 * stubbing is still open is what Mockito reports as unfinished stubbing.
	 */
	private void givenEntries(final ProductModel... products)
	{
		final List<AbstractOrderEntryModel> entries = new ArrayList<>();
		for (final ProductModel product : products)
		{
			final AbstractOrderEntryModel entry = mock(AbstractOrderEntryModel.class);
			when(entry.getProduct()).thenReturn(product);
			entries.add(entry);
		}
		when(order.getEntries()).thenReturn(entries);
	}

	private ProductModel entryProduct(final String code)
	{
		return order.getEntries().stream()
				.map(AbstractOrderEntryModel::getProduct)
				.filter(p -> code.equals(p.getCode()))
				.findFirst()
				.orElseThrow();
	}

	private static ProductModel product(final String code)
	{
		final ProductModel product = mock(ProductModel.class);
		when(product.getCode()).thenReturn(code);
		return product;
	}

	@Test
	public void neverTouchesTheServiceWhenNothingIsMapped() throws Exception
	{
		givenEntries(product(PLAIN_PRODUCT));

		activator.activateFor(order);

		verify(subscriptionBillingService, never()).activateSubscription(any(), any());
	}
}
