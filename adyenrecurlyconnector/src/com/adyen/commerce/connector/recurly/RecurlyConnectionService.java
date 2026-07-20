package com.adyen.commerce.connector.recurly;

import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.RetryableBillingException;

public interface RecurlyConnectionService {
    boolean testConnection() throws RetryableBillingException, ConnectorNotConfiguredException;
}
