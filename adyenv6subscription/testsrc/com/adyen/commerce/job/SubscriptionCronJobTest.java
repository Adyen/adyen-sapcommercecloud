package com.adyen.commerce.job;

import com.adyen.commerce.repository.SubscriptionRepository;
import com.adyen.commerce.service.impl.DefaultRecurringOrderService;
import de.hybris.platform.cronjob.enums.CronJobResult;
import de.hybris.platform.cronjob.enums.CronJobStatus;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.servicelayer.cronjob.PerformResult;
import de.hybris.platform.subscriptionservices.model.SubscriptionModel;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SubscriptionCronJobTest {

    @InjectMocks
    private SubscriptionCronJob subscriptionCronJob;


    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private DefaultRecurringOrderService recurringOrderService;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testPerform_noSubscriptions_shouldReturnSuccess() {
        // Arrange
        when(subscriptionRepository.getActiveSubscriptionByNextChargeDay()).thenReturn(Collections.emptyList());
        CronJobModel cronJobModel = new CronJobModel();

        // Act
        PerformResult result = subscriptionCronJob.perform(cronJobModel);

        // Assert
        assertEquals(CronJobResult.SUCCESS, result.getResult());
        assertEquals(CronJobStatus.FINISHED, result.getStatus());
    }

    @Test
    public void testPerform_withSubscriptions_shouldReturnSuccess() {
        // Arrange
        SubscriptionModel subscriptionModel = mock(SubscriptionModel.class);
        when(subscriptionRepository.getActiveSubscriptionByNextChargeDay()).thenReturn(List.of(subscriptionModel));
        CronJobModel cronJobModel = new CronJobModel();

        // Act
        PerformResult result = subscriptionCronJob.perform(cronJobModel);

        // Assert
        assertEquals(CronJobResult.SUCCESS, result.getResult());
        assertEquals(CronJobStatus.FINISHED, result.getStatus());
    }

}
