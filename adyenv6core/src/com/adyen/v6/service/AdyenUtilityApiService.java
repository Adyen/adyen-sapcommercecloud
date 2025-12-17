package com.adyen.v6.service;

import com.adyen.model.checkout.PaypalUpdateOrderRequest;
import com.adyen.model.checkout.PaypalUpdateOrderResponse;
import com.adyen.service.exception.ApiException;

import java.io.IOException;

public interface AdyenUtilityApiService {

    PaypalUpdateOrderResponse paypalUpdateOrder(PaypalUpdateOrderRequest paypalUpdateOrderRequest) throws Exception;
}
