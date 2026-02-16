package com.adyen.v6.controllers.checkout;

import com.adyen.model.checkout.ApplePayDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.facades.AdyenExpressCheckoutFacade;
import com.adyen.v6.request.ApplePayExpressRequest;
import de.hybris.platform.acceleratorstorefrontcommons.security.GUIDCookieStrategy;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/express-checkout/apple/")
public class AdyenApplePayExpressCheckoutController {
    private static final Logger LOG = Logger.getLogger(AdyenApplePayExpressCheckoutController.class);

    @Resource
    private AdyenExpressCheckoutFacade adyenExpressCheckoutFacade;

    @Resource
    private GUIDCookieStrategy guidCookieStrategy;

    @PostMapping("PDP")
    public ResponseEntity applePayExpressPDP(final HttpServletRequest request, final HttpServletResponse response, @RequestBody ApplePayExpressRequest applePayExpressPDPRequest) throws Exception {

        PaymentRequest paymentRequest = getPaymentRequest(applePayExpressPDPRequest);

        PaymentResponse paymentsResponse = adyenExpressCheckoutFacade.expressCheckoutPDP(applePayExpressPDPRequest.getCartId(),
                paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_APPLEPAY, applePayExpressPDPRequest.getAddressData(), request);

        guidCookieStrategy.setCookie(request, response);

        return new ResponseEntity<>(paymentsResponse, HttpStatus.OK);
    }

    @PostMapping("cart")
    public ResponseEntity cartExpressCheckout(final HttpServletRequest request, final HttpServletResponse response, @RequestBody ApplePayExpressRequest applePayExpressRequest) throws Exception {

        PaymentRequest paymentRequest = getPaymentRequest(applePayExpressRequest);

        PaymentResponse paymentsResponse = adyenExpressCheckoutFacade.expressCheckoutCart(paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_APPLEPAY,
                applePayExpressRequest.getAddressData(), request);

        guidCookieStrategy.setCookie(request, response);

        return new ResponseEntity<>(paymentsResponse, HttpStatus.OK);
    }

    private static PaymentRequest getPaymentRequest(ApplePayExpressRequest request) {
        PaymentRequest paymentRequest = new PaymentRequest();
        ApplePayDetails applePayDetails = request.getApplePayDetails();
        applePayDetails.setType(ApplePayDetails.TypeEnum.APPLEPAY);
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(applePayDetails));
        return paymentRequest;
    }


    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = Exception.class)
    public void adyenComponentExceptionHandler(Exception e) {
        if (e instanceof ApiException) {
            LOG.error("Api Exception: " + ((ApiException) e).getResponseBody());
        }
        LOG.error("Exception during ApplePayExpress processing", e);
    }
}
