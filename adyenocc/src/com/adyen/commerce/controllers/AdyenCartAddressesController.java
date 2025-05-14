package com.adyen.commerce.controllers;

import com.adyen.commerce.validators.AdyenOccAddressValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.webservicescommons.cache.CacheControl;
import de.hybris.platform.webservicescommons.cache.CacheControlDirective;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.annotation.Resource;

import static com.adyen.commerce.constants.AdyenoccConstants.ADYEN_USER_CART_PREFIX;

@Controller
@RequestMapping(value = ADYEN_USER_CART_PREFIX)
@CacheControl(directive = CacheControlDirective.NO_CACHE)
@Tag(name = "Cart Addresses")
public class AdyenCartAddressesController {

    protected static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(AdyenCartAddressesController.class);

    @Resource(name = "userFacade")
    private UserFacade userFacade;

    @Resource(name = "checkoutFacade")
    private CheckoutFacade checkoutFacade;

    @Resource(name = "cartFacade")
    private CartFacade cartFacade;

    @Resource(name = "adyenOccAddressValidator")
    private AdyenOccAddressValidator addressValidator;


    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_GUEST", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = "/addresses/delivery", produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    @Operation(
            operationId = "createCartDeliveryAddress",
            summary = "Creates a delivery address for the cart.",
            description = "Creates a new address, validates it " +
                    "and then assigns it to the cart as the delivery address. " +
                    "The address should include customer details (firstName, lastName, titleCode, phone) " +
                    "and address information (country.isocode, line1, line2, town, postalCode, region.isocode).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody( // Defines the request body for Swagger
                    description = "Address object that needs to be created and set as the delivery address.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AddressData.class)
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "201", // Explicitly matching @ResponseStatus
                            description = "Delivery address created and set successfully. The created address data is returned.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = AddressData.class) // Response body is serialized AddressData
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad Request. This can occur if: <br>" +
                                    "<ul>" +
                                    "<li>The provided address data is invalid (e.g., missing required fields, fails validation).</li>" +
                                    "<li>The system fails to set the delivery address to the cart (e.g., cart not found, or internal error during address assignment).</li>" +
                                    "</ul>",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE) // Assuming error responses might also be JSON (e.g., an empty JSON object or an error structure)
                    ),
                    @ApiResponse(responseCode = "401", description = "Unauthorized. Authentication required."),
                    @ApiResponse(responseCode = "403", description = "Forbidden. Insufficient permissions.")
                    // Add other relevant error codes like 500 if applicable
            }
    )
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> createCartDeliveryAddress(@Parameter(
            description = "Request body containing customer details (firstName, lastName, titleCode, phone) and address information (country.isocode, line1, line2, town, postalCode, region.isocode) in XML or JSON format.",
            required = true) @RequestBody final AddressData addressData) throws JsonProcessingException {
        final Errors errors = new BeanPropertyBindingResult(addressData, "addressData");
        addressValidator.validate(addressData, errors);
        userFacade.addAddress(addressData);
        if (checkoutFacade.setDeliveryAddress(addressData))
            return ResponseEntity.ok(objectMapper.writeValueAsString(addressData));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

}
