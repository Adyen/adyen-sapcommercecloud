package com.adyen.commerce.occ.controllers;

import com.adyen.commerce.occ.validators.AdyenOccAddressValidator;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercewebservicescommons.errors.exceptions.CartAddressException;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

import static com.adyen.commerce.constants.AdyenoccConstants.ADYEN_USER_PREFIX;

@RestController
@RequestMapping(value = ADYEN_USER_PREFIX)
public class AdyenAddressController {

    @Autowired
    private AdyenOccAddressValidator addressValidator;

    @Autowired
    private UserFacade userFacade;

    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_TRUSTED_CLIENT", "ROLE_CUSTOMERMANAGERGROUP"})
    @RequestMapping(value = "/addresses/billing", method = RequestMethod.POST, consumes = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    @ResponseStatus(value = HttpStatus.CREATED)
    @Operation(operationId = "createAddress", summary = "Creates a new billing address.", description = "Creates a new billing address with detailed information provided.")
    @ApiBaseSiteIdAndUserIdParam
    public ResponseEntity<AddressData> createBillingAddress(@Parameter(description = "Address object.", required = true) @RequestBody final AddressData address) {
        final Errors errors = new BeanPropertyBindingResult(address, "address");
        addressValidator.validate(address, errors);

        if (errors.hasErrors()) {
            throw new CartAddressException("Billing address is not valid", CartAddressException.NOT_VALID);
        }

        address.setShippingAddress(false);
        address.setBillingAddress(true);
        address.setVisibleInAddressBook(false);

        userFacade.addAddress(address);

        return ResponseEntity.status(HttpStatus.CREATED).body(address);
    }

}
