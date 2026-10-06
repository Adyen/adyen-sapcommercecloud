package com.adyen.v6.service;

import com.adyen.v6.model.AdyenDonationModel;
import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.repository.AdyenDonationRepository;
import com.adyen.v6.repository.PaymentTransactionRepository;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.model.ModelService;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

public class DefaultAdyenDonationReconciliationService implements AdyenDonationReconciliationService {
    private static final Logger LOG = Logger.getLogger(DefaultAdyenDonationReconciliationService.class);
    private static final String COMPLETED = "completed";
    private static final String REFUSED = "refused";

    private ModelService modelService;
    private AdyenDonationRepository adyenDonationRepository;
    private PaymentTransactionRepository paymentTransactionRepository;

    @Override
    public void reconcile(final AdyenNotificationModel notification) {
        if (StringUtils.isAnyBlank(notification.getMerchantReference(), notification.getOriginalReference())) {
            LOG.error("Cannot reconcile DONATION webhook without merchant or original PSP reference");
            return;
        }

        final PaymentTransactionModel transaction = paymentTransactionRepository
                .getTransactionModel(notification.getOriginalReference());
        if (transaction == null || !(transaction.getOrder() instanceof OrderModel)) {
            LOG.error("Cannot find order for DONATION webhook with original PSP reference "
                    + notification.getOriginalReference());
            return;
        }

        AdyenDonationModel donation = adyenDonationRepository.findByReference(notification.getMerchantReference());
        if (donation == null) {
            donation = modelService.create(AdyenDonationModel.class);
            donation.setReference(notification.getMerchantReference());
            donation.setOrder((OrderModel) transaction.getOrder());
        }

        donation.setPspReference(notification.getPspReference());
        donation.setOriginalPspReference(notification.getOriginalReference());
        donation.setStatus(Boolean.TRUE.equals(notification.getSuccess()) ? COMPLETED : REFUSED);
        donation.setAmountValue(notification.getAmountValue());
        donation.setAmountCurrency(notification.getAmountCurrency());
        modelService.save(donation);
    }

    public void setModelService(final ModelService modelService) {
        this.modelService = modelService;
    }

    public void setAdyenDonationRepository(final AdyenDonationRepository adyenDonationRepository) {
        this.adyenDonationRepository = adyenDonationRepository;
    }

    public void setPaymentTransactionRepository(final PaymentTransactionRepository paymentTransactionRepository) {
        this.paymentTransactionRepository = paymentTransactionRepository;
    }
}
