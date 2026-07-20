package com.adyen.commerce.connector.recurly.client;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.recurly.dto.RecurlyAccountCreateRequest;
import com.adyen.commerce.connector.recurly.dto.RecurlyAccountResponse;
import com.adyen.commerce.connector.recurly.dto.RecurlySubscriptionCreateRequest;
import com.adyen.commerce.connector.recurly.dto.RecurlySubscriptionResponse;

public interface RecurlyApiClient
{
    RecurlyAccountResponse createAccount(
            RecurlyAccountCreateRequest request,
            String idempotencyKey) throws RetryableBillingException, ConnectorNotConfiguredException;

    RecurlySubscriptionResponse createSubscription(
            RecurlySubscriptionCreateRequest request,
            String idempotencyKey) throws RetryableBillingException, ConnectorNotConfiguredException;
}