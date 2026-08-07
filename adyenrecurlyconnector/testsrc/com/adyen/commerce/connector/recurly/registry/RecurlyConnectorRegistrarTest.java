package com.adyen.commerce.connector.recurly.registry;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.bootstrap.annotations.UnitTest;

@UnitTest
public class RecurlyConnectorRegistrarTest
{
    @Test
    public void registersConnectorExactlyOnce()
    {
        final SubscriptionBillingConnector connector = mock(SubscriptionBillingConnector.class);        when(connector.platform()).thenReturn(BillingPlatform.RECURLY);
        final List<SubscriptionBillingConnector> connectors = new ArrayList<>();
        final RecurlyConnectorRegistrar registrar = new RecurlyConnectorRegistrar(connectors, connector);

        registrar.afterPropertiesSet();
        registrar.afterPropertiesSet();

        assertEquals(List.of(connector), connectors);
    }
}
