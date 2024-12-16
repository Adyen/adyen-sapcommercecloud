package com.adyen.commerce.controllerbase;

import com.adyen.commerce.exception.AdyenControllerException;
import com.adyen.commerce.facades.AdyenCheckoutApiFacade;
import com.adyen.commerce.request.PlaceOrderRequest;
import com.adyen.commerce.response.OCCPlaceOrderResponse;
import com.adyen.commerce.response.PlaceOrderResponse;
import com.adyen.commerce.validators.PaymentRequestValidator;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hybris.platform.acceleratorfacades.flow.CheckoutFlowFacade;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.site.BaseSiteService;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.validation.BeanPropertyBindingResult;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;

import static com.adyen.commerce.constants.AdyenwebcommonsConstants.*;
import static com.adyen.commerce.util.ErrorMessageUtil.getErrorMessageByRefusalReason;
import static com.adyen.commerce.util.FieldValidationUtil.getFieldCodesFromValidation;
import static com.adyen.model.checkout.PaymentResponse.ResultCodeEnum.*;

public abstract class PlaceOrderControllerBase {
    private static final Logger LOGGER = Logger.getLogger(PlaceOrderControllerBase.class);
    protected static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CHECKOUT_ERROR_FORM_ENTRY_INVALID = "checkout.error.paymentethod.formentry.invalid";
    private static final String GET_TYPE = "getType";


    public PlaceOrderControllerBase() {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public PlaceOrderResponse placeOrder(PlaceOrderRequest placeOrderRequest, HttpServletRequest request) {
        OCCPlaceOrderResponse occPlaceOrderResponse = placeOrderOCC(placeOrderRequest, request);
        occPlaceOrderResponse.setOrderData(null);
        return occPlaceOrderResponse;
    }

    public OCCPlaceOrderResponse placeOrderOCC(PlaceOrderRequest placeOrderRequest, HttpServletRequest request) {

        String adyenPaymentMethodType = extractPaymentMethodType(placeOrderRequest);

        preHandleAndValidateRequest(placeOrderRequest, adyenPaymentMethodType);

        if (!isCartValid()) {
            LOGGER.warn("Cart is invalid.");
            throw new AdyenControllerException(CHECKOUT_ERROR_AUTHORIZATION_FAILED);
        }

        return handlePayment(request, placeOrderRequest);
    }

    public PlaceOrderResponse handleAdditionalDetails(final PaymentDetailsRequest paymentDetailsRequest) {
        OCCPlaceOrderResponse occPlaceOrderResponse = handleAdditionalDetailsOCC(paymentDetailsRequest);
        occPlaceOrderResponse.setOrderData(null);
        return occPlaceOrderResponse;
    }

    public OCCPlaceOrderResponse handleAdditionalDetailsOCC(final PaymentDetailsRequest paymentDetailsRequest) {
        try {
            OrderData orderData = getAdyenCheckoutApiFacade().placeOrderWithAdditionalDetails(paymentDetailsRequest);

            String orderCode = getCheckoutCustomerStrategy().isAnonymousCheckout() ? orderData.getGuid() : orderData.getCode();

            OCCPlaceOrderResponse placeOrderResponse = new OCCPlaceOrderResponse();
            placeOrderResponse.setOrderNumber(orderCode);
            placeOrderResponse.setOrderData(orderData);
            return placeOrderResponse;
        } catch (Exception e) {
            LOGGER.error("Exception", e);
            throw new AdyenControllerException(CHECKOUT_ERROR_AUTHORIZATION_FAILED);
        }
    }

    public void handleCancel() throws InvalidCartException, CalculationException {
        getAdyenCheckoutFacade().restoreCartFromOrderCodeInSession();
    }

    private static String extractPaymentMethodType(PlaceOrderRequest placeOrderRequest) throws AdyenControllerException {
        if (placeOrderRequest == null || placeOrderRequest.getPaymentRequest() == null || placeOrderRequest.getPaymentRequest().getPaymentMethod() == null) {
            throw new AdyenControllerException(CHECKOUT_ERROR_AUTHORIZATION_FAILED);
        }
        Object actualInstance = placeOrderRequest.getPaymentRequest().getPaymentMethod().getActualInstance();
        if (actualInstance == null) {
            throw new AdyenControllerException(CHECKOUT_ERROR_AUTHORIZATION_FAILED);
        }
        Class<?> aClass = actualInstance.getClass();
        try {
            return aClass.getMethod(GET_TYPE).invoke(actualInstance).toString();
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new AdyenControllerException(CHECKOUT_ERROR_AUTHORIZATION_FAILED);
        }
    }

    private void preHandleAndValidateRequest(PlaceOrderRequest placeOrderRequest, String adyenPaymentMethod) {
        final BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(placeOrderRequest, "placeOrderRequest");

        boolean showRememberDetails = getAdyenCheckoutApiFacade().showRememberDetails();
        boolean holderNameRequired = getAdyenCheckoutApiFacade().getHolderNameRequired();

        PaymentRequestValidator paymentRequestValidator = new PaymentRequestValidator(getAdyenCheckoutApiFacade().getStoredCards(), showRememberDetails, holderNameRequired);
        paymentRequestValidator.validate(placeOrderRequest, bindingResult);

        if (bindingResult.hasErrors()) {
            LOGGER.warn("Payment form is invalid.");
            LOGGER.warn(bindingResult.getAllErrors().stream().map(DefaultMessageSourceResolvable::getCode).reduce((x, y) -> (x + " " + y)));
            throw new AdyenControllerException(CHECKOUT_ERROR_FORM_ENTRY_INVALID, getFieldCodesFromValidation(bindingResult));
        }

        getAdyenCheckoutApiFacade().preHandlePlaceOrder(placeOrderRequest.getPaymentRequest(), adyenPaymentMethod,
                placeOrderRequest.getBillingAddress(), placeOrderRequest.isUseAdyenDeliveryAddress());
    }

    protected boolean isCartValid() {

        if (getCheckoutFlowFacade().hasNoDeliveryAddress()) {
            LOGGER.error("No delivery address.");
            return false;
        }

        if (getCheckoutFlowFacade().hasNoDeliveryMode()) {
            LOGGER.error("No delivery mode.");
            return false;
        }

        if (getCheckoutFlowFacade().hasNoPaymentInfo()) {
            LOGGER.error("No payment info.");
            return false;
        }

        final CartData cartData = getCheckoutFlowFacade().getCheckoutCart();

        if (!getCheckoutFlowFacade().containsTaxValues()) {
            LOGGER.error(String.format("Cart %s does not have any tax values, which means the tax cacluation was not properly done, placement of order can't continue", cartData.getCode()));
            LOGGER.error("Tax missing.");
            return false;

        }

        if (!cartData.isCalculated()) {
            LOGGER.error(String.format("Cart %s has a calculated flag of FALSE, placement of order can't continue", cartData.getCode()));
            LOGGER.error("Cart not calculated.");
            return false;

        }

        return true;
    }

    private OCCPlaceOrderResponse handlePayment(HttpServletRequest request, PlaceOrderRequest placeOrderRequest) {
        final CartData cartData = getCartFacade().getSessionCart();

        String errorMessage = CHECKOUT_ERROR_AUTHORIZATION_FAILED;

        try {
            cartData.setAdyenReturnUrl(getPaymentRedirectReturnUrl());
            OrderData orderData = getAdyenCheckoutApiFacade().placeOrderWithPayment(request, cartData, placeOrderRequest.getPaymentRequest());

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

    private OCCPlaceOrderResponse executeAction(PaymentResponse paymentsResponse) {
        OCCPlaceOrderResponse placeOrderResponse = new OCCPlaceOrderResponse();
        placeOrderResponse.setPaymentsResponse(paymentsResponse);
        placeOrderResponse.setExecuteAction(true);
        placeOrderResponse.setPaymentsAction(paymentsResponse.getAction());
        placeOrderResponse.setOrderNumber(paymentsResponse.getMerchantReference());
        return placeOrderResponse;
    }

    public abstract String getPaymentRedirectReturnUrl();

    public abstract AdyenCheckoutApiFacade getAdyenCheckoutApiFacade();

    public abstract CheckoutFlowFacade getCheckoutFlowFacade();

    public abstract CartFacade getCartFacade();

    public abstract BaseSiteService getBaseSiteService();

    public abstract SiteBaseUrlResolutionService getSiteBaseUrlResolutionService();

    public abstract AdyenCheckoutFacade getAdyenCheckoutFacade();

    public abstract CheckoutCustomerStrategy getCheckoutCustomerStrategy();

}
