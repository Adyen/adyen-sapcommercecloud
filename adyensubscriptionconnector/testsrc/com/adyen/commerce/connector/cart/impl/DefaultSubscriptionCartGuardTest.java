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
package com.adyen.commerce.connector.cart.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.adyen.commerce.connector.cart.CartAdjustment;
import com.adyen.commerce.connector.cart.CartAssessment;
import com.adyen.commerce.connector.cart.SubscriptionCartStatus;
import com.adyen.commerce.connector.duplicate.BlockingSubscription;
import com.adyen.commerce.connector.duplicate.DuplicateCheckRequest;
import com.adyen.commerce.connector.duplicate.DuplicateCheckStage;
import com.adyen.commerce.connector.duplicate.Freshness;
import com.adyen.commerce.connector.duplicate.SubscriptionOwnershipService;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException;
import com.adyen.commerce.connector.product.SubscriptionProductRule;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.selfservice.SubscriptionLifecycleState;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * The cart rules, entry by entry: what the guard decides for an added entry, a changed one and a whole cart, and
 * what applying a decision does to the cart.
 */
@UnitTest
public class DefaultSubscriptionCartGuardTest
{
	private static final String GOLD = "GOLD-MONTHLY";
	private static final String SILVER = "SILVER-MONTHLY";
	private static final String MUG = "MUG";

	private DefaultSubscriptionCartGuard guard;
	private SubscriptionBillingConnectorRegistry registry;
	private SubscriptionProductRule productRule;
	private SubscriptionOwnershipService ownership;
	private BaseStoreService baseStoreService;
	private ModelService modelService;
	private UserService userService;
	private SubscriptionBillingConnector connector;
	private BaseStoreModel store;
	private CartModel cart;
	private CustomerModel customer;
	private final List<AbstractOrderEntryModel> entries = new ArrayList<>();
	private final List<String> subscriptionProducts = new ArrayList<>(List.of(GOLD, SILVER));

	@Before
	public void setUp() throws Exception
	{
		guard = new DefaultSubscriptionCartGuard();
		registry = mock(SubscriptionBillingConnectorRegistry.class);
		productRule = mock(SubscriptionProductRule.class);
		ownership = mock(SubscriptionOwnershipService.class);
		baseStoreService = mock(BaseStoreService.class);
		modelService = mock(ModelService.class);
		userService = mock(UserService.class);
		connector = mock(SubscriptionBillingConnector.class);
		store = mock(BaseStoreModel.class);
		cart = mock(CartModel.class);
		customer = mock(CustomerModel.class);

		guard.setConnectorRegistry(registry);
		guard.setSubscriptionProductRule(productRule);
		guard.setSubscriptionOwnershipService(ownership);
		guard.setBaseStoreService(baseStoreService);
		guard.setModelService(modelService);
		guard.setUserService(userService);

		when(store.getUid()).thenReturn("electronics");
		when(cart.getStore()).thenReturn(store);
		when(cart.getUser()).thenReturn(customer);
		when(cart.getEntries()).thenAnswer(invocation -> List.copyOf(entries));
		when(registry.getActiveConnector(store)).thenReturn(connector);
		when(userService.isAnonymousUser(any())).thenReturn(false);
		when(ownership.findBlocking(any())).thenReturn(Optional.empty());
		when(productRule.isSubscriptionProduct(any(), any(), any())).thenAnswer(
				invocation -> subscriptionProducts.contains(invocation.<ProductModel> getArgument(2).getCode()));
	}

	// --- adding ---

	@Test
	public void aSubscriptionTheCustomerAlreadyOwnsIsTakenBackOut() throws Exception
	{
		final AbstractOrderEntryModel added = entry(GOLD, 1);
		when(ownership.findBlocking(any())).thenReturn(Optional.of(blocking()));

		final CartAdjustment adjustment = guard.checkAddedEntry(cart, added).orElseThrow();

		assertEquals(SubscriptionCartStatus.ALREADY_OWNED, adjustment.statusCode());
		assertTrue(adjustment.removesEntry());
		assertEquals(DuplicateCheckStage.ADD_TO_CART, adjustment.stage());
	}

	/** Ownership is read from local records only, and the cart being filled is not evidence against itself. */
	@Test
	public void ownershipIsAskedLocallyForTheCartAndTheAddStage() throws Exception
	{
		final AbstractOrderEntryModel added = entry(GOLD, 1);

		guard.checkAddedEntry(cart, added);

		final ArgumentCaptor<DuplicateCheckRequest> request = ArgumentCaptor.forClass(DuplicateCheckRequest.class);
		verify(ownership).findBlocking(request.capture());
		assertEquals(Freshness.LOCAL, request.getValue().freshness());
		assertEquals(cart, request.getValue().excludedOrder());
		assertEquals(DuplicateCheckStage.ADD_TO_CART, request.getValue().stage());
		assertEquals(customer, request.getValue().customer());
	}

	@Test
	public void anAnonymousCartIsNotAskedWhatItOwnsButKeepsTheLimits() throws Exception
	{
		when(userService.isAnonymousUser(customer)).thenReturn(true);
		entry(GOLD, 1);
		final AbstractOrderEntryModel added = entry(SILVER, 1);

		final CartAdjustment adjustment = guard.checkAddedEntry(cart, added).orElseThrow();

		assertEquals(SubscriptionCartStatus.LIMIT_PER_CART, adjustment.statusCode());
		verify(ownership, never()).findBlocking(any());
	}

	@Test
	public void aGuestCartIsNotAskedWhatItOwnsButKeepsTheLimits() throws Exception
	{
		when(customer.getType()).thenReturn(CustomerType.GUEST);
		entry(GOLD, 1);
		final AbstractOrderEntryModel added = entry(SILVER, 1);

		final CartAdjustment adjustment = guard.checkAddedEntry(cart, added).orElseThrow();

		assertEquals(SubscriptionCartStatus.LIMIT_PER_CART, adjustment.statusCode());
		verify(ownership, never()).findBlocking(any());
	}

	@Test
	public void aSecondDifferentSubscriptionProductIsTakenBackOut() throws Exception
	{
		entry(GOLD, 1);
		final AbstractOrderEntryModel added = entry(SILVER, 1);

		final CartAdjustment adjustment = guard.checkAddedEntry(cart, added).orElseThrow();

		assertEquals(SubscriptionCartStatus.LIMIT_PER_CART, adjustment.statusCode());
		assertTrue(adjustment.removesEntry());
	}

	@Test
	public void aQuantityAboveOneIsCutToOne() throws Exception
	{
		final AbstractOrderEntryModel added = entry(GOLD, 3);

		final CartAdjustment adjustment = guard.checkAddedEntry(cart, added).orElseThrow();

		assertEquals(SubscriptionCartStatus.QUANTITY_LIMIT, adjustment.statusCode());
		assertEquals(1, adjustment.keepQuantity());
	}

	@Test
	public void theSameSubscriptionAddedAgainAddsNothing() throws Exception
	{
		entry(GOLD, 1);
		final AbstractOrderEntryModel added = entry(GOLD, 1);

		final CartAdjustment adjustment = guard.checkAddedEntry(cart, added).orElseThrow();

		assertEquals(SubscriptionCartStatus.QUANTITY_LIMIT, adjustment.statusCode());
		assertEquals(0, adjustment.keepQuantity());
	}

	@Test
	public void aFirstSubscriptionOfQuantityOneIsLeftAlone() throws Exception
	{
		final AbstractOrderEntryModel added = entry(GOLD, 1);

		assertTrue(guard.checkAddedEntry(cart, added).isEmpty());
	}

	@Test
	public void aProductThatIsNotASubscriptionIsNeverTouched() throws Exception
	{
		entry(GOLD, 1);
		final AbstractOrderEntryModel added = entry(MUG, 5);

		assertTrue(guard.checkAddedEntry(cart, added).isEmpty());
		verify(ownership, never()).findBlocking(any());
	}

	@Test
	public void aProductThatCannotBeClassifiedIsTreatedAsNotASubscription() throws Exception
	{
		final AbstractOrderEntryModel added = entry(GOLD, 3);
		org.mockito.Mockito.doThrow(new SubscriptionProductUndecidableException("cannot tell", null)).when(productRule)
				.isSubscriptionProduct(any(), any(), any());

		assertTrue(guard.checkAddedEntry(cart, added).isEmpty());
		verify(ownership, never()).findBlocking(any());
	}

	@Test
	public void aStoreWithoutABillingPlatformSellsNoSubscriptions() throws Exception
	{
		final AbstractOrderEntryModel added = entry(GOLD, 3);
		when(registry.getActiveConnector(store)).thenThrow(new ConnectorNotConfiguredException("none"));

		assertTrue(guard.checkAddedEntry(cart, added).isEmpty());
		assertTrue(guard.checkCart(cart).isEmpty());
	}

	@Test
	public void aCartWithoutAStoreIsJudgedInTheCurrentStore() throws Exception
	{
		when(cart.getStore()).thenReturn(null);
		when(baseStoreService.getCurrentBaseStore()).thenReturn(store);
		final AbstractOrderEntryModel added = entry(GOLD, 3);

		assertTrue(guard.checkAddedEntry(cart, added).isPresent());
	}

	// --- changing ---

	@Test
	public void raisingTheQuantityOfASubscriptionCutsItBackToOne() throws Exception
	{
		final AbstractOrderEntryModel updated = entry(GOLD, 2);

		final CartAdjustment adjustment = guard.checkUpdatedEntry(cart, updated).orElseThrow();

		assertEquals(SubscriptionCartStatus.QUANTITY_LIMIT, adjustment.statusCode());
		assertEquals(1, adjustment.keepQuantity());
		assertEquals(DuplicateCheckStage.UPDATE_QUANTITY, adjustment.stage());
	}

	/** Changing a quantity is not buying: a subscription already in the cart is not asked about again. */
	@Test
	public void changingAQuantityDoesNotAskWhetherTheCustomerOwnsItNorLookAtOtherProducts() throws Exception
	{
		entry(SILVER, 1);
		final AbstractOrderEntryModel updated = entry(GOLD, 1);
		when(ownership.findBlocking(any())).thenReturn(Optional.of(blocking()));

		assertTrue(guard.checkUpdatedEntry(cart, updated).isEmpty());
		verify(ownership, never()).findBlocking(any());
	}

	@Test
	public void aProductThatIsNotASubscriptionKeepsItsQuantityOnUpdate() throws Exception
	{
		final AbstractOrderEntryModel updated = entry(MUG, 9);

		assertTrue(guard.checkUpdatedEntry(cart, updated).isEmpty());
	}

	// --- validating ---

	@Test
	public void validationKeepsTheFirstSubscriptionProductAndDropsTheOthers() throws Exception
	{
		final AbstractOrderEntryModel first = entry(GOLD, 1);
		final AbstractOrderEntryModel mug = entry(MUG, 4);
		final AbstractOrderEntryModel other = entry(SILVER, 1);
		final AbstractOrderEntryModel third = entry(SILVER, 1);

		final List<CartAdjustment> adjustments = guard.checkCart(cart);

		assertEquals(2, adjustments.size());
		assertEquals(other, adjustments.get(0).entry());
		assertEquals(SubscriptionCartStatus.LIMIT_PER_CART, adjustments.get(0).statusCode());
		assertEquals(third, adjustments.get(1).entry());
		assertEquals(SubscriptionCartStatus.LIMIT_PER_CART, adjustments.get(1).statusCode());
		assertFalse(adjustments.stream().anyMatch(a -> a.entry() == first || a.entry() == mug));
		assertEquals(DuplicateCheckStage.CART_VALIDATION, adjustments.get(0).stage());
	}

	@Test
	public void validationRemovesWhatTheCustomerOwnsAndThenKeepsTheNextSubscription() throws Exception
	{
		final AbstractOrderEntryModel owned = entry(GOLD, 1);
		final AbstractOrderEntryModel next = entry(SILVER, 1);
		when(ownership.findBlocking(any())).thenAnswer(invocation ->
				GOLD.equals(invocation.<DuplicateCheckRequest> getArgument(0).product().getCode())
						? Optional.of(blocking())
						: Optional.empty());

		final List<CartAdjustment> adjustments = guard.checkCart(cart);

		assertEquals(1, adjustments.size());
		assertEquals(owned, adjustments.get(0).entry());
		assertEquals(SubscriptionCartStatus.ALREADY_OWNED, adjustments.get(0).statusCode());
		assertFalse(adjustments.stream().anyMatch(a -> a.entry() == next));
	}

	@Test
	public void validationCutsAQuantityAboveOneAndCountsWhatIsLeftForTheSameProduct() throws Exception
	{
		final AbstractOrderEntryModel big = entry(GOLD, 3);
		final AbstractOrderEntryModel again = entry(GOLD, 1);

		final List<CartAdjustment> adjustments = guard.checkCart(cart);

		assertEquals(2, adjustments.size());
		assertEquals(big, adjustments.get(0).entry());
		assertEquals(1, adjustments.get(0).keepQuantity());
		assertEquals(SubscriptionCartStatus.QUANTITY_LIMIT, adjustments.get(0).statusCode());
		assertEquals(again, adjustments.get(1).entry());
		assertEquals(0, adjustments.get(1).keepQuantity());
	}

	@Test
	public void validationOfACleanCartChangesNothing() throws Exception
	{
		entry(GOLD, 1);
		entry(MUG, 7);

		assertTrue(guard.checkCart(cart).isEmpty());
	}

	@Test
	public void validationDoesNotAskTheRuleTwiceAboutOneProduct() throws Exception
	{
		entry(MUG, 1);
		entry(MUG, 1);
		entry(MUG, 1);

		guard.checkCart(cart);

		verify(productRule, org.mockito.Mockito.times(1)).isSubscriptionProduct(any(), any(), any());
	}

	// --- applying ---

	@Test
	public void applyingARemovalRemovesTheEntryAndRefreshesTheCart() throws Exception
	{
		final AbstractOrderEntryModel entry = entry(GOLD, 1);

		guard.apply(cart, new CartAdjustment(entry, SubscriptionCartStatus.LIMIT_PER_CART, 0,
				DuplicateCheckStage.ADD_TO_CART));

		verify(modelService).remove(entry);
		verify(modelService).refresh(cart);
	}

	@Test
	public void applyingACutSetsTheQuantityAndKeepsTheEntry() throws Exception
	{
		final AbstractOrderEntryModel entry = entry(GOLD, 3);

		guard.apply(cart, new CartAdjustment(entry, SubscriptionCartStatus.QUANTITY_LIMIT, 1,
				DuplicateCheckStage.ADD_TO_CART));

		verify(entry).setQuantity(Long.valueOf(1));
		verify(modelService).save(entry);
		verify(modelService, never()).remove(any(Object.class));
	}

	// --- before authorisation ---

	/** The payment check asks ownership itself, with a fresh answer; the cart rules must not ask it, or log a block. */
	@Test
	public void theAssessmentForAuthorisationNeverAsksOwnership() throws Exception
	{
		entry(GOLD, 1);
		when(ownership.findBlocking(any())).thenReturn(Optional.of(blocking()));

		final CartAssessment assessment = guard.assessForAuthorisation(cart);

		assertEquals(1, assessment.subscriptionEntries().size());
		assertTrue(assessment.limitViolations().isEmpty());
		assertEquals(store, assessment.store());
		verify(ownership, never()).findBlocking(any());
	}

	@Test
	public void theAssessmentNamesALimitBreachAndLeavesTheCartAlone() throws Exception
	{
		entry(GOLD, 1);
		entry(SILVER, 1);
		entry(MUG, 1);

		final CartAssessment assessment = guard.assessForAuthorisation(cart);

		assertEquals(2, assessment.subscriptionEntries().size());
		assertEquals(1, assessment.limitViolations().size());
		assertEquals(SubscriptionCartStatus.LIMIT_PER_CART, assessment.limitViolations().get(0).statusCode());
		assertEquals(DuplicateCheckStage.PRE_AUTH, assessment.limitViolations().get(0).stage());
		verify(modelService, never()).remove(any(Object.class));
		verify(modelService, never()).save(any(Object.class));
	}

	@Test
	public void theAssessmentOfAQuantityAboveOneNamesTheQuantityLimit() throws Exception
	{
		entry(GOLD, 2);

		final CartAssessment assessment = guard.assessForAuthorisation(cart);

		assertEquals(SubscriptionCartStatus.QUANTITY_LIMIT, assessment.limitViolations().get(0).statusCode());
	}

	@Test
	public void theAssessmentOfACartWithoutSubscriptionsIsEmpty() throws Exception
	{
		entry(MUG, 5);

		assertFalse(guard.assessForAuthorisation(cart).hasSubscription());
	}

	@Test
	public void theAssessmentOfACartInAStoreWithoutAConnectorIsEmpty() throws Exception
	{
		entry(GOLD, 1);
		when(registry.getActiveConnector(store)).thenThrow(new ConnectorNotConfiguredException("none"));

		assertFalse(guard.assessForAuthorisation(cart).hasSubscription());
	}

	// --- fixtures ---

	private BlockingSubscription blocking()
	{
		return new BlockingSubscription(BlockingSubscription.Source.EXISTING_SUBSCRIPTION, "SUB-1",
				SubscriptionLifecycleState.ACTIVE, null);
	}

	/** An entry in the cart, in the order created. */
	private AbstractOrderEntryModel entry(final String productCode, final long quantity)
	{
		final ProductModel product = mock(ProductModel.class);
		when(product.getCode()).thenReturn(productCode);
		final AbstractOrderEntryModel entry = mock(AbstractOrderEntryModel.class);
		when(entry.getProduct()).thenReturn(product);
		when(entry.getQuantity()).thenReturn(quantity);
		when(entry.getOrder()).thenReturn(cart);
		entries.add(entry);
		return entry;
	}
}
