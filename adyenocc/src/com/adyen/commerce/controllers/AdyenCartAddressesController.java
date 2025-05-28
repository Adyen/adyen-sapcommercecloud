package com.adyen.commerce.controllers;

import com.adyen.commerce.api.AdyenCartAddressesApi;
import com.adyen.commerce.validators.AdyenOccAddressValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.webservicescommons.cache.CacheControl;
import de.hybris.platform.webservicescommons.cache.CacheControlDirective;
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
public class AdyenCartAddressesController implements AdyenCartAddressesApi {

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
