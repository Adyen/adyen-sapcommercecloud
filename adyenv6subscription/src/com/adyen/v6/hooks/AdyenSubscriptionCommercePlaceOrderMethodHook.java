package com.adyen.v6.hooks;

import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.subscriptionservices.enums.SubscriptionStatus;
import de.hybris.platform.subscriptionservices.model.BillingFrequencyModel;
import de.hybris.platform.subscriptionservices.model.SubscriptionModel;
import de.hybris.platform.subscriptionservices.model.SubscriptionTermModel;
import de.hybris.platform.subscriptionservices.subscription.impl.DefaultSubscriptionCommercePlaceOrderMethodHook;
import org.apache.commons.collections.CollectionUtils;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Optional;

public class AdyenSubscriptionCommercePlaceOrderMethodHook extends DefaultSubscriptionCommercePlaceOrderMethodHook {

    private static final Logger LOG = LoggerFactory.getLogger(AdyenSubscriptionCommercePlaceOrderMethodHook.class);

    @Override
    public void afterPlaceOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result) {
        LOG.info("Processing order after placement: {}", result.getOrder());
        Optional.ofNullable(result.getOrder())
                .filter(order -> CollectionUtils.isNotEmpty(order.getChildren()))
                .ifPresent(this::handleSubscriptionOrder);
    }

    protected void handleSubscriptionOrder(OrderModel order){
        order.getChildren().forEach(childOrder -> {
            childOrder.setPaymentInfo(getModelService().clone(order.getPaymentInfo()));
            getModelService().save(childOrder);
        });

        createSubscriptionsForOrderEntries(order);
    }

    protected void createSubscriptionsForOrderEntries(final OrderModel order) {
        LOG.info("Creating subscriptions for order entries: {}", order);
        order.getEntries().stream()
                .filter(entry -> CollectionUtils.isNotEmpty(entry.getChildEntries()) && CollectionUtils.isEmpty(entry.getEntryGroupNumbers()))
                .forEach(this::createSubscriptionFromOrderEntry);
    }

    protected void createSubscriptionFromOrderEntry(final AbstractOrderEntryModel entry) {
        LOG.info("Creating subscription from order entry: {}", entry);
        SubscriptionModel subscription = buildSubscriptionModel(entry);
        getModelService().save(subscription);
        LOG.info("Subscription created: {}", subscription);
    }

    private SubscriptionModel buildSubscriptionModel(final AbstractOrderEntryModel entry) {
        SubscriptionModel subscription = getModelService().create(SubscriptionModel.class);
        AbstractOrderModel order = entry.getOrder();
        SubscriptionTermModel subscriptionTerm = entry.getProduct().getSubscriptionTerm();
        BillingFrequencyModel billingFrequency = subscriptionTerm.getBillingPlan().getBillingFrequency();
        LocalDate nextChargeDate = calculateNextChargeDate(order.getDate(), billingFrequency.getCode());

        subscription.setOrderNumber(order.getCode());
        subscription.setPlacedOn(order.getDate());
        subscription.setNextChargeDate(nextChargeDate.toDate());
        subscription.setSubscriptionStatus(SubscriptionStatus.ACTIVE.getCode());
        subscription.setSubscriptionOrder((OrderModel) order);
        subscription.setCustomerId(order.getUser().getUid());

        return subscription;
    }

    private LocalDate calculateNextChargeDate(final Date orderDate, final String billingFrequencyCode) {
        LocalDate localOrderDate = LocalDate.fromDateFields(orderDate);
        switch (billingFrequencyCode.toLowerCase()) {
            case "monthly":
                return localOrderDate.plusMonths(1);
            case "quarterly":
                return localOrderDate.plusMonths(3);
            case "yearly":
                return localOrderDate.plusMonths(12);
            default:
                String errorMessage = "Unsupported billing frequency code: " + billingFrequencyCode;
                LOG.error(errorMessage);
                throw new IllegalArgumentException(errorMessage);
        }
    }
}