package com.adyen.v6.controllers.checkout;

import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.facades.AdyenExpressCheckoutFacade;
import com.adyen.v6.request.GooglePayExpressCartRequest;
import com.adyen.v6.request.GooglePayExpressPDPRequest;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.security.GUIDCookieStrategy;
import de.hybris.platform.servicelayer.session.SessionService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/express-checkout/google/")
public class AdyenGooglePayExpressCheckoutController {
    private static final Logger LOG = Logger.getLogger(AdyenApplePayExpressCheckoutController.class);

    @Autowired
    private AdyenExpressCheckoutFacade adyenExpressCheckoutFacade;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private GUIDCookieStrategy guidCookieStrategy;

    @PostMapping("PDP")
    public ResponseEntity googlePayExpressPDP(final HttpServletRequest request, final HttpServletResponse response, @RequestBody GooglePayExpressPDPRequest googlePayExpressPDPRequest) throws Exception {

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(googlePayExpressPDPRequest.getGooglePayDetails()));

        PaymentResponse paymentsResponse = adyenExpressCheckoutFacade.expressCheckoutPDP(googlePayExpressPDPRequest.getProductCode(),
                paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_GOOGLE_PAY, googlePayExpressPDPRequest.getAddressData(), request);

        guidCookieStrategy.setCookie(request, response);
        sessionService.setAttribute(WebConstants.ANONYMOUS_CHECKOUT, Boolean.TRUE);

        return new ResponseEntity<>(paymentsResponse, HttpStatus.OK);
    }

    @PostMapping("cart")
    public ResponseEntity googlePayCartExpressCheckout(final HttpServletRequest request, final HttpServletResponse response, @RequestBody GooglePayExpressCartRequest googlePayExpressCartRequest) throws Exception {

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(googlePayExpressCartRequest.getGooglePayDetails()));

        PaymentResponse paymentsResponse = adyenExpressCheckoutFacade.expressCheckoutCart(paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_GOOGLE_PAY,
                googlePayExpressCartRequest.getAddressData(), request);

        guidCookieStrategy.setCookie(request, response);
        sessionService.setAttribute(WebConstants.ANONYMOUS_CHECKOUT, Boolean.TRUE);

        return new ResponseEntity<>(paymentsResponse, HttpStatus.OK);
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = Exception.class)
    public void adyenComponentExceptionHandler(Exception e) {
        LOG.error("Exception during GooglePayExpress processing", e);
    }
}
