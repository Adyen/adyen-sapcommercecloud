package com.adyen.commerce.decorator;

import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.model.RequestInfo;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.user.CustomerModel;

public interface AdyenPaymentRequestDecorator {

    void decoratePaymentRequest(final PaymentRequest paymentRequest, final CartData cartData, final PaymentRequest originPaymentsRequest,
                                final RequestInfo requestInfo,final CustomerModel customerModel);

}
