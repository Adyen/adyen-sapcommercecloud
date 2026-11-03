package com.adyen.v6.facades.impl;

import com.adyen.commerce.facades.impl.DefaultAdyenCheckoutFacade;
import com.adyen.model.checkout.CheckoutVoucherAction;
import com.adyen.model.checkout.PaymentResponseAction;
import com.adyen.v6.repository.OrderRepository;
import com.adyen.v6.service.AdyenVoucherService;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.order.CartFactory;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.session.SessionService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Placing a voucher (PresentToShopper) order must complete even if storing the voucher expiry fails.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.Silent.class)
public class DefaultAdyenCheckoutFacadeVoucherTest {
    @Mock
    private CartService cartService;
    @Mock
    private CartFactory cartFactory;
    @Mock
    private CheckoutFacade checkoutFacade;
    @Mock
    private SessionService sessionService;
    @Mock
    private ModelService modelService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private AdyenVoucherService voucherService;
    @Mock
    private CartModel cart;
    @Mock
    private CartModel newCart;
    @Mock
    private OrderModel order;

    private TestFacade facade;
    private final OrderData orderData = new OrderData();

    @Before
    public void setUp() throws Exception {
        orderData.setCode("00001");
        facade = new TestFacade();
        facade.setCartService(cartService);
        facade.setCartFactory(cartFactory);
        facade.setCheckoutFacade(checkoutFacade);
        facade.setSessionService(sessionService);
        facade.setModelService(modelService);
        facade.setOrderRepository(orderRepository);
        facade.setAdyenVoucherService(voucherService);
        when(cartService.getSessionCart()).thenReturn(cart);
        when(cartFactory.createCart()).thenReturn(newCart);
        when(checkoutFacade.placeOrder()).thenReturn(orderData);
        when(orderRepository.getOrderModel("00001")).thenReturn(order);
    }

    private PaymentResponseAction voucherAction() {
        return new PaymentResponseAction(new CheckoutVoucherAction());
    }

    private void assertOrderPlaced() {
        verify(sessionService).setAttribute(DefaultAdyenCheckoutFacade.SESSION_PENDING_ORDER_CODE, "00001");
        verify(cartService).setSessionCart(newCart);
    }

    @Test
    public void orderIsPlacedAndExpiryStored() throws Exception {
        final PaymentResponseAction action = voucherAction();
        assertSame(orderData, facade.placePendingOrder("PresentToShopper", action));
        verify(voucherService).storeVoucherExpiry(order, action);
        assertOrderPlaced();
    }

    @Test
    public void orderIsPlacedWhenVoucherServiceIsMissing() throws Exception {
        facade.setAdyenVoucherService(null);
        assertSame(orderData, facade.placePendingOrder("PresentToShopper", voucherAction()));
        assertOrderPlaced();
    }

    @Test
    public void orderIsPlacedWhenVoucherServiceThrows() throws Exception {
        doThrow(new IllegalStateException("boom")).when(voucherService).storeVoucherExpiry(any(), any());
        assertSame(orderData, facade.placePendingOrder("PresentToShopper", voucherAction()));
        assertOrderPlaced();
    }

    @Test
    public void orderIsPlacedWhenOrderLookupThrows() throws Exception {
        when(orderRepository.getOrderModel("00001")).thenThrow(new IllegalStateException("boom"));
        assertSame(orderData, facade.placePendingOrder("PresentToShopper", voucherAction()));
        assertOrderPlaced();
    }

    /** Exposes the protected method to the test. */
    private static class TestFacade extends DefaultAdyenCheckoutFacade {
        @Override
        public OrderData placePendingOrder(String resultCode, PaymentResponseAction action) throws InvalidCartException {
            return super.placePendingOrder(resultCode, action);
        }
    }
}
