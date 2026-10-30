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
package com.adyen.commerce.connector.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.adyen.commerce.connector.cart.CartAdjustment;
import com.adyen.commerce.connector.cart.CartAssessment;
import com.adyen.commerce.connector.cart.SubscriptionCartGuard;
import com.adyen.commerce.connector.cart.SubscriptionCartStatus;
import com.adyen.commerce.connector.duplicate.BlockingSubscription;
import com.adyen.commerce.connector.duplicate.DuplicateCheckRequest;
import com.adyen.commerce.connector.duplicate.DuplicateCheckStage;
import com.adyen.commerce.connector.duplicate.Freshness;
import com.adyen.commerce.connector.duplicate.SubscriptionOwnershipService;
import com.adyen.commerce.connector.selfservice.SubscriptionLifecycleState;
import com.adyen.v6.service.PaymentAuthorisationRefusedException;
import com.adyen.v6.service.PaymentAuthorisationStage;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * The check in front of the payment: the per-cart limits refuse without touching the cart, ownership is asked fresh
 * (and never of the cart's own order), an anonymous or guest customer owns nothing, and a platform that does not
 * answer is the ownership service's business, not this guard's.
 */
@UnitTest
public class AdyenSubscriptionPaymentAuthorisationGuardTest
{
	private AdyenSubscriptionPaymentAuthorisationGuard guard;
	private SubscriptionCartGuard cartGuard;
	private SubscriptionOwnershipService ownership;
	private UserService userService;
	private CartModel cart;
	private CustomerModel customer;
	private BaseStoreModel store;
	private SessionService sessionService;
	private long now = 1_000_000L;
	private final Map<String, Object> sessionAttributes = new HashMap<>();
	private final List<AbstractOrderEntryModel> entries = new ArrayList<>();

	@Before
	public void setUp()
	{
		guard = new AdyenSubscriptionPaymentAuthorisationGuard()
		{
			@Override
			protected long currentTimeMillis()
			{
				return now;
			}
		};
		sessionService = mock(SessionService.class);
		when(sessionService.getAttribute(anyString())).thenAnswer(invocation -> sessionAttributes.get(invocation.getArgument(0)));
		doAnswer(invocation ->
		{
			sessionAttributes.put(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(sessionService).setAttribute(anyString(), any());
		doAnswer(invocation -> sessionAttributes.remove(invocation.<String>getArgument(0))).when(sessionService)
				.removeAttribute(anyString());
		guard.setSessionService(sessionService);
		guard.setRefreshReuseSeconds(120);
		cartGuard = mock(SubscriptionCartGuard.class);
		ownership = mock(SubscriptionOwnershipService.class);
		userService = mock(UserService.class);
		cart = mock(CartModel.class);
		customer = mock(CustomerModel.class);
		store = mock(BaseStoreModel.class);
		guard.setSubscriptionCartGuard(cartGuard);
		guard.setSubscriptionOwnershipService(ownership);
		guard.setUserService(userService);

		when(cart.getUser()).thenReturn(customer);
		when(cart.getCode()).thenReturn("CART-1");
		when(customer.getUid()).thenReturn("anna@example.com");
		when(userService.isAnonymousUser(any())).thenReturn(false);
		when(ownership.findBlocking(any())).thenReturn(Optional.empty());
		when(cartGuard.assessForAuthorisation(cart)).thenAnswer(
				invocation -> new CartAssessment(store, List.copyOf(entries), List.of()));
	}

	@Test
	public void aSubscriptionTheCustomerOwnsRefusesThePaymentAndPointsToMySubscriptions()
	{
		final AbstractOrderEntryModel gold = entry("GOLD");
		when(ownership.findBlocking(any())).thenReturn(Optional.of(blocking()));

		final PaymentAuthorisationRefusedException refusal = assertThrows(PaymentAuthorisationRefusedException.class,
				() -> guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT));

		assertEquals("subscriptionAlreadyOwned", refusal.getErrorCode());
		assertEquals("checkout.error.subscriptionAlreadyOwned", refusal.getMessageKey());
		assertEquals("/my-account/subscriptions", refusal.getResolutionPath());
		assertEquals(gold.getProduct(), requestSeen().product());
	}

	/** A stale local record is checked against the platform, and the cart being paid is never its own evidence. */
	@Test
	public void ownershipIsAskedFreshForThePreAuthStageExcludingTheCart()
	{
		entry("GOLD");

		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);

		final DuplicateCheckRequest request = requestSeen();
		assertEquals(Freshness.REFRESH, request.freshness());
		assertEquals(DuplicateCheckStage.PRE_AUTH, request.stage());
		assertEquals(cart, request.excludedOrder());
		assertEquals(customer, request.customer());
		assertEquals(store, request.baseStore());
	}

	@Test
	public void aSubscriptionNobodyBlocksPasses()
	{
		entry("GOLD");

		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PARTIAL_PAYMENT_ORDER);

		verify(ownership).findBlocking(any());
	}

	@Test
	public void moreThanOneSubscriptionProductRefusesWithoutAskingOwnership()
	{
		final AbstractOrderEntryModel gold = entry("GOLD");
		final AbstractOrderEntryModel silver = entry("SILVER");
		when(cartGuard.assessForAuthorisation(cart)).thenReturn(new CartAssessment(store, List.of(gold, silver),
				List.of(new CartAdjustment(silver, SubscriptionCartStatus.LIMIT_PER_CART, 0, DuplicateCheckStage.PRE_AUTH))));

		final PaymentAuthorisationRefusedException refusal = assertThrows(PaymentAuthorisationRefusedException.class,
				() -> guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT));

		assertEquals("subscriptionLimitPerCart", refusal.getErrorCode());
		assertNull(refusal.getResolutionPath());
		verify(ownership, never()).findBlocking(any());
	}

	@Test
	public void aQuantityAboveOneRefuses()
	{
		final AbstractOrderEntryModel gold = entry("GOLD");
		when(cartGuard.assessForAuthorisation(cart)).thenReturn(new CartAssessment(store, List.of(gold),
				List.of(new CartAdjustment(gold, SubscriptionCartStatus.QUANTITY_LIMIT, 1, DuplicateCheckStage.PRE_AUTH))));

		final PaymentAuthorisationRefusedException refusal = assertThrows(PaymentAuthorisationRefusedException.class,
				() -> guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT));

		assertEquals("subscriptionQuantityLimit", refusal.getErrorCode());
	}

	@Test
	public void aCartWithoutASubscriptionPassesAndAsksNothing()
	{
		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);

		verify(ownership, never()).findBlocking(any());
	}

	@Test
	public void anAnonymousCustomerOwnsNothingButIsHeldToTheLimits()
	{
		final AbstractOrderEntryModel gold = entry("GOLD");
		when(userService.isAnonymousUser(customer)).thenReturn(true);
		when(ownership.findBlocking(any())).thenReturn(Optional.of(blocking()));

		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);
		verify(ownership, never()).findBlocking(any());

		when(cartGuard.assessForAuthorisation(cart)).thenReturn(new CartAssessment(store, List.of(gold),
				List.of(new CartAdjustment(gold, SubscriptionCartStatus.QUANTITY_LIMIT, 1, DuplicateCheckStage.PRE_AUTH))));
		assertThrows(PaymentAuthorisationRefusedException.class,
				() -> guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT));
	}

	@Test
	public void aGuestCustomerOwnsNothing()
	{
		entry("GOLD");
		when(customer.getType()).thenReturn(CustomerType.GUEST);
		when(ownership.findBlocking(any())).thenReturn(Optional.of(blocking()));

		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);

		verify(ownership, never()).findBlocking(any());
	}

	@Test
	public void theSameProductInTwoEntriesIsAskedOnce()
	{
		final AbstractOrderEntryModel first = entry("GOLD");
		final AbstractOrderEntryModel second = mock(AbstractOrderEntryModel.class);
		final ProductModel sameProduct = first.getProduct();
		when(second.getProduct()).thenReturn(sameProduct);
		entries.add(second);

		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);

		verify(ownership, times(1)).findBlocking(any());
	}

	@Test
	public void aSecondCheckOfTheSameCartWithinTheReuseWindowAsksLocal()
	{
		entry("GOLD");

		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PARTIAL_PAYMENT_ORDER);
		now += 119_000L;
		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PARTIAL_PAYMENT);

		assertEquals(List.of(Freshness.REFRESH, Freshness.LOCAL), freshnessesAsked());
	}

	@Test
	public void changedEntriesAskRefreshAgain()
	{
		entry("GOLD");
		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);

		entries.clear();
		entry("SILVER");
		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);

		assertEquals(List.of(Freshness.REFRESH, Freshness.REFRESH), freshnessesAsked());
	}

	@Test
	public void aChangedQuantityAsksRefreshAgain()
	{
		final AbstractOrderEntryModel gold = entry("GOLD");
		when(gold.getQuantity()).thenReturn(1L);
		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);

		when(gold.getQuantity()).thenReturn(2L);
		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);

		assertEquals(List.of(Freshness.REFRESH, Freshness.REFRESH), freshnessesAsked());
	}

	@Test
	public void anExpiredMarkAsksRefreshAgain()
	{
		entry("GOLD");
		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);

		now += 120_000L;
		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);

		assertEquals(List.of(Freshness.REFRESH, Freshness.REFRESH), freshnessesAsked());
	}

	@Test
	public void anotherCartAsksRefresh()
	{
		entry("GOLD");
		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);

		when(cart.getCode()).thenReturn("CART-2");
		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);

		assertEquals(List.of(Freshness.REFRESH, Freshness.REFRESH), freshnessesAsked());
	}

	@Test
	public void aFoundBlockIsNeverRemembered()
	{
		entry("GOLD");
		when(ownership.findBlocking(any())).thenReturn(Optional.of(blocking()));

		assertThrows(PaymentAuthorisationRefusedException.class,
				() -> guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT));
		assertThrows(PaymentAuthorisationRefusedException.class,
				() -> guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT));

		assertEquals(List.of(Freshness.REFRESH, Freshness.REFRESH), freshnessesAsked());
		assertNull(sessionAttributes.get(AdyenSubscriptionPaymentAuthorisationGuard.REFRESH_MARK_ATTRIBUTE_PREFIX + "CART-1"));
	}

	@Test
	public void aBlockFoundByTheLocalCheckDropsTheMark()
	{
		entry("GOLD");
		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);
		when(ownership.findBlocking(any())).thenReturn(Optional.of(blocking()));

		assertThrows(PaymentAuthorisationRefusedException.class,
				() -> guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT));

		assertNull(sessionAttributes.get(AdyenSubscriptionPaymentAuthorisationGuard.REFRESH_MARK_ATTRIBUTE_PREFIX + "CART-1"));
	}

	@Test
	public void aReuseWindowOfZeroAsksRefreshEveryTime()
	{
		guard.setRefreshReuseSeconds(0);
		entry("GOLD");

		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);
		guard.beforeAuthorisation(cart, PaymentAuthorisationStage.PAYMENT);

		assertEquals(List.of(Freshness.REFRESH, Freshness.REFRESH), freshnessesAsked());
	}

	private List<Freshness> freshnessesAsked()
	{
		final ArgumentCaptor<DuplicateCheckRequest> request = ArgumentCaptor.forClass(DuplicateCheckRequest.class);
		verify(ownership, atLeastOnce()).findBlocking(request.capture());
		return request.getAllValues().stream().map(DuplicateCheckRequest::freshness).toList();
	}

	private DuplicateCheckRequest requestSeen()
	{
		final ArgumentCaptor<DuplicateCheckRequest> request = ArgumentCaptor.forClass(DuplicateCheckRequest.class);
		verify(ownership).findBlocking(request.capture());
		return request.getValue();
	}

	private BlockingSubscription blocking()
	{
		return new BlockingSubscription(BlockingSubscription.Source.EXISTING_SUBSCRIPTION, "SUB-1",
				SubscriptionLifecycleState.ACTIVE, null);
	}

	private AbstractOrderEntryModel entry(final String productCode)
	{
		final ProductModel product = mock(ProductModel.class);
		when(product.getCode()).thenReturn(productCode);
		final AbstractOrderEntryModel entry = mock(AbstractOrderEntryModel.class);
		when(entry.getProduct()).thenReturn(product);
		entries.add(entry);
		return entry;
	}
}
