package com.adyen.commerce.occ.api;

import com.adyen.commerce.request.PartialPaymentOrderRequest;
import com.adyen.commerce.response.PartialPaymentOrderResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Adyen Partial Payment")
public interface AdyenPartialPaymentOrderApi {

    @Operation(
            operationId = "createPartialPaymentOrder",
            summary = "Create a partial payment order for gift cards",
            description = "Creates a partial payment order for gift cards. This endpoint is called when a gift card needs to be processed as part of a partial payment flow.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The partial payment order request containing amount and payment method details",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PartialPaymentOrderRequest.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Partial payment order created successfully. Response containing order data and PSP reference.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = PartialPaymentOrderResponse.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad Request - Invalid request data (missing amount, invalid amount value, missing currency, etc.)",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
                    ),
                    @ApiResponse(responseCode = "401", description = "Unauthorized. Authentication required."),
                    @ApiResponse(responseCode = "403", description = "Forbidden. Insufficient permissions."),
                    @ApiResponse(
                            responseCode = "500", 
                            description = "Internal Server Error - Unexpected error during partial payment order creation",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
                    ),
                    @ApiResponse(
                            responseCode = "503",
                            description = "Service Unavailable - Communication error with payment provider",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
                    )
            }
    )
    @ApiBaseSiteIdUserIdAndCartIdParam
    ResponseEntity<String> createPartialPaymentOrder(
            @Parameter(
                    description = "The partial payment order request containing amount and payment method details",
                    required = true
            ) @RequestBody final PartialPaymentOrderRequest request
    ) throws JsonProcessingException;
}