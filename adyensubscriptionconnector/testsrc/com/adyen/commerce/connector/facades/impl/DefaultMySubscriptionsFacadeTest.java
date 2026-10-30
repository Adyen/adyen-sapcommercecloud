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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.context.SubscriptionStoreContext;
import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.dto.ConnectorCapabilities;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.dto.PaymentMethodChangeOutcome;
import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;
import com.adyen.commerce.connector.dto.PaymentMethodChangeSupport;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentEffect;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentSupport;
import com.adyen.commerce.connector.dto.PaymentMethodSource;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
import com.adyen.commerce.connector.dto.SubscriptionCancellation;
import com.adyen.commerce.connector.dto.TokenImportStyle;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeOfferData;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeReport;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeResult;
import com.adyen.commerce.connector.facades.data.PaymentMethodEnrollmentNotice;
import com.adyen.commerce.connector.facades.data.SubscriptionDisplayState;
import com.adyen.commerce.connector.facades.data.SubscriptionEntryData;
import com.adyen.commerce.connector.facades.data.SubscriptionOverviewData;
import com.adyen.commerce.connector.facades.data.SubscriptionPlatformSectionData;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.selfservice.impl.DefaultCustomerSubscriptionService;
import com.adyen.commerce.connector.service.SubscriptionBillingService;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.connector.token.AdyenTokenHandleFactory;
import com.adyen.commerce.services.AdyenStoredCardAuthorisationService;
import com.adyen.model.checkout.StoredPaymentMethodResource;
import com.adyen.v6.exceptions.AdyenStoredCardsUnavailableException;
import com.adyen.v6.service.AdyenStoredCardsService;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.product.ProductService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.impl.SearchResultImpl;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * What the shopper is told, per billing platform, and what they are allowed to do about it.
 *
 * <p>Runs the facade over the real {@link DefaultCustomerSubscriptionService}, with only its ports mocked:
 * the rules live in the service and the placement in the facade, and the page depends on both.</p>
 */
@UnitTest
public class DefaultMySubscriptionsFacadeTest
{
	private static final BillingPlatform CHARGEBEE = BillingPlatform.valueOf("CHARGEBEE");
	private static final BillingPlatform RECURLY = BillingPlatform.valueOf("RECURLY");

	@Mock
	private UserService userService;
	@Mock
	private FlexibleSearchService flexibleSearchService;
	@Mock
	private ProductService productService;
	@Mock
	private EnumerationService enumerationService;
	@Mock
	private SubscriptionBillingService subscriptionBillingService;
	@Mock
	private SubscriptionStoreContext storeContext;
	@Mock
	private CustomerModel customer;
	@Mock
	private AdyenStoredCardsService storedCardsService;
	@Mock
	private AdyenStoredCardAuthorisationService storedCardAuthorisationService;
	@Mock
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	@Mock
	private AdyenTokenHandleFactory tokenHandleFactory;
	@Mock
	private SubscriptionBillingConnector connector;

	private DefaultMySubscriptionsFacade facade;
	/** The storefront every row belongs to unless a test deliberately puts one somewhere else. */
	private BaseStoreModel theStore;
	private List<BillingSubscriptionRefModel> subscriptions = List.of();
	private List<BillingActivationAttemptModel> abandoned = List.of();

	@Before
	public void setUp() throws Exception
	{
		MockitoAnnotations.openMocks(this);

		theStore = store("store-1");
		final DefaultCustomerSubscriptionService service = new DefaultCustomerSubscriptionService();
		service.setFlexibleSearchService(flexibleSearchService);
		service.setSubscriptionBillingService(subscriptionBillingService);
		service.setConnectorRegistry(connectorRegistry);
		service.setStoreContext(storeContext);
		service.setTokenHandleFactory(tokenHandleFactory);
		service.setStoredCardsService(storedCardsService);
		service.setStoredCardAuthorisationService(storedCardAuthorisationService);

		facade = new DefaultMySubscriptionsFacade();
		facade.setUserService(userService);
		facade.setProductService(productService);
		facade.setEnumerationService(enumerationService);
		facade.setCustomerSubscriptionService(service);

		when(userService.getCurrentUser()).thenReturn(customer);
		when(userService.isAnonymousUser(customer)).thenReturn(false);
		givenLoadedPlatforms(CHARGEBEE);
		when(connectorRegistry.getConnector(any())).thenReturn(connector);
		when(connector.capabilities()).thenReturn(capabilities(PaymentMethodChangeScope.NOT_SUPPORTED));

		// The store comes from the originating order and a call without one is refused, as the real context does.
		when(storeContext.storeOf(any())).thenAnswer(invocation -> storeOf(invocation.getArgument(0)));
		when(storeContext.callInStoreOf(any(), any())).thenAnswer(invocation -> {
			final BaseStoreModel store = storeOf(invocation.getArgument(0));
			if (store == null)
			{
				throw new PreconditionFailedException("no originating store");
			}
			return invocation.<SubscriptionStoreContext.StoreBoundWork<Object>> getArgument(1).call(store);
		});
		stubSearches();
	}

	// --- sections ---

	@Test
	public void givesEachLoadedPlatformItsOwnSectionInRegistrationOrder()
	{
		givenLoadedPlatforms(RECURLY, CHARGEBEE);
		givenSubscriptions(ref("cb-1", CHARGEBEE), ref("rc-1", RECURLY), ref("cb-2", CHARGEBEE));

		final List<SubscriptionPlatformSectionData> sections = facade.getSubscriptionsForCurrentCustomer().getSections();

		assertEquals(2, sections.size());
		assertEquals("RECURLY", sections.get(0).getPlatformCode());
		assertEquals(List.of("rc-1"), codes(sections.get(0)));
		assertEquals("CHARGEBEE", sections.get(1).getPlatformCode());
		assertEquals(List.of("cb-1", "cb-2"), codes(sections.get(1)));
	}

	/** A connector that is not loaded can act on nothing, so its subscriptions are not listed at all. */
	@Test
	public void listsNothingOnAPlatformWhoseConnectorIsNotLoaded()
	{
		givenLoadedPlatforms(CHARGEBEE);
		givenSubscriptions(ref("rc-1", RECURLY));

		assertTrue(facade.getSubscriptionsForCurrentCustomer().isEmpty());
	}

	@Test
	public void givesNoSectionToAPlatformTheShopperHasNothingOn()
	{
		givenLoadedPlatforms(CHARGEBEE, RECURLY);
		givenSubscriptions(ref("cb-1", CHARGEBEE));

		final List<SubscriptionPlatformSectionData> sections = facade.getSubscriptionsForCurrentCustomer().getSections();

		assertEquals(1, sections.size());
		assertEquals("CHARGEBEE", sections.get(0).getPlatformCode());
	}

	/** An order that never became a subscription is reported under its own platform, not on every section. */
	@Test
	public void listsAnOrderAwaitingSetupUnderItsOwnPlatform()
	{
		givenLoadedPlatforms(CHARGEBEE, RECURLY);
		givenSubscriptions(ref("cb-1", CHARGEBEE));
		givenAbandoned(attempt("ORDER-7", RECURLY));

		final List<SubscriptionPlatformSectionData> sections = facade.getSubscriptionsForCurrentCustomer().getSections();

		assertEquals(2, sections.size());
		assertTrue(sections.get(0).getOrdersAwaitingSetup().isEmpty());
		assertEquals(List.of("ORDER-7"), sections.get(1).getOrdersAwaitingSetup());
		assertTrue(sections.get(1).getSubscriptions().isEmpty());
	}

	@Test
	public void namesTheSectionAsTheConnectorExtensionLocalizesItsPlatform()
	{
		givenSubscriptions(ref("cb-1", CHARGEBEE));
		when(enumerationService.getEnumerationName(CHARGEBEE)).thenReturn("Chargebee");

		assertEquals("Chargebee", onlySection().getPlatformName());
	}

	@Test
	public void hasNothingToOfferWhileNoConnectorIsLoaded()
	{
		givenLoadedPlatforms();

		assertFalse(facade.hasAvailablePlatforms());
	}

	/** A control of one section can never act on a subscription of another, even the shopper's own. */
	@Test
	public void refusesARequestFromAnotherPlatformsSection() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenVaultHolding("card-mine");

		assertFalse(facade.cancelForCurrentCustomer("RECURLY", "code-1"));
		assertEquals(PaymentMethodChangeResult.FAILED,
				facade.changePaymentMethodForCurrentCustomer("RECURLY", "code-1", "card-mine"));
		assertNull(facade.paymentMethodEnrollmentUrlForCurrentCustomer("RECURLY", "code-1"));

		verify(subscriptionBillingService, never()).cancel(any(), any());
		verify(subscriptionBillingService, never()).changePaymentMethod(any(), any());
		verify(subscriptionBillingService, never()).paymentMethodEnrollmentPage(any());
	}

	@Test
	public void refusesARequestThatNamesNoPlatform() throws Exception
	{
		givenSubscriptions(ref(NormalizedSubscriptionStatus.ACTIVE));

		assertFalse(facade.cancelForCurrentCustomer(null, "code-1"));

		verify(subscriptionBillingService, never()).cancel(any(), any());
	}

	// --- describing a row ---

	@Test
	public void aFutureDatedSubscriptionSaysWhenItStarts()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.PENDING);
		final Date tomorrow = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
		when(ref.getCurrentPeriodStart()).thenReturn(tomorrow);
		givenSubscriptions(ref);

		final SubscriptionEntryData entry = onlyEntry();

		assertEquals(SubscriptionDisplayState.STARTING_SOON, entry.getState());
		assertEquals(tomorrow, entry.getEffectiveDate());
	}

	@Test
	public void promisesNoDateForASubscriptionNoPlatformHasConfirmedYet()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getPlatformUpdatedAt()).thenReturn(null);
		when(ref.getCurrentPeriodEnd()).thenReturn(new Date());
		givenSubscriptions(ref);

		final SubscriptionEntryData entry = onlyEntry();

		assertEquals(SubscriptionDisplayState.SETTING_UP, entry.getState());
		assertNull(entry.getEffectiveDate());
		assertFalse(entry.isCancellable());
	}

	/**
	 * Without the originating store there are no credentials with which to reach the platform, so the row
	 * offers nothing. It still appears and is still described, because it is billing somebody.
	 */
	@Test
	public void describesASubscriptionItCannotActOnInsteadOfCallingItUnknown() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getOrder()).thenReturn(null);
		givenSubscriptions(ref);

		final SubscriptionEntryData entry = onlyEntry();

		assertEquals(SubscriptionDisplayState.ACTIVE, entry.getState());
		assertFalse(entry.isManageable());
		assertFalse(entry.isCancellable());
		assertFalse(entry.isPaymentMethodChangeable());
	}

	/** A button posting an empty code could only be refused. */
	@Test
	public void offersNoButtonForARowThatHasNoPublicIdentifierYet()
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getCode()).thenReturn(null);
		givenSubscriptions(ref);

		final SubscriptionEntryData entry = onlyEntry();

		assertEquals(SubscriptionDisplayState.ACTIVE, entry.getState());
		assertFalse(entry.isCancellable());
	}

	/** getProductForCode throws rather than returning null when the product is gone. */
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

	@Test
	public void anAnonymousVisitorSeesAnEmptyPageRatherThanAnError()
	{
		final UserModel anonymous = mock(UserModel.class);
		when(userService.getCurrentUser()).thenReturn(anonymous);
		when(userService.isAnonymousUser(anonymous)).thenReturn(true);

		assertTrue(facade.getSubscriptionsForCurrentCustomer().isEmpty());
		assertFalse(facade.cancelForCurrentCustomer("CHARGEBEE", "code-1"));
	}

	// --- ownership and cancelling ---

	@Test
	public void cancellingAsksForTheEndOfThePaidPeriod() throws Exception
	{
		givenSubscriptions(ref(NormalizedSubscriptionStatus.ACTIVE));

		assertTrue(facade.cancelForCurrentCustomer("CHARGEBEE", "code-1"));

		verify(subscriptionBillingService).cancel(any(), any(SubscriptionCancellation.class));
	}

	/** The lookup names the customer in the query, so another customer's code matches nothing at all. */
	@Test
	public void refusesACodeThatIsNotThisCustomersWithoutSayingWhy() throws Exception
	{
		givenSubscriptions();

		assertFalse(facade.cancelForCurrentCustomer("CHARGEBEE", "someone-elses-code"));

		verify(subscriptionBillingService, never()).cancel(any(), any());
	}

	/** The state is re-derived from the row rather than trusted from a form that may be minutes old. */
	@Test
	public void refusesToCancelSomethingThatIsNoLongerInACancellableState() throws Exception
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getCancelAtPeriodEnd()).thenReturn(Boolean.TRUE);
		givenSubscriptions(ref);

		assertFalse(facade.cancelForCurrentCustomer("CHARGEBEE", "code-1"));

		verify(subscriptionBillingService, never()).cancel(any(), any());
	}

	@Test
	public void refusesToActOnASubscriptionWithNoOriginatingStore() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getOrder()).thenReturn(null);
		givenSubscriptions(ref);
		givenVaultHolding("card-mine");

		assertFalse(facade.cancelForCurrentCustomer("CHARGEBEE", "code-1"));
		assertEquals(PaymentMethodChangeResult.FAILED,
				facade.changePaymentMethodForCurrentCustomer("CHARGEBEE", "code-1", "card-mine"));

		verify(subscriptionBillingService, never()).cancel(any(), any());
		verify(subscriptionBillingService, never()).changePaymentMethod(any(), any());
	}

	// --- the platform's own payment-method page ---

	/** The same lookup guards the hosted page: on some platforms its address signs its holder in. */
	@Test
	public void mintsNoHostedPageAddressForACodeThatIsNotThisCustomers() throws Exception
	{
		givenSubscriptions();

		assertNull(facade.paymentMethodEnrollmentUrlForCurrentCustomer("CHARGEBEE", "someone-elses-code"));

		verify(subscriptionBillingService, never()).paymentMethodEnrollmentPage(any());
	}

	@Test
	public void mintsNoHostedPageAddressWhenTheServiceOffersNone() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(subscriptionBillingService.paymentMethodEnrollmentPage(any())).thenReturn(Optional.empty());

		assertNull(facade.paymentMethodEnrollmentUrlForCurrentCustomer("CHARGEBEE", "code-1"));
	}

	/** Rendering decides only whether to invite the shopper, from declared capability; it mints no address. */
	@Test
	public void namesTheRowAndTheEffectWithoutMintingAnAddress() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(connector.capabilities()).thenReturn(new ConnectorCapabilities(false, true, false, true, true,
				TokenImportStyle.SLASH_JOINED, PaymentMethodChangeSupport.NONE,
				new PaymentMethodEnrollmentSupport(PaymentMethodEnrollmentEffect.REPLACES_METHOD_ON_FILE)));

		final SubscriptionPlatformSectionData section = onlySection();

		assertEquals(1, section.getPaymentMethodEnrollmentOffers().size());
		assertEquals("code-1", section.getPaymentMethodEnrollmentOffers().get(0).getSubscriptionCode());
		assertEquals(PaymentMethodEnrollmentNotice.REPLACES_METHOD_ON_FILE,
				section.getPaymentMethodEnrollmentOffers().get(0).getNotice());
		verify(subscriptionBillingService, never()).paymentMethodEnrollmentPage(any());
	}

	// --- changing the payment method ---

	/**
	 * The token id is a request parameter and the select on the page is a convenience rather than a control,
	 * so ownership is checked against the cards this shopper holds.
	 */
	@Test
	public void refusesAPaymentMethodThatIsNotOnThisShoppersVaultListing() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenVaultHolding("card-mine");

		assertEquals(PaymentMethodChangeResult.FAILED,
				facade.changePaymentMethodForCurrentCustomer("CHARGEBEE", "code-1", "card-somebody-elses"));

		verify(subscriptionBillingService, never()).changePaymentMethod(any(), any());
	}

	@Test
	public void acceptsAPaymentMethodTheShopperActuallyHolds() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenVaultHolding("card-mine");

		// The wording the shopper reads follows what the platform reported, not what was advertised.
		assertEquals(PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS,
				facade.changePaymentMethodForCurrentCustomer("CHARGEBEE", "code-1", "card-mine"));

		verify(subscriptionBillingService).changePaymentMethod(any(), any());
	}

	/** A vault listing that could not be read says nothing about ownership, so the change cannot proceed. */
	@Test
	public void refusesWhenTheVaultListingCannotBeRead() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(storedCardsService.getStoredCardsOrThrow(customer)).thenThrow(
				new AdyenStoredCardsUnavailableException("Adyen is unreachable", new IOException("timeout")));

		assertEquals(PaymentMethodChangeResult.FAILED,
				facade.changePaymentMethodForCurrentCustomer("CHARGEBEE", "code-1", "card-mine"));

		verify(subscriptionBillingService, never()).changePaymentMethod(any(), any());
	}

	@Test
	public void refusesToChangeThePaymentMethodOfASubscriptionThatHasEnded() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.EXPIRED);
		givenVaultHolding("card-mine");

		assertEquals(PaymentMethodChangeResult.FAILED,
				facade.changePaymentMethodForCurrentCustomer("CHARGEBEE", "code-1", "card-mine"));

		verify(subscriptionBillingService, never()).changePaymentMethod(any(), any());
	}

	@Test
	public void offersThePaymentMethodChangeOnASubscriptionThatIsPastDue() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.PAST_DUE);
		givenVaultHolding("card-mine");

		assertEquals(PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS,
				facade.changePaymentMethodForCurrentCustomer("CHARGEBEE", "code-1", "card-mine"));
	}

	/** A platform that cannot do this is answered with its own result, not with "please try again". */
	@Test
	public void tellsTheShopperWhenThePlatformCannotChangeThePaymentMethodAtAll() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(connector.capabilities()).thenReturn(capabilities(PaymentMethodChangeScope.NOT_SUPPORTED));
		givenVaultHolding("card-mine");

		assertEquals(PaymentMethodChangeResult.NOT_SUPPORTED_HERE,
				facade.changePaymentMethodForCurrentCustomer("CHARGEBEE", "code-1", "card-mine"));

		verify(subscriptionBillingService, never()).changePaymentMethod(any(), any());
	}

	// --- where the controls go ---

	@Test
	public void doesNotOfferTheChangeWhenNoSubscriptionInTheSectionSupportsIt() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(connector.capabilities()).thenReturn(capabilities(PaymentMethodChangeScope.NOT_SUPPORTED));
		givenVaultHolding("card-mine");

		final SubscriptionPlatformSectionData section = onlySection();

		assertTrue(section.getPaymentMethodChangeOffers().isEmpty());
		assertFalse(section.isPaymentMethodChangeOffered());
		assertFalse(section.isPaymentMethodChangeSupported());
	}

	/** The account-wide control is built from what the platform accepts, here the shopper's vaulted cards. */
	@Test
	public void keepsOneAccountWideControlForAPlatformThatChangesTheWholeAccount() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenVaultHolding("card-mine");

		final SubscriptionPlatformSectionData section = onlySection();

		assertEquals(1, section.getPaymentMethodChangeOffers().size());
		final PaymentMethodChangeOfferData offer = section.getPaymentMethodChangeOffers().get(0);
		assertEquals("code-1", offer.getSubscriptionCode());
		assertEquals("card-mine", offer.getAdyenVaultOptions().get(0).getId());
		assertTrue(offer.getPaymentMethodOptions().isEmpty());
	}

	/** With nothing to choose from, the control is not offered at all rather than offered empty. */
	@Test
	public void offersNoAccountWideControlWithoutAnythingToChooseFrom() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenVaultHolding();

		assertTrue(onlySection().getPaymentMethodChangeOffers().isEmpty());
	}

	@Test
	public void doesNotOfferTheChangeForARowWithNoPublicCode() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		when(subscriptions.get(0).getCode()).thenReturn(null);
		givenVaultHolding("card-mine");

		assertTrue(onlySection().getPaymentMethodChangeOffers().isEmpty());
	}

	/** One control per account: a shopper can hold accounts in two stores selling on one platform. */
	@Test
	public void offersOneAccountWideControlPerBillingAccount() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		givenVaultHolding("card-mine");
		givenSubscriptions(ref("code-a", CHARGEBEE), inStore(ref("code-b", CHARGEBEE), store("store-2")));

		final List<PaymentMethodChangeOfferData> offers = onlySection().getPaymentMethodChangeOffers();

		assertEquals(List.of("code-a", "code-b"), offers.stream().map(PaymentMethodChangeOfferData::getSubscriptionCode).toList());
		assertEquals(List.of("store-1 name", "store-2 name"),
				offers.stream().map(PaymentMethodChangeOfferData::getAccountName).toList());
	}

	/**
	 * A platform that pins the method per subscription gets its control in the row, so there is no
	 * account-wide one, and that must not be read as the change being unavailable.
	 */
	@Test
	public void putsTheControlInTheRowWithoutClaimingTheChangeIsUnavailable() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.SUBSCRIPTION);
		givenPlatformHolding("billing-1", "billing-2");
		givenSubscriptions(ref(NormalizedSubscriptionStatus.ACTIVE));

		final SubscriptionPlatformSectionData section = onlySection();
		final SubscriptionEntryData entry = section.getSubscriptions().get(0);

		assertTrue(entry.isPaymentMethodControl());
		assertTrue(section.getPaymentMethodChangeOffers().isEmpty());
		assertTrue(section.isPaymentMethodChangeOffered());
		assertTrue(section.isPaymentMethodChangeSupported());
	}

	@Test
	public void marksTheMethodTheSubscriptionIsBilledToNow() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.SUBSCRIPTION);
		givenPlatformHolding("billing-1", "billing-2");
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getExternalPaymentMethodId()).thenReturn("stored-ref");
		when(connector.listedPaymentMethodId("stored-ref")).thenReturn("billing-2");
		givenSubscriptions(ref);

		final SubscriptionEntryData entry = onlyEntry();

		assertFalse(entry.getPaymentMethodOptions().get(0).isCurrent());
		assertTrue(entry.getPaymentMethodOptions().get(1).isCurrent());
	}

	/** One option is not a choice but the instrument already billing, so no control is offered. */
	@Test
	public void offersNoRowControlWhenThePlatformHoldsOnlyOneMethod() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.SUBSCRIPTION);
		givenPlatformHolding("billing-1");
		givenSubscriptions(ref(NormalizedSubscriptionStatus.ACTIVE));

		final SubscriptionEntryData entry = onlyEntry();

		assertTrue(entry.isPaymentMethodChangeable());
		assertFalse(entry.isPaymentMethodControl());
		assertTrue(entry.getPaymentMethodOptions().isEmpty());
	}

	@Test
	public void marksTheRowWhosePlatformCannotChangeItsCard() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.NOT_SUPPORTED);
		givenSubscriptions(ref(NormalizedSubscriptionStatus.ACTIVE));

		final SubscriptionEntryData entry = onlyEntry();

		assertFalse(entry.isPaymentMethodChangeable());
		// The state still allows it, which marks this row as an exception worth explaining.
		assertTrue(entry.getState().isPaymentMethodChangeable());
	}

	/**
	 * The codeless row cannot be named in a form, yet the account-wide control changes the account and moves
	 * it anyway, so "cannot be named" must not be reported as "will not be changed".
	 */
	@Test
	public void doesNotSingleOutACodelessRowThatItsAccountsControlChangesAnyway() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		givenVaultHolding("card-mine");
		givenSubscriptions(ref("code-a", CHARGEBEE), ref(null, CHARGEBEE));

		final SubscriptionPlatformSectionData section = onlySection();
		final SubscriptionEntryData codeless = section.getSubscriptions().get(1);

		assertEquals("code-a", section.getPaymentMethodChangeOffers().get(0).getSubscriptionCode());
		assertFalse(codeless.isPaymentMethodChangeable());
		assertTrue(codeless.isPaymentMethodChangeCovered());
	}

	@Test
	public void singlesOutACodelessRowWhenNoControlCoversIt() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		givenVaultHolding("card-mine");
		givenSubscriptions(ref(null, CHARGEBEE));

		assertFalse(onlyEntry().isPaymentMethodChangeCovered());
	}

	/** The control of one account moves nothing on another, so it does not cover the other's rows. */
	@Test
	public void doesNotClaimToCoverARowOfAnotherAccount() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		givenVaultHolding("card-mine");
		givenSubscriptions(ref("code-a", CHARGEBEE), inStore(ref(null, CHARGEBEE), store("store-2")));

		final SubscriptionPlatformSectionData section = onlySection();

		assertEquals(1, section.getPaymentMethodChangeOffers().size());
		assertTrue(section.getSubscriptions().get(0).isPaymentMethodChangeCovered());
		assertFalse(section.getSubscriptions().get(1).isPaymentMethodChangeCovered());
	}

	/** Not changeable today is not the same as a provider that cannot change cards. */
	@Test
	public void doesNotCallAProviderIncapableWhenOnlyTodaysStateSaysNo() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		final BillingSubscriptionRefModel ref = ref("code-a", CHARGEBEE);
		when(ref.getPlatformUpdatedAt()).thenReturn(null);
		givenSubscriptions(ref);

		final SubscriptionPlatformSectionData section = onlySection();

		assertEquals(SubscriptionDisplayState.SETTING_UP, section.getSubscriptions().get(0).getState());
		assertFalse(section.isPaymentMethodChangeOffered());
		assertTrue(section.isPaymentMethodChangeSupported());
	}

	/** The platform's methods belong to the account, so rows of one account share a single lookup. */
	@Test
	public void asksThePlatformOnceForAnAccountNoMatterHowManyRowsItHas() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.SUBSCRIPTION);
		givenPlatformHolding("billing-1", "billing-2");
		givenSubscriptions(ref("code-a", CHARGEBEE), ref("code-b", CHARGEBEE));

		final SubscriptionPlatformSectionData section = onlySection();

		assertEquals(2, section.getSubscriptions().get(0).getPaymentMethodOptions().size());
		assertEquals(2, section.getSubscriptions().get(1).getPaymentMethodOptions().size());
		verify(subscriptionBillingService, times(1)).listPaymentMethods(any());
	}

	/**
	 * The same physical card reaches the row twice - as a vaulted token and as the billing info it was
	 * imported into. It is offered once, as the platform's own.
	 */
	@Test
	public void offersACardTheProviderAlreadyImportedOnlyOnce() throws Exception
	{
		givenAVaultAndAPlatformThatAcceptsIt();
		givenVaultHoldingDescribedCards("token-1", "token-2");
		when(subscriptionBillingService.listPaymentMethods(any())).thenReturn(List.of(
				new PlatformPaymentMethod("billing-1", "Saved payment method", null, true, "token-1")));
		givenSubscriptions(ref(NormalizedSubscriptionStatus.ACTIVE));

		final SubscriptionEntryData entry = onlyEntry();

		assertEquals(1, entry.getPaymentMethodOptions().size());
		assertEquals("billing-1", entry.getPaymentMethodOptions().get(0).getId());
		assertEquals(1, entry.getAdyenVaultOptions().size());
		assertEquals("token-2", entry.getAdyenVaultOptions().get(0).getId());
	}

	@Test
	public void namesAnImportedCardTheWayTheVaultNamesIt() throws Exception
	{
		givenAVaultAndAPlatformThatAcceptsIt();
		givenVaultHoldingDescribedCards("token-1", "token-2");
		when(subscriptionBillingService.listPaymentMethods(any())).thenReturn(List.of(
				new PlatformPaymentMethod("billing-1", "Saved payment method", null, true, "token-1")));
		givenSubscriptions(ref(NormalizedSubscriptionStatus.ACTIVE));

		assertEquals("•••• 1111", onlyEntry().getPaymentMethodOptions().get(0).getDisplayLabel());
	}

	@Test
	public void keepsTheProvidersOwnLabelWhenItDescribesTheCard() throws Exception
	{
		givenAVaultAndAPlatformThatAcceptsIt();
		givenVaultHoldingDescribedCards("token-1", "token-2");
		when(subscriptionBillingService.listPaymentMethods(any())).thenReturn(List.of(
				new PlatformPaymentMethod("billing-1", "Visa 4242",
						new CardMetadata("visa", "4242", null, null, null), true, "token-1")));
		givenSubscriptions(ref(NormalizedSubscriptionStatus.ACTIVE));

		assertEquals("Visa 4242", onlyEntry().getPaymentMethodOptions().get(0).getDisplayLabel());
	}

	// --- one method behind several subscriptions ---

	@Test
	public void putsOneMethodBehindEverySubscriptionOnTheSameAccount() throws Exception
	{
		givenAPinningPlatform();
		givenSubscriptions(pinnedRef("sub-1", "customer-1"), pinnedRef("sub-2", "customer-1"),
				pinnedRef("sub-3", "customer-1"));

		final PaymentMethodChangeReport report =
				facade.changePaymentMethodForAllSubscriptions("CHARGEBEE", "code-1", "billing-9");

		assertEquals(3, report.moved());
		assertEquals(0, report.failed());
		assertEquals(PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS, report.result());
		verify(subscriptionBillingService, times(3)).changePaymentMethod(any(), any());
	}

	@Test
	public void leavesSubscriptionsUnderAnotherCustomerReferenceAlone() throws Exception
	{
		givenAPinningPlatform();
		givenSubscriptions(pinnedRef("sub-1", "customer-1"), pinnedRef("sub-2", "customer-OTHER"));

		assertEquals(1, facade.changePaymentMethodForAllSubscriptions("CHARGEBEE", "code-1", "billing-9").moved());
		verify(subscriptionBillingService, times(1)).changePaymentMethod(any(), any());
	}

	/** The customer reference repeats on every store of a platform; only the store tells the accounts apart. */
	@Test
	public void leavesSubscriptionsSoldByAnotherStoreAlone() throws Exception
	{
		givenAPinningPlatform();
		givenSubscriptions(pinnedRef("sub-1", "customer-1"),
				inStore(pinnedRef("sub-2", "customer-1"), store("store-2")));

		final PaymentMethodChangeReport report =
				facade.changePaymentMethodForAllSubscriptions("CHARGEBEE", "code-1", "billing-9");

		// Not a failure either: a row the chosen method could never reach is not something to report.
		assertEquals(1, report.moved());
		assertEquals(0, report.failed());
	}

	@Test
	public void reportsCountsWhenOnlySomeOfThemMove() throws Exception
	{
		givenAPinningPlatform();
		givenSubscriptions(pinnedRef("sub-1", "customer-1"), pinnedRef("sub-2", "customer-1"));
		when(subscriptionBillingService.changePaymentMethod(any(), any()))
				.thenReturn(new PaymentMethodChangeOutcome(new BillingPaymentMethodRef(CHARGEBEE, "billing-9"),
						PaymentMethodChangeScope.SUBSCRIPTION))
				.thenThrow(new IllegalStateException("the platform said no"));

		final PaymentMethodChangeReport report =
				facade.changePaymentMethodForAllSubscriptions("CHARGEBEE", "code-1", "billing-9");

		assertEquals(1, report.moved());
		assertEquals(1, report.failed());
		assertTrue(report.isPartial());
		assertEquals(PaymentMethodChangeResult.CHANGED_THIS_SUBSCRIPTION, report.result());
	}

	@Test
	public void doesNotFanOutWhenTheChangeWasAlreadyAccountWide() throws Exception
	{
		givenSubscriptionOnAPlatformThatSupportsTheChange(NormalizedSubscriptionStatus.ACTIVE);
		givenVaultHolding("card-mine");

		final PaymentMethodChangeReport report =
				facade.changePaymentMethodForAllSubscriptions("CHARGEBEE", "code-1", "card-mine");

		assertEquals(PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS, report.result());
		verify(subscriptionBillingService, times(1)).changePaymentMethod(any(), any());
	}

	@Test
	public void fansOutNothingWhenTheNamedSubscriptionWasRefused() throws Exception
	{
		givenSubscriptions();

		final PaymentMethodChangeReport report =
				facade.changePaymentMethodForAllSubscriptions("CHARGEBEE", "someone-elses-code", "card-mine");

		assertEquals(PaymentMethodChangeResult.FAILED, report.result());
		assertEquals(0, report.moved());
		verify(subscriptionBillingService, never()).changePaymentMethod(any(), any());
	}

	@Test
	public void offersToShareOneMethodOnlyWhenAnotherRowWouldActuallyMove() throws Exception
	{
		givenAPinningPlatform();
		givenSubscriptions(pinnedRef("sub-1", "customer-1"));

		assertFalse(onlyEntry().isPaymentMethodShareable());

		givenSubscriptions(pinnedRef("sub-1", "customer-1"), pinnedRef("sub-2", "customer-1"));

		assertTrue(onlySection().getSubscriptions().get(0).isPaymentMethodShareable());
	}

	@Test
	public void doesNotOfferToShareOneMethodOnAnAccountWidePlatform() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		givenSubscriptions(pinnedRef("sub-1", "customer-1"), pinnedRef("sub-2", "customer-1"));

		assertFalse(onlySection().getSubscriptions().get(0).isPaymentMethodShareable());
	}

	// --- fixtures ---

	/** A platform that pins a method per subscription; the posted option has to be one it lists. */
	private void givenAPinningPlatform() throws Exception
	{
		givenConnectorDeclaring(PaymentMethodChangeScope.SUBSCRIPTION);
		when(subscriptionBillingService.listPaymentMethods(any())).thenReturn(List.of(
				new PlatformPaymentMethod("billing-9", "Visa 4242", null, false),
				new PlatformPaymentMethod("billing-8", "Visa 1881", null, true)));
		when(subscriptionBillingService.changePaymentMethod(any(), any()))
				.thenReturn(new PaymentMethodChangeOutcome(new BillingPaymentMethodRef(CHARGEBEE, "billing-9"),
						PaymentMethodChangeScope.SUBSCRIPTION));
	}

	private void givenAVaultAndAPlatformThatAcceptsIt()
	{
		when(connector.capabilities()).thenReturn(new ConnectorCapabilities(false, true, false, true, true,
				TokenImportStyle.SLASH_JOINED,
				new PaymentMethodChangeSupport(PaymentMethodChangeScope.SUBSCRIPTION,
						Set.of(PaymentMethodSource.ALREADY_ON_PLATFORM, PaymentMethodSource.ADYEN_VAULTED_TOKEN)),
				PaymentMethodEnrollmentSupport.NONE));
	}

	private void givenSubscriptionOnAPlatformThatSupportsTheChange(final NormalizedSubscriptionStatus status)
			throws Exception
	{
		final BillingSubscriptionRefModel ref = ref(status);
		when(ref.getExternalCustomerId()).thenReturn("customer-1");
		givenSubscriptions(ref);
		givenConnectorDeclaring(PaymentMethodChangeScope.CUSTOMER);
		// A real handle rather than a mock: the request record validates its arguments.
		when(tokenHandleFactory.createForVaultedToken(any(), any(), any(), any(), any()))
				.thenReturn(new AdyenTokenHandle("MERCHANT", "shopper-1", "card-mine", null, null));
		when(subscriptionBillingService.changePaymentMethod(any(), any()))
				.thenReturn(new PaymentMethodChangeOutcome(new BillingPaymentMethodRef(CHARGEBEE, "pm_1"),
						PaymentMethodChangeScope.CUSTOMER));
	}

	private void givenConnectorDeclaring(final PaymentMethodChangeScope scope)
	{
		when(connector.capabilities()).thenReturn(capabilities(scope));
	}

	private static ConnectorCapabilities capabilities(final PaymentMethodChangeScope scope)
	{
		// The source matches the scope: a connector that supports the change must name one it accepts.
		final PaymentMethodChangeSupport support = scope.isSupported()
				? new PaymentMethodChangeSupport(scope, Set.of(scope == PaymentMethodChangeScope.SUBSCRIPTION
						? PaymentMethodSource.ALREADY_ON_PLATFORM : PaymentMethodSource.ADYEN_VAULTED_TOKEN))
				: PaymentMethodChangeSupport.NONE;
		return new ConnectorCapabilities(false, true, false, true, true, TokenImportStyle.SLASH_JOINED,
				support, PaymentMethodEnrollmentSupport.NONE);
	}

	private void givenPlatformHolding(final String... ids) throws Exception
	{
		final List<PlatformPaymentMethod> methods = new ArrayList<>();
		for (final String id : ids)
		{
			methods.add(new PlatformPaymentMethod(id, "Card " + id, null, false));
		}
		when(subscriptionBillingService.listPaymentMethods(any())).thenReturn(methods);
	}

	private void givenVaultHolding(final String... storedPaymentMethodIds) throws Exception
	{
		final List<StoredPaymentMethodResource> cards = new ArrayList<>();
		for (final String id : storedPaymentMethodIds)
		{
			final StoredPaymentMethodResource card = new StoredPaymentMethodResource();
			card.setId(id);
			cards.add(card);
		}
		when(storedCardsService.getStoredCardsOrThrow(customer)).thenReturn(cards);
	}

	/** Cards the vault can actually describe, which is what makes the borrowed label observable. */
	private void givenVaultHoldingDescribedCards(final String... ids) throws Exception
	{
		final List<StoredPaymentMethodResource> cards = new ArrayList<>();
		for (int i = 0; i < ids.length; i++)
		{
			final StoredPaymentMethodResource card = new StoredPaymentMethodResource();
			card.setId(ids[i]);
			card.setLastFour(String.valueOf(1111 * (i + 1)));
			cards.add(card);
		}
		when(storedCardsService.getStoredCardsOrThrow(customer)).thenReturn(cards);
	}

	private void givenLoadedPlatforms(final BillingPlatform... platforms)
	{
		when(connectorRegistry.getAvailablePlatforms()).thenReturn(new LinkedHashSet<>(List.of(platforms)));
	}

	private void givenSubscriptions(final BillingSubscriptionRefModel... refs)
	{
		subscriptions = List.of(refs);
	}

	private void givenAbandoned(final BillingActivationAttemptModel... attempts)
	{
		abandoned = List.of(attempts);
	}

	/**
	 * The subscription query answers with every row and the lookup by code with the first one, which is how
	 * the tests name "the" subscription; the activation query answers with the abandoned attempts.
	 */
	private void stubSearches()
	{
		when(flexibleSearchService.search(any(FlexibleSearchQuery.class))).thenAnswer(invocation -> {
			final String query = ((FlexibleSearchQuery) invocation.getArgument(0)).getQuery();
			final List<Object> rows = new ArrayList<>();
			if (query.contains("{BillingActivationAttempt"))
			{
				rows.addAll(abandoned);
			}
			else if (query.contains("{code} = ?code"))
			{
				subscriptions.stream().limit(1).forEach(rows::add);
			}
			else
			{
				rows.addAll(subscriptions);
			}
			return new SearchResultImpl<>(rows, rows.size(), rows.size(), 0);
		});
	}

	private SubscriptionPlatformSectionData onlySection()
	{
		final SubscriptionOverviewData overview = facade.getSubscriptionsForCurrentCustomer();
		assertEquals(1, overview.getSections().size());
		return overview.getSections().get(0);
	}

	private SubscriptionEntryData onlyEntry()
	{
		final SubscriptionPlatformSectionData section = onlySection();
		assertEquals(1, section.getSubscriptions().size());
		return section.getSubscriptions().get(0);
	}

	private static List<String> codes(final SubscriptionPlatformSectionData section)
	{
		return section.getSubscriptions().stream().map(SubscriptionEntryData::getCode).toList();
	}

	private BillingSubscriptionRefModel pinnedRef(final String externalId, final String externalCustomerId)
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getExternalSubscriptionId()).thenReturn(externalId);
		when(ref.getExternalCustomerId()).thenReturn(externalCustomerId);
		return ref;
	}

	private BillingSubscriptionRefModel ref(final String code, final BillingPlatform platform)
	{
		final BillingSubscriptionRefModel ref = ref(NormalizedSubscriptionStatus.ACTIVE);
		when(ref.getCode()).thenReturn(code);
		when(ref.getPlatform()).thenReturn(platform);
		return ref;
	}

	private BillingSubscriptionRefModel ref(final NormalizedSubscriptionStatus status)
	{
		final BillingSubscriptionRefModel ref = mock(BillingSubscriptionRefModel.class);
		when(ref.getCode()).thenReturn("code-1");
		when(ref.getPlatform()).thenReturn(CHARGEBEE);
		when(ref.getStatus()).thenReturn(status.name());
		// Present by default: absent, the row lands in SETTING_UP, which is a state of its own.
		when(ref.getPlatformUpdatedAt()).thenReturn(new Date());
		return inStore(ref, theStore);
	}

	/** Puts a row in a given store, which is what decides whether two rows share a platform account. */
	private BillingSubscriptionRefModel inStore(final BillingSubscriptionRefModel ref, final BaseStoreModel store)
	{
		final OrderModel order = mock(OrderModel.class);
		when(order.getStore()).thenReturn(store);
		when(ref.getOrder()).thenReturn(order);
		return ref;
	}

	private static BaseStoreModel store(final String uid)
	{
		final BaseStoreModel store = mock(BaseStoreModel.class);
		when(store.getUid()).thenReturn(uid);
		when(store.getName()).thenReturn(uid + " name");
		return store;
	}

	private BillingActivationAttemptModel attempt(final String orderCode, final BillingPlatform platform)
	{
		final BillingActivationAttemptModel attempt = mock(BillingActivationAttemptModel.class);
		final OrderModel order = mock(OrderModel.class);
		when(order.getCode()).thenReturn(orderCode);
		when(attempt.getOrder()).thenReturn(order);
		when(attempt.getPlatform()).thenReturn(platform);
		return attempt;
	}

	private static BaseStoreModel storeOf(final BillingSubscriptionRefModel ref)
	{
		final OrderModel order = ref == null ? null : (OrderModel) ref.getOrder();
		return order == null ? null : order.getStore();
	}
}
