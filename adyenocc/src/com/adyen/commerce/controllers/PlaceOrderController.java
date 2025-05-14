package com.adyen.commerce.controllers;

import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.commerce.controllerbase.PlaceOrderControllerBase;
import com.adyen.commerce.facades.AdyenCheckoutApiFacade;
import com.adyen.commerce.request.PlaceOrderRequest;
import com.adyen.commerce.resolver.PaymentRedirectReturnUrlResolver;
import com.adyen.commerce.response.OCCPlaceOrderResponse;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.hybris.platform.acceleratorfacades.flow.CheckoutFlowFacade;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
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
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@ApiVersion("v2")
@Tag(name = "Adyen")
public class PlaceOrderController extends PlaceOrderControllerBase {

    @Autowired
    private AdyenCheckoutApiFacade adyenCheckoutApiFacade;

    @Autowired
    private CheckoutFlowFacade checkoutFlowFacade;

    @Autowired
    private CartFacade cartFacade;

    @Resource(name = "siteBaseUrlResolutionService")
    private SiteBaseUrlResolutionService siteBaseUrlResolutionService;

    @Resource(name = "baseSiteService")
    private BaseSiteService baseSiteService;

    @Autowired
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Resource(name = "checkoutCustomerStrategy")
    private CheckoutCustomerStrategy checkoutCustomerStrategy;

    @Autowired
    private PaymentRedirectReturnUrlResolver paymentRedirectReturnUrlResolver;

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = AdyenoccConstants.ADYEN_USER_CART_PREFIX + "/place-order", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "placeOrder",
            summary = "Handle place order request",
            description = "Places order based on request data",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody( // Define the request body explicitly
                    description = "Place order request details",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PlaceOrderRequest.class) // Specify the actual schema here
                    )
            ),
            responses = {
                    @ApiResponse( // Define the response explicitly
                            responseCode = "200",
                            description = "Order placement response",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = OCCPlaceOrderResponse.class) // Specify the actual schema here
                            )
                    ),
                    // You might want to add other response codes like 400, 500 etc.
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error")
            }
    )
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> onPlaceOrder(@RequestBody String placeOrderStringRequest, HttpServletRequest request) throws Exception {
        PlaceOrderRequest placeOrderRequest = objectMapper.readValue(placeOrderStringRequest, PlaceOrderRequest.class);
        OCCPlaceOrderResponse placeOrderResponse = super.placeOrderOCC(placeOrderRequest, request);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = AdyenoccConstants.ADYEN_USER_PREFIX + "/additional-details", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "additionalDetails",
            summary = "Handle additional payment details",
            description = "Submits additional payment details for an order, often required for 3D Secure or other redirect flows.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Additional payment details",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PaymentDetailsRequest.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Additional details submission response, typically an order confirmation or status.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = OCCPlaceOrderResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Bad Request - Invalid additional details provided"),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error")
            }
    )
    @ApiBaseSiteIdAndUserIdParam
    public ResponseEntity<String> onAdditionalDetails(@RequestBody PaymentDetailsRequest detailsRequest) throws JsonProcessingException {
        OCCPlaceOrderResponse placeOrderResponse = handleAdditionalDetailsOCC(detailsRequest);

        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }


    @Override
    public String getPaymentRedirectReturnUrl() {
        return paymentRedirectReturnUrlResolver.resolvePaymentRedirectReturnUrl();
    }

    @Override
    public AdyenCheckoutApiFacade getAdyenCheckoutApiFacade() {
        return adyenCheckoutApiFacade;
    }

    @Override
    public CheckoutFlowFacade getCheckoutFlowFacade() {
        return checkoutFlowFacade;
    }

    @Override
    public CartFacade getCartFacade() {
        return cartFacade;
    }

    @Override
    public BaseSiteService getBaseSiteService() {
        return baseSiteService;
    }

    @Override
    public SiteBaseUrlResolutionService getSiteBaseUrlResolutionService() {
        return siteBaseUrlResolutionService;
    }

    @Override
    public AdyenCheckoutFacade getAdyenCheckoutFacade() {
        return adyenCheckoutFacade;
    }

    @Override
    public CheckoutCustomerStrategy getCheckoutCustomerStrategy() {
        return checkoutCustomerStrategy;
    }
}
