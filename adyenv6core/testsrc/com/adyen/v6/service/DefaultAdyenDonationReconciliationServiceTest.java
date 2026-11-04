package com.adyen.v6.service;

import com.adyen.v6.model.AdyenDonationModel;
import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.repository.AdyenDonationRepository;
import com.adyen.v6.repository.PaymentTransactionRepository;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.model.ModelService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultAdyenDonationReconciliationServiceTest {
    @Mock
    private ModelService modelService;
    @Mock
    private AdyenDonationRepository donationRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private PaymentTransactionModel transaction;
    @Mock
    private OrderModel order;
    @Mock
    private AdyenDonationModel donation;

    private DefaultAdyenDonationReconciliationService service;

    @Before
    public void setUp() {
        service = new DefaultAdyenDonationReconciliationService();
        service.setModelService(modelService);
        service.setAdyenDonationRepository(donationRepository);
        service.setPaymentTransactionRepository(paymentTransactionRepository);
    }

    @Test
    public void createsCompletedDonationLinkedToOrder() {
        final AdyenNotificationModel notification = donationNotification(true);
        when(paymentTransactionRepository.getTransactionModel("original-psp")).thenReturn(transaction);
        when(transaction.getOrder()).thenReturn(order);
        when(donationRepository.findByReference("donation-reference")).thenReturn(null);
        when(modelService.create(AdyenDonationModel.class)).thenReturn(donation);

        service.reconcile(notification);

        verify(donation).setReference("donation-reference");
        verify(donation).setOrder(order);
        verify(donation).setStatus("completed");
        verify(donation).setPspReference("donation-psp");
        verify(modelService).save(donation);
    }

    @Test
    public void updatesExistingDonationWithoutCreatingAnotherOne() {
        final AdyenNotificationModel notification = donationNotification(false);
        when(paymentTransactionRepository.getTransactionModel("original-psp")).thenReturn(transaction);
        when(transaction.getOrder()).thenReturn(order);
        when(donationRepository.findByReference("donation-reference")).thenReturn(donation);

        service.reconcile(notification);

        verify(modelService, never()).create(AdyenDonationModel.class);
        verify(donation).setStatus("refused");
        verify(modelService).save(donation);
    }

    @Test
    public void throwsWhenOriginalPaymentCannotBeMatchedToAnOrder() {
        when(paymentTransactionRepository.getTransactionModel("original-psp")).thenReturn(null);

        try {
            service.reconcile(donationNotification(true));
            fail("Expected IllegalStateException");
        } catch (final IllegalStateException expected) {
            // expected
        }

        verify(modelService, never()).create(any(Class.class));
        verify(modelService, never()).save(any());
    }

    @Test
    public void derivesOriginalReferenceFromMerchantReferenceWhenMissing() {
        final AdyenNotificationModel notification = donationNotification(true);
        when(notification.getOriginalReference()).thenReturn(null);
        when(notification.getMerchantReference()).thenReturn("ORIG-1-donation-1700000000000");
        when(paymentTransactionRepository.getTransactionModel("ORIG-1")).thenReturn(transaction);
        when(transaction.getOrder()).thenReturn(order);
        when(donationRepository.findByReference("ORIG-1-donation-1700000000000")).thenReturn(null);
        when(modelService.create(AdyenDonationModel.class)).thenReturn(donation);

        service.reconcile(notification);

        verify(donation).setOriginalPspReference("ORIG-1");
        verify(modelService).save(donation);
    }

    @Test
    public void usesLastDonationSeparatorWhenDerivingOriginalReference() {
        final AdyenNotificationModel notification = mock(AdyenNotificationModel.class, withSettings().lenient());
        when(notification.getMerchantReference()).thenReturn("A-donation-B-donation-123");

        assertEquals("A-donation-B", service.resolveOriginalReference(notification));
    }

    @Test
    public void doesNotDeriveOriginalReferenceFromNonDonationMerchantReference() {
        final AdyenNotificationModel notification = mock(AdyenNotificationModel.class, withSettings().lenient());
        when(notification.getMerchantReference()).thenReturn("order-123", "ORIG-donation-abc");
        assertNull(service.resolveOriginalReference(notification));
        assertNull(service.resolveOriginalReference(notification));
    }

    @Test
    public void throwsWhenNoOriginalReferenceCanBeResolved() {
        final AdyenNotificationModel notification = donationNotification(true);
        when(notification.getOriginalReference()).thenReturn(null);
        when(notification.getMerchantReference()).thenReturn("order-123");

        try {
            service.reconcile(notification);
            fail("Expected IllegalStateException");
        } catch (final IllegalStateException expected) {
            // expected
        }

        verify(modelService, never()).save(any());
    }

    @Test
    public void throwsWhenMerchantReferenceIsMissing() {
        final AdyenNotificationModel notification = donationNotification(true);
        when(notification.getMerchantReference()).thenReturn(null);

        try {
            service.reconcile(notification);
            fail("Expected IllegalStateException");
        } catch (final IllegalStateException expected) {
            // expected
        }

        verify(modelService, never()).save(any());
    }

    private AdyenNotificationModel donationNotification(final boolean success) {
        final AdyenNotificationModel notification = mock(AdyenNotificationModel.class, withSettings().lenient());
        when(notification.getMerchantReference()).thenReturn("donation-reference");
        when(notification.getOriginalReference()).thenReturn("original-psp");
        when(notification.getPspReference()).thenReturn("donation-psp");
        when(notification.getAmountValue()).thenReturn(BigDecimal.valueOf(1000));
        when(notification.getAmountCurrency()).thenReturn("EUR");
        when(notification.getSuccess()).thenReturn(success);
        return notification;
    }
}
