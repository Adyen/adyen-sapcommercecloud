package com.adyen.v6.actions;

import com.adyen.v6.constants.Adyenv6consignmentpartialcaptureexampleConstants;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.service.AdyenModificationsApiService;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.ordersplitting.model.ConsignmentEntryModel;
import de.hybris.platform.ordersplitting.model.ConsignmentModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.processengine.action.AbstractSimpleDecisionAction;
import de.hybris.platform.servicelayer.i18n.I18NService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.task.RetryLaterException;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

public class AdyenCaptureConsignmentsAction extends AbstractSimpleDecisionAction<OrderProcessModel> {
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    private I18NService i18NService;

    @Override
    public Transition executeAction(OrderProcessModel orderProcessModel) throws RetryLaterException, Exception {
        if (Adyenv6consignmentpartialcaptureexampleConstants.PARTIAL_CAPTURE_DEMO) {
            OrderModel order = orderProcessModel.getOrder();

            Currency currency = i18NService.getBestMatchingJavaCurrency(order.getCurrency().getIsocode());
            Optional<PaymentTransactionModel> paymentTransactionModelOptional = order.getPaymentTransactions().stream().findFirst();

            if (paymentTransactionModelOptional.isPresent()) {
                for (ConsignmentModel consignment : order.getConsignments()) {
                    BigDecimal consignmentValue = calculateConsignmentValue(consignment);
                    getAdyenPaymentService(order.getStore()).capture(consignmentValue, currency, paymentTransactionModelOptional.get().getCode(), order.getCode());
                }
            } else {
                return Transition.NOK;
            }
        }

        return Transition.OK;
    }

    private BigDecimal calculateConsignmentValue(ConsignmentModel consignmentModel) {
        BigDecimal sum = new BigDecimal(0);
        for (ConsignmentEntryModel consignmentEntry : consignmentModel.getConsignmentEntries()) {
            Double totalPrice = consignmentEntry.getOrderEntry().getTotalPrice();
            sum = sum.add(BigDecimal.valueOf(totalPrice));
        }
        return sum;
    }

    private AdyenModificationsApiService getAdyenPaymentService(BaseStoreModel baseStoreModel) {
        return adyenPaymentServiceFactory.createAdyenModificationsApiService(baseStoreModel);
    }

    public void setAdyenPaymentServiceFactory(AdyenPaymentServiceFactory adyenPaymentServiceFactory) {
        this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
    }

    public void setI18NService(I18NService i18NService) {
        this.i18NService = i18NService;
    }
}

