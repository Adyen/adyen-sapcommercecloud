/*
 * Copyright (c) 2019 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.v6.validator;

import de.hybris.platform.acceleratorstorefrontcommons.forms.AddressForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.validation.AddressValidator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * Validator for address forms. Enforces the order of validation
 */
@Component("addressValidator")
public class AdyenAddressValidator extends AddressValidator {
    private static final int MAX_FIELD_LENGTH = 255;
    private static final int MAX_POSTCODE_LENGTH = 10;
    private static final int POSTCODE_BR_LENGTH = 8;
    private static final String REGEX_INDIAN_PHONE = "^(?:(?:\\+|0{0,2})91[-\\s]*)?[789]\\d{9}$|^(\\d[-\\s]?){10}\\d$";
    private static final Pattern VALIDATE_INDIAN_PHONE_FIELD_PATTERN = Pattern.compile(REGEX_INDIAN_PHONE);

    @Override
    public boolean supports(final Class<?> aClass) {
        return AddressForm.class.equals(aClass);
    }

    @Override
    public void validate(final Object object, final Errors errors) {
        final AddressForm addressForm = (AddressForm) object;
        validateStandardFields(addressForm, errors);
        validateCountrySpecificFields(addressForm, errors);
    }

    protected void validateStandardFields(final AddressForm addressForm, final Errors errors) {
        validateStringField(addressForm.getCountryIso(), AddressField.COUNTRY, MAX_FIELD_LENGTH, errors);
        validateStringField(addressForm.getFirstName(), AddressField.FIRSTNAME, MAX_FIELD_LENGTH, errors);
        validateStringField(addressForm.getLastName(), AddressField.LASTNAME, MAX_FIELD_LENGTH, errors);
        validateStringField(addressForm.getLine1(), AddressField.LINE1, MAX_FIELD_LENGTH, errors);
        validateStringField(addressForm.getTownCity(), AddressField.TOWN, MAX_FIELD_LENGTH, errors);
        validateStringField(addressForm.getPostcode(), AddressField.POSTCODE, MAX_POSTCODE_LENGTH, errors);
    }

    protected void validateCountrySpecificFields(final AddressForm addressForm, final Errors errors) {

        final String isoCode = addressForm.getCountryIso();
        if (isoCode != null) {
            switch (CountryCode.lookup(isoCode)) {
                case CHINA, CANADA, USA:
                    validateStringFieldLength(addressForm.getTitleCode(), AddressField.TITLE, MAX_FIELD_LENGTH, errors);
                    validateFieldNotNull(addressForm.getRegionIso(), AddressField.REGION, errors);
                    break;
                case JAPAN:
                    validateFieldNotNull(addressForm.getRegionIso(), AddressField.REGION, errors);
                    validateFieldNotNull(addressForm.getLine2(), AddressField.LINE2, errors);
                    break;
                case BRAZIL:
                    validateFieldNotNull(addressForm.getRegionIso(), AddressField.REGION, errors);
                    validateFieldNotNull(addressForm.getLine2(), AddressField.LINE2, errors);
                    validateFieldNotNull(addressForm.getPostcode(), AddressField.POSTCODE, errors);
                    validateStringFieldLengthPostalCodeBR(addressForm.getPostcode(), AddressField.POSTCODE_BR, POSTCODE_BR_LENGTH, errors);
                    break;
                case INDIA:
                    validateStringFieldLength(addressForm.getTitleCode(), AddressField.TITLE, MAX_FIELD_LENGTH, errors);
                    validateIndianPhoneField(addressForm.getPhone(),AddressField.PHONE_IN,errors);
                    break;
                default:
                    validateStringFieldLength(addressForm.getTitleCode(), AddressField.TITLE, MAX_FIELD_LENGTH, errors);
                    break;
            }
        }
    }

    protected static void validateStringField(final String addressField, final AddressField fieldType,
                                              final int maxFieldLength, final Errors errors) {
        if (addressField == null || StringUtils.isEmpty(addressField) || (StringUtils.length(addressField) > maxFieldLength)) {
            errors.rejectValue(fieldType.getFieldKey(), fieldType.getErrorKey());
        }
    }
    protected static void validateIndianPhoneField(final String addressField, final AddressField fieldType, final Errors errors) {

        if(!VALIDATE_INDIAN_PHONE_FIELD_PATTERN.matcher(addressField).matches()){
            errors.rejectValue(fieldType.getFieldKey(), fieldType.getErrorKey());
        }
    }

    protected static void validateStringFieldLengthPostalCodeBR(final String addressField, final AddressField fieldType,
                                                                final int maxFieldLength, final Errors errors) {
        if (StringUtils.isNotEmpty(addressField) && StringUtils.length(addressField) != maxFieldLength) {
            errors.rejectValue(fieldType.getFieldKey(), fieldType.getErrorKey());
        }

    }

    protected static void validateStringFieldLength(final String field, final AddressField fieldType, final int maxFieldLength,
                                                    final Errors errors) {
        if (StringUtils.isNotEmpty(field) && StringUtils.length(field) > maxFieldLength) {
            errors.rejectValue(fieldType.getFieldKey(), fieldType.getErrorKey());
        }
    }

    protected static void validateFieldNotNull(final String addressField, final AddressField fieldType,
                                               final Errors errors) {
        if (addressField == null) {
            errors.rejectValue(fieldType.getFieldKey(), fieldType.getErrorKey());
        }
    }

    protected enum CountryCode {
        INDIA("IN"),USA("US"), CANADA("CA"), JAPAN("JP"), CHINA("CN"), BRITAIN("GB"), GERMANY("DE"), BRAZIL("BR"), DEFAULT("");

        private String isoCode;

        private static Map<String, CountryCode> lookupMap = new HashMap<>();

        static {
            for (final CountryCode code : CountryCode.values()) {
                lookupMap.put(code.getIsoCode(), code);
            }
        }

        private CountryCode(final String isoCodeStr) {
            this.isoCode = isoCodeStr;
        }

        public static CountryCode lookup(final String isoCodeStr) {
            CountryCode code = lookupMap.get(isoCodeStr);
            if (code == null) {
                code = DEFAULT;
            }
            return code;
        }

        public String getIsoCode() {
            return isoCode;
        }
    }

    protected enum AddressField {
        TITLE("titleCode", "address.title.invalid"), FIRSTNAME("firstName", "address.firstName.invalid"),
        LASTNAME("lastName", "address.lastName.invalid"), LINE1("line1", "address.line1.invalid"),
        LINE2("line2", "address.line2.invalid"), TOWN("townCity", "address.townCity.invalid"),
        POSTCODE("postcode", "address.postcode.invalid"), REGION("regionIso", "address.regionIso.invalid"),
        COUNTRY("countryIso", "address.country.invalid"), LINE2_BR("line2", "address.line2BR.invalid"),
        POSTCODE_BR("postcode", "address.postcodeBR.invalid"),
        PHONE_IN("phone", "address.phoneIN.invalid");

        private String fieldKey;
        private String errorKey;

        private AddressField(final String fieldKey, final String errorKey) {
            this.fieldKey = fieldKey;
            this.errorKey = errorKey;
        }

        public String getFieldKey() {
            return fieldKey;
        }

        public String getErrorKey() {
            return errorKey;
        }
    }
}
