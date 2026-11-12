package com.adyen.commerce.facades.impl;

import com.adyen.commerce.data.AdyenPartialPaymentOrderData;
import com.adyen.commerce.facades.AdyenPartialPaymentOrderFacade;
import com.adyen.model.checkout.CheckoutOrderResponse;
import com.adyen.model.checkout.EncryptedOrderData;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.repository.AdyenPartialPaymentOrderRepository;
import com.adyen.v6.service.AdyenCheckoutApiService;
import com.adyen.v6.service.PaymentAuthorisationRefusedException;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.session.SessionService;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * /orders/cancel needs the Adyen order (its pspReference and latest orderData), not the gift card balance-check
 * pspReference the record is keyed on. One Adyen order covers every gift card of the cart.
 */
@UnitTest
public class DefaultAdyenCheckoutApiFacadeOrderCancelTest
{
    private final AdyenCheckoutApiService apiService = mock(AdyenCheckoutApiService.class);
    private final AdyenPartialPaymentOrderFacade partialPaymentOrderFacade = mock(AdyenPartialPaymentOrderFacade.class);
    private final AdyenPartialPaymentOrderRepository repository = mock(AdyenPartialPaymentOrderRepository.class);
    private final CartService cartService = mock(CartService.class);
    private final CartModel cart = mock(CartModel.class);
    private final ModelService modelService = mock(ModelService.class);
    private final RequestInfo requestInfo = mock(RequestInfo.class);
    private final AdyenPartialPaymentOrderModel giftCard1 = record("BALANCE-1");
    private final AdyenPartialPaymentOrderModel giftCard2 = record("BALANCE-2");
    private DefaultAdyenCheckoutApiFacade facade;

    private static AdyenPartialPaymentOrderModel record(final String psp)
    {
        final AdyenPartialPaymentOrderModel model = mock(AdyenPartialPaymentOrderModel.class);
        when(model.getPspReference()).thenReturn(psp);
        when(model.getStatus()).thenReturn(AdyenPartialPaymentStatus.AUTHORIZED);
        return model;
    }

    @Before
    public void setUp() throws Exception
    {
        facade = spy(new DefaultAdyenCheckoutApiFacade());
        facade.setCartService(cartService);
        facade.setSessionService(mock(SessionService.class));
        facade.setAdyenPartialPaymentOrderFacade(partialPaymentOrderFacade);
        facade.setAdyenPartialPaymentOrderRepository(repository);
        doReturn(apiService).when(facade).getAdyenPaymentService();
        doReturn(modelService).when(facade).getModelService();
        doReturn("en").when(facade).getShopperLocale();
        doReturn(mock(CheckoutCustomerStrategy.class)).when(facade).getCheckoutCustomerStrategy();
        when(cartService.getSessionCart()).thenReturn(cart);
        when(cart.getAdyenPartialPaymentOrders()).thenReturn(List.of(giftCard1));
    }

    private PaymentRequest requestWithOrder(final String psp, final String data)
    {
        return new PaymentRequest().order(new EncryptedOrderData().pspReference(psp).orderData(data));
    }

    @Test
    public void aRefusedPaymentCancelsTheOrderFromTheResponse() throws Exception
    {
        when(apiService.processPaymentRequest(any(), any(), any(), any(), any())).thenReturn(new PaymentResponse()
                .resultCode(PaymentResponse.ResultCodeEnum.REFUSED)
                .order(new CheckoutOrderResponse().pspReference("ORDER-RESP").orderData("DATA-RESP")));

        assertThrows(AdyenNonAuthorizedPaymentException.class, () -> facade.placeOrderWithPayment(null, new CartData(),
                requestWithOrder("ORDER-REQ", "DATA-REQ"), requestInfo, new AdyenPartialPaymentOrderData()));

        verify(partialPaymentOrderFacade).cancelPartialPaymentOrder("BALANCE-1", "ORDER-RESP", "DATA-RESP");
        verify(partialPaymentOrderFacade, never()).cancelPartialPaymentOrder(anyString());
    }

    @Test
    public void aRefusedPaymentWithoutOrderInTheResponseCancelsTheOrderFromTheRequest() throws Exception
    {
        when(apiService.processPaymentRequest(any(), any(), any(), any(), any()))
                .thenReturn(new PaymentResponse().resultCode(PaymentResponse.ResultCodeEnum.REFUSED));

        assertThrows(AdyenNonAuthorizedPaymentException.class, () -> facade.placeOrderWithPayment(null, new CartData(),
                requestWithOrder("ORDER-REQ", "DATA-REQ"), requestInfo, new AdyenPartialPaymentOrderData()));

        verify(partialPaymentOrderFacade).cancelPartialPaymentOrder("BALANCE-1", "ORDER-REQ", "DATA-REQ");
    }

    @Test
    public void aGuardRefusalCancelsTheOrderFromTheRequest() throws Exception
    {
        when(apiService.processPaymentRequest(any(), any(), any(), any(), any()))
                .thenThrow(new PaymentAuthorisationRefusedException("blocked"));

        assertThrows(PaymentAuthorisationRefusedException.class, () -> facade.placeOrderWithPayment(null, new CartData(),
                requestWithOrder("ORDER-REQ", "DATA-REQ"), requestInfo, new AdyenPartialPaymentOrderData()));

        verify(partialPaymentOrderFacade).cancelPartialPaymentOrder("BALANCE-1", "ORDER-REQ", "DATA-REQ");
    }

    @Test
    public void withoutAnyOrderTheStoredValuesAreUsed() throws Exception
    {
        when(apiService.processPaymentRequest(any(), any(), any(), any(), any()))
                .thenReturn(new PaymentResponse().resultCode(PaymentResponse.ResultCodeEnum.REFUSED));

        assertThrows(AdyenNonAuthorizedPaymentException.class, () -> facade.placeOrderWithPayment(null, new CartData(),
                new PaymentRequest(), requestInfo, new AdyenPartialPaymentOrderData()));

        verify(partialPaymentOrderFacade).cancelPartialPaymentOrder("BALANCE-1");
    }

    @Test
    public void onlyTheCurrentRecordIsCancelledAndMarked()
    {
        // records are prepended, so giftCard2 is the newer one
        when(cart.getAdyenPartialPaymentOrders()).thenReturn(List.of(giftCard2, giftCard1));

        facade.cancelPartialPaymentOrdersOnFailure("BALANCE-2", "ORDER-1", "DATA-1");

        verify(partialPaymentOrderFacade, times(1)).cancelPartialPaymentOrder(anyString(), anyString(), anyString());
        verify(partialPaymentOrderFacade).cancelPartialPaymentOrder("BALANCE-2", "ORDER-1", "DATA-1");
        verify(giftCard1, never()).setStatus(any());
        verify(giftCard2, never()).setStatus(any());
        verify(modelService, never()).save(any());
    }

    @Test
    public void withoutCurrentRecordTheNewestOneIsCancelled()
    {
        when(cart.getAdyenPartialPaymentOrders()).thenReturn(List.of(giftCard2, giftCard1));

        facade.cancelPartialPaymentOrdersOnFailure("ORDER-1", "DATA-1");

        verify(partialPaymentOrderFacade).cancelPartialPaymentOrder("BALANCE-2", "ORDER-1", "DATA-1");
    }

    @Test
    public void emptyOrderDataInTheResponseIsTakenFromTheRequestOfTheSameOrder()
    {
        final EncryptedOrderData resolved = facade.resolveOrderToCancel(new CheckoutOrderResponse().pspReference("O"),
                new EncryptedOrderData().pspReference("O").orderData("REQ-DATA"));
        org.junit.Assert.assertEquals("REQ-DATA", resolved.getOrderData());

        final EncryptedOrderData other = facade.resolveOrderToCancel(new CheckoutOrderResponse().pspReference("O"),
                new EncryptedOrderData().pspReference("OTHER").orderData("REQ-DATA"));
        org.junit.Assert.assertNull(other.getOrderData());
    }

    @Test
    public void otherRecordsStayUntouchedWhenAdyenRejectsTheCancel()
    {
        when(cart.getAdyenPartialPaymentOrders()).thenReturn(List.of(giftCard1, giftCard2));
        doThrow(new RuntimeException("14_037")).when(partialPaymentOrderFacade)
                .cancelPartialPaymentOrder("BALANCE-1", "ORDER-1", "DATA-1");

        facade.cancelPartialPaymentOrdersOnFailure("ORDER-1", "DATA-1");

        verify(giftCard2, never()).setStatus(any());
        verify(modelService, never()).save(any());
    }

    @Test
    public void aFailedOrderDataRefreshNeverFailsTheAuthorisedLeg()
    {
        when(repository.findPartialPaymentOrderByPspReference("BALANCE-1")).thenReturn(giftCard1);
        doThrow(new RuntimeException("db down")).when(modelService).save(giftCard1);
        final PaymentResponse response = new PaymentResponse().order(new CheckoutOrderResponse().orderData("NEW"));

        facade.refreshPartialPaymentOrderData("BALANCE-1", response);

        verify(modelService).save(giftCard1);
    }
}
