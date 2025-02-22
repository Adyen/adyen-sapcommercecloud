package com.adyen.commerce.controllers.expresscheckout;

import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.commerce.request.PayPalExpressCartRequest;
import com.adyen.commerce.request.PayPalExpressPDPRequest;
import com.adyen.commerce.request.PayPalIntermediateRequest;
import com.adyen.commerce.resolver.PaymentRedirectReturnUrlResolver;
import com.adyen.commerce.response.OCCPlaceOrderResponse;
import com.adyen.commerce.response.PayPalIntermediateResponse;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PayPalDetails;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.facades.AdyenExpressCheckoutFacade;
import com.adyen.v6.facades.AdyenPayPalExpressCheckoutFacade;
import com.adyen.v6.response.PayPalExpressSubmitResponse;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(value = AdyenoccConstants.ADYEN_USER_CART_PREFIX + "/express-checkout/paypal")
@ApiVersion("v2")
@Tag(name = "Adyen")
public class PayPalExpressCheckoutController extends ExpressCheckoutControllerBase {

    @Autowired
    private CartFacade cartFacade;

    @Autowired
    private CheckoutCustomerStrategy checkoutCustomerStrategy;

    @Autowired
    private AdyenExpressCheckoutFacade adyenExpressCheckoutFacade;

    @Autowired
    private PaymentRedirectReturnUrlResolver paymentRedirectReturnUrlResolver;

    @Autowired
    private AdyenPayPalExpressCheckoutFacade adyenPayPalExpressCheckoutFacade;


    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = "/PDP", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "placeOrderPayPalExpressPDP", summary = "Handle PayPalExpress place order request", description =
            "Places order based on request data")
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> PayPalCartExpressCheckoutPDP(final HttpServletRequest request, @RequestBody String PayPalExpressPDPRequestString) throws Exception {
        PayPalExpressPDPRequest payPalExpressPDPRequest = objectMapper.readValue(PayPalExpressPDPRequestString, PayPalExpressPDPRequest.class);

        PaymentRequest paymentRequest = getPaymentRequest(payPalExpressPDPRequest);

        OCCPlaceOrderResponse placeOrderResponse = handlePayment(request, paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_PAYPAL, payPalExpressPDPRequest.getAddressData(), payPalExpressPDPRequest.getProductCode(), true);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = "/cart", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "placeOrderPayPalExpressCart", summary = "Handle PayPalExpress place order request", description =
            "Places order based on request data")
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> PayPalCartExpressCheckoutCart(final HttpServletRequest request, @RequestBody String PayPalExpressCartRequestString) throws Exception {
        PayPalExpressCartRequest payPalExpressCartRequest = objectMapper.readValue(PayPalExpressCartRequestString, PayPalExpressCartRequest.class);
        PaymentRequest paymentRequest = getPaymentRequest(payPalExpressCartRequest);

        OCCPlaceOrderResponse placeOrderResponse = handlePayment(request, paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_PAYPAL, payPalExpressCartRequest.getAddressData(), null, false);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = "/submit/PDP", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "placeOrderPayPalExpressPDP", summary = "Handle PayPalExpress place order request", description =
            "Places order based on request data")
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> onSubmitPDP(final HttpServletRequest request, final HttpServletResponse response, @RequestBody String payPalIntermediateRequestString) throws Exception {
        PayPalIntermediateRequest payPalIntermediateRequest = objectMapper.readValue(payPalIntermediateRequestString, PayPalIntermediateRequest.class);
        PayPalDetails payPalDetails = payPalIntermediateRequest.getPayPalDetails();
        PaymentRequest paymentRequest = new PaymentRequest();
        payPalDetails.setType(PayPalDetails.TypeEnum.PAYPAL);
        payPalDetails.setSubtype(PayPalDetails.SubtypeEnum.EXPRESS);

        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(payPalDetails));


        try {
            PayPalExpressSubmitResponse payPalExpressSubmitResponse = adyenPayPalExpressCheckoutFacade.onPayPalPDPSubmit(paymentRequest, payPalIntermediateRequest.getProductCode());
            PayPalIntermediateResponse paymentResponse = new PayPalIntermediateResponse();
            paymentResponse.setPaymentResponse(payPalExpressSubmitResponse.getPaymentResponse());
            paymentResponse.setExpressCartGuid(payPalExpressSubmitResponse.getExpressCartGuid().toString());
            String paymentResponseString = objectMapper.writeValueAsString(paymentResponse);
            return new ResponseEntity<>(paymentResponseString, HttpStatus.OK);

        } catch (ApiException e){
            LOGGER.error(e.getError());
            LOGGER.error(e.getMessage());

            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

    }

    private static <T extends PayPalExpressCartRequest> PaymentRequest getPaymentRequest(T request) {
        PaymentRequest paymentRequest = new PaymentRequest();
        PayPalDetails payPalDetails = request.getPayPalDetails();
        payPalDetails.setType(PayPalDetails.TypeEnum.PAYPAL);
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(payPalDetails));
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
