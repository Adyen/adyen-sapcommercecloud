package com.adyen.commerce.facades.impl;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.checkout.ResponsePaymentMethod;
import com.adyen.v6.repository.OrderRepository;
import com.adyen.v6.service.AdyenOrderService;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.order.CartService;

@UnitTest
public class DefaultAdyenCheckoutFacadePaymentInfoTest
{
    private CartService cartService;
    private CheckoutFacade checkoutFacade;
    private OrderRepository orderRepository;
    private AdyenOrderService adyenOrderService;
    private TestableAdyenCheckoutFacade facade;

    @Before
    public void setUp()
    {
        cartService = mock(CartService.class);
        checkoutFacade = mock(CheckoutFacade.class);
        orderRepository = mock(OrderRepository.class);
        adyenOrderService = mock(AdyenOrderService.class);

        facade = new TestableAdyenCheckoutFacade();
        facade.setCartService(cartService);
        facade.setCheckoutFacade(checkoutFacade);
        facade.setOrderRepository(orderRepository);
        facade.setAdyenOrderService(adyenOrderService);
    }

    @Test
    public void storesPaymentInfoOnCartBeforePlaceOrderAndThenOnOrder() throws Exception
    {
        final CartModel cart = mock(CartModel.class);
        final OrderModel order = mock(OrderModel.class);
        final OrderData orderData = mock(OrderData.class);
        final PaymentResponse response = mock(PaymentResponse.class);
        final ResponsePaymentMethod paymentMethod = mock(ResponsePaymentMethod.class);
        final Map<String, String> additionalData = Map.of("networkTxReference", "NTID-42");

        when(cartService.getSessionCart()).thenReturn(cart);
        when(checkoutFacade.placeOrder()).thenReturn(orderData);
        when(orderData.getCode()).thenReturn("00060001");
        when(orderRepository.getOrderModel("00060001")).thenReturn(order);
        when(response.getPaymentMethod()).thenReturn(paymentMethod);
        when(paymentMethod.getType()).thenReturn("scheme");
        when(response.getAdditionalData()).thenReturn(additionalData);

        assertSame(orderData, facade.createOrder(response));

        final InOrder sequence = inOrder(adyenOrderService, checkoutFacade);
        sequence.verify(adyenOrderService).updatePaymentInfo(cart, "scheme", additionalData);
        sequence.verify(checkoutFacade).placeOrder();
        verify(adyenOrderService).updatePaymentInfo(order, "scheme", additionalData);
    }

    private static class TestableAdyenCheckoutFacade extends DefaultAdyenCheckoutFacade
    {
        OrderData createOrder(final PaymentResponse response) throws Exception
        {
            return createOrderFromPaymentResponse(response);
        }
    }
}
