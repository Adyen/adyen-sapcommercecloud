package com.adyen.v6.controllers.checkout;

import com.adyen.model.checkout.ApplePayDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.facades.AdyenExpressCheckoutFacade;
import com.adyen.v6.request.ApplePayExpressRequest;
import de.hybris.platform.acceleratorstorefrontcommons.security.GUIDCookieStrategy;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.order.exceptions.CalculationException;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/express-checkout/apple/")
public class AdyenApplePayExpressCheckoutController {
    private static final Logger LOG = Logger.getLogger(AdyenApplePayExpressCheckoutController.class);

    @Autowired
    private AdyenExpressCheckoutFacade adyenExpressCheckoutFacade;

    @Autowired
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

    /* Prevent breaking current implementation. To be removed when implementation will be completed. */
    private String createCartWithProduct(String productCode) throws CalculationException {
        CartData cart = adyenExpressCheckoutFacade.createOrGetCartForExpressCheckout(productCode);
        adyenExpressCheckoutFacade.prepareCartForExpressCheckoutWithProduct(cart.getCode(), productCode, 1);
        return cart.getCode();
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
        LOG.error("Exception during ApplePayExpress processing", e);
    }
}
