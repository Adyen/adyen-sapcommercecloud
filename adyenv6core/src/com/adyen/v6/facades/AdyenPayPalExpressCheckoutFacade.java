package com.adyen.v6.facades;

import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.checkout.PaypalUpdateOrderResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.response.PayPalExpressSubmitResponse;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commerceservices.customer.DuplicateUidException;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;

import java.io.IOException;

public interface AdyenPayPalExpressCheckoutFacade {
    PayPalExpressSubmitResponse onPayPalPDPSubmit(PaymentRequest paymentRequest, String productCode) throws IOException, ApiException;

    PaymentResponse onPayPalCartSubmit(PaymentRequest paymentRequest) throws IOException, ApiException;

    void onPayPalAuthorizedPDP(String cartGuid, AddressData addressData, String paymentMethod) throws DuplicateUidException, InvalidCartException, CalculationException;

    void onPayPalAuthorizedCart(AddressData addressData, String paymentMethod) throws DuplicateUidException, InvalidCartException, CalculationException;

    PaypalUpdateOrderResponse updateShippingAddress(final AddressData addressData, final String pspReference, final String paymentData, final String cartGuid) throws IOException, ApiException, DuplicateUidException, CalculationException;

    PaypalUpdateOrderResponse updateShippingMethod(final String shippingMethodCode, final String pspReference, final String paymentData, final String cartGuid) throws IOException, ApiException, CalculationException;
}
