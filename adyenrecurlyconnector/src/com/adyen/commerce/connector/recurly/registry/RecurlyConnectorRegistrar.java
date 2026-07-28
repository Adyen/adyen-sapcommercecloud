package com.adyen.commerce.connector.recurly.registry;

import java.util.List;

import org.springframework.beans.factory.InitializingBean;

import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

/** Adds the Recurly adapter to the mutable list owned by the connector registry. */
public class RecurlyConnectorRegistrar implements InitializingBean
{
    private List<SubscriptionBillingConnector> connectors;
    private SubscriptionBillingConnector connector;

    @Override
    public void afterPropertiesSet()
    {
        if (connectors.stream().noneMatch(existing -> existing.platform().equals(connector.platform())))
        {
            connectors.add(connector);
        }
    }

    public void setConnectors(final List<SubscriptionBillingConnector> connectors)
    {
        this.connectors = connectors;
    }

    public void setConnector(final SubscriptionBillingConnector connector)
    {
        this.connector = connector;
    }
}
