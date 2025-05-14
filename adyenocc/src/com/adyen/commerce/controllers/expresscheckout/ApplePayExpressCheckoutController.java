 package com.adyen.commerce.controllers.expresscheckout;

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
 import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
 import io.swagger.v3.oas.annotations.Operation;
 import io.swagger.v3.oas.annotations.media.Content;
 import io.swagger.v3.oas.annotations.media.Schema;
 import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    @Operation(
            operationId = "placeOrderApplePayExpressPDP",
            summary = "Handle Apple Pay Express place order request from PDP",
            description = "Places an order using Apple Pay Express Checkout initiated from the Product Detail Page (PDP). " +
                    "The request should contain Apple Pay token details and optionally address data.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Apple Pay Express PDP request details, including Apple Pay token and address information.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApplePayExpressRequest.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Order placed successfully using Apple Pay Express from PDP. Returns order confirmation details.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = OCCPlaceOrderResponse.class) // Actual response object
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Bad Request - Invalid Apple Pay data, cart issue, or address validation failure."),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required."),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions."),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error - Error during order processing.")
            }
    )
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> applePayPDPExpressCheckout(final HttpServletRequest request, @RequestBody String applePayExpressPDPStringRequest) throws Exception {
        ApplePayExpressRequest applePayExpressRequest = objectMapper.readValue(applePayExpressPDPStringRequest, ApplePayExpressRequest.class);

        PaymentRequest paymentRequest = getPaymentRequest(applePayExpressRequest);

        OCCPlaceOrderResponse placeOrderResponse = handlePayment(request, paymentRequest, Adyenv6coreConstants.PAYMENT_METHOD_APPLEPAY, applePayExpressRequest.getAddressData(), applePayExpressRequest.getCartId(), true);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = "/cart", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "placeOrderApplePayExpressCart",
            summary = "Handle Apple Pay Express place order request from Cart",
            description = "Places an order using Apple Pay Express Checkout initiated from the Cart page. " +
                    "The request should contain Apple Pay token details and optionally address data.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Apple Pay Express Cart request details, including Apple Pay token and address information.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApplePayExpressRequest.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Order placed successfully using Apple Pay Express from Cart. Returns order confirmation details.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = OCCPlaceOrderResponse.class) // Actual response object
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Bad Request - Invalid Apple Pay data, cart issue, or address validation failure."),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required."),
                    @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions."),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error - Error during order processing.")
            }
    )
    @ApiBaseSiteIdUserIdAndCartIdParam
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

