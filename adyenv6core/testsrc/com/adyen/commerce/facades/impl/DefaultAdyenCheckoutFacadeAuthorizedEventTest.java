package com.adyen.commerce.facades.impl;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.checkout.ResponsePaymentMethod;
import com.adyen.v6.event.AdyenPaymentAuthorizedEventPublisher;
import com.adyen.v6.repository.OrderRepository;
import com.adyen.v6.service.AdyenOrderService;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.order.CartService;

/**
 * Which ordinary (non-3DS) checkout outcomes announce an authorization.
 *
 * <p>This is the announcement subscription activation runs on for a plain card payment. Waiting for Adyen's
 * notification instead does not work: it arrives while placeOrder() is still running, looks the order up
 * before it exists, and gives up silently.</p>
 */
@UnitTest
public class DefaultAdyenCheckoutFacadeAuthorizedEventTest
{
    private static final Map<String, String> ADDITIONAL_DATA = Map.of("networkTxReference", "NTID-42");

    private CartService cartService;
    private CheckoutFacade checkoutFacade;
    private OrderRepository orderRepository;
    private AdyenOrderService adyenOrderService;
    private AdyenPaymentAuthorizedEventPublisher publisher;
    private TestableAdyenCheckoutFacade facade;

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
        publisher = mock(AdyenPaymentAuthorizedEventPublisher.class);

        facade = new TestableAdyenCheckoutFacade();
        facade.setCartService(cartService);
        facade.setCheckoutFacade(checkoutFacade);
        facade.setOrderRepository(orderRepository);
        facade.setAdyenOrderService(adyenOrderService);
        facade.setAdyenPaymentAuthorizedEventPublisher(publisher);

        order = mock(OrderModel.class);
        response = mock(PaymentResponse.class);
        orderData = mock(OrderData.class);
        final ResponsePaymentMethod paymentMethod = mock(ResponsePaymentMethod.class);
        final CartModel cart = mock(CartModel.class);

        when(cartService.getSessionCart()).thenReturn(cart);
        when(checkoutFacade.placeOrder()).thenReturn(orderData);
        when(orderData.getCode()).thenReturn("00060001");
        when(orderRepository.getOrderModel("00060001")).thenReturn(order);
        when(response.getPaymentMethod()).thenReturn(paymentMethod);
        when(paymentMethod.getType()).thenReturn("scheme");
        when(response.getAdditionalData()).thenReturn(ADDITIONAL_DATA);
    }

    /**
     * The order must exist and carry its token before the announcement, which is the whole reason the
     * announcement is made here rather than from the notification.
     */
    @Test
    public void announcesTheAuthorizationOnceTheOrderExistsAndCarriesItsToken() throws Exception
    {
        when(response.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.AUTHORISED);

        facade.createOrder(response);

        final InOrder sequence = inOrder(checkoutFacade, adyenOrderService, publisher);
        sequence.verify(checkoutFacade).placeOrder();
        sequence.verify(adyenOrderService).updatePaymentInfo((AbstractOrderModel) order, "scheme", ADDITIONAL_DATA);
        sequence.verify(publisher).publishAuthorized(order);
    }

    /**
     * The one that would cost real money. A pending payment is routed into the very same method as an
     * authorized one and leaves an identical authorization transaction entry behind, so nothing about the
     * order distinguishes them - only the result code does.
     */
    @Test
    public void announcesNothingForAPendingPayment() throws Exception
    {
        when(response.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.PENDING);

        facade.createOrder(response);

        verify(publisher, never()).publishAuthorized(order);
    }

    @Test
    public void announcesNothingForAPaymentOnlyReceived() throws Exception
    {
        when(response.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.RECEIVED);

        facade.createOrder(response);

        verify(publisher, never()).publishAuthorized(order);
    }

    @Test
    public void announcesNothingWhenTheShopperStillHasSomethingToDo() throws Exception
    {
        when(response.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.PRESENTTOSHOPPER);

        facade.createOrder(response);

        verify(publisher, never()).publishAuthorized(order);
    }

    @Test
    public void announcesNothingWithoutAResultCode() throws Exception
    {
        when(response.getResultCode()).thenReturn(null);

        facade.createOrder(response);

        verify(publisher, never()).publishAuthorized(order);
    }

    /**
     * The shopper has already been charged by this point, so the announcement is not allowed to cost the
     * order: the fraud report and the returned OrderData still have to happen.
     */
    @Test
    public void stillFinishesTheOrderWhenTheAnnouncementFails() throws Exception
    {
        when(response.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.AUTHORISED);
        doThrow(new IllegalStateException("multicaster is down")).when(publisher).publishAuthorized(order);

        // Fails the test by propagating if the announcement is ever allowed to escape.
        assertSame(orderData, facade.createOrder(response));

        verify(adyenOrderService).storeFraudReport(eq(order), any(), any());
    }

    private static class TestableAdyenCheckoutFacade extends DefaultAdyenCheckoutFacade
    {
        OrderData createOrder(final PaymentResponse response) throws Exception
        {
            return createOrderFromPaymentResponse(response);
        }
    }
}
