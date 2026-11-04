package com.adyen.v6.actions.order;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests AdyenCheckPendingOrderAction
 */
@UnitTest
@RunWith(MockitoJUnitRunner.Silent.class)
public class AdyenCheckPendingOrderActionTest {
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final String PRESENT_TO_SHOPPER = "PresentToShopper";

    @Mock
    private OrderProcessModel process;
    @Mock
    private OrderModel order;
    @Mock
    private ModelService modelService;
    @Mock
    private ConfigurationService configurationService;
    @Mock
    private Configuration configuration;
    @Mock
    private PaymentTransactionModel transaction;

    private AdyenCheckPendingOrderAction action;

    @Before
    public void setUp() {
        action = new AdyenCheckPendingOrderAction() {
            @Override
            protected Instant now() {
                return NOW;
            }
        };
        action.setConfigurationService(configurationService);
        action.setModelService(modelService);
        when(configurationService.getConfiguration()).thenReturn(configuration);
        when(process.getOrder()).thenReturn(order);
        when(process.getCode()).thenReturn("process");
        when(order.getCode()).thenReturn("00001");
        when(order.getStatus()).thenReturn(OrderStatus.PAYMENT_PENDING);
        when(order.getPaymentTransactions()).thenReturn(Collections.emptyList());
    }

    private void voucher() {
        when(order.getStatusInfo()).thenReturn(PRESENT_TO_SHOPPER);
    }

    private static Date at(Instant instant) {
        return Date.from(instant);
    }

    private void assertWait() {
        assertEquals("WAIT", action.execute(process));
        verify(order, never()).setStatus(any());
        verify(modelService, never()).save(any());
    }

    private void assertProcessingError() {
        assertEquals("NOK", action.execute(process));
        verify(order).setStatus(OrderStatus.PROCESSING_ERROR);
        verify(modelService).save(order);
    }

    @Test
    public void nonVoucherPendingOrderGoesToProcessingError() {
        when(order.getStatusInfo()).thenReturn("Pending");
        when(order.getAdyenVoucherExpiresAt()).thenReturn(at(NOW.plus(Duration.ofDays(1))));
        assertProcessingError();
    }

    @Test
    public void voucherBeforeExpiryWaits() {
        voucher();
        when(order.getAdyenVoucherExpiresAt()).thenReturn(at(NOW.plus(Duration.ofDays(1))));
        assertWait();
    }

    @Test
    public void voucherAfterExpiryPlusDefaultGraceGoesToProcessingError() {
        voucher();
        when(order.getAdyenVoucherExpiresAt()).thenReturn(at(NOW.minus(Duration.ofHours(6)).minusSeconds(1)));
        assertProcessingError();
    }

    @Test
    public void voucherAfterExpiryButWithinGraceWaits() {
        voucher();
        when(order.getAdyenVoucherExpiresAt()).thenReturn(at(NOW.minus(Duration.ofHours(5))));
        assertWait();
    }

    @Test
    public void configuredGracePeriodIsUsed() {
        voucher();
        when(configuration.getString("adyen.voucher.pending.grace.period")).thenReturn("PT1H");
        when(order.getAdyenVoucherExpiresAt()).thenReturn(at(NOW.minus(Duration.ofHours(2))));
        assertProcessingError();
    }

    @Test
    public void voucherWithoutExpiryWithinFallbackWaits() {
        voucher();
        when(order.getCreationtime()).thenReturn(at(NOW.minus(Duration.ofDays(2))));
        assertWait();
    }

    @Test
    public void voucherWithoutExpiryAfterFallbackGoesToProcessingError() {
        voucher();
        when(order.getCreationtime()).thenReturn(at(NOW.minus(Duration.ofDays(3)).minusSeconds(1)));
        assertProcessingError();
    }

    @Test
    public void invalidDurationPropertiesFallBackToDefaults() {
        voucher();
        when(configuration.getString("adyen.voucher.pending.grace.period")).thenReturn("garbage");
        when(configuration.getString("adyen.voucher.pending.fallback.max.wait")).thenReturn("3 days");
        when(order.getAdyenVoucherExpiresAt()).thenReturn(at(NOW.minus(Duration.ofHours(5))));
        assertWait();
        when(order.getAdyenVoucherExpiresAt()).thenReturn(null);
        when(order.getCreationtime()).thenReturn(at(NOW.minus(Duration.ofDays(2))));
        assertEquals("WAIT", action.execute(process));
        when(order.getCreationtime()).thenReturn(at(NOW.minus(Duration.ofDays(4))));
        assertEquals("NOK", action.execute(process));
    }

    @Test
    public void orderWithTransactionsIsOk() {
        voucher();
        when(order.getPaymentTransactions()).thenReturn(Collections.singletonList(transaction));
        assertEquals("OK", action.execute(process));
        verify(modelService, never()).save(any());
    }

    @Test
    public void cancelledOrderIsNok() {
        when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
        assertEquals("NOK", action.execute(process));
        verify(modelService, never()).save(any());
    }

    @Test
    public void nullOrderIsNok() {
        when(process.getOrder()).thenReturn(null);
        assertEquals("NOK", action.execute(process));
    }

    @Test
    public void farFutureExpiryIsCappedByMaxWait() {
        voucher();
        when(order.getCreationtime()).thenReturn(at(NOW.minus(Duration.ofDays(31))));
        when(order.getAdyenVoucherExpiresAt()).thenReturn(at(NOW.plus(Duration.ofDays(3650))));
        assertProcessingError();
    }

    @Test
    public void farFutureExpiryWithinMaxWaitWaits() {
        voucher();
        when(order.getCreationtime()).thenReturn(at(NOW.minus(Duration.ofDays(29))));
        when(order.getAdyenVoucherExpiresAt()).thenReturn(at(NOW.plus(Duration.ofDays(3650))));
        assertWait();
    }

    @Test
    public void configuredMaxWaitIsUsedAndInvalidValueFallsBackToDefault() {
        voucher();
        when(order.getCreationtime()).thenReturn(at(NOW.minus(Duration.ofDays(5))));
        when(order.getAdyenVoucherExpiresAt()).thenReturn(at(NOW.plus(Duration.ofDays(100))));
        when(configuration.getString("adyen.voucher.pending.max.wait")).thenReturn("not-a-duration");
        assertEquals("WAIT", action.execute(process));
        when(configuration.getString("adyen.voucher.pending.max.wait")).thenReturn("P2D");
        assertEquals("NOK", action.execute(process));
    }

    @Test
    public void transitionsContainWait() {
        assertEquals(java.util.Set.of("OK", "NOK", "WAIT"), action.getTransitions());
    }
}
