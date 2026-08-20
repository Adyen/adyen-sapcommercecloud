package com.adyen.commerce.connector.payment;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.PlanNotMappedException;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
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
		paymentRequest = mock(PaymentRequest.class);

		decorator = new SubscriptionPaymentRequestDecorator();
		decorator.setCartService(cartService);
		decorator.setConnectorRegistry(connectorRegistry);

		when(cartService.getSessionCart()).thenReturn(cart);
		when(cart.getStore()).thenReturn(store);
		when(store.getUid()).thenReturn("electronics");
		when(store.getActiveBillingPlatform()).thenReturn(BillingPlatform.RECURLY);
		when(connectorRegistry.getActiveConnector(store)).thenReturn(connector);
		when(connector.platform()).thenReturn(BillingPlatform.RECURLY);
	}

	@Test
	public void allowsSchemeAndForcesSubscriptionTokenization() throws Exception
	{
		givenMappedProduct("300938");
		cartData.setAdyenPaymentMethod("scheme");

		decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);

		verify(paymentRequest).setEnableRecurring(null);
		verify(paymentRequest).setEnableOneClick(null);
		verify(paymentRequest).setStorePaymentMethod(true);
		verify(paymentRequest).setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
	}

	@Test
	public void allowsStoredCard() throws Exception
	{
		givenMappedProduct("300938");
		cartData.setAdyenPaymentMethod("adyen_oneclick_token-1");

		decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);

		verify(paymentRequest).setStorePaymentMethod(true);
	}

	@Test
	public void rejectsKlarnaBeforePayment() throws Exception
	{
		givenMappedProduct("300938");
		cartData.setAdyenPaymentMethod("klarna");

		try
		{
			decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);
			fail("Expected unsupported payment method rejection");
		}
		catch (final IllegalArgumentException e)
		{
			assertTrue(e.getMessage().contains("select a credit or debit card"));
		}
	}

	@Test
	public void leavesOrdinaryCartUntouched() throws Exception
	{
		givenUnmappedProduct("ordinary");
		cartData.setAdyenPaymentMethod("klarna");

		decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);

		verify(connector).resolvePlan(any(PlanResolutionRequest.class));
		verify(paymentRequest, never()).setStorePaymentMethod(any());
	}

	@Test
	public void leavesStoreWithoutConnectorConfigurationUntouched() throws Exception
	{
		when(store.getActiveBillingPlatform()).thenReturn(null);

		decorator.decoratePaymentRequest(paymentRequest, cartData, null, null, null);

		verify(connectorRegistry, never()).getActiveConnector(any());
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

	private void givenProduct(final String code)
	{
		final ProductModel product = mock(ProductModel.class);
		final AbstractOrderEntryModel entry = mock(AbstractOrderEntryModel.class);
		when(product.getCode()).thenReturn(code);
		when(entry.getProduct()).thenReturn(product);
		when(cart.getEntries()).thenReturn(List.of(entry));
	}
}
