package com.adyen.commerce.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercewebservicescommons.errors.exceptions.CartAddressException;
import de.hybris.platform.webservicescommons.cache.CacheControl;
import de.hybris.platform.webservicescommons.cache.CacheControlDirective;
import de.hybris.platform.webservicescommons.errors.exceptions.WebserviceValidationException;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import com.adyen.commerce.validators.AdyenAddressValidator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.annotation.Resource;

import static com.adyen.commerce.constants.AdyenoccConstants.ADYEN_USER_CART_PREFIX;
import static de.hybris.platform.webservicescommons.util.YSanitizer.sanitize;

@Controller
@RequestMapping(value = ADYEN_USER_CART_PREFIX)
@CacheControl(directive = CacheControlDirective.NO_CACHE)
@Tag(name = "Cart Addresses")
public class AdyenCartAddressesController{

    protected static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(AdyenCartAddressesController.class);

    @Resource(name = "userFacade")
    private UserFacade userFacade;

    @Resource(name = "checkoutFacade")
    private CheckoutFacade checkoutFacade;

    @Resource(name = "cartFacade")
    private CartFacade cartFacade;

    @Resource(name = "adyenAddressValidator")
    private AdyenAddressValidator addressValidator;


    @Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_GUEST", "ROLE_TRUSTED_CLIENT" })
    @PostMapping(value = "/addresses/delivery", produces = { MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    @Operation(operationId = "createCartDeliveryAddress", summary = "Creates a delivery address for the cart.", description = "Creates an address and assigns it to the cart as the delivery address.")
    @ApiBaseSiteIdUserIdAndCartIdParam
    public ResponseEntity<String> createCartDeliveryAddress(@Parameter(
            description = "Request body containing customer details (firstName, lastName, titleCode, phone) and address information (country.isocode, line1, line2, town, postalCode, region.isocode) in XML or JSON format.",
            required = true) @RequestBody final String addressRequest) throws JsonProcessingException {
        AddressData addressData = objectMapper.readValue(addressRequest, AddressData.class);
        final Errors errors = new BeanPropertyBindingResult(addressData, "addressData");
        addressValidator.validate(addressData, errors);
        if (errors.hasErrors())
        {
            throw new WebserviceValidationException(errors);
        }
        userFacade.addAddress(addressData);
        setCartDeliveryAddressInternal(addressData.getId());
        return ResponseEntity.ok(objectMapper.writeValueAsString(addressData));
    }

    protected CartData setCartDeliveryAddressInternal(final String addressId)
    {
        final AddressData address = new AddressData();
        address.setId(addressId);

        if (checkoutFacade.setDeliveryAddress(address))
        {
            return cartFacade.getSessionCart();
        }
        throw new CartAddressException(
                "Address given by id " + sanitize(addressId) + " cannot be set as delivery address in this cart",
                CartAddressException.CANNOT_SET, addressId);
    }

}
