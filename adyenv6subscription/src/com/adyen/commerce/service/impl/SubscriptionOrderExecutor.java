package com.adyen.commerce.service.impl;

import com.adyen.commerce.factory.AdyenSubscriptionPaymentServiceFactory;
import com.adyen.commerce.service.SubscriptionAdyenCheckoutApiService;
import com.adyen.commerce.utils.NextChargeDateUtil;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.service.AdyenTransactionService;
import de.hybris.platform.commerceservices.impersonation.ImpersonationService;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.services.BaseStoreService;
import de.hybris.platform.subscriptionservices.model.SubscriptionModel;
import org.apache.commons.collections.CollectionUtils;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class SubscriptionOrderExecutor implements ImpersonationService.Executor<Void, Exception> {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionOrderExecutor.class);

    public static final String BEAN_NAME = "subscriptionOrderExecutor";

    private static final List<String> SUBSCRIPTION_TERMS = Arrays.asList("monthly", "quarterly", "yearly");


    private AdyenSubscriptionPaymentServiceFactory adyenSubscriptionPaymentServiceFactory;
    private AdyenTransactionService adyenTransactionService;
    private BaseStoreService baseStoreService;
    private ModelService modelService;


    private final SubscriptionModel subscriptionModel;


    public SubscriptionOrderExecutor(final SubscriptionModel subscriptionModel)
    {
        this.subscriptionModel = subscriptionModel;
    }

    public Void execute() throws Exception {
        OrderModel parentSubscriptionOrder = subscriptionModel.getSubscriptionOrder();

        Optional<AbstractOrderModel> firstBillOrderOptional = parentSubscriptionOrder.getChildren().stream()
                .filter(o -> "onfirstbill".equals(o.getBillingTime().getCode())).findFirst();

        Optional<AbstractOrderModel> subscriptionOrderOptional = parentSubscriptionOrder.getChildren().stream()
                .filter(o -> SUBSCRIPTION_TERMS.contains(o.getBillingTime().getCode())).findFirst();

        PaymentResponse paymentResponse;

        if (subscriptionOrderOptional.isPresent() && firstBillOrderOptional.isPresent()
                && CollectionUtils.isEmpty(firstBillOrderOptional.get().getPaymentTransactions())) {

            AbstractOrderModel onFirstBillOrder = firstBillOrderOptional.get();

            paymentResponse = getAdyenPaymentService().processSubscriptionPaymentRequest(subscriptionOrderOptional.get(), onFirstBillOrder);

            adyenTransactionService.authorizeOrderModel(onFirstBillOrder,
                    onFirstBillOrder.getCode(), paymentResponse.getPspReference());


        } else if (subscriptionOrderOptional.isPresent()) {
            paymentResponse = getAdyenPaymentService().processSubscriptionPaymentRequest(subscriptionOrderOptional.get());
        } else {
            throw new IllegalArgumentException("No subscription order to be processed");
        }

        if (paymentResponse.getResultCode() != PaymentResponse.ResultCodeEnum.AUTHORISED
                && paymentResponse.getResultCode() != PaymentResponse.ResultCodeEnum.SUCCESS) {
            throw new RuntimeException("Not authorized or success result for subscription order: " + subscriptionOrderOptional.get().getCode());
        }


        LocalDate nextChargeDate = NextChargeDateUtil.calculateNextChargeDate(new Date(), subscriptionModel.getBillingCycleType(),
                subscriptionModel.getBillingFrequency(), subscriptionModel.getBillingCycleDay());

        subscriptionModel.setNextChargeDate(nextChargeDate.toDate());
        modelService.save(subscriptionModel);


        return null;
    }


    public SubscriptionAdyenCheckoutApiService getAdyenPaymentService() {
        return adyenSubscriptionPaymentServiceFactory.createAdyenSubscriptionCheckoutApiService(baseStoreService.getCurrentBaseStore());
    }


    public AdyenSubscriptionPaymentServiceFactory getAdyenSubscriptionPaymentServiceFactory() {
        return adyenSubscriptionPaymentServiceFactory;
    }

    public void setAdyenSubscriptionPaymentServiceFactory(AdyenSubscriptionPaymentServiceFactory adyenSubscriptionPaymentServiceFactory) {
        this.adyenSubscriptionPaymentServiceFactory = adyenSubscriptionPaymentServiceFactory;
    }

    public void setAdyenTransactionService(AdyenTransactionService adyenTransactionService) {
        this.adyenTransactionService = adyenTransactionService;
    }

    public BaseStoreService getBaseStoreService() {
        return baseStoreService;
    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }

    public void setModelService(ModelService modelService) {
        this.modelService = modelService;
    }
}
