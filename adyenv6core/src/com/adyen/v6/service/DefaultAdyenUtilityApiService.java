package com.adyen.v6.service;

import com.adyen.commerce.services.AdyenRequestService;
import com.adyen.model.RequestOptions;
import com.adyen.model.checkout.PaypalUpdateOrderRequest;
import com.adyen.model.checkout.PaypalUpdateOrderResponse;
import com.adyen.service.checkout.UtilityApi;
import com.adyen.service.exception.ApiException;
import de.hybris.platform.store.BaseStoreModel;
import org.apache.log4j.Logger;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.util.UUID;

public class DefaultAdyenUtilityApiService extends AbstractAdyenApiService implements AdyenUtilityApiService {
    private static final Logger LOG = Logger.getLogger(DefaultAdyenUtilityApiService.class);

    public DefaultAdyenUtilityApiService(BaseStoreModel baseStore, String merchantAccount, AdyenRequestService adyenRequestService, RetryTemplate adyenCustomerInteractionRetryTemplate, RetryTemplate adyenBackgroundProcessRetryTemplate) {
        super(baseStore, merchantAccount, adyenRequestService, adyenCustomerInteractionRetryTemplate, adyenBackgroundProcessRetryTemplate);
    }

    public PaypalUpdateOrderResponse paypalUpdateOrder(PaypalUpdateOrderRequest paypalUpdateOrderRequest) throws IOException, ApiException {
        UtilityApi utilityApi = new UtilityApi(client);

        RequestOptions requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        try {
            return adyenCustomerInteractionRetryTemplate.execute(context -> {
                LOG.debug(paypalUpdateOrderRequest);
                PaypalUpdateOrderResponse paypalUpdateOrderResponse = utilityApi.updatesOrderForPaypalExpressCheckout(paypalUpdateOrderRequest, requestOptions);
                LOG.debug(paypalUpdateOrderResponse);
                return paypalUpdateOrderResponse;
            });
        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw (ApiException) e;
            } else if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}
