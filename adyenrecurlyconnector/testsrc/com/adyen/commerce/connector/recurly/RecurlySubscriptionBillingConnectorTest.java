package com.adyen.commerce.connector.recurly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.BillingCustomerRef;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.SubscriptionCreateRequest;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.recurly.client.RecurlyApiClient;
import com.adyen.commerce.connector.recurly.client.RecurlySubscriptionParams;
import com.adyen.commerce.connector.recurly.config.RecurlyConfigService;
import com.adyen.commerce.connector.recurly.plan.RecurlyPlanResolver;
import com.adyen.commerce.connector.recurly.webhook.RecurlyWebhookParser;

import de.hybris.bootstrap.annotations.UnitTest;

@UnitTest
public class RecurlySubscriptionBillingConnectorTest
{
    private static final Instant NOW = Instant.parse("2026-07-21T10:00:00Z");

    @Mock
    private RecurlyApiClient apiClient;
    @Mock
    private RecurlyConfigService configService;
    @Mock
    private RecurlyPlanResolver planResolver;
    @Mock
    private RecurlyWebhookParser webhookParser;

    private RecurlySubscriptionBillingConnector connector;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);
        connector = new RecurlySubscriptionBillingConnector(apiClient, configService, planResolver, webhookParser,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(configService.getMinimumStartDelaySeconds()).thenReturn(300);
    }

    @Test
    public void nullStartDateBecomesARecurlySafeFutureDate() throws Exception
    {
        when(apiClient.createSubscription(any())).thenReturn("uuid-subscription");

        connector.createSubscription(request(null));

        final ArgumentCaptor<RecurlySubscriptionParams> params =
                ArgumentCaptor.forClass(RecurlySubscriptionParams.class);
        verify(apiClient).createSubscription(params.capture());
        assertEquals("2026-07-21T10:05:00Z", params.getValue().startsAt());
        assertEquals("billing-1", params.getValue().billingInfoId());
        assertEquals("ntid-1", params.getValue().networkTransactionId());
    }

    @Test
    public void explicitNonFutureStartDateIsRejected()
    {
        assertThrows(PreconditionFailedException.class, () -> connector.createSubscription(request(NOW)));
    }

    private SubscriptionCreateRequest request(final Instant startsAt)
    {
        return new SubscriptionCreateRequest(new BillingCustomerRef(BillingPlatform.RECURLY, "code-customer"),
                new BillingPaymentMethodRef(BillingPlatform.RECURLY, "billing-1::ntid::ntid-1"),
                new PlanRef("monthly", null), 1, null, "EUR", null, startsAt, Map.of(), "ORDER-1");
    }
}
