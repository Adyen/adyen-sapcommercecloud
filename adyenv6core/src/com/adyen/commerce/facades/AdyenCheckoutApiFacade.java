package com.adyen.commerce.facades;

import com.adyen.commerce.data.AdyenPartialPaymentOrderData;
import com.adyen.commerce.dto.OrderPaymentResult;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.adyen.v6.forms.AddressForm;
import com.adyen.v6.model.RequestInfo;
import de.hybris.platform.commercefacades.order.data.CartData;

import javax.servlet.http.HttpServletRequest;

public interface AdyenCheckoutApiFacade extends AdyenCheckoutFacade {

    void preHandlePlaceOrder(PaymentRequest paymentRequest, String adyenPaymentMethod, AddressForm billingAddress, Boolean useAdyenDeliveryAddress);

    OrderPaymentResult placeOrderWithPayment(final HttpServletRequest request, final CartData cartData, PaymentRequest paymentRequest, RequestInfo requestInfo) throws Exception;

    OrderPaymentResult placeOrderWithPayment(final HttpServletRequest request, final CartData cartData, PaymentRequest paymentRequest, RequestInfo requestInfo, AdyenPartialPaymentOrderData partialPaymentOrderData) throws Exception;

    OrderPaymentResult placeOrderWithPaymentOCC(final HttpServletRequest request, final CartData cartData, PaymentRequest paymentRequest, RequestInfo requestInfo) throws Exception;

    OrderPaymentResult placeOrderWithAdditionalDetails(PaymentDetailsRequest detailsRequest) throws Exception;
}
