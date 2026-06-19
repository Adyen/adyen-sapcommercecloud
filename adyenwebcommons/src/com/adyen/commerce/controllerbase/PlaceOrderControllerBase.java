package com.adyen.commerce.controllerbase;

import com.adyen.commerce.data.AdyenPartialPaymentOrderData;
import com.adyen.commerce.dto.OrderPaymentResult;
import com.adyen.commerce.exception.AdyenControllerException;
import com.adyen.commerce.facades.AdyenCheckoutApiFacade;
import com.adyen.commerce.request.PlaceOrderRequest;
import com.adyen.commerce.response.OCCPlaceOrderResponse;
import com.adyen.commerce.response.PlaceOrderResponse;
import com.adyen.commerce.validators.PaymentRequestValidator;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.commerce.facades.AdyenCheckoutFacade;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.service.AdyenPartialPaymentService;
import com.adyen.v6.service.AdyenShopperIpResolverService;
import com.adyen.v6.util.RemainingAmountCalculator;
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
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.validation.BeanPropertyBindingResult;

import java.lang.reflect.InvocationTargetException;

import static com.adyen.commerce.constants.AdyenwebcommonsConstants.CHECKOUT_ERROR_AUTHORIZATION_FAILED;
import static com.adyen.commerce.constants.AdyenwebcommonsConstants.CHECKOUT_ERROR_FORM_ENTRY_INVALID;
import static com.adyen.commerce.util.ErrorMessageUtil.getErrorMessageByRefusalReason;
import static com.adyen.commerce.util.FieldValidationUtil.getFieldCodesFromValidation;
import static com.adyen.model.checkout.PaymentResponse.ResultCodeEnum.*;

public abstract class PlaceOrderControllerBase {
    private static final Logger LOGGER = Logger.getLogger(PlaceOrderControllerBase.class);
    protected static final ObjectMapper objectMapper = new ObjectMapper();

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
            OrderPaymentResult orderPaymentResult = getAdyenCheckoutApiFacade().placeOrderWithAdditionalDetails(paymentDetailsRequest);

            String orderCode = getCheckoutCustomerStrategy().isAnonymousCheckout() ? orderPaymentResult.getOrderData().getGuid() : orderPaymentResult.getOrderData().getCode();

            OCCPlaceOrderResponse placeOrderResponse = new OCCPlaceOrderResponse();
            placeOrderResponse.setOrderNumber(orderCode);
            placeOrderResponse.setOrderData(orderPaymentResult.getOrderData());
            placeOrderResponse.setPaymentDetailsResponse(orderPaymentResult.getPaymentDetailsResponse());
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

    protected void preHandleAndValidateRequest(PlaceOrderRequest placeOrderRequest, String adyenPaymentMethod) {
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

        CartData sessionCart = getCartFacade().getSessionCart();
        if (sessionCart == null || sessionCart.getEntries().isEmpty()) {
            throw new AdyenControllerException();
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
            String shopperIp = getAdyenShopperIpResolverService().resolveShopperIp(request);

            cartData.setAdyenReturnUrl(getPaymentRedirectReturnUrl());
            RequestInfo requestInfo = new RequestInfo(request, shopperIp);
            requestInfo.setStorefrontType(placeOrderRequest.getStorefrontType());
            requestInfo.setStorefrontVersion(placeOrderRequest.getStorefrontVersion());

            // Handle partial payment scenarios
            if (placeOrderRequest.getPartialPaymentId() != null && !placeOrderRequest.getPartialPaymentId().isEmpty()) {
                LOGGER.info("Processing partial payment with ID: " + placeOrderRequest.getPartialPaymentId());

                // Check if this is a second call for remaining amount payment by looking in CartData first
                AdyenPartialPaymentOrderData partialPaymentData = findPartialPaymentOrderDataByPspReference(cartData, placeOrderRequest.getPartialPaymentId());
                if (partialPaymentData != null && "AUTHORIZED".equals(partialPaymentData.getStatus())) {
                    LOGGER.info("Processing second payment for remaining amount: " + partialPaymentData.getRemainingAmount());
                    // Still need to get the model for the remaining amount payment processing
                    return handleRemainingAmountPayment(request, placeOrderRequest, cartData, requestInfo, partialPaymentData);
                } else {
                    // First call - process gift card payment
                    return handlePartialPayment(request, placeOrderRequest, cartData, requestInfo);
                }
            }

            OrderPaymentResult orderPaymentResult = getAdyenCheckoutApiFacade().placeOrderWithPayment(request, cartData, placeOrderRequest.getPaymentRequest(), requestInfo);
            OrderData orderData = orderPaymentResult.getOrderData();
            String orderCode = getCheckoutCustomerStrategy().isAnonymousCheckout() ? orderData.getGuid() : orderData.getCode();

            OCCPlaceOrderResponse placeOrderResponse = new OCCPlaceOrderResponse();
            placeOrderResponse.setOrderNumber(orderCode);
            placeOrderResponse.setOrderData(orderData);
            placeOrderResponse.setPaymentsResponse(orderPaymentResult.getPaymentResponse());
            return placeOrderResponse;
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

    /**
     * Handle partial payment processing for gift cards
     * Retrieves the partial payment from CartData and makes authorization call to Adyen
     */
    protected OCCPlaceOrderResponse handlePartialPayment(HttpServletRequest request, PlaceOrderRequest placeOrderRequest,
                                                      CartData cartData, RequestInfo requestInfo) {
        try {
            // Retrieve partial payment from CartData
            AdyenPartialPaymentOrderData partialPaymentData = findPartialPaymentOrderDataByPspReference(cartData, placeOrderRequest.getPartialPaymentId());
            if (partialPaymentData == null) {
                LOGGER.error("Partial payment not found for ID: " + placeOrderRequest.getPartialPaymentId());
                throw new AdyenControllerException(CHECKOUT_ERROR_AUTHORIZATION_FAILED);
            }

            LOGGER.info("Found partial payment: " + partialPaymentData.getPspReference() +
                       " with charged amount: " + partialPaymentData.getGiftCardChargedAmount());

            // Process partial payment authorization through facade
            PaymentResponse paymentResponse = getAdyenCheckoutApiFacade().processPartialPaymentAuthorization(
                cartData,
                placeOrderRequest.getPaymentRequest(),
                requestInfo,
                getCheckoutCustomerStrategy().getCurrentUserForCheckout(),
                partialPaymentData
            );

            // Handle the payment response
            if (PaymentResponse.ResultCodeEnum.AUTHORISED == paymentResponse.getResultCode()) {
                // Calculate remaining amount using helper class
                java.math.BigDecimal remainingAmount = RemainingAmountCalculator.calculateRemainingAmount(cartData, partialPaymentData);

                // Return response indicating partial payment was processed but order not placed
                OCCPlaceOrderResponse response = new OCCPlaceOrderResponse();
                response.setPaymentsResponse(paymentResponse);
                response.setPartialPaymentProcessed(true);
                response.setRemainingAmount(remainingAmount);
                response.setPartialPaymentId(paymentResponse.getPspReference());

                LOGGER.info("Gift card payment authorized successfully. Gift card amount: " + partialPaymentData.getGiftCardChargedAmount() +
                           ", Remaining amount: " + remainingAmount);
                return response;

            } else if (REDIRECTSHOPPER == paymentResponse.getResultCode() || CHALLENGESHOPPER == paymentResponse.getResultCode() ||
                      IDENTIFYSHOPPER == paymentResponse.getResultCode() || PENDING == paymentResponse.getResultCode() ||
                      PRESENTTOSHOPPER == paymentResponse.getResultCode()) {

                LOGGER.debug("Gift card payment requires action: " + paymentResponse.getResultCode());
                return executeAction(paymentResponse);

            } else {
                LOGGER.error("Gift card authorization failed: " + paymentResponse.getResultCode() +
                           " Refusal reason: " + paymentResponse.getRefusalReason());
                String errorMessage = getErrorMessageByRefusalReason(paymentResponse.getRefusalReason());
                throw new AdyenControllerException(errorMessage);
            }

        } catch (AdyenControllerException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error processing partial payment: " + ExceptionUtils.getStackTrace(e));
            throw new AdyenControllerException(CHECKOUT_ERROR_AUTHORIZATION_FAILED);
        }
    }

    /**
     * Handle the second payment call for remaining amount after gift card payment
     * This method processes the remaining amount with a different payment method and completes the order
     */
    protected OCCPlaceOrderResponse handleRemainingAmountPayment(HttpServletRequest request, PlaceOrderRequest placeOrderRequest,
                                                              CartData cartData, RequestInfo requestInfo,
                                                              AdyenPartialPaymentOrderData partialPayment) {
        try {
            LOGGER.info("Processing remaining amount payment. Partial payment PSP: " + partialPayment.getPspReference() +
                    ", Remaining amount: " + partialPayment.getRemainingAmount());


            OrderPaymentResult orderPaymentResult = getAdyenCheckoutApiFacade().placeOrderWithPayment(
                    request, cartData, placeOrderRequest.getPaymentRequest(), requestInfo, partialPayment);

            PaymentResponse paymentResponse = orderPaymentResult.getPaymentResponse();

            LOGGER.info("Remaining amount payment response: " + paymentResponse.getResultCode() +
                    " PSP Reference: " + paymentResponse.getPspReference());

            // Handle the payment response
            if (PaymentResponse.ResultCodeEnum.AUTHORISED == paymentResponse.getResultCode()) {
                // Both payments are now authorized, proceed with order placement
                LOGGER.info("Remaining amount payment authorized. Proceeding with order placement.");

                // Update partial payment status to completed
                getAdyenCheckoutApiFacade().updatePartialPaymentStatus(partialPayment, AdyenPartialPaymentStatus.AUTHORIZED);
                // Return the order data from the payment result
                OrderData orderData = orderPaymentResult.getOrderData();
                String orderCode = getCheckoutCustomerStrategy().isAnonymousCheckout() ? orderData.getGuid() : orderData.getCode();

                OCCPlaceOrderResponse placeOrderResponse = new OCCPlaceOrderResponse();
                placeOrderResponse.setOrderNumber(orderCode);
                placeOrderResponse.setOrderData(orderData);
                placeOrderResponse.setPaymentsResponse(paymentResponse);
                placeOrderResponse.setPartialPaymentCompleted(true);

                LOGGER.info("Order placed successfully with partial payments. Order code: " + orderCode);
                return placeOrderResponse;

            }  else {
                LOGGER.error("Remaining amount payment failed: " + paymentResponse.getResultCode() +
                        " Refusal reason: " + paymentResponse.getRefusalReason());

                // Mark partial payment as failed
                getAdyenCheckoutApiFacade().updatePartialPaymentStatus(partialPayment, AdyenPartialPaymentStatus.FAILED);
                String errorMessage = getErrorMessageByRefusalReason(paymentResponse.getRefusalReason());
                throw new AdyenControllerException(errorMessage);
            }

        } catch (AdyenNonAuthorizedPaymentException e) {
            LOGGER.info("Handling AdyenNonAuthorizedPaymentException. Checking PaymentResponse.");
            PaymentResponse paymentsResponse = e.getPaymentsResponse();
            if (REDIRECTSHOPPER == paymentsResponse.getResultCode() || CHALLENGESHOPPER == paymentsResponse.getResultCode() ||
                    IDENTIFYSHOPPER == paymentsResponse.getResultCode() || PENDING == paymentsResponse.getResultCode() ||
                    PRESENTTOSHOPPER == paymentsResponse.getResultCode()) {
                LOGGER.debug("PaymentResponse is " + paymentsResponse.getResultCode() + ", executing action for pspReference: " + paymentsResponse.getPspReference());
                return executeAction(paymentsResponse);
            }
            throw new AdyenControllerException(CHECKOUT_ERROR_AUTHORIZATION_FAILED);
        } catch (AdyenControllerException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error processing remaining amount payment: " + ExceptionUtils.getStackTrace(e));

            // Mark partial payment as failed
            getAdyenCheckoutApiFacade().updatePartialPaymentStatus(partialPayment, AdyenPartialPaymentStatus.FAILED);
            throw new AdyenControllerException(CHECKOUT_ERROR_AUTHORIZATION_FAILED);
        }
    }

    /**
     * Find partial payment order data from CartData by PSP reference
     */
    protected AdyenPartialPaymentOrderData findPartialPaymentOrderDataByPspReference(CartData cartData, String pspReference) {
        if (cartData.getAdyenPartialPaymentOrders() != null) {
            for (AdyenPartialPaymentOrderData partialPaymentData : cartData.getAdyenPartialPaymentOrders()) {
                if (pspReference.equals(partialPaymentData.getPspReference())) {
                    return partialPaymentData;
                }
            }
        }
        return null;
    }

    public abstract String getPaymentRedirectReturnUrl();

    public abstract AdyenCheckoutApiFacade getAdyenCheckoutApiFacade();

    public abstract CheckoutFlowFacade getCheckoutFlowFacade();

    public abstract CartFacade getCartFacade();

    public abstract BaseSiteService getBaseSiteService();

    public abstract SiteBaseUrlResolutionService getSiteBaseUrlResolutionService();

    public abstract AdyenCheckoutFacade getAdyenCheckoutFacade();

    public abstract CheckoutCustomerStrategy getCheckoutCustomerStrategy();

    public abstract AdyenPartialPaymentService getAdyenPartialPaymentService();

    public abstract AdyenShopperIpResolverService getAdyenShopperIpResolverService();

}
