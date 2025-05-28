package com.adyen.commerce.api;
import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.commerce.request.PlaceOrderRequest;
import com.adyen.commerce.response.OCCPlaceOrderResponse;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;

import javax.servlet.http.HttpServletRequest;

@Tag(name = "Adyen")
public interface AdyenPlaceOrderApi {

    @Operation(
            operationId = "placeOrder",
            summary = "Handle place order request",
            description = "Places order based on request data",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Place order request details",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PlaceOrderRequest.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Order placement response",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = OCCPlaceOrderResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized. Authentication required."),
                    @ApiResponse(responseCode = "403", description = "Forbidden. Insufficient permissions."),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error")
            }
    )
    @ApiBaseSiteIdUserIdAndCartIdParam
    ResponseEntity<String> onPlaceOrder(@org.springframework.web.bind.annotation.RequestBody String placeOrderStringRequest, HttpServletRequest request) throws Exception;

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
                    @ApiResponse(responseCode = "401", description = "Unauthorized. Authentication required."),
                    @ApiResponse(responseCode = "403", description = "Forbidden. Insufficient permissions."),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error")
            }
    )
    @ApiBaseSiteIdAndUserIdParam
    ResponseEntity<String> onAdditionalDetails(@org.springframework.web.bind.annotation.RequestBody PaymentDetailsRequest detailsRequest) throws JsonProcessingException;
}
