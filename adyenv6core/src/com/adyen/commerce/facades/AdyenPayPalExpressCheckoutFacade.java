package com.adyen.commerce.facades;

import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.checkout.PaypalUpdateOrderRequest;
import com.adyen.model.checkout.PaypalUpdateOrderResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.response.PayPalExpressSubmitResponse;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commerceservices.customer.DuplicateUidException;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * Facade responsible for orchestrating PayPal express checkout flows.
 */
public interface AdyenPayPalExpressCheckoutFacade {

    PayPalExpressSubmitResponse onPayPalPDPSubmit(HttpServletRequest request, PaymentRequest paymentRequest, String productCode) throws IOException, ApiException;

    PayPalExpressSubmitResponse onPayPalPDPSubmitOCC(HttpServletRequest request, PaymentRequest paymentRequest) throws IOException, ApiException;

    PaymentResponse onPayPalCartSubmit(HttpServletRequest request, PaymentRequest paymentRequest) throws IOException, ApiException;

    void onPayPalAuthorizedPDP(String cartGuid, AddressData addressData, String paymentMethod) throws DuplicateUidException, InvalidCartException, CalculationException;

    void onPayPalAuthorizedCart(AddressData addressData, String paymentMethod) throws DuplicateUidException, InvalidCartException, CalculationException;

    PaypalUpdateOrderResponse updateShippingAddress(final AddressData addressData, final String pspReference, final String paymentData, final String cartGuid) throws IOException, ApiException, DuplicateUidException, CalculationException;

    PaypalUpdateOrderResponse updateShippingMethod(final String shippingMethodCode, final String pspReference, final String paymentData, final String cartGuid) throws IOException, ApiException, CalculationException;

    PaypalUpdateOrderResponse getPaypalUpdateOrderResponse(PaypalUpdateOrderRequest paypalUpdateOrderRequest) throws IOException, ApiException;
}
