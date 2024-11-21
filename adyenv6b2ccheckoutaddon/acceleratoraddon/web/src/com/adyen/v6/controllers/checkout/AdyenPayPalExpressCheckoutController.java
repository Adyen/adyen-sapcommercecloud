package com.adyen.v6.controllers.checkout;

import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PayPalDetails;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.facades.AdyenExpressCheckoutFacade;
import com.adyen.v6.request.PayPalExpressCartRequest;
import com.adyen.v6.request.PayPalExpressPDPRequest;
import de.hybris.platform.acceleratorstorefrontcommons.security.GUIDCookieStrategy;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/express-checkout/paypal/")
public class AdyenPayPalExpressCheckoutController {
    private static final Logger LOG = Logger.getLogger(AdyenApplePayExpressCheckoutController.class);

    @Autowired
    private AdyenExpressCheckoutFacade adyenExpressCheckoutFacade;

    @Autowired
    private GUIDCookieStrategy guidCookieStrategy;

    @PostMapping("PDP")
    public ResponseEntity payPalExpressPDP(final HttpServletRequest request, final HttpServletResponse response, @RequestBody PayPalExpressPDPRequest paypalExpressPDPRequest) throws Exception {

        PaymentRequest paymentRequest = getPaymentRequest(paypalExpressPDPRequest);

        PaymentResponse paymentsResponse = adyenExpressCheckoutFacade.expressCheckoutPDP(paypalExpressPDPRequest.getProductCode(),
                paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_PAYPAL, paypalExpressPDPRequest.getAddressData(), request);

        guidCookieStrategy.setCookie(request, response);

        return new ResponseEntity<>(paymentsResponse, HttpStatus.OK);
    }

    @PostMapping("cart")
    public ResponseEntity paypalCartExpressCheckout(final HttpServletRequest request, final HttpServletResponse response, @RequestBody PayPalExpressCartRequest paypalExpressCartRequest) throws Exception {

        PaymentRequest paymentRequest = getPaymentRequest(paypalExpressCartRequest);

        PaymentResponse paymentsResponse = adyenExpressCheckoutFacade.expressCheckoutCart(paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_PAYPAL,
                paypalExpressCartRequest.getAddressData(), request);

        guidCookieStrategy.setCookie(request, response);

        return new ResponseEntity<>(paymentsResponse, HttpStatus.OK);
    }

    private static <T extends PayPalExpressCartRequest> PaymentRequest getPaymentRequest(T request) {
        PaymentRequest paymentRequest = new PaymentRequest();
        PayPalDetails paypalDetails = request.getPayPalDetails();
        paypalDetails.setType(PayPalDetails.TypeEnum.PAYPAL);
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(paypalDetails));
        return paymentRequest;
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = Exception.class)
    public void adyenComponentExceptionHandler(Exception e) {
        LOG.error("Exception during PaypalExpress processing", e);
    }
}
