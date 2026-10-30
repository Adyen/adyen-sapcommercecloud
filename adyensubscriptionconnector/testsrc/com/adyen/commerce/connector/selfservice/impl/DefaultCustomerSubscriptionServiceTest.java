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
package com.adyen.commerce.connector.selfservice.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.context.SubscriptionStoreContext;
import com.adyen.commerce.connector.dto.ConnectorCapabilities;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;
import com.adyen.commerce.connector.dto.PaymentMethodChangeSupport;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentSupport;
import com.adyen.commerce.connector.dto.PaymentMethodSource;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
import com.adyen.commerce.connector.dto.TokenImportStyle;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.selfservice.PaymentMethodNotOwnedException;
import com.adyen.commerce.connector.selfservice.SubscriptionLifecycleState;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.services.AdyenStoredCardAuthorisationService;
import com.adyen.model.checkout.StoredPaymentMethodResource;
import com.adyen.v6.exceptions.AdyenStoredCardsUnavailableException;
import com.adyen.v6.service.AdyenStoredCardsService;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Where a subscription stands for the shopper, and what its platform would accept.
 */
@UnitTest
public class DefaultCustomerSubscriptionServiceTest
{
	private static final BillingPlatform PLATFORM = BillingPlatform.valueOf("CHARGEBEE");

	@Mock
	private SubscriptionStoreContext storeContext;
	@Mock
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	@Mock
	private SubscriptionBillingConnector connector;
	@Mock
	private AdyenStoredCardsService storedCardsService;
	@Mock
	private AdyenStoredCardAuthorisationService storedCardAuthorisationService;
	@Mock
	private CustomerModel customer;

	private DefaultCustomerSubscriptionService service;
	private BaseStoreModel store;

	@Before
	public void setUp() throws Exception
	{
		MockitoAnnotations.openMocks(this);
		service = new DefaultCustomerSubscriptionService();
		service.setStoreContext(storeContext);
		service.setConnectorRegistry(connectorRegistry);
		service.setStoredCardsService(storedCardsService);
		service.setStoredCardAuthorisationService(storedCardAuthorisationService);

		store = mock(BaseStoreModel.class);
		when(connectorRegistry.getConnector(any())).thenReturn(connector);
		when(storeContext.storeOf(any())).thenReturn(store);
		when(storeContext.callInStoreOf(any(), any())).thenAnswer(invocation -> invocation
				.<SubscriptionStoreContext.StoreBoundWork<Object>> getArgument(1).call(store));
	}

	// --- the state table ---

	/**
	 * Whether any platform has confirmed the row is asked before the status. Only a reconciliation writes
	 * platformUpdatedAt; lastSyncedAt is cleared to hurry the sweep along and means nothing here.
	 */
	@Test
	public void saysNothingAboutASubscriptionNoPlatformHasConfirmedYet()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getPlatformUpdatedAt()).thenReturn(null);

		assertEquals(SubscriptionLifecycleState.SETTING_UP, service.getState(ref));
		assertFalse(SubscriptionLifecycleState.SETTING_UP.isCancellable());
	}

	@Test
	public void anActiveSubscriptionRenewsAndCanBeStopped()
	{
		assertEquals(SubscriptionLifecycleState.ACTIVE, service.getState(ref(NormalizedSubscriptionStatus.ACTIVE)));
		assertTrue(SubscriptionLifecycleState.ACTIVE.isCancellable());
	}

	/** A stopped subscription stays ACTIVE on the platform until its term runs out. */
	@Test
	public void anActiveSubscriptionAlreadyStoppedIsEndingAndCannotBeStoppedAgain()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getCancelAtPeriodEnd()).thenReturn(Boolean.TRUE);

		assertEquals(SubscriptionLifecycleState.ENDING, service.getState(ref));
		assertFalse(SubscriptionLifecycleState.ENDING.isCancellable());
	}

	/** Dunning outranks the pending end, so a shopper who stopped it must not be told it will be retried. */
	@Test
	public void anUnpaidInvoiceOnAnAlreadyStoppedSubscriptionIsItsOwnState()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.PAST_DUE);
		when(ref.getCancelAtPeriodEnd()).thenReturn(Boolean.TRUE);

		assertEquals(SubscriptionLifecycleState.PAST_DUE_ENDING, service.getState(ref));
	}

	@Test
	public void aPaymentProblemStillLetsTheShopperStopTheSubscription()
	{
		assertEquals(SubscriptionLifecycleState.PAST_DUE,
				service.getState(ref(NormalizedSubscriptionStatus.PAST_DUE)));
		assertTrue(SubscriptionLifecycleState.PAST_DUE.isCancellable());
	}

	@Test
	public void aFutureDatedSubscriptionIsStartingSoon()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.PENDING);
		when(ref.getCurrentPeriodStart()).thenReturn(new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)));

		assertEquals(SubscriptionLifecycleState.STARTING_SOON, service.getState(ref));
	}

	@Test
	public void aPendingSubscriptionWithNoStartDateIsStillJustBeingSetUp()
	{
		assertEquals(SubscriptionLifecycleState.SETTING_UP,
				service.getState(ref(NormalizedSubscriptionStatus.PENDING)));
	}

	@Test
	public void anEndedSubscriptionIsEndedWhicheverWordThePlatformUsed()
	{
		assertEquals(SubscriptionLifecycleState.ENDED, service.getState(ref(NormalizedSubscriptionStatus.EXPIRED)));
		assertEquals(SubscriptionLifecycleState.ENDED, service.getState(ref(NormalizedSubscriptionStatus.CANCELLED)));
		assertEquals(SubscriptionLifecycleState.ENDED, service.getState(ref(NormalizedSubscriptionStatus.FAILED)));
	}

	/** The status column is a plain string, so it can hold a value outside this vocabulary. */
	@Test
	public void anUnrecognisedStatusLiteralIsUnavailableAndOffersNothing()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getStatus()).thenReturn("transferred");

		assertEquals(SubscriptionLifecycleState.UNAVAILABLE, service.getState(ref));
		assertFalse(SubscriptionLifecycleState.UNAVAILABLE.isCancellable());
		assertFalse(SubscriptionLifecycleState.UNAVAILABLE.isPaymentMethodChangeable());
	}

	// --- what the platform would accept ---

	/**
	 * A platform that charges an imported token as a merchant-initiated transaction refuses one without a
	 * network transaction id, so such a card is never offered for it.
	 */
	@Test
	public void withholdsVaultedCardsWithoutANetworkTransactionIdFromAPlatformThatNeedsOne() throws Exception
	{
		givenConnectorAcceptingVaultedCards(true);
		givenVault(card("card-reported", "ntid-1"), card("card-recorded", null), card("card-without", null));
		when(storedCardAuthorisationService.networkTxReferencesFor(customer))
				.thenReturn(Map.of("card-recorded", "ntid-2"));

		final List<PlatformPaymentMethod> accepted =
				service.getAcceptedVaultedCards(customer, ref(NormalizedSubscriptionStatus.ACTIVE));

		assertEquals(List.of("card-reported", "card-recorded"), accepted.stream().map(PlatformPaymentMethod::id).toList());
	}

	@Test
	public void offersEveryVaultedCardToAPlatformThatNeedsNoNetworkTransactionId() throws Exception
	{
		givenConnectorAcceptingVaultedCards(false);
		givenVault(card("card-a", null), card("card-b", null));

		assertEquals(2, service.getAcceptedVaultedCards(customer, ref(NormalizedSubscriptionStatus.ACTIVE)).size());
	}

	@Test
	public void offersNoVaultedCardToAPlatformThatDoesNotTakeThem() throws Exception
	{
		when(connector.capabilities()).thenReturn(new ConnectorCapabilities(false, true, false, true, true,
				TokenImportStyle.SLASH_JOINED,
				new PaymentMethodChangeSupport(PaymentMethodChangeScope.SUBSCRIPTION,
						Set.of(PaymentMethodSource.ALREADY_ON_PLATFORM)),
				PaymentMethodEnrollmentSupport.NONE));
		givenVault(card("card-a", null));

		assertTrue(service.getAcceptedVaultedCards(customer, ref(NormalizedSubscriptionStatus.ACTIVE)).isEmpty());
	}

	/** The vault read is the one of the customer the caller names, never whoever is in session. */
	@Test
	public void readsTheVaultOfTheCustomerItIsAskedAbout() throws Exception
	{
		givenConnectorAcceptingVaultedCards(false);
		givenVault(card("card-a", null));
		final CustomerModel somebodyElse = mock(CustomerModel.class);

		assertTrue(service.getAcceptedVaultedCards(somebodyElse, ref(NormalizedSubscriptionStatus.ACTIVE)).isEmpty());
		assertEquals(1, service.getAcceptedVaultedCards(customer, ref(NormalizedSubscriptionStatus.ACTIVE)).size());
	}

	/**
	 * An outage tells nothing about whose card it is: it must not surface as "not your card", which is the
	 * answer for a vault that was read and does not contain the card.
	 */
	@Test(expected = RetryableBillingException.class)
	public void anOutageIsNotMistakenForACardThatIsNotTheShoppers() throws Exception
	{
		givenVaultUnreadable();

		service.ownedCard(customer, "card-a");
	}

	@Test(expected = PaymentMethodNotOwnedException.class)
	public void refusesACardThatIsNotInAVaultThatWasRead() throws Exception
	{
		givenVault(card("card-a", null));

		service.ownedCard(customer, "card-b");
	}

	@Test
	public void acceptsACardThatIsInTheVault() throws Exception
	{
		givenVault(card("card-a", null), card("card-b", null));

		assertEquals("card-b", service.ownedCard(customer, "card-b").getId());
	}

	/** Showing the page needs no vault, so an outage withholds the vaulted cards instead of failing it. */
	@Test
	public void withholdsVaultedCardsWhenTheVaultCannotBeRead() throws Exception
	{
		givenConnectorAcceptingVaultedCards(false);
		givenVaultUnreadable();

		assertTrue(service.getAcceptedVaultedCards(customer, ref(NormalizedSubscriptionStatus.ACTIVE)).isEmpty());
	}

	private void givenVaultUnreadable() throws Exception
	{
		when(storedCardsService.getStoredCardsOrThrow(customer))
				.thenThrow(new AdyenStoredCardsUnavailableException("Adyen is unreachable", new IOException("timeout")));
	}

	private void givenConnectorAcceptingVaultedCards(final boolean requiresNetworkTransactionId)
	{
		when(connector.capabilities()).thenReturn(new ConnectorCapabilities(requiresNetworkTransactionId, true,
				false, true, true, TokenImportStyle.SLASH_JOINED,
				new PaymentMethodChangeSupport(PaymentMethodChangeScope.CUSTOMER,
						Set.of(PaymentMethodSource.ADYEN_VAULTED_TOKEN)),
				PaymentMethodEnrollmentSupport.NONE));
	}

	private static StoredPaymentMethodResource card(final String id, final String networkTxReference)
	{
		final StoredPaymentMethodResource card = new StoredPaymentMethodResource();
		card.setId(id);
		card.setLastFour("1111");
		card.setNetworkTxReference(networkTxReference);
		return card;
	}

	private void givenVault(final StoredPaymentMethodResource... cards) throws Exception
	{
		when(storedCardsService.getStoredCardsOrThrow(customer)).thenReturn(List.of(cards));
	}

	private BillingSubscriptionRefModel ref(final NormalizedSubscriptionStatus status)
	{
		final BillingSubscriptionRefModel ref = mock(BillingSubscriptionRefModel.class);
		when(ref.getCode()).thenReturn("code-1");
		when(ref.getPlatform()).thenReturn(PLATFORM);
		when(ref.getStatus()).thenReturn(status.name());
		when(ref.getPlatformUpdatedAt()).thenReturn(new Date());
		final OrderModel order = mock(OrderModel.class);
		when(order.getStore()).thenReturn(store);
		when(ref.getOrder()).thenReturn(order);
		return ref;
	}
}
