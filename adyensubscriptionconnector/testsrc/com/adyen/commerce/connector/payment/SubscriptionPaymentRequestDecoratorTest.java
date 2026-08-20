package com.adyen.commerce.connector.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException;
import com.adyen.commerce.connector.product.impl.DefaultSubscriptionProductRule;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.commerce.services.impl.RecurringContractHelper;
import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.store.BaseStoreModel;

@UnitTest
public class SubscriptionPaymentRequestDecoratorTest
{
	private static final String STORED_REFERENCE = "8415995487014051";

	private SubscriptionPaymentRequestDecorator decorator;
	private CartService cartService;
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	private SubscriptionBillingConnector connector;
	private CartModel cart;
	private BaseStoreModel store;
	private CartData cartData;
	private PaymentRequest paymentRequest;

	@Before
	public void setUp() throws Exception
	{
		cartService = mock(CartService.class);
		connectorRegistry = mock(SubscriptionBillingConnectorRegistry.class);
		connector = mock(SubscriptionBillingConnector.class);
		cart = mock(CartModel.class);
		store = mock(BaseStoreModel.class);
		cartData = new CartData();
		paymentRequest = newCardRequest();

		decorator = new SubscriptionPaymentRequestDecorator();
		decorator.setCartService(cartService);
		decorator.setConnectorRegistry(connectorRegistry);
		// The real rule, not a mock: it is now the only copy of "what counts as a subscription product", and
		// what these tests are really asserting is what this class does with each of its three answers.
		decorator.setSubscriptionProductRule(new DefaultSubscriptionProductRule());

		when(cartService.getSessionCart()).thenReturn(cart);
		when(cart.getStore()).thenReturn(store);
		when(store.getUid()).thenReturn("electronics");
		when(store.getActiveBillingPlatform()).thenReturn(BillingPlatform.RECURLY);
		when(connectorRegistry.findConnector(BillingPlatform.RECURLY)).thenReturn(Optional.of(connector));
		when(connector.platform()).thenReturn(BillingPlatform.RECURLY);
	}

	// ------------------------------------------------------ subscription cart

	@Test
	public void newCardIsStoredUnderTheSubscriptionContract() throws Exception
	{
		givenMappedProduct("300938");
		cartData.setAdyenPaymentMethod("scheme");

		decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);

		assertTrue(paymentRequest.getStorePaymentMethod());
		assertEquals(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION, paymentRequest.getRecurringProcessingModel());
		assertEquals(PaymentRequest.ShopperInteractionEnum.ECOMMERCE, paymentRequest.getShopperInteraction());
		assertNull(paymentRequest.getEnableRecurring());
		assertNull(paymentRequest.getEnableOneClick());
	}

	@Test
	public void plainCardConstantIsAcceptedJustLikeScheme() throws Exception
	{
		givenMappedProduct("300938");
		cartData.setAdyenPaymentMethod("card");

		decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);

		assertTrue(paymentRequest.getStorePaymentMethod());
		assertEquals(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION, paymentRequest.getRecurringProcessingModel());
	}

	/**
	 * The Checkout API rejects storePaymentMethod next to either deprecated flag, and a request assembled by
	 * an older storefront handler can still carry them.
	 */
	@Test
	public void deprecatedFlagsAreClearedBeforeTokenizationIsRequested() throws Exception
	{
		givenMappedProduct("300938");
		cartData.setAdyenPaymentMethod("scheme");
		paymentRequest.setEnableRecurring(Boolean.TRUE);
		paymentRequest.setEnableOneClick(Boolean.TRUE);

		decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);

		assertNull(paymentRequest.getEnableRecurring());
		assertNull(paymentRequest.getEnableOneClick());
		assertTrue(paymentRequest.getStorePaymentMethod());
	}

	/**
	 * A saved card already has its reference; asking Adyen to mint a second one would leave the shopper with a
	 * duplicate stored card. The contract itself still has to be declared, which is what the connector needs.
	 */
	@Test
	public void savedCardDeclaresTheContractWithoutBeingStoredAgain() throws Exception
	{
		givenMappedProduct("300938");
		cartData.setAdyenPaymentMethod("adyen_oneclick_" + STORED_REFERENCE);
		paymentRequest = storedCardRequest();

		decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);

		assertFalse(paymentRequest.getStorePaymentMethod());
		assertEquals(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION, paymentRequest.getRecurringProcessingModel());
	}

	/**
	 * The {@code adyen_oneclick_} prefix marks any saved method, not a saved card, so it is not on its own a
	 * reason to tokenize.
	 */
	@Test
	public void savedSelectionThatProducedNoCardTokenIsRejected() throws Exception
	{
		givenMappedProduct("300938");
		cartData.setAdyenPaymentMethod("adyen_oneclick_" + STORED_REFERENCE);
		paymentRequest = new PaymentRequest();

		expectRejection();
	}

	@Test
	public void unsupportedMethodIsRejectedWithAMessageTheCheckoutLayerCanShow() throws Exception
	{
		givenMappedProduct("300938");
		cartData.setAdyenPaymentMethod("klarna");

		try
		{
			decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);
			fail("Expected the unsupported payment method to be rejected");
		}
		catch (final RecurringContractHelper.TokenizationNotSupportedException e)
		{
			assertEquals(RecurringContractHelper.PAYMENT_METHOD_NOT_SUPPORTED, e.getErrorCode());
			assertTrue(e.getMessage().contains("klarna"));
		}
		assertNull("nothing may be tokenized on the way out", paymentRequest.getStorePaymentMethod());
	}

	@Test
	public void missingPaymentMethodIsRejectedRatherThanTokenizedBlindly() throws Exception
	{
		givenMappedProduct("300938");
		cartData.setAdyenPaymentMethod(null);

		expectRejection();
	}

	// -------------------------------------------------- non-subscription cart

	@Test
	public void ordinaryCartOnAnUnsupportedMethodIsUntouched() throws Exception
	{
		givenUnmappedProduct("ordinary");
		cartData.setAdyenPaymentMethod("klarna");

		decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);

		verify(connector).resolvePlan(any(PlanResolutionRequest.class));
		assertUntouched(paymentRequest);
	}

	/**
	 * The regression that matters most: an ordinary cart paid by card must keep exactly the contract the
	 * payment method handlers chose, not be upgraded to a subscription one on the way past.
	 */
	@Test
	public void ordinaryCartOnACardKeepsTheContractTheHandlersChose() throws Exception
	{
		givenUnmappedProduct("ordinary");
		cartData.setAdyenPaymentMethod("scheme");
		paymentRequest.setStorePaymentMethod(Boolean.TRUE);
		paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE);
		paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);

		decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);

		assertTrue(paymentRequest.getStorePaymentMethod());
		assertEquals(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE, paymentRequest.getRecurringProcessingModel());
		assertEquals(PaymentRequest.ShopperInteractionEnum.ECOMMERCE, paymentRequest.getShopperInteraction());
	}

	@Test
	public void cartWithNoEntriesIsUntouched() throws Exception
	{
		when(cart.getEntries()).thenReturn(null);
		cartData.setAdyenPaymentMethod("klarna");

		decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);

		assertUntouched(paymentRequest);
	}

	@Test
	public void storeWithoutConnectorConfigurationIsUntouched() throws Exception
	{
		when(store.getActiveBillingPlatform()).thenReturn(null);
		cartData.setAdyenPaymentMethod("klarna");

		decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);

		verify(connectorRegistry, never()).findConnector(any());
		assertUntouched(paymentRequest);
	}

	/**
	 * A store whose {@code activeBillingPlatform} names a platform no connector bean answers for — a
	 * deployment that simply does not include that adapter. The checkout is refused, and the cost is real:
	 * this takes down every checkout in the store, ordinary carts included. It is still the lesser harm.
	 * "No connector" does not mean "nothing here is a subscription" — the plan mappings and the subscription
	 * products outlive the adapter being removed — it means the question cannot be answered. Letting the cart
	 * through would send the payment out untokenized, and the activator would meet the same missing connector
	 * and dead-letter the attempt at once, {@code ConnectorNotConfiguredException} being terminal: a paid
	 * order, no subscription, no retry.
	 */
	@Test
	public void missingConnectorBeanRefusesRatherThanGuessing() throws Exception
	{
		when(connectorRegistry.findConnector(any())).thenReturn(Optional.empty());
		givenProduct("300938");
		cartData.setAdyenPaymentMethod("scheme");

		try
		{
			decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);
			fail("Expected the checkout to be refused while the store's connector is missing");
		}
		catch (final IllegalStateException e)
		{
			assertTrue(e.getMessage().contains("RECURLY"));
		}

		verify(connector, never()).resolvePlan(any(PlanResolutionRequest.class));
		assertUntouched(paymentRequest);
	}

	/**
	 * The decorator classifies every entry rather than stopping at the first match, so that it and the
	 * activator reach the same verdict on a mixed cart whichever order the entries happen to be in. With the
	 * mapped product first, an early return would have tokenized and let the order be paid — and the
	 * activator, which does scan everything, would then have refused it and dead-lettered after its retries.
	 */
	@Test
	public void undecidableEntryAfterAMappedOneStillRefuses() throws Exception
	{
		givenProducts("300938", "300939");
		when(connector.resolvePlan(argThat(r -> r != null && "300938".equals(r.productCode()))))
				.thenReturn(new PlanRef("monthly", null));
		when(connector.resolvePlan(argThat(r -> r != null && "300939".equals(r.productCode()))))
				.thenThrow(new IllegalStateException("FlexibleSearch is unhappy"));
		cartData.setAdyenPaymentMethod("scheme");

		try
		{
			decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);
			fail("Expected the undecidable entry to refuse the checkout even though another entry resolved");
		}
		catch (final IllegalStateException e)
		{
			assertTrue(e.getMessage().contains("300939"));
		}

		assertUntouched(paymentRequest);
	}

	/**
	 * The one place this deliberately does not mirror {@code DefaultSubscriptionOrderActivator}: both ask the
	 * same {@code SubscriptionProductRule}, and both get the same "could not tell", but only this one refuses.
	 * Carrying on here would send the request without forced tokenization and the shopper would be charged for
	 * a subscription that can never be activated. Before the money moves, the safe answer is to stop.
	 */
	@Test
	public void resolverThatBlowsUpStopsTheCheckoutRatherThanChargingBlindly() throws Exception
	{
		givenProduct("300938");
		when(connector.resolvePlan(any(PlanResolutionRequest.class)))
				.thenThrow(new IllegalStateException("FlexibleSearch is unhappy"));
		cartData.setAdyenPaymentMethod("scheme");

		try
		{
			decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);
			fail("Expected the checkout to fail rather than authorize an untokenized subscription payment");
		}
		catch (final IllegalStateException e)
		{
			assertTrue(e.getMessage().contains("300938"));
			// The cause is what makes it diagnosable, and what tells this apart from a bug in the decorator.
			assertTrue(e.getCause() instanceof SubscriptionProductUndecidableException);
		}

		assertUntouched(paymentRequest);
	}

	/**
	 * A resolver refusing with a checked {@code BillingException} that is not "no mapping" is the same
	 * "could not tell" as an unchecked blow-up, and gets the same refusal.
	 */
	@Test
	public void resolverThatFailsWithABillingExceptionAlsoStopsTheCheckout() throws Exception
	{
		givenProduct("300938");
		when(connector.resolvePlan(any(PlanResolutionRequest.class)))
				.thenThrow(new RetryableBillingException("Recurly timed out"));
		cartData.setAdyenPaymentMethod("scheme");

		try
		{
			decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);
			fail("Expected the checkout to fail rather than authorize an untokenized subscription payment");
		}
		catch (final IllegalStateException e)
		{
			assertTrue(e.getCause() instanceof SubscriptionProductUndecidableException);
		}

		assertUntouched(paymentRequest);
	}

	// -------------------------------------------------------------- helpers

	private void expectRejection()
	{
		try
		{
			decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);
			fail("Expected the payment method to be rejected before authorization");
		}
		catch (final RecurringContractHelper.TokenizationNotSupportedException e)
		{
			assertEquals(RecurringContractHelper.PAYMENT_METHOD_NOT_SUPPORTED, e.getErrorCode());
		}
	}

	private void assertUntouched(final PaymentRequest request)
	{
		assertNull("storePaymentMethod", request.getStorePaymentMethod());
		assertNull("recurringProcessingModel", request.getRecurringProcessingModel());
		assertNull("shopperInteraction", request.getShopperInteraction());
	}

	private PaymentRequest newCardRequest()
	{
		final PaymentRequest request = new PaymentRequest();
		request.setPaymentMethod(new CheckoutPaymentMethod(
				new CardDetails().encryptedCardNumber("test_4111111111111111").type(CardDetails.TypeEnum.SCHEME)));
		return request;
	}

	private PaymentRequest storedCardRequest()
	{
		final PaymentRequest request = new PaymentRequest();
		request.setPaymentMethod(new CheckoutPaymentMethod(
				new CardDetails().recurringDetailReference(STORED_REFERENCE)));
		return request;
	}

	private void givenMappedProduct(final String code) throws Exception
	{
		givenProduct(code);
		when(connector.resolvePlan(any(PlanResolutionRequest.class))).thenReturn(new PlanRef("plan-1", null));
	}

	private void givenUnmappedProduct(final String code) throws Exception
	{
		givenProduct(code);
		when(connector.resolvePlan(any(PlanResolutionRequest.class)))
				.thenThrow(new PlanNotMappedException("not mapped"));
	}

	/** Entries in the given order, so a test can pin the outcome to the position of the awkward one. */
	private void givenProducts(final String... codes)
	{
		final List<AbstractOrderEntryModel> entries = new ArrayList<>();
		for (final String code : codes)
		{
			final ProductModel product = mock(ProductModel.class);
			final AbstractOrderEntryModel entry = mock(AbstractOrderEntryModel.class);
			when(product.getCode()).thenReturn(code);
			when(entry.getProduct()).thenReturn(product);
			entries.add(entry);
		}
		when(cart.getEntries()).thenReturn(entries);
	}

	private void givenProduct(final String code)
	{
		final ProductModel product = mock(ProductModel.class);
		final AbstractOrderEntryModel entry = mock(AbstractOrderEntryModel.class);
		when(product.getCode()).thenReturn(code);
		when(entry.getProduct()).thenReturn(product);
		when(cart.getEntries()).thenReturn(List.of(entry));
	}
}
