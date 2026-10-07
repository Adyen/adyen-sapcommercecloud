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
    public void doesNotPersistWhenOriginalPaymentCannotBeMatchedToAnOrder() {
        when(paymentTransactionRepository.getTransactionModel("original-psp")).thenReturn(null);

        service.reconcile(donationNotification(true));

        verify(modelService, never()).create(any(Class.class));
        verify(modelService, never()).save(any());
    }

    private AdyenNotificationModel donationNotification(final boolean success) {
        final AdyenNotificationModel notification = new AdyenNotificationModel();
        notification.setMerchantReference("donation-reference");
        notification.setOriginalReference("original-psp");
        notification.setPspReference("donation-psp");
        notification.setAmountValue(BigDecimal.valueOf(1000));
        notification.setAmountCurrency("EUR");
        notification.setSuccess(success);
        return notification;
    }
}
