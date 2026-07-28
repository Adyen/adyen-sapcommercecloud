package com.adyen.commerce.connector.recurly.webhook;

import com.adyen.commerce.connector.dto.NormalizedBillingEvent;
import com.adyen.commerce.connector.dto.RawWebhook;
import com.adyen.commerce.connector.exception.BillingException;

public interface RecurlyWebhookParser
{
    NormalizedBillingEvent parse(RawWebhook raw) throws BillingException;
}
