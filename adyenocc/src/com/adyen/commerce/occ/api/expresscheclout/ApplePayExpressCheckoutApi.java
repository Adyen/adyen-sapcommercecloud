package com.adyen.commerce.occ.api.expresscheclout;


import com.adyen.commerce.occ.request.ApplePayExpressRequest;
import com.adyen.commerce.response.OCCPlaceOrderResponse;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "Adyen")
public interface ApplePayExpressCheckoutApi {

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
    ResponseEntity<String> applePayPDPExpressCheckout(final HttpServletRequest request, @RequestBody String applePayExpressPDPStringRequest) throws Exception;

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
    ResponseEntity<String> applePayCartExpressCheckout(final HttpServletRequest request, @RequestBody String applePayExpressCartStringRequest) throws Exception;
}
