package com.adyen.commerce.validators;

import de.hybris.platform.webservicescommons.errors.exceptions.WebserviceValidationException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Validator;
import org.springframework.validation.Errors;
import de.hybris.platform.commercefacades.user.data.AddressData;


public class AdyenAddressValidator implements Validator {
    private static final int MAX_COUNTRY_CODE_LENGTH = 2;
    private static final String FIELD_COUNTRY = "country.isocode";

    @Override
    public boolean supports(Class<?> clazz) {
        return AddressData.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        AddressData address = (AddressData) target;

        // Validate country
        if (address == null || address.getCountry() == null ||
                address.getCountry().getIsocode() == null ||
                address.getCountry().getIsocode().length() > MAX_COUNTRY_CODE_LENGTH) {
            errors.rejectValue(FIELD_COUNTRY, "address.country.invalid");
            throw new WebserviceValidationException(errors);
        }

        // Validate required fields
        validateRequiredFields(address, errors);
    }

    private void validateRequiredFields(AddressData address, Errors errors) {
        if (StringUtils.isBlank(address.getFirstName())) {
            errors.rejectValue("firstName", "address.firstName.required");
        }
        if (StringUtils.isBlank(address.getLastName())) {
            errors.rejectValue("lastName", "address.lastName.required");
        }
        if (StringUtils.isBlank(address.getLine1())) {
            errors.rejectValue("line1", "address.line1.required");
        }
        if (StringUtils.isBlank(address.getTown())) {
            errors.rejectValue("town", "address.town.required");
        }
        if (StringUtils.isBlank(address.getPostalCode())) {
            errors.rejectValue("postalCode", "address.postalCode.required");
        }

        if (errors.hasErrors()) {
            throw new WebserviceValidationException(errors);
        }
    }
}