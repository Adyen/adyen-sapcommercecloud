package com.adyen.v6.service;

import com.adyen.model.checkout.CheckoutVoucherAction;
import com.adyen.model.checkout.CheckoutRedirectAction;
import com.adyen.model.checkout.PaymentResponseAction;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.model.ModelService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests DefaultAdyenVoucherService
 */
@UnitTest
@RunWith(MockitoJUnitRunner.Silent.class)
public class DefaultAdyenVoucherServiceTest {
    @Mock
    private OrderModel order;
    @Mock
    private ModelService modelService;

    private DefaultAdyenVoucherService service;

    @Before
    public void setUp() {
        service = new DefaultAdyenVoucherService();
        service.setModelService(modelService);
    }

    private PaymentResponseAction voucher(final String expiresAt) {
        final CheckoutVoucherAction voucher = new CheckoutVoucherAction();
        voucher.setExpiresAt(expiresAt);
        return new PaymentResponseAction(voucher);
    }

    @Test
    public void validExpiryIsStored() {
        service.storeVoucherExpiry(order, voucher("2026-11-10T23:59:59+09:00"));
        verify(order).setAdyenVoucherExpiresAt(Date.from(Instant.parse("2026-11-10T14:59:59Z")));
        verify(modelService).save(order);
    }

    @Test
    public void expiryWithoutOffsetIsTreatedAsUtc() {
        service.storeVoucherExpiry(order, voucher("2023-03-20T16:51:00"));
        verify(order).setAdyenVoucherExpiresAt(Date.from(Instant.parse("2023-03-20T16:51:00Z")));
        verify(modelService).save(order);
    }

    @Test
    public void dateOnlyExpiryIsEndOfDayUtc() {
        service.storeVoucherExpiry(order, voucher("2023-03-20"));
        verify(order).setAdyenVoucherExpiresAt(Date.from(Instant.parse("2023-03-20T23:59:59.999999999Z")));
        verify(modelService).save(order);
    }

    @Test
    public void nullExpiryIsNotStored() {
        service.storeVoucherExpiry(order, voucher(null));
        verify(order, never()).setAdyenVoucherExpiresAt(any());
        verify(modelService, never()).save(any());
    }

    @Test
    public void garbageExpiryIsNotStoredAndDoesNotThrow() {
        service.storeVoucherExpiry(order, voucher("next tuesday"));
        verify(order, never()).setAdyenVoucherExpiresAt(any());
        verify(modelService, never()).save(any());
    }

    @Test
    public void nonVoucherActionDoesNothing() {
        service.storeVoucherExpiry(order, new PaymentResponseAction(new CheckoutRedirectAction()));
        service.storeVoucherExpiry(order, null);
        verify(order, never()).setAdyenVoucherExpiresAt(any());
        verify(modelService, never()).save(any());
    }
}
