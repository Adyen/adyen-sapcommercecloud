package com.adyen.commerce.api;

import com.adyen.service.exception.ApiException;
import com.adyen.v6.dto.CheckoutConfigDTO;
import com.adyen.v6.dto.ExpressCheckoutConfigDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Adyen")
public interface AdyenPaymentMethodsApi {

    @Operation(
            operationId = "getCheckoutConfiguration",
            summary = "Get checkout configuration",
            description = "Returns configuration for Adyen dropin component",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Adyen checkout configuration details",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = CheckoutConfigDTO.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error")
            }
    )
    @ApiBaseSiteIdUserIdAndCartIdParam
    ResponseEntity<String> getCheckoutConfiguration() throws ApiException, JsonProcessingException;

    @Operation(
            operationId = "getExpressPDPCheckoutConfiguration",
            summary = "Get express product page checkout configuration",
            description = "Returns configuration for express payments on PDP",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Adyen express checkout configuration for PDP",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ExpressCheckoutConfigDTO.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error")
            }
    )
    @ApiBaseSiteIdAndUserIdParam
    ResponseEntity<String> getExpressPDPCheckoutConfiguration(String productCode) throws ApiException, JsonProcessingException;

    @Operation(
            operationId = "getExpressCartCheckoutConfiguration",
            summary = "Get express cart page checkout configuration",
            description = "Returns configuration for express payments on cart",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Adyen express checkout configuration for cart",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ExpressCheckoutConfigDTO.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error")
            }
    )
    @ApiBaseSiteIdUserIdAndCartIdParam
    ResponseEntity<String> getExpressCartCheckoutConfiguration() throws ApiException, JsonProcessingException, CalculationException;
}