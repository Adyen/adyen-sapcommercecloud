package com.adyen.commerce.controllers.expresscheckout;

import com.adyen.commerce.exception.AdyenControllerException;
import com.adyen.commerce.response.OCCPlaceOrderResponse;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.v6.facades.AdyenExpressCheckoutFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;

import static com.adyen.commerce.constants.AdyenwebcommonsConstants.CHECKOUT_ERROR_AUTHORIZATION_FAILED;
import static com.adyen.commerce.util.ErrorMessageUtil.getErrorMessageByRefusalReason;
import static com.adyen.model.checkout.PaymentResponse.ResultCodeEnum.*;

public abstract class ExpressCheckoutControllerBase {
    private static final Logger LOGGER = Logger.getLogger(ExpressCheckoutControllerBase.class);
    protected static final ObjectMapper objectMapper = new ObjectMapper();


    protected OCCPlaceOrderResponse handlePayment(HttpServletRequest request, PaymentRequest paymentRequest, String paymentMethod, AddressData addressData, String productCode, boolean isPDPCheckout) {
        final CartData cartData = getCartFacade().getSessionCart();

        String errorMessage = CHECKOUT_ERROR_AUTHORIZATION_FAILED;

        try {
            cartData.setAdyenReturnUrl(getPaymentRedirectReturnUrl());

            OrderData orderData;

            if (isPDPCheckout) {
                orderData = getAdyenCheckoutApiFacade().expressCheckoutPDPOCC(productCode, paymentRequest, paymentMethod, addressData, request);
            } else {
                orderData = getAdyenCheckoutApiFacade().expressCheckoutCartOCC(paymentRequest, paymentMethod, addressData, request);
            }

            String orderCode = getCheckoutCustomerStrategy().isAnonymousCheckout() ? orderData.getGuid() : orderData.getCode();

            OCCPlaceOrderResponse placeOrderResponse = new OCCPlaceOrderResponse();
            placeOrderResponse.setOrderNumber(orderCode);
            placeOrderResponse.setOrderData(orderData);
            return placeOrderResponse;

        } catch (ApiException e) {
            LOGGER.error("API exception: ", e);
        } catch (AdyenNonAuthorizedPaymentException e) {
            LOGGER.info("Handling AdyenNonAuthorizedPaymentException. Checking PaymentResponse.");
            PaymentResponse paymentsResponse = e.getPaymentsResponse();
            if (REDIRECTSHOPPER == paymentsResponse.getResultCode() || CHALLENGESHOPPER == paymentsResponse.getResultCode() ||
                    IDENTIFYSHOPPER == paymentsResponse.getResultCode() || PENDING == paymentsResponse.getResultCode() ||
                    PRESENTTOSHOPPER == paymentsResponse.getResultCode()) {
                LOGGER.debug("PaymentResponse is " + paymentsResponse.getResultCode() + ", executing action for pspReference: " + paymentsResponse.getPspReference());
                return executeAction(paymentsResponse);
            } else if (REFUSED == paymentsResponse.getResultCode()) {
                LOGGER.info("PaymentResponse is REFUSED, pspReference: " + paymentsResponse.getPspReference());
                errorMessage = getErrorMessageByRefusalReason(paymentsResponse.getRefusalReason());
            } else if (PaymentResponse.ResultCodeEnum.ERROR == paymentsResponse.getResultCode()) {
                LOGGER.error("PaymentResponse is ERROR, reason: " + paymentsResponse.getRefusalReason() + " pspReference: " + paymentsResponse.getPspReference());
            }
        } catch (Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }

        throw new AdyenControllerException(errorMessage);
    }

    protected OCCPlaceOrderResponse executeAction(PaymentResponse paymentsResponse) {
        OCCPlaceOrderResponse placeOrderResponse = new OCCPlaceOrderResponse();
        placeOrderResponse.setPaymentsResponse(paymentsResponse);
        placeOrderResponse.setExecuteAction(true);
        placeOrderResponse.setPaymentsAction(paymentsResponse.getAction());
        placeOrderResponse.setOrderNumber(paymentsResponse.getMerchantReference());
        return placeOrderResponse;
    }

    public abstract CartFacade getCartFacade();

    public abstract CheckoutCustomerStrategy getCheckoutCustomerStrategy();

    public abstract String getPaymentRedirectReturnUrl();

    public abstract AdyenExpressCheckoutFacade getAdyenCheckoutApiFacade();
}
