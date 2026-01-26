package com.adyen.commerce.occ.api;

import com.adyen.commerce.request.GiftCardBalanceRequest;
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

@Tag(name = "Adyen Gift Card")
public interface AdyenGiftCardApi {

    @Operation(
            operationId = "checkGiftCardBalance",
            summary = "Check gift card balance for partial payments",
            description = "Checks the available balance on a gift card before processing. This endpoint is called to verify the available balance on a gift card before processing as part of a partial payment flow.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The gift card balance request containing card details and amount",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GiftCardBalanceRequest.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Gift card balance check successful. Response containing available balance and transaction limit.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad Request - Invalid gift card request data",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
                    ),
                    @ApiResponse(responseCode = "401", description = "Unauthorized. Authentication required."),
                    @ApiResponse(responseCode = "403", description = "Forbidden. Insufficient permissions."),
                    @ApiResponse(
                            responseCode = "500", 
                            description = "Internal Server Error - Unexpected error during gift card balance check",
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
    ResponseEntity<String> checkGiftCardBalance(
            @Parameter(
                    description = "The gift card balance request containing card details and amount",
                    required = true
            ) @org.springframework.web.bind.annotation.RequestBody final GiftCardBalanceRequest request
    );
}