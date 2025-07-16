package com.adyen.commerce.occ.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Tag(name = "Cart Addresses")
public interface AdyenCartAddressesApi {


    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    @Operation(
            operationId = "createCartDeliveryAddress",
            summary = "Creates a delivery address for the cart.",
            description = "Creates a new address, validates it " +
                    "and then assigns it to the cart as the delivery address. " +
                    "The address should include customer details (firstName, lastName, titleCode, phone) " +
                    "and address information (country.isocode, line1, line2, town, postalCode, region.isocode).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Address object that needs to be created and set as the delivery address.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AddressData.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Delivery address created and set successfully. The created address data is returned.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = AddressData.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad Request.",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
                    ),
                    @ApiResponse(responseCode = "401", description = "Unauthorized. Authentication required."),
                    @ApiResponse(responseCode = "403", description = "Forbidden. Insufficient permissions."),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error")

            }
    )
    @ApiBaseSiteIdUserIdAndCartIdParam
    ResponseEntity<String> createCartDeliveryAddress(@Parameter(
            description = "Request body containing customer details (firstName, lastName, titleCode, phone) and address information (country.isocode, line1, line2, town, postalCode, region.isocode) in XML or JSON format.",
            required = true) @org.springframework.web.bind.annotation.RequestBody final AddressData addressData) throws JsonProcessingException;
}