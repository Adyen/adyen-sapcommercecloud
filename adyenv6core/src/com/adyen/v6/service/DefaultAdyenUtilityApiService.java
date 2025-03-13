package com.adyen.v6.service;

import com.adyen.commerce.services.AdyenRequestService;
import com.adyen.model.checkout.PaypalUpdateOrderRequest;
import com.adyen.model.checkout.PaypalUpdateOrderResponse;
import com.adyen.service.checkout.UtilityApi;
import com.adyen.service.exception.ApiException;
import de.hybris.platform.store.BaseStoreModel;
import org.apache.log4j.Logger;

import java.io.IOException;

public class DefaultAdyenUtilityApiService extends AbstractAdyenApiService implements AdyenUtilityApiService {
    private static final Logger LOG = Logger.getLogger(DefaultAdyenUtilityApiService.class);

    public DefaultAdyenUtilityApiService(BaseStoreModel baseStore, String merchantAccount, AdyenRequestService adyenRequestService) {
        super(baseStore, merchantAccount, adyenRequestService);
    }

    public PaypalUpdateOrderResponse paypalUpdateOrder(PaypalUpdateOrderRequest paypalUpdateOrderRequest) throws IOException, ApiException {
        UtilityApi utilityApi = new UtilityApi(client);

        LOG.debug(paypalUpdateOrderRequest);
        PaypalUpdateOrderResponse paypalUpdateOrderResponse = utilityApi.updatesOrderForPaypalExpressCheckout(paypalUpdateOrderRequest);
        LOG.debug(paypalUpdateOrderResponse);

        return paypalUpdateOrderResponse;
    }
}
