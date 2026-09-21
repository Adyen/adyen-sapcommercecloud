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
package com.adyen.commerce.connector.facades.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.ConnectorCapabilities;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentSupport;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentEffect;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.dto.PaymentMethodChangeOutcome;
import com.adyen.commerce.connector.dto.PaymentMethodChangeSupport;
import com.adyen.commerce.connector.dto.PaymentMethodSource;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;
import com.adyen.commerce.connector.dto.TokenImportStyle;
import com.adyen.commerce.connector.dto.SubscriptionCancellation;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeResult;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeReport;
import com.adyen.commerce.connector.facades.data.SubscriptionDisplayState;
import com.adyen.commerce.connector.facades.data.SubscriptionOverviewData;
import com.adyen.commerce.connector.facades.data.SubscriptionEntryData;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.service.SubscriptionBillingService;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.connector.token.AdyenTokenHandleFactory;
import com.adyen.commerce.facades.AdyenStoredCardsFacade;
import com.adyen.model.checkout.StoredPaymentMethodResource;
import com.adyen.v6.dto.StoredCardsPageData;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.product.ProductService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * What the shopper is told, and what they are allowed to do about it.
 */
@UnitTest
public class DefaultMySubscriptionsFacadeTest
{
	@Mock
	private UserService userService;
	@Mock
	private FlexibleSearchService flexibleSearchService;
	@Mock
	private ProductService productService;
	@Mock
	private SubscriptionBillingService subscriptionBillingService;
	@Mock
	private SessionService sessionService;
	@Mock
	private BaseSiteService baseSiteService;
	@Mock
	private CustomerModel customer;
	@Mock
	private AdyenStoredCardsFacade storedCardsFacade;
	@Mock
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	@Mock
	private AdyenTokenHandleFactory tokenHandleFactory;
	@Mock
	private SubscriptionBillingConnector connector;

	private DefaultMySubscriptionsFacade facade;
	/** The storefront every row belongs to unless a test deliberately puts one somewhere else. */
	private BaseStoreModel theStore;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);

		theStore = mock(BaseStoreModel.class);
		facade = new DefaultMySubscriptionsFacade();
		facade.setUserService(userService);
		facade.setFlexibleSearchService(flexibleSearchService);
		facade.setProductService(productService);
		facade.setSubscriptionBillingService(subscriptionBillingService);
		facade.setSessionService(sessionService);
		facade.setBaseSiteService(baseSiteService);
		facade.setStoredCardsFacade(storedCardsFacade);
		facade.setConnectorRegistry(connectorRegistry);
		facade.setTokenHandleFactory(tokenHandleFactory);

		when(userService.getCurrentUser()).thenReturn(customer);
		when(userService.isAnonymousUser(customer)).thenReturn(false);

		// Returns what the body returned, as the real session service does; the facade reads that result.
		when(sessionService.executeInLocalViewWithParams(any(), any(SessionExecutionBody.class)))
				.thenAnswer(invocation -> invocation.<SessionExecutionBody> getArgument(1).execute());
	}

	// --- the state table ---

	/**
	 * Whether any platform has confirmed the row is asked before the status. Only a reconciliation writes
	 * platformUpdatedAt, so it is the one dependable signal: lastSyncedAt is cleared to hurry the sweep along
	 * after a cancellation whose follow-up read failed, and means nothing here.
	 */
	@Test
	public void saysNothingAboutASubscriptionNoPlatformHasConfirmedYet()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getPlatformUpdatedAt()).thenReturn(null);

		assertEquals(SubscriptionDisplayState.SETTING_UP, facade.displayState(ref));
		assertNull(facade.effectiveDate(ref, SubscriptionDisplayState.SETTING_UP));
		assertFalse(SubscriptionDisplayState.SETTING_UP.isCancellable());
	}

	@Test
	public void anActiveSubscriptionRenewsAndCanBeStopped()
	{
		assertEquals(SubscriptionDisplayState.ACTIVE, facade.displayState(ref(NormalizedSubscriptionStatus.ACTIVE)));
		assertTrue(SubscriptionDisplayState.ACTIVE.isCancellable());
	}

	/**
	 * Both adapters keep a stopped subscription ACTIVE until its term runs out, and say so only through
	 * cancelAtPeriodEnd.
	 */
	@Test
	public void anActiveSubscriptionAlreadyStoppedIsShownAsEndingAndOffersNoButton()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getCancelAtPeriodEnd()).thenReturn(Boolean.TRUE);

		assertEquals(SubscriptionDisplayState.ENDING, facade.displayState(ref));
		assertFalse(SubscriptionDisplayState.ENDING.isCancellable());
	}

	/**
	 * Dunning outranks the pending end on both adapters, so a shopper who has already stopped their
	 * subscription must not be told their provider will retry.
	 */
	@Test
	public void anUnpaidInvoiceOnAnAlreadyStoppedSubscriptionIsItsOwnState()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.PAST_DUE);
		when(ref.getCancelAtPeriodEnd()).thenReturn(Boolean.TRUE);

		assertEquals(SubscriptionDisplayState.PAST_DUE_ENDING, facade.displayState(ref));
	}

	/** The state shoppers most often want to leave from, so it keeps its button. */
	@Test
	public void aPaymentProblemStillLetsTheShopperStopTheSubscription()
	{
		assertEquals(SubscriptionDisplayState.PAST_DUE, facade.displayState(ref(NormalizedSubscriptionStatus.PAST_DUE)));
		assertTrue(SubscriptionDisplayState.PAST_DUE.isCancellable());
	}

	@Test
	public void aFutureDatedSubscriptionSaysWhenItStarts()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.PENDING);
		final Date tomorrow = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
		when(ref.getCurrentPeriodStart()).thenReturn(tomorrow);

		assertEquals(SubscriptionDisplayState.STARTING_SOON, facade.displayState(ref));
		assertEquals(tomorrow, facade.effectiveDate(ref, SubscriptionDisplayState.STARTING_SOON));
	}

	@Test
	public void aPendingSubscriptionWithNoStartDateIsStillJustBeingSetUp()
	{
		assertEquals(SubscriptionDisplayState.SETTING_UP,
				facade.displayState(ref(NormalizedSubscriptionStatus.PENDING)));
	}

	@Test
	public void anEndedSubscriptionSaysSoWhicheverWordThePlatformUsed()
	{
		assertEquals(SubscriptionDisplayState.ENDED, facade.displayState(ref(NormalizedSubscriptionStatus.EXPIRED)));
		assertEquals(SubscriptionDisplayState.ENDED, facade.displayState(ref(NormalizedSubscriptionStatus.CANCELLED)));
		assertEquals(SubscriptionDisplayState.ENDED, facade.displayState(ref(NormalizedSubscriptionStatus.FAILED)));
	}

	/**
	 * The status column is a plain string, so it can hold a value outside this vocabulary; guessing at it
	 * would put a wrong word in front of a shopper.
	 */
	@Test
	public void anUnrecognisedStatusLiteralSaysNothingAndOffersNothing()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getStatus()).thenReturn("transferred");

		assertEquals(SubscriptionDisplayState.UNAVAILABLE, facade.displayState(ref));
		assertFalse(SubscriptionDisplayState.UNAVAILABLE.isCancellable());
	}

	/**
	 * Without the originating store there are no credentials with which to reach the platform, so the row
	 * offers nothing. It still appears and is still described, because it is billing somebody and its status
	 * is known.
	 */
	@Test
	public void aSubscriptionWhoseStoreCannotBeDeterminedIsDescribedButNotActedOn()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getOrder()).thenReturn(null);

		assertEquals(SubscriptionDisplayState.ACTIVE, facade.displayState(ref));
		assertFalse(facade.toEntry(ref).isCancellable());
	}

	/**
	 * A reference carries no public identifier until essential data has minted one, and a button posting an
	 * empty code could only be refused.
	 */
	@Test
	public void offersNoButtonForARowThatHasNoPublicIdentifierYet()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getCode()).thenReturn(null);

		final SubscriptionEntryData entry = facade.toEntry(ref);

		assertEquals(SubscriptionDisplayState.ACTIVE, entry.getState());
		assertFalse("a row with no code cannot be acted on, so it must not look as though it can",
				entry.isCancellable());
	}

	// --- ownership and cancelling ---

	@Test
	public void cancellingAsksForTheEndOfThePaidPeriod() throws Exception
	{
		givenSingleResult(ref(NormalizedSubscriptionStatus.ACTIVE));

		assertTrue(facade.cancelForCurrentCustomer("code-1"));

		verify(subscriptionBillingService).cancel(any(), any(SubscriptionCancellation.class));
	}

	/**
	 * The lookup names the customer in the query rather than comparing after loading, so another customer's
	 * code matches nothing at all.
	 */
	@Test
	public void refusesACodeThatIsNotThisCustomersWithoutSayingWhy() throws Exception
	{
		givenNoResults();

		assertFalse(facade.cancelForCurrentCustomer("someone-elses-code"));

		verify(subscriptionBillingService, never()).cancel(any(), any());
	}

	/**
	 * The same lookup guards the hosted page, and it has to: on Recurly that address signs its holder into
	 * the account, so a code belonging to somebody else must yield nothing rather than a link.
	 */
	@Test
	public void mintsNoHostedPageAddressForACodeThatIsNotThisCustomers() throws Exception
	{
		givenNoResults();

		assertNull(facade.paymentMethodEnrollmentUrlForCurrentCustomer("someone-elses-code"));

		verify(subscriptionBillingService, never()).paymentMethodEnrollmentPage(any());
	}

	/** A platform hosting no such page is not asked for an address. */
	@Test
	public void mintsNoHostedPageAddressWhenTheServiceOffersNone() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(subscriptionBillingService.paymentMethodEnrollmentPage(any())).thenReturn(Optional.empty());

		assertNull(facade.paymentMethodEnrollmentUrlForCurrentCustomer("code-1"));
	}

	/**
	 * Rendering the list decides only whether to invite the shopper, from declared capability; it never
	 * mints the address, which on Recurly would be a credential minted for somebody who has not clicked.
	 */
	@Test
	public void namesTheRowAndTheEffectWithoutMintingAnAddress() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(connector.capabilities()).thenReturn(new ConnectorCapabilities(false, true, false, true, true,
				TokenImportStyle.SLASH_JOINED, PaymentMethodChangeSupport.NONE,
				new PaymentMethodEnrollmentSupport(PaymentMethodEnrollmentEffect.REPLACES_METHOD_ON_FILE)));

		final SubscriptionOverviewData overview = facade.getSubscriptionsForCurrentCustomer();

		assertEquals("code-1", overview.getPaymentMethodEnrollmentSubscriptionCode());
		assertEquals(PaymentMethodEnrollmentEffect.REPLACES_METHOD_ON_FILE,
				overview.getPaymentMethodEnrollmentEffect());
		verify(subscriptionBillingService, never()).paymentMethodEnrollmentPage(any());
	}

	@Test
	public void putsOneCardBehindEverySubscriptionOnTheSamePlatformAndAccount() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenAPinningPlatform();
		givenSiblings(pinnedRef("sub-1", "cb-customer-1"), pinnedRef("sub-2", "cb-customer-1"),
				pinnedRef("sub-3", "cb-customer-1"));

		final PaymentMethodChangeReport report =
				facade.changePaymentMethodForAllSubscriptions("code-1", "billing-9");

		assertEquals(3, report.moved());
		assertEquals(0, report.failed());
		assertEquals(PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS, report.result());
		verify(subscriptionBillingService, times(3)).changePaymentMethod(any(), any());
	}

	/** A payment method identifier means nothing under another billing account of the same shopper. */
	@Test
	public void leavesSubscriptionsUnderAnotherBillingAccountAlone() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenAPinningPlatform();
		givenSiblings(pinnedRef("sub-1", "cb-customer-1"), pinnedRef("sub-2", "cb-customer-OTHER"));

		final PaymentMethodChangeReport report =
				facade.changePaymentMethodForAllSubscriptions("code-1", "billing-9");

		assertEquals(1, report.moved());
		verify(subscriptionBillingService, times(1)).changePaymentMethod(any(), any());
	}

	/**
	 * Each subscription is its own call, so one refusing says nothing about the others. The counts are
	 * what the page needs in order to avoid calling a half-done fan-out a success.
	 */
	@Test
	public void reportsCountsWhenOnlySomeOfThemMove() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenAPinningPlatform();
		givenSiblings(pinnedRef("sub-1", "cb-customer-1"), pinnedRef("sub-2", "cb-customer-1"));
		when(subscriptionBillingService.changePaymentMethod(any(), any()))
				.thenReturn(new PaymentMethodChangeOutcome(
						new BillingPaymentMethodRef(BillingPlatform.CHARGEBEE, "billing-9"),
						PaymentMethodChangeScope.SUBSCRIPTION))
				.thenThrow(new IllegalStateException("the platform said no"));

		final PaymentMethodChangeReport report =
				facade.changePaymentMethodForAllSubscriptions("code-1", "billing-9");

		assertEquals(1, report.moved());
		assertEquals(1, report.failed());
		assertTrue(report.isPartial());
		// Not reported as a completed fan-out: something was left behind.
		assertEquals(PaymentMethodChangeResult.CHANGED_THIS_SUBSCRIPTION, report.result());
	}

	/** Nothing to fan out where the platform already moves every subscription of the customer. */
	@Test
	public void doesNotFanOutWhenTheChangeWasAlreadyCustomerScoped() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenVaultHolding("card-mine");

		final PaymentMethodChangeReport report =
				facade.changePaymentMethodForAllSubscriptions("code-1", "card-mine");

		assertEquals(PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS, report.result());
		verify(subscriptionBillingService, times(1)).changePaymentMethod(any(), any());
	}

	/** A refused first call fans out to nothing, so a failure cannot move anybody else's billing. */
	@Test
	public void fansOutNothingWhenTheNamedSubscriptionWasRefused() throws Exception
	{
		givenNoResults();

		final PaymentMethodChangeReport report =
				facade.changePaymentMethodForAllSubscriptions("someone-elses-code", "card-mine");

		assertEquals(PaymentMethodChangeResult.FAILED, report.result());
		assertEquals(0, report.moved());
		verify(subscriptionBillingService, never()).changePaymentMethod(any(), any());
	}

	/**
	 * A platform that pins a method per subscription: the scope the fan-out exists for. The option the
	 * shopper posts has to be one the platform lists, since that is what choiceFor validates against.
	 */
	private void givenAPinningPlatform() throws Exception
	{
		when(connector.capabilities()).thenReturn(capabilities(PaymentMethodChangeScope.SUBSCRIPTION));
		when(subscriptionBillingService.listPaymentMethods(any()))
				.thenReturn(List.of(new PlatformPaymentMethod("billing-9", "Visa 4242", null, false)));
		when(subscriptionBillingService.changePaymentMethod(any(), any()))
				.thenReturn(new PaymentMethodChangeOutcome(
						new BillingPaymentMethodRef(BillingPlatform.CHARGEBEE, "billing-9"),
						PaymentMethodChangeScope.SUBSCRIPTION));
	}

	/** Puts a row in a given store, which is what decides whether two rows share a platform account. */
	private BillingSubscriptionRefModel inStore(final BillingSubscriptionRefModel ref,
			final BaseStoreModel store)
	{
		final OrderModel order = mock(OrderModel.class);
		when(order.getStore()).thenReturn(store);
		when(ref.getOrder()).thenReturn(order);
		return ref;
	}

	/**
	 * The customer reference is minted from the shopper's global SAP id, so it is the same string on every
	 * site of a platform. Only the store tells two platform accounts apart, and sending a card into the
	 * wrong one would use another store's credentials.
	 */
	@Test
	public void leavesSubscriptionsSoldByAnotherStoreAlone() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenAPinningPlatform();
		givenSiblings(pinnedRef("sub-1", "cb-customer-1"),
				inStore(pinnedRef("sub-2", "cb-customer-1"), mock(BaseStoreModel.class)));

		final PaymentMethodChangeReport report =
				facade.changePaymentMethodForAllSubscriptions("code-1", "billing-9");

		// Not a failure either: a row the chosen card could never reach is not something to report.
		assertEquals(1, report.moved());
		assertEquals(0, report.failed());
		verify(subscriptionBillingService, times(1)).changePaymentMethod(any(), any());
	}

	@Test
	public void offersToShareOneCardOnlyWhenAnotherRowWouldActuallyMove() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenAPinningPlatform();
		givenSiblings(pinnedRef("sub-1", "cb-customer-1"));

		assertFalse(facade.getSubscriptionsForCurrentCustomer().getSubscriptions().get(0)
				.isPaymentMethodShareable());

		givenSiblings(pinnedRef("sub-1", "cb-customer-1"), pinnedRef("sub-2", "cb-customer-1"));

		assertTrue(facade.getSubscriptionsForCurrentCustomer().getSubscriptions().get(0)
				.isPaymentMethodShareable());
	}

	/** Nothing to share where the platform already moves every subscription of the customer at once. */
	@Test
	public void doesNotOfferToShareOneCardOnACustomerScopedPlatform() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenSiblings(pinnedRef("sub-1", "cb-customer-1"), pinnedRef("sub-2", "cb-customer-1"));

		assertFalse(facade.getSubscriptionsForCurrentCustomer().getSubscriptions().get(0)
				.isPaymentMethodShareable());
	}

	private BillingSubscriptionRefModel pinnedRef(final String externalId, final String externalCustomerId)
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getPlatform()).thenReturn(BillingPlatform.CHARGEBEE);
		when(ref.getExternalSubscriptionId()).thenReturn(externalId);
		when(ref.getExternalCustomerId()).thenReturn(externalCustomerId);
		return ref;
	}

	/**
	 * The first entry answers findOwnSubscription, which takes the head of the result; the rest are the
	 * siblings the fan-out has to find for itself.
	 */
	@SuppressWarnings("unchecked")
	private void givenSiblings(final BillingSubscriptionRefModel... refs)
	{
		final SearchResult<BillingSubscriptionRefModel> result = mock(SearchResult.class);
		when(result.getResult()).thenReturn(List.of(refs));
		final SearchResult<BillingSubscriptionRefModel> none = mock(SearchResult.class);
		when(none.getResult()).thenReturn(List.of());
		when(flexibleSearchService.<BillingSubscriptionRefModel> search(any(FlexibleSearchQuery.class)))
				.thenAnswer(invocation -> {
					final String asked = ((FlexibleSearchQuery) invocation.getArgument(0)).getQuery();
					return asked == null || asked.contains("{BillingSubscriptionRef}") ? result : none;
				});
	}

	/** The state is re-derived from the row rather than trusted from a form that may be minutes old. */
	@Test
	public void refusesToCancelSomethingThatIsNoLongerInACancellableState() throws Exception
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getCancelAtPeriodEnd()).thenReturn(Boolean.TRUE);
		givenSingleResult(ref);

		assertFalse(facade.cancelForCurrentCustomer("code-1"));

		verify(subscriptionBillingService, never()).cancel(any(), any());
	}

	@Test
	public void anAnonymousVisitorSeesAnEmptyPageRatherThanAnError()
	{
		final UserModel anonymous = mock(UserModel.class);
		when(userService.getCurrentUser()).thenReturn(anonymous);
		when(userService.isAnonymousUser(anonymous)).thenReturn(true);

		assertTrue(facade.getSubscriptionsForCurrentCustomer().isEmpty());
		assertFalse(facade.cancelForCurrentCustomer("code-1"));
	}

	// --- naming ---

	/**
	 * A subscription outlives the catalogue entry it was sold from, and getProductForCode throws rather than
	 * returning null when the product is gone — so the fallback has to be in a catch.
	 */
	@Test
	public void fallsBackToTheProductCodeWhenTheProductIsNoLongerInTheCatalogue()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getProductCode()).thenReturn("SUB-1");
		when(productService.getProductForCode("SUB-1")).thenThrow(new IllegalArgumentException("gone"));

		assertEquals("SUB-1", facade.displayName(ref));
	}

	@Test
	public void neverShowsThePlatformsPlanCodeAsAName()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getProductCode()).thenReturn(null);
		when(ref.getPlanCode()).thenReturn("test-subscription-plan-EUR-Monthly");

		assertNull(facade.displayName(ref));
	}

	// --- changing the payment method ---

	/**
	 * The token id is a request parameter and the select on the page is a convenience rather than a control,
	 * so ownership is checked here against the cards this shopper holds. Adyen refusing to retrieve somebody
	 * else's token is a property of one platform's import call, not a promise every connector makes.
	 */
	@Test
	public void refusesAPaymentMethodThatIsNotOnThisShoppersVaultListing() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenVaultHolding("card-mine");

		assertEquals(PaymentMethodChangeResult.FAILED,
				facade.changePaymentMethodForCurrentCustomer("code-1", "card-somebody-elses"));

		verify(subscriptionBillingService, never()).changePaymentMethod(any(), any());
	}

	/** The same listing, read successfully, containing the card: the one case that goes through. */
	@Test
	public void acceptsAPaymentMethodTheShopperActuallyHolds() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenVaultHolding("card-mine");

		// The wording the shopper reads follows what the platform reported, not what was advertised.
		assertEquals(PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS,
				facade.changePaymentMethodForCurrentCustomer("code-1", "card-mine"));

		verify(subscriptionBillingService).changePaymentMethod(any(), any());
	}

	/** A vault listing that could not be read says nothing about ownership, so the change cannot proceed. */
	@Test
	public void refusesWhenTheVaultListingCannotBeRead() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(storedCardsFacade.getStoredCardsPageDataForCurrentCustomer())
				.thenThrow(new IllegalStateException("Adyen is unreachable"));

		assertEquals(PaymentMethodChangeResult.FAILED,
				facade.changePaymentMethodForCurrentCustomer("code-1", "card-mine"));

		verify(subscriptionBillingService, never()).changePaymentMethod(any(), any());
	}

	/**
	 * Re-derived from the row rather than trusted from the form, as the cancellation is: a page rendered
	 * minutes ago can still offer a control for a subscription that has since ended.
	 */
	@Test
	public void refusesToChangeThePaymentMethodOfASubscriptionThatHasEnded() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.EXPIRED);
		givenVaultHolding("card-mine");

		assertEquals(PaymentMethodChangeResult.FAILED,
				facade.changePaymentMethodForCurrentCustomer("code-1", "card-mine"));

		verify(subscriptionBillingService, never()).changePaymentMethod(any(), any());
	}

	/** Past due is the state shoppers most often want to fix, so the change is offered there. */
	@Test
	public void offersThePaymentMethodChangeOnASubscriptionThatIsPastDue() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.PAST_DUE);
		givenVaultHolding("card-mine");

		assertEquals(PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS,
				facade.changePaymentMethodForCurrentCustomer("code-1", "card-mine"));

		verify(subscriptionBillingService).changePaymentMethod(any(), any());
	}

	/**
	 * A platform that cannot do this is answered with its own result, which the page turns into a sentence
	 * rather than into "please try again". No platform name travels on this path.
	 */
	@Test
	public void tellsTheShopperWhenThePlatformCannotChangeThePaymentMethodAtAll() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(connector.capabilities()).thenReturn(capabilities(PaymentMethodChangeScope.NOT_SUPPORTED));
		givenVaultHolding("card-mine");

		assertEquals(PaymentMethodChangeResult.NOT_SUPPORTED_HERE,
				facade.changePaymentMethodForCurrentCustomer("code-1", "card-mine"));

		verify(subscriptionBillingService, never()).changePaymentMethod(any(), any());
	}

	/** And the page does not offer a control it knows cannot work. */
	@Test
	public void doesNotOfferTheChangeWhenNoSubscriptionOnThePageSupportsIt() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(connector.capabilities()).thenReturn(capabilities(PaymentMethodChangeScope.NOT_SUPPORTED));

		final SubscriptionOverviewData overview = offerFor(findTheOnlySubscription());

		assertNull(overview.getPaymentMethodSubscriptionCode());
		assertEquals(PaymentMethodChangeScope.NOT_SUPPORTED, overview.getPaymentMethodChangeScope());
		assertFalse(overview.isAnyPaymentMethodChangeable());
	}

	/**
	 * The row bills normally and its platform supports the change, but with no public identifier the form
	 * cannot be built, so the control is withheld rather than offered and refused on submission.
	 */
	@Test
	public void doesNotOfferTheChangeForARowWithNoPublicCode() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(findTheOnlySubscription().getCode()).thenReturn(null);

		final SubscriptionOverviewData overview = offerFor(findTheOnlySubscription());

		assertNull(overview.getPaymentMethodSubscriptionCode());
		assertFalse(overview.isAnyPaymentMethodChangeable());
	}

	/**
	 * A platform that pins the method to one subscription gets its control in the row, so there is nothing
	 * above the list — and that emptiness must not be read as the change being unavailable.
	 */
	@Test
	public void putsTheControlInTheRowWithoutClaimingTheChangeIsUnavailable() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(connector.capabilities()).thenReturn(capabilities(PaymentMethodChangeScope.SUBSCRIPTION));

		final SubscriptionEntryData entry = facade.toEntry(findTheOnlySubscription());
		final SubscriptionOverviewData overview = offerFor(findTheOnlySubscription());

		assertTrue(entry.isPaymentMethodChangeable());
		assertEquals(PaymentMethodChangeScope.SUBSCRIPTION, entry.getPaymentMethodChangeScope());
		assertTrue(overview.isAnyPaymentMethodChangeable());
		// No control above the list: it would move only this one row while looking like it governed the page.
		assertNull(overview.getPaymentMethodSubscriptionCode());
	}

	/** The customer-scoped case keeps its single control above the list. */
	@Test
	public void keepsOneControlAboveTheListForACustomerScopedPlatform() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);

		final SubscriptionOverviewData overview = offerFor(findTheOnlySubscription());

		assertTrue(overview.isAnyPaymentMethodChangeable());
		assertEquals(PaymentMethodChangeScope.CUSTOMER, overview.getPaymentMethodChangeScope());
		assertEquals("code-1", overview.getPaymentMethodSubscriptionCode());
	}

	/**
	 * A row on a platform that cannot change its card can sit in a list under a control that can, so it
	 * carries that fact itself.
	 */
	@Test
	public void marksTheRowWhosePlatformCannotChangeItsCard() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(connector.capabilities()).thenReturn(capabilities(PaymentMethodChangeScope.NOT_SUPPORTED));

		final SubscriptionEntryData entry = facade.toEntry(findTheOnlySubscription());

		assertFalse(entry.isPaymentMethodChangeable());
		assertEquals(PaymentMethodChangeScope.NOT_SUPPORTED, entry.getPaymentMethodChangeScope());
		// The state still allows it, which is what marks this row as an exception worth explaining rather
		// than one that simply has nothing left to bill.
		assertTrue(entry.getState().isPaymentMethodChangeable());
	}

	/**
	 * Two subscriptions on one customer-scoped platform. The codeless one cannot be named in a form, yet the
	 * control above the list changes the customer's payment source and moves it anyway, so "cannot be named"
	 * must not be reported to the shopper as "will not be changed".
	 */
	@Test
	public void doesNotSingleOutACodelessRowThatThePageControlChangesAnyway() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		final SubscriptionEntryData named = facade.toEntry(chargebeeRef("code-a"));
		final SubscriptionEntryData codeless = facade.toEntry(chargebeeRef(null));

		final SubscriptionOverviewData overview = offerFor(named, codeless);

		assertEquals("code-a", overview.getPaymentMethodSubscriptionCode());
		assertFalse(codeless.isPaymentMethodChangeable());
		// Still moved by the control above the list, so the page must say nothing about it.
		assertTrue(codeless.isPaymentMethodChangeCovered());
	}

	/** With no control above the list, the same codeless row is genuinely left out and must say so. */
	@Test
	public void singlesOutACodelessRowWhenNoControlCoversIt() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		final SubscriptionEntryData codeless = facade.toEntry(chargebeeRef(null));

		offerFor(codeless);

		assertFalse(codeless.isPaymentMethodChangeCovered());
	}

	/**
	 * A customer-scoped control does not reach across platforms, so a row on a platform that cannot do the
	 * change at all is uncovered even when the page offers one.
	 */
	@Test
	public void doesNotClaimToCoverARowOnAPlatformThatCannotChangeItsCard() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		final SubscriptionEntryData chargebee = facade.toEntry(chargebeeRef("code-a"));
		givenConnectorDeclaring(PaymentMethodChangeScope.NOT_SUPPORTED);
		final SubscriptionEntryData other = facade.toEntry(chargebeeRef("code-b"));

		final SubscriptionOverviewData overview = offerFor(chargebee, other);

		assertTrue(overview.isAnyPaymentMethodChangeable());
		assertTrue(chargebee.isPaymentMethodChangeCovered());
		assertFalse(other.isPaymentMethodChangeCovered());
	}

	/**
	 * A subscription not yet confirmed by its platform is not changeable today, but its provider is capable,
	 * so the page-wide "we can't change the card online" sentence must stay off.
	 */
	@Test
	public void doesNotCallAProviderIncapableWhenOnlyTodaysStateSaysNo() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		final BillingSubscriptionRefModel ref = chargebeeRef("code-a");
		when(ref.getPlatformUpdatedAt()).thenReturn(null);

		final SubscriptionOverviewData overview = offerFor(facade.toEntry(ref));

		assertEquals(SubscriptionDisplayState.SETTING_UP, overview.getSubscriptions().get(0).getState());
		assertFalse(overview.isAnyPaymentMethodChangeable());
		assertTrue(overview.isPaymentMethodChangeSupportedSomewhere());
	}

	/** A row whose platform cannot be reached is describable and unbuttoned, not undescribable. */
	@Test
	public void describesASubscriptionItCannotActOnInsteadOfCallingItUnknown() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		final BillingSubscriptionRefModel ref = chargebeeRef("code-a");
		when(ref.getOrder()).thenReturn(null);

		final SubscriptionEntryData entry = facade.toEntry(ref);

		assertEquals(SubscriptionDisplayState.ACTIVE, entry.getState());
		// Nothing is offered: every action needs the store the missing order would have named.
		assertFalse(entry.isManageable());
		assertFalse(entry.isCancellable());
		assertFalse(entry.isPaymentMethodChangeable());
	}

	/** And the POST path refuses it in its own right rather than failing somewhere deeper. */
	@Test
	public void refusesToActOnASubscriptionWithNoOriginatingStore() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		final BillingSubscriptionRefModel ref = chargebeeRef("code-1");
		when(ref.getOrder()).thenReturn(null);
		givenSingleResult(ref);
		givenVaultHolding("card-mine");

		assertFalse(facade.cancelForCurrentCustomer("code-1"));
		assertEquals(PaymentMethodChangeResult.FAILED,
				facade.changePaymentMethodForCurrentCustomer("code-1", "card-mine"));

		verify(subscriptionBillingService, never()).cancel(any(), any());
		verify(subscriptionBillingService, never()).changePaymentMethod(any(), any());
	}

	/**
	 * A platform's payment methods belong to the customer and not to a subscription, so every row on one
	 * platform shares a single lookup instead of repeating the same round trip per row.
	 */
	@Test
	public void asksThePlatformOnceForACustomerNoMatterHowManyRowsTheyHave() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.SUBSCRIPTION);
		when(subscriptionBillingService.listPaymentMethods(any())).thenReturn(List.of(
				new PlatformPaymentMethod("billing-1", "Visa 4242", null, true),
				new PlatformPaymentMethod("billing-2", "Visa 1881", null, false)));

		final Map<String, List<PlatformPaymentMethod>> cache = new java.util.HashMap<>();
		final SubscriptionEntryData first = facade.toEntry(chargebeeRef("code-a"), cache);
		final SubscriptionEntryData second = facade.toEntry(chargebeeRef("code-b"), cache);

		assertEquals(2, first.getPaymentMethodOptions().size());
		assertEquals(2, second.getPaymentMethodOptions().size());
		verify(subscriptionBillingService, times(1)).listPaymentMethods(any());
	}

	/** One option is not a choice but the instrument already billing, so no control is offered. */
	@Test
	public void offersNoRowControlWhenThePlatformHoldsOnlyOneMethod() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.SUBSCRIPTION);
		when(subscriptionBillingService.listPaymentMethods(any())).thenReturn(List.of(
				new PlatformPaymentMethod("billing-1", "Visa 4242", null, true)));

		final SubscriptionEntryData entry = facade.toEntry(chargebeeRef("code-a"));

		assertTrue(entry.isPaymentMethodChangeable());
		assertTrue(entry.getPaymentMethodOptions().isEmpty());
	}

	private void givenConnectorDeclaring(final PaymentMethodChangeScope scope) throws Exception
	{
		when(connectorRegistry.getConnector(BillingPlatform.CHARGEBEE)).thenReturn(connector);
		when(connector.capabilities()).thenReturn(capabilities(scope));
	}

	private BillingSubscriptionRefModel chargebeeRef(final String code)
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getPlatform()).thenReturn(BillingPlatform.CHARGEBEE);
		when(ref.getCode()).thenReturn(code);
		return ref;
	}

	/** Builds the page-level offer from one row, the way the overview does. */
	private SubscriptionOverviewData offerFor(final BillingSubscriptionRefModel ref)
	{
		return offerFor(facade.toEntry(ref));
	}

	private SubscriptionOverviewData offerFor(final SubscriptionEntryData... entries)
	{
		final SubscriptionOverviewData overview = new SubscriptionOverviewData();
		overview.setSubscriptions(List.of(entries));
		facade.applyPaymentMethodChangeOffer(overview);
		return overview;
	}

	/** The row the single-result search stub is returning, so a test can change its mind about it. */
	private BillingSubscriptionRefModel findTheOnlySubscription()
	{
		return flexibleSearchService.<BillingSubscriptionRefModel> search(mock(FlexibleSearchQuery.class))
				.getResult().get(0);
	}

	private void givenSubscriptionOnAPlatformThatSupportsTheChange(final NormalizedSubscriptionStatus status)
			throws Exception
	{
		final BillingSubscriptionRefModel ref = ref(status);
		when(ref.getPlatform()).thenReturn(BillingPlatform.CHARGEBEE);
		when(ref.getExternalCustomerId()).thenReturn("cb-customer-1");
		givenSingleResult(ref);
		when(connectorRegistry.getConnector(BillingPlatform.CHARGEBEE)).thenReturn(connector);
		when(connector.capabilities()).thenReturn(capabilities(PaymentMethodChangeScope.CUSTOMER));
		// A real handle rather than a mock: the request record validates its arguments.
		when(tokenHandleFactory.createForVaultedToken(any(), any(), any(), any(), any()))
				.thenReturn(new AdyenTokenHandle("MERCHANT", "shopper-1", "card-mine", null, null));
		when(subscriptionBillingService.changePaymentMethod(any(), any()))
				.thenReturn(new PaymentMethodChangeOutcome(
						new BillingPaymentMethodRef(BillingPlatform.CHARGEBEE, "pm_1"),
						PaymentMethodChangeScope.CUSTOMER));
	}

	private static ConnectorCapabilities capabilities(final PaymentMethodChangeScope scope)
	{
		// The source matches the scope: a connector that supports the change must name one it accepts, and
		// the record rejects any other combination.
		final PaymentMethodChangeSupport support = scope.isSupported()
				? new PaymentMethodChangeSupport(scope, Set.of(scope == PaymentMethodChangeScope.SUBSCRIPTION
						? PaymentMethodSource.ALREADY_ON_PLATFORM : PaymentMethodSource.ADYEN_VAULTED_TOKEN))
				: PaymentMethodChangeSupport.NONE;
		return new ConnectorCapabilities(false, true, false, true, true, TokenImportStyle.SLASH_JOINED,
				support, PaymentMethodEnrollmentSupport.NONE);
	}

	private void givenVaultHolding(final String... storedPaymentMethodIds)
	{
		final StoredCardsPageData page = mock(StoredCardsPageData.class);
		final List<StoredPaymentMethodResource> cards = new java.util.ArrayList<>();
		for (final String id : storedPaymentMethodIds)
		{
			final StoredPaymentMethodResource card = new StoredPaymentMethodResource();
			card.setId(id);
			cards.add(card);
		}
		when(page.getStoredCards()).thenReturn(cards);
		when(storedCardsFacade.getStoredCardsPageDataForCurrentCustomer()).thenReturn(page);
	}

	private BillingSubscriptionRefModel ref(final NormalizedSubscriptionStatus status)
	{
		final BillingSubscriptionRefModel ref = mock(BillingSubscriptionRefModel.class);
		when(ref.getCode()).thenReturn("code-1");
		when(ref.getStatus()).thenReturn(status.name());
		// Present by default: absent, the row lands in SETTING_UP, which is a state of its own.
		when(ref.getPlatformUpdatedAt()).thenReturn(new Date());
		// One store for every row unless a test says otherwise: the store is what selects the platform
		// account, so rows sharing a storefront must share it here too.
		return inStore(ref, theStore);
	}

	@SuppressWarnings("unchecked")
	/**
	 * Only the subscription query answers with the row. The list page also looks up orders stuck in
	 * activation, and handing that lookup a {@code BillingSubscriptionRef} would fail on the cast rather
	 * than on anything the test is about.
	 */
	private void givenSingleResult(final BillingSubscriptionRefModel ref)
	{
		final SearchResult<BillingSubscriptionRefModel> result = mock(SearchResult.class);
		when(result.getResult()).thenReturn(List.of(ref));
		final SearchResult<BillingSubscriptionRefModel> none = mock(SearchResult.class);
		when(none.getResult()).thenReturn(List.of());
		when(flexibleSearchService.<BillingSubscriptionRefModel> search(any(FlexibleSearchQuery.class)))
				.thenAnswer(invocation -> {
					// findTheOnlySubscription() asks with a mock carrying no query text at all; only a
					// query that names another type is answered with nothing.
					final String asked = ((FlexibleSearchQuery) invocation.getArgument(0)).getQuery();
					return asked == null || asked.contains("{BillingSubscriptionRef}") ? result : none;
				});
	}

	@SuppressWarnings("unchecked")
	private void givenNoResults()
	{
		final SearchResult<BillingSubscriptionRefModel> result = mock(SearchResult.class);
		when(result.getResult()).thenReturn(List.of());
		when(flexibleSearchService.<BillingSubscriptionRefModel> search(any(FlexibleSearchQuery.class)))
				.thenReturn(result);
	}
}
