package com.adyen.v6.controllers.checkout;

import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.GooglePayDetails;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.commerce.facades.AdyenExpressCheckoutFacade;
import com.adyen.v6.request.GooglePayExpressRequest;
import de.hybris.platform.acceleratorstorefrontcommons.security.GUIDCookieStrategy;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


@Controller
@RequestMapping("/express-checkout/google/")
public class AdyenGooglePayExpressCheckoutController {
    private static final Logger LOG = Logger.getLogger(AdyenGooglePayExpressCheckoutController.class);

    @Resource
    private AdyenExpressCheckoutFacade adyenExpressCheckoutFacade;

    @Resource
    private GUIDCookieStrategy guidCookieStrategy;

    @PostMapping("PDP")
    public ResponseEntity googlePayExpressPDP(final HttpServletRequest request, final HttpServletResponse response, @RequestBody GooglePayExpressRequest googlePayExpressPDPRequest) throws Exception {

        PaymentRequest paymentRequest = getPaymentRequest(googlePayExpressPDPRequest);

        PaymentResponse paymentsResponse = adyenExpressCheckoutFacade.expressCheckoutPDP(googlePayExpressPDPRequest.getCartId(),
                paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_GOOGLE_PAY, googlePayExpressPDPRequest.getAddressData(), request);

        guidCookieStrategy.setCookie(request, response);

        return new ResponseEntity<>(paymentsResponse, HttpStatus.OK);
    }

    @PostMapping("cart")
    public ResponseEntity googlePayCartExpressCheckout(final HttpServletRequest request, final HttpServletResponse response, @RequestBody GooglePayExpressRequest googlePayExpressRequest) throws Exception {

        PaymentRequest paymentRequest = getPaymentRequest(googlePayExpressRequest);

        PaymentResponse paymentsResponse = adyenExpressCheckoutFacade.expressCheckoutCart(paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_GOOGLE_PAY,
                googlePayExpressRequest.getAddressData(), request);

        guidCookieStrategy.setCookie(request, response);

        return new ResponseEntity<>(paymentsResponse, HttpStatus.OK);
    }

    private static PaymentRequest getPaymentRequest(GooglePayExpressRequest request) {
        PaymentRequest paymentRequest = new PaymentRequest();
        GooglePayDetails googlePayDetails = request.getGooglePayDetails();
        googlePayDetails.setType(GooglePayDetails.TypeEnum.GOOGLEPAY);
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(googlePayDetails));
        return paymentRequest;
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = Exception.class)
    public void adyenComponentExceptionHandler(Exception e) {
        if (e instanceof ApiException) {
            LOG.error("Api Exception: " + ((ApiException) e).getResponseBody());
        }
        LOG.error("Exception during GooglePayExpress processing", e);
    }
}
