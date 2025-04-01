package com.adyen.commerce.controllerbase;

import com.adyen.model.checkout.PaymentCompletionDetails;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commerceservices.i18n.CommerceCommonI18NService;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;

import static com.adyen.commerce.constants.AdyenwebcommonsConstants.CHECKOUT_ERROR_AUTHORIZATION_FAILED;
import static com.adyen.commerce.util.ErrorMessageUtil.getErrorMessageByRefusalReason;
import static com.adyen.model.checkout.PaymentDetailsResponse.ResultCodeEnum;
import static com.adyen.model.checkout.PaymentDetailsResponse.ResultCodeEnum.REFUSED;


public abstract class RedirectControllerBase {

    private static final Logger LOGGER = Logger.getLogger(RedirectControllerBase.class);
    private static final String REDIRECT_RESULT = "redirectResult";
    private static final String PAYLOAD = "payload";
    private static final String NON_AUTHORIZED_ERROR = "Handling AdyenNonAuthorizedPaymentException. Checking PaymentResponse.";
    private static final String REDIRECTING_TO_CART_PAGE = "Redirecting to cart page...";


    public String authoriseRedirectGetPayment(final HttpServletRequest request) {
        String redirectResult = request.getParameter(REDIRECT_RESULT);
        PaymentDetailsRequest paymentDetailsRequest = new PaymentDetailsRequest();
        if (redirectResult != null && !redirectResult.isEmpty()) {
            PaymentCompletionDetails details = new PaymentCompletionDetails();
            details.setRedirectResult(redirectResult);
            paymentDetailsRequest.details(details);
        } else {
            String payload = request.getParameter(PAYLOAD);
            if (payload != null && !payload.isEmpty()) {
                PaymentCompletionDetails details = new PaymentCompletionDetails();
                details.setPayload(payload);
                paymentDetailsRequest.details(details);
            }
        }
        return authoriseRedirectPayment(paymentDetailsRequest);
    }


    public String authoriseRedirectPostPayment(final PaymentDetailsRequest detailsRequest) {
        return authoriseRedirectPayment(detailsRequest);
    }

    private String authoriseRedirectPayment(final PaymentDetailsRequest details) {
        LOGGER.info("Redirect payment authorization");

        try {
            OrderData orderData = getAdyenCheckoutFacade().handle3DSResponse(details);

            LOGGER.debug("Redirecting to confirmation");

            return getOrderConfirmationUrl(orderData);

        } catch (AdyenNonAuthorizedPaymentException e) {
            LOGGER.debug(NON_AUTHORIZED_ERROR);
            String errorMessage = CHECKOUT_ERROR_AUTHORIZATION_FAILED;
            PaymentDetailsResponse response = e.getPaymentsDetailsResponse();
            if (response != null) {
                if (REFUSED.equals(response.getResultCode())) {
                    LOGGER.info("PaymentResponse " + response.getPspReference() + " is REFUSED: " + response);
                    errorMessage = getErrorMessageByRefusalReason(response.getRefusalReason());
                }
                if (ResultCodeEnum.ERROR.equals(response.getResultCode())) {
                    LOGGER.error("Payment " + response.getPspReference() + " result is error, reason:  "
                            + response.getRefusalReason());
                }
            }
            return getErrorRedirectUrl(errorMessage);
        } catch (CalculationException | InvalidCartException e) {
            LOGGER.warn(e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }

        LOGGER.warn(REDIRECTING_TO_CART_PAGE);
        return getCartUrl();
    }

    public abstract String getErrorRedirectUrl(String errorMessage);

    public abstract String getOrderConfirmationUrl(OrderData orderData);

    public abstract String getCartUrl();

    public abstract AdyenCheckoutFacade getAdyenCheckoutFacade();
}
