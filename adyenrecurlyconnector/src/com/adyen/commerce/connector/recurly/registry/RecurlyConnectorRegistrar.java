package com.adyen.commerce.connector.recurly.registry;

import java.util.List;

import org.springframework.beans.factory.InitializingBean;

import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

/** Adds the Recurly adapter to the mutable list owned by the connector registry. */
public class RecurlyConnectorRegistrar implements InitializingBean
{
    private final List<SubscriptionBillingConnector> connectors;
    private final SubscriptionBillingConnector connector;

    public RecurlyConnectorRegistrar(final List<SubscriptionBillingConnector> connectors,
                                     final SubscriptionBillingConnector connector)
    {
        this.connectors = connectors;
        this.connector = connector;
    }

    @Override
    public void afterPropertiesSet()
    {
        if (connectors.stream().noneMatch(existing -> existing.platform().equals(connector.platform())))
        {
            connectors.add(connector);
        }
    }
}
