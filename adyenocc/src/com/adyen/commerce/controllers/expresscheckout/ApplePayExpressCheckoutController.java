 package com.adyen.commerce.controllers.expresscheckout;

 import com.adyen.commerce.api.expresscheclout.ApplePayExpressCheckoutApi;
 import com.adyen.commerce.constants.AdyenoccConstants;
 import com.adyen.commerce.request.ApplePayExpressRequest;
 import com.adyen.commerce.resolver.PaymentRedirectReturnUrlResolver;
 import com.adyen.commerce.response.OCCPlaceOrderResponse;
 import com.adyen.model.checkout.ApplePayDetails;
 import com.adyen.model.checkout.CheckoutPaymentMethod;
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
@RequestMapping(value = AdyenoccConstants.ADYEN_USER_CART_PREFIX + "/express-checkout/apple")
@ApiVersion("v2")
public class ApplePayExpressCheckoutController extends ExpressCheckoutControllerBase implements ApplePayExpressCheckoutApi {

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
    public ResponseEntity<String> applePayPDPExpressCheckout(final HttpServletRequest request, @RequestBody String applePayExpressPDPStringRequest) throws Exception {
        ApplePayExpressRequest applePayExpressRequest = objectMapper.readValue(applePayExpressPDPStringRequest, ApplePayExpressRequest.class);

        PaymentRequest paymentRequest = getPaymentRequest(applePayExpressRequest);

        OCCPlaceOrderResponse placeOrderResponse = handlePayment(request, paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_APPLEPAY, applePayExpressRequest.getAddressData(), applePayExpressRequest.getCartId(), true);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = "/cart", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> applePayCartExpressCheckout(final HttpServletRequest request, @RequestBody String applePayExpressCartStringRequest) throws Exception {
        ApplePayExpressRequest applePayExpressRequest = objectMapper.readValue(applePayExpressCartStringRequest, ApplePayExpressRequest.class);

        PaymentRequest paymentRequest = getPaymentRequest(applePayExpressRequest);

        OCCPlaceOrderResponse placeOrderResponse = handlePayment(request, paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_APPLEPAY, applePayExpressRequest.getAddressData(), null, false);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    private static PaymentRequest getPaymentRequest(ApplePayExpressRequest request) {
        PaymentRequest paymentRequest = new PaymentRequest();
        ApplePayDetails applePayDetails = request.getApplePayDetails();
        applePayDetails.setType(ApplePayDetails.TypeEnum.APPLEPAY);
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(applePayDetails));
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

