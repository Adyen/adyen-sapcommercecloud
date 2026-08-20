package com.adyen.commerce.facades.impl;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doThrow;
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
import com.adyen.v6.service.AdyenTransactionService;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.order.CartService;

@UnitTest
public class DefaultAdyenCheckoutFacadePaymentInfoTest
{
    private static final Map<String, String> ADDITIONAL_DATA = Map.of("networkTxReference", "NTID-42");
    private static final String RECURRING_DETAIL_REFERENCE = "recurring.recurringDetailReference";

    private CartService cartService;
    private CheckoutFacade checkoutFacade;
    private OrderRepository orderRepository;
    private AdyenOrderService adyenOrderService;
    private AdyenTransactionService adyenTransactionService;
    private TestableAdyenCheckoutFacade facade;

    private CartModel cart;
    private OrderModel order;
    private OrderData orderData;
    private PaymentResponse response;

    @Before
    public void setUp() throws Exception
    {
        cartService = mock(CartService.class);
        checkoutFacade = mock(CheckoutFacade.class);
        orderRepository = mock(OrderRepository.class);
        adyenOrderService = mock(AdyenOrderService.class);
        adyenTransactionService = mock(AdyenTransactionService.class);

        facade = new TestableAdyenCheckoutFacade();
        facade.setCartService(cartService);
        facade.setCheckoutFacade(checkoutFacade);
        facade.setOrderRepository(orderRepository);
        facade.setAdyenOrderService(adyenOrderService);
        facade.setAdyenTransactionService(adyenTransactionService);

        cart = mock(CartModel.class);
        order = mock(OrderModel.class);
        orderData = mock(OrderData.class);
        response = mock(PaymentResponse.class);
        final ResponsePaymentMethod paymentMethod = mock(ResponsePaymentMethod.class);

        when(cartService.getSessionCart()).thenReturn(cart);
        when(checkoutFacade.placeOrder()).thenReturn(orderData);
        when(orderData.getCode()).thenReturn("00060001");
        when(orderRepository.getOrderModel("00060001")).thenReturn(order);
        when(response.getPaymentMethod()).thenReturn(paymentMethod);
        when(paymentMethod.getType()).thenReturn("scheme");
        when(response.getAdditionalData()).thenReturn(ADDITIONAL_DATA);
    }

    @Test
    public void storesPaymentInfoOnCartBeforePlaceOrderAndThenOnOrder() throws Exception
    {
        assertSame(orderData, facade.createOrder(response));

        final InOrder sequence = inOrder(adyenOrderService, checkoutFacade);
        sequence.verify(adyenOrderService).updatePaymentInfo(cart, "scheme", ADDITIONAL_DATA);
        sequence.verify(checkoutFacade).placeOrder();
        sequence.verify(adyenOrderService).updatePaymentInfo((AbstractOrderModel) order, "scheme", ADDITIONAL_DATA);
    }

    /**
     * The payment is authorized before any of this runs, so the cart write - which is the optional half of
     * the pair - must not be able to leave the shopper charged and orderless.
     */
    @Test
    public void placesTheOrderEvenWhenTheCartWriteFails() throws Exception
    {
        doThrow(new IllegalStateException("no payment info on this cart"))
                .when(adyenOrderService).updatePaymentInfo(cart, "scheme", ADDITIONAL_DATA);

        assertSame(orderData, facade.createOrder(response));

        verify(checkoutFacade).placeOrder();
        verify(adyenOrderService).updatePaymentInfo((AbstractOrderModel) order, "scheme", ADDITIONAL_DATA);
    }

    @Test
    public void placesTheOrderEvenWithoutASessionCart() throws Exception
    {
        when(cartService.getSessionCart()).thenReturn(null);

        assertSame(orderData, facade.createOrder(response));

        verify(checkoutFacade).placeOrder();
        verify(adyenOrderService).updatePaymentInfo((AbstractOrderModel) order, "scheme", ADDITIONAL_DATA);
    }

    @Test
    public void storesTheRecurringReferenceOnTheCartPaymentInfo()
    {
        final PaymentInfoModel paymentInfo = mock(PaymentInfoModel.class);
        when(response.getAdditionalData()).thenReturn(Map.of(RECURRING_DETAIL_REFERENCE, "REF-1"));
        when(cart.getPaymentInfo()).thenReturn(paymentInfo);

        facade.updateAdyenSelectedReferenceIfPresent(cart, response);

        verify(paymentInfo).setAdyenSelectedReference("REF-1");
    }

    /**
     * The same cart-without-payment-info shape the pre-place-order write already tolerates, on the other
     * path that dereferences it. Adyen has accepted the payment by the time this runs, so the missing
     * reference has to cost the reference and nothing else.
     */
    @Test
    public void placesTheOrderWhenTheCartCarriesNoPaymentInfoForTheRecurringReference() throws Exception
    {
        when(response.getAdditionalData()).thenReturn(Map.of(RECURRING_DETAIL_REFERENCE, "REF-1"));
        when(cart.getPaymentInfo()).thenReturn(null);

        assertSame(orderData, facade.createAuthorized(response));

        verify(checkoutFacade).placeOrder();
    }

    private static class TestableAdyenCheckoutFacade extends DefaultAdyenCheckoutFacade
    {
        OrderData createOrder(final PaymentResponse response) throws Exception
        {
            return createOrderFromPaymentResponse(response);
        }

        OrderData createAuthorized(final PaymentResponse response) throws Exception
        {
            return createAuthorizedOrder(response);
        }
    }
}
