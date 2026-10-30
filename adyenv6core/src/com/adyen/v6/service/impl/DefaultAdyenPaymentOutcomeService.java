package com.adyen.v6.service.impl;

import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.service.AdyenBusinessProcessService;
import com.adyen.v6.service.AdyenPaymentOutcomeService;
import com.adyen.v6.util.AmountUtil;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.model.ModelService;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.log4j.Logger;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;

public class DefaultAdyenPaymentOutcomeService
        implements AdyenPaymentOutcomeService {

    private static final Logger LOG =
            Logger.getLogger(DefaultAdyenPaymentOutcomeService.class);

    private ModelService modelService;
    private TransactionOperations transactionTemplate;
    private AdyenBusinessProcessService adyenBusinessProcessService;

    @Override
    public boolean markOfferClosed(
            final OrderModel order,
            final AdyenNotificationModel notification) {

        return markPaymentFailed(
                order,
                notification,
                OrderStatus.CANCELLED,
                "OFFER_CLOSED");
    }

    @Override
    public boolean markAuthorisationFailed(
            final OrderModel order,
            final AdyenNotificationModel notification) {

        return markPaymentFailed(
                order,
                notification,
                OrderStatus.PAYMENT_NOT_AUTHORIZED,
                "AUTHORISATION_FAILED");
    }

    protected boolean markPaymentFailed(
            final OrderModel order,
            final AdyenNotificationModel notification,
            final OrderStatus targetStatus,
            final String failureReason) {

        final Boolean changed = transactionTemplate.execute(status -> {
            modelService.refresh(order);

            if (isFullyAuthorised(order)) {
                LOG.warn("Ignoring payment failure for already authorised order: "
                        + order.getCode());
                return Boolean.FALSE;
            }

            if (BooleanUtils.isTrue(
                    order.getAdyenCartRestorationRequired())) {

                LOG.info("Payment failure already registered for order: "
                        + order.getCode());
                return Boolean.FALSE;
            }

            if (order.getAdyenRestoredCart() != null) {
                LOG.info("Cart already restored for order: "
                        + order.getCode());
                return Boolean.FALSE;
            }

            if (!OrderStatus.PAYMENT_PENDING.equals(order.getStatus())) {
                LOG.warn("Ignoring payment failure for order "
                        + order.getCode()
                        + " in status "
                        + order.getStatus());

                return Boolean.FALSE;
            }

            order.setStatus(targetStatus);
            order.setStatusInfo(
                    failureReason + ": " + notification.getPspReference());

            order.setAdyenPaymentFailureReason(failureReason);
            order.setAdyenCartRestorationRequired(Boolean.TRUE);

            modelService.save(order);
            return Boolean.TRUE;
        });

        if (BooleanUtils.isTrue(changed)) {
            adyenBusinessProcessService.triggerOrderProcessEvent(
                    order,
                    Adyenv6coreConstants.PROCESS_EVENT_ADYEN_PAYMENT_RESULT);
        }

        return BooleanUtils.isTrue(changed);
    }

    protected boolean isFullyAuthorised(final OrderModel order) {
        if (order.getPaymentTransactions() == null
                || order.getPaymentTransactions().isEmpty()) {
            return false;
        }

        BigDecimal authorisedAmount = BigDecimal.ZERO;

        for (final PaymentTransactionModel transaction
                : order.getPaymentTransactions()) {

            if (transaction == null
                    || transaction.getEntries() == null
                    || transaction.getPlannedAmount() == null) {
                continue;
            }

            final boolean acceptedAuthorization =
                    transaction.getEntries().stream()
                            .filter(entry -> entry != null)
                            .anyMatch(this::isAcceptedAuthorization);

            if (acceptedAuthorization) {
                authorisedAmount =
                        authorisedAmount.add(transaction.getPlannedAmount());
            }
        }

        final BigDecimal orderAmount =
                AmountUtil.calculateAmountWithTaxes(order);

        return authorisedAmount.compareTo(orderAmount) >= 0;
    }

    protected boolean isAcceptedAuthorization(
            final PaymentTransactionEntryModel entry) {

        return PaymentTransactionType.AUTHORIZATION.equals(entry.getType())
                && TransactionStatus.ACCEPTED.name()
                .equals(entry.getTransactionStatus());
    }

    public void setModelService(final ModelService modelService) {
        this.modelService = modelService;
    }

    public void setTransactionTemplate(
            final TransactionOperations transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public void setAdyenBusinessProcessService(
            final AdyenBusinessProcessService service) {
        this.adyenBusinessProcessService = service;
    }
}