package com.adyen.commerce.occ.controllers.expresscheckout;

import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.commerce.occ.api.expresscheclout.PayPalExpressCheckoutApi;
import com.adyen.commerce.occ.request.PayPalExpressCartRequest;
import com.adyen.commerce.occ.request.PayPalExpressPDPRequest;
import com.adyen.commerce.occ.request.PayPalIntermediateRequest;
import com.adyen.commerce.response.OCCPlaceOrderResponse;
import com.adyen.model.checkout.*;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.facades.AdyenExpressCheckoutFacade;
import com.adyen.v6.facades.AdyenPayPalExpressCheckoutFacade;
import com.adyen.v6.response.PayPalExpressSubmitResponse;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@ApiVersion("v2")
public class PayPalExpressCheckoutController extends ExpressCheckoutControllerBase implements PayPalExpressCheckoutApi {

    private static final String EXPRESS_CHECKOUT_PAYPAL = "/express-checkout/paypal";
    private static final String UPDATE_ORDER_PAYPAL_EXPRESS_CHECKOUT_PATH = AdyenoccConstants.ADYEN_USER_CART_PREFIX + EXPRESS_CHECKOUT_PAYPAL + "/update-order";
    private static final String ADYEN_USER_CART_PAYPAL_PREFIX = AdyenoccConstants.ADYEN_USER_CART_PREFIX + EXPRESS_CHECKOUT_PAYPAL;
    @Autowired
    private CartFacade cartFacade;

    @Autowired
    private CheckoutCustomerStrategy checkoutCustomerStrategy;

    @Autowired
    private AdyenExpressCheckoutFacade adyenExpressCheckoutFacade;

    @Autowired
    private AdyenPayPalExpressCheckoutFacade adyenPayPalExpressCheckoutFacade;


    @Override
    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = ADYEN_USER_CART_PAYPAL_PREFIX + "/PDP", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> PayPalCartExpressCheckoutPDP(final HttpServletRequest request, @RequestBody String PayPalExpressPDPRequestString) throws Exception {
        PayPalExpressPDPRequest payPalExpressPDPRequest = objectMapper.readValue(PayPalExpressPDPRequestString, PayPalExpressPDPRequest.class);

        PaymentRequest paymentRequest = getPaymentRequest(payPalExpressPDPRequest);

        OCCPlaceOrderResponse placeOrderResponse = handlePayment(request, paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_PAYPAL, payPalExpressPDPRequest.getAddressData(), payPalExpressPDPRequest.getCartId(), true);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    @Override
    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = ADYEN_USER_CART_PAYPAL_PREFIX + "/cart", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> PayPalCartExpressCheckoutCart(final HttpServletRequest request, @RequestBody String PayPalExpressCartRequestString) throws Exception {
        PayPalExpressCartRequest payPalExpressCartRequest = objectMapper.readValue(PayPalExpressCartRequestString, PayPalExpressCartRequest.class);
        PaymentRequest paymentRequest = getPaymentRequest(payPalExpressCartRequest);

        OCCPlaceOrderResponse placeOrderResponse = handlePayment(request, paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_PAYPAL, payPalExpressCartRequest.getAddressData(), null, false);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    @Override
    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value =  ADYEN_USER_CART_PAYPAL_PREFIX+ "/submit/PDP", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> onSubmitPDP(final HttpServletRequest request, final HttpServletResponse response, @RequestBody String payPalIntermediateRequestString) throws Exception {
        PayPalIntermediateRequest payPalIntermediateRequest = objectMapper.readValue(payPalIntermediateRequestString, PayPalIntermediateRequest.class);
        PayPalDetails payPalDetails = payPalIntermediateRequest.getPayPalDetails();
        PaymentRequest paymentRequest = new PaymentRequest();
        payPalDetails.setType(PayPalDetails.TypeEnum.PAYPAL);
        payPalDetails.setSubtype(PayPalDetails.SubtypeEnum.EXPRESS);
        paymentRequest.setReference(payPalIntermediateRequest.getCartId());

        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(payPalDetails));


        try {
            PayPalExpressSubmitResponse payPalExpressSubmitResponse = adyenPayPalExpressCheckoutFacade.onPayPalPDPSubmitOCC(request, paymentRequest);
            String paymentResponseString = objectMapper.writeValueAsString(payPalExpressSubmitResponse.getPaymentResponse());
            return new ResponseEntity<>(paymentResponseString, HttpStatus.OK);

        } catch (ApiException e){
            LOGGER.error(String.format("PayPal submit request failed. Error: {}, Message: {}, Response body: {}",
                    e.getError().toString(), e.getMessage(), e.getResponseBody()));
            return ResponseEntity.badRequest().build();
        }

    }

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = UPDATE_ORDER_PAYPAL_EXPRESS_CHECKOUT_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> paypalUpdateOrder(final HttpServletRequest request, final HttpServletResponse response, @RequestBody String payPalpalUpdateOrderRequestString) throws Exception {
        PaypalUpdateOrderRequest paypalUpdateOrderRequest = objectMapper.readValue(payPalpalUpdateOrderRequestString, PaypalUpdateOrderRequest.class);
        PaypalUpdateOrderResponse paypalUpdateOrderResponse = adyenPayPalExpressCheckoutFacade.getPaypalUpdateOrderResponse(paypalUpdateOrderRequest);
        String paymentResponseString = objectMapper.writeValueAsString(paypalUpdateOrderResponse);
        return new ResponseEntity<>(paymentResponseString, HttpStatus.OK);
    }

    private static <T extends PayPalExpressCartRequest> PaymentRequest getPaymentRequest(T request) {
        PaymentRequest paymentRequest = new PaymentRequest();
        PayPalDetails payPalDetails = request.getPayPalDetails();
        payPalDetails.setType(PayPalDetails.TypeEnum.PAYPAL);
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(payPalDetails));
        paymentRequest.setReturnUrl(request.getReturnUrl());
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
    public AdyenExpressCheckoutFacade getAdyenCheckoutApiFacade() {
        return adyenExpressCheckoutFacade;
    }
}
