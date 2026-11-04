package com.adyen.v6.service;

import com.adyen.v6.model.AdyenDonationModel;
import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.repository.AdyenDonationRepository;
import com.adyen.v6.repository.PaymentTransactionRepository;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.model.ModelService;
import org.apache.commons.lang3.StringUtils;

public class DefaultAdyenDonationReconciliationService implements AdyenDonationReconciliationService {
    private static final String COMPLETED = "completed";
    private static final String REFUSED = "refused";
    private static final String DONATION_REFERENCE_SEPARATOR = "-donation-";

    private ModelService modelService;
    private AdyenDonationRepository adyenDonationRepository;
    private PaymentTransactionRepository paymentTransactionRepository;

    @Override
    public void reconcile(final AdyenNotificationModel notification) {
        final String originalReference = resolveOriginalReference(notification);
        if (StringUtils.isAnyBlank(notification.getMerchantReference(), originalReference)) {
            final String message = "Cannot reconcile DONATION webhook without merchant or original PSP reference";
            throw new IllegalStateException(message);
        }

        final PaymentTransactionModel transaction = paymentTransactionRepository
                .getTransactionModel(originalReference);
        if (transaction == null || !(transaction.getOrder() instanceof OrderModel)) {
            final String message = "Cannot find order for DONATION webhook with original PSP reference "
                    + originalReference;
            throw new IllegalStateException(message);
        }

        AdyenDonationModel donation = adyenDonationRepository.findByReference(notification.getMerchantReference());
        if (donation == null) {
            donation = modelService.create(AdyenDonationModel.class);
            donation.setReference(notification.getMerchantReference());
            donation.setOrder((OrderModel) transaction.getOrder());
        }

        donation.setPspReference(notification.getPspReference());
        donation.setOriginalPspReference(originalReference);
        donation.setStatus(Boolean.TRUE.equals(notification.getSuccess()) ? COMPLETED : REFUSED);
        donation.setAmountValue(notification.getAmountValue());
        donation.setAmountCurrency(notification.getAmountCurrency());
        modelService.save(donation);
    }

    protected String resolveOriginalReference(final AdyenNotificationModel notification) {
        if (StringUtils.isNotBlank(notification.getOriginalReference())) {
            return notification.getOriginalReference();
        }
        final String merchantReference = notification.getMerchantReference();
        if (merchantReference == null) {
            return null;
        }
        final int index = merchantReference.lastIndexOf(DONATION_REFERENCE_SEPARATOR);
        if (index <= 0) {
            return null;
        }
        final String suffix = merchantReference.substring(index + DONATION_REFERENCE_SEPARATOR.length());
        return StringUtils.isNumeric(suffix) ? merchantReference.substring(0, index) : null;
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
