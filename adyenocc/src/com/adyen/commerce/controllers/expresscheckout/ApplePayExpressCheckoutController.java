 package com.adyen.commerce.controllers.expresscheckout;

 import com.adyen.commerce.constants.AdyenoccConstants;
 import com.adyen.commerce.request.ApplePayExpressCartRequest;
 import com.adyen.commerce.request.ApplePayExpressPDPRequest;
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
 import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
 import io.swagger.v3.oas.annotations.Operation;
 import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Adyen")
public class ApplePayExpressCheckoutController extends ExpressCheckoutControllerBase {

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
    @Operation(operationId = "placeOrderApplePayExpressPDP", summary = "Handle applePayExpress place order request", description =
            "Places order based on request data")
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> applePayPDPExpressCheckout(final HttpServletRequest request, @RequestBody String applePayExpressPDPStringRequest) throws Exception {
        ApplePayExpressPDPRequest applePayExpressPDPRequest = objectMapper.readValue(applePayExpressPDPStringRequest, ApplePayExpressPDPRequest.class);

        PaymentRequest paymentRequest = getPaymentRequest(applePayExpressPDPRequest);

        OCCPlaceOrderResponse placeOrderResponse = handlePayment(request, paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_APPLEPAY, applePayExpressPDPRequest.getAddressData(), applePayExpressPDPRequest.getProductCode(), true);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = "/cart", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "placeOrderApplePayExpressCart", summary = "Handle applePayExpress place order request", description =
            "Places order based on request data")
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> applePayCartExpressCheckout(final HttpServletRequest request, @RequestBody String applePayExpressCartStringRequest) throws Exception {
        ApplePayExpressCartRequest applePayExpressCartRequest = objectMapper.readValue(applePayExpressCartStringRequest, ApplePayExpressCartRequest.class);

        PaymentRequest paymentRequest = getPaymentRequest(applePayExpressCartRequest);

        OCCPlaceOrderResponse placeOrderResponse = handlePayment(request, paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_APPLEPAY, applePayExpressCartRequest.getAddressData(), null, false);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    private static <T extends ApplePayExpressCartRequest> PaymentRequest getPaymentRequest(T request) {
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

