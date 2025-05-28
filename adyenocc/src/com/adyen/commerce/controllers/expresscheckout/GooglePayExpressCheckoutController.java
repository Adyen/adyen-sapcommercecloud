package com.adyen.commerce.controllers.expresscheckout;

import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.commerce.request.GooglePayExpressRequest;
import com.adyen.commerce.resolver.PaymentRedirectReturnUrlResolver;
import com.adyen.commerce.response.OCCPlaceOrderResponse;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.GooglePayDetails;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.facades.AdyenExpressCheckoutFacade;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(value = AdyenoccConstants.ADYEN_USER_CART_PREFIX + "/express-checkout/google")
@ApiVersion("v2")
public class GooglePayExpressCheckoutController extends ExpressCheckoutControllerBase {

    @Autowired
    private CartFacade cartFacade;

    @Autowired
    private CheckoutCustomerStrategy checkoutCustomerStrategy;

    @Autowired
    private AdyenExpressCheckoutFacade adyenExpressCheckoutFacade;

    @Autowired
    private PaymentRedirectReturnUrlResolver paymentRedirectReturnUrlResolver;


    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = "/PDP", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> googlePayCartExpressCheckoutPDP(final HttpServletRequest request, @RequestBody String googlePayExpressPDPRequestString) throws Exception {
        GooglePayExpressRequest googlePayExpressRequest = objectMapper.readValue(googlePayExpressPDPRequestString, GooglePayExpressRequest.class);

        PaymentRequest paymentRequest = getPaymentRequest(googlePayExpressRequest);

        OCCPlaceOrderResponse placeOrderResponse = handlePayment(request, paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_GOOGLE_PAY, googlePayExpressRequest.getAddressData(), googlePayExpressRequest.getCartId(), true);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = "/cart", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> googlePayCartExpressCheckoutCart(final HttpServletRequest request, @RequestBody String googlePayExpressCartRequestString) throws Exception {
        GooglePayExpressRequest googlePayExpressRequest = objectMapper.readValue(googlePayExpressCartRequestString, GooglePayExpressRequest.class);
        PaymentRequest paymentRequest = getPaymentRequest(googlePayExpressRequest);

        OCCPlaceOrderResponse placeOrderResponse = handlePayment(request, paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_GOOGLE_PAY, googlePayExpressRequest.getAddressData(), null, false);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    private static PaymentRequest getPaymentRequest(GooglePayExpressRequest request) {
        PaymentRequest paymentRequest = new PaymentRequest();
        GooglePayDetails googlePayDetails = request.getGooglePayDetails();
        googlePayDetails.setType(GooglePayDetails.TypeEnum.GOOGLEPAY);
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(googlePayDetails));
        return paymentRequest;
    }

    @Override
    public CartFacade getCartFacade() {
        return cartFacade;
    }

    @Override
    public CheckoutCustomerStrategy getCheckoutCustomerStrategy() {
        return checkoutCustomerStrategy;
    }

    @Override
    public String getPaymentRedirectReturnUrl() {
        return paymentRedirectReturnUrlResolver.resolvePaymentRedirectReturnUrl();
    }

    @Override
    public AdyenExpressCheckoutFacade getAdyenCheckoutApiFacade() {
        return adyenExpressCheckoutFacade;
    }
}
