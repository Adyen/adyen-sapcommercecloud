package com.adyen.commerce.job;

import com.adyen.commerce.repository.SubscriptionRepository;
import com.adyen.commerce.service.impl.DefaultRecurringOrderService;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.servicelayer.cronjob.AbstractJobPerformable;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.subscriptionservices.model.SubscriptionModel;
import org.apache.log4j.Logger;

import java.util.List;

public class SubscriptionCronJob extends AbstractJobPerformable<CronJobModel> {

    private static final Logger LOG = Logger.getLogger(SubscriptionCronJob.class);

    private SubscriptionRepository subscriptionRepository;
    private DefaultRecurringOrderService recurringOrderService;


    @Override
    public PerformResult perform(CronJobModel cronJobModel) {
        List<SubscriptionModel> subscriptionsToCharge = subscriptionRepository.getActiveSubscriptionByNextChargeDay();
        boolean exceptionsOccurred = processSubscriptions(subscriptionsToCharge);
        return getResultBasedOnExceptions(exceptionsOccurred);
    }

    private boolean processSubscriptions(List<SubscriptionModel> subscriptionsToCharge) {
        boolean exceptionsOccurred = false;
        for (SubscriptionModel subscriptionModel : subscriptionsToCharge) {
            exceptionsOccurred |= processSubscription(subscriptionModel);
        }
        return exceptionsOccurred;
    }

    private boolean processSubscription(SubscriptionModel subscriptionModel) {
        boolean exceptionOccurred = false;
        try {
            if (subscriptionModel.getSubscriptionOrder() != null) {
                processSubscriptionOrder(subscriptionModel);
            }
        } catch (Exception e) {
            LOG.error("Exception occurred while processing subscription: " + subscriptionModel.getId(), e);
            exceptionOccurred = true;
        }
        return exceptionOccurred;
    }

    private void processSubscriptionOrder(SubscriptionModel subscriptionModel) throws Exception {
        recurringOrderService.createRecurringOrderForSubscription(subscriptionModel);
    }

    private PerformResult getResultBasedOnExceptions(boolean exceptionsOccurred) {
        if (exceptionsOccurred) {
            return new PerformResult(CronJobResult.ERROR, CronJobStatus.FINISHED);
        }
        return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED);
    }

    public void setSubscriptionRepository(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }


    public void setRecurringOrderService(DefaultRecurringOrderService recurringOrderService) {
        this.recurringOrderService = recurringOrderService;
    }
}
