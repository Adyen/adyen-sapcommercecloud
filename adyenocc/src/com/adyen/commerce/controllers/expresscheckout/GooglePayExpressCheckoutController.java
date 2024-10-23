package com.adyen.commerce.controllers.expresscheckout;

import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.commerce.request.GooglePayExpressCartRequest;
import com.adyen.commerce.request.GooglePayExpressPDPRequest;
import com.adyen.commerce.resolver.PaymentRedirectReturnUrlResolver;
import com.adyen.commerce.response.OCCPlaceOrderResponse;
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
@RequestMapping(value = AdyenoccConstants.ADYEN_USER_CART_PREFIX + "/express-checkout/google")
@ApiVersion("v2")
@Tag(name = "Adyen")
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
    @Operation(operationId = "placeOrderGooglePayExpressPDP", summary = "Handle googlePayExpress place order request", description =
            "Places order based on request data")
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> googlePayCartExpressCheckout(final HttpServletRequest request, @RequestBody GooglePayExpressPDPRequest googlePayExpressPDPRequest) throws Exception {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(googlePayExpressPDPRequest.getGooglePayDetails()));

        OCCPlaceOrderResponse placeOrderResponse = handlePayment(request, paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_GOOGLE_PAY, googlePayExpressPDPRequest.getAddressData(), googlePayExpressPDPRequest.getProductCode(), true);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = "/cart", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "placeOrderGooglePayExpressCart", summary = "Handle googlePayExpress place order request", description =
            "Places order based on request data")
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> googlePayCartExpressCheckout(final HttpServletRequest request, @RequestBody GooglePayExpressCartRequest googlePayExpressCartRequest) throws Exception {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(googlePayExpressCartRequest.getGooglePayDetails()));

        OCCPlaceOrderResponse placeOrderResponse = handlePayment(request, paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_GOOGLE_PAY, googlePayExpressCartRequest.getAddressData(), null, false);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
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
