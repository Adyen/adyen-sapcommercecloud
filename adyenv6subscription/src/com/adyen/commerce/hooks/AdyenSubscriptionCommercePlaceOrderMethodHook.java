package com.adyen.commerce.hooks;

import com.adyen.commerce.utils.NextChargeDateUtil;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.subscriptionservices.enums.SubscriptionStatus;
import de.hybris.platform.subscriptionservices.model.BillingFrequencyModel;
import de.hybris.platform.subscriptionservices.model.BillingPlanModel;
import de.hybris.platform.subscriptionservices.model.SubscriptionModel;
import de.hybris.platform.subscriptionservices.model.SubscriptionTermModel;
import de.hybris.platform.subscriptionservices.subscription.impl.DefaultSubscriptionCommercePlaceOrderMethodHook;
import org.apache.commons.collections4.CollectionUtils
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    protected void handleSubscriptionOrder(OrderModel order) {
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

    protected SubscriptionModel buildSubscriptionModel(final AbstractOrderEntryModel entry) {
        SubscriptionModel subscription = getModelService().create(SubscriptionModel.class);
        AbstractOrderModel order = entry.getOrder();
        SubscriptionTermModel subscriptionTerm = entry.getProduct().getSubscriptionTerm();
        BillingPlanModel billingPlan = subscriptionTerm.getBillingPlan();
        BillingFrequencyModel billingFrequency = billingPlan.getBillingFrequency();
        LocalDate nextChargeDate = NextChargeDateUtil.calculateNextChargeDate(order.getDate(), billingPlan);


        subscription.setOrderNumber(order.getCode());
        subscription.setPlacedOn(order.getDate());
        subscription.setNextChargeDate(nextChargeDate.toDate());
        subscription.setSubscriptionStatus(SubscriptionStatus.ACTIVE.getCode());
        subscription.setSubscriptionOrder((OrderModel) order);
        subscription.setCustomerId(order.getUser().getUid());
        subscription.setStartDate(order.getDate());
        subscription.setBillingCycleDay(billingPlan.getBillingCycleDay());
        subscription.setBillingCycleType(billingPlan.getBillingCycleType());
        subscription.setBillingFrequency(billingFrequency.getCode());

        return subscription;
    }
}