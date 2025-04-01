/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.forms.validation;

import com.adyen.v6.forms.AdyenPaymentForm;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import java.util.Set;

import static com.adyen.v6.constants.Adyenv6coreConstants.RATEPAY;

public class AdyenPaymentFormValidator implements Validator {
    private Set<String> storedCards;
    private boolean showRememberTheseDetails;
    private boolean showSocialSecurityNumber;
    private final boolean cardHolderNameRequired;
    private boolean telephoneNumberRequired;

    public AdyenPaymentFormValidator(Set<String> storedCards, boolean showRememberTheseDetails, boolean showSocialSecurityNumber, boolean cardHolderNameRequired) {
        this.storedCards = storedCards;
        this.showRememberTheseDetails = showRememberTheseDetails;
        this.showSocialSecurityNumber = showSocialSecurityNumber;
        this.cardHolderNameRequired = cardHolderNameRequired;
    }

    @Override
    public boolean supports(final Class<?> aClass) {
        return AdyenPaymentForm.class.equals(aClass);
    }

    public boolean isTelephoneNumberRequired() {
        return telephoneNumberRequired;
    }

    public void setTelephoneNumberRequired(boolean telephoneNumberRequired) {
        this.telephoneNumberRequired = telephoneNumberRequired;
    }

    @Override
    public void validate(Object o, Errors errors) {
        AdyenPaymentForm form = (AdyenPaymentForm) o;

        //Check CSE token in case of CC/OneClick
        if (form.isCC()) {
            if (StringUtils.isEmpty(form.getCseToken()) && (StringUtils.isEmpty(form.getEncryptedCardNumber())
                    || StringUtils.isEmpty(form.getEncryptedExpiryMonth())
                    || StringUtils.isEmpty(form.getEncryptedExpiryYear())
                    || (cardHolderNameRequired && StringUtils.isEmpty(form.getCardHolder())))) {
                errors.reject("checkout.error.paymentmethod.cse.missing");
            }
        }

        if (form.isOneClick()) {
            if (StringUtils.isEmpty(form.getCseToken()) && StringUtils.isEmpty(form.getEncryptedSecurityCode())) {
                errors.reject("checkout.error.paymentmethod.cse.missing");
            }
        }

        //Check selectedReference in case of oneClick
        if (form.getSelectedReference() != null && ! form.getSelectedReference().isEmpty()) {
            if (storedCards == null || ! storedCards.contains(form.getSelectedReference())) {
                errors.reject("checkout.error.paymentmethod.recurringDetailReference.invalid");
            }
        }

        //Check remember these details
        if (form.isCC() && form.getRememberTheseDetails()) {
            if (! showRememberTheseDetails) {
                errors.reject("checkout.error.paymentethod.rememberdetails.invalid");
            }
        }

        // check if date or social seucrity number is set
        if (RATEPAY.equals(form.getPaymentMethod())) {

            if (showSocialSecurityNumber) {
                if (form.getSocialSecurityNumber().isEmpty()) {
                    errors.reject("checkout.error.paymentethod.ssn.invalid");
                }
            }
            if (form.getDob() == null) {
                errors.reject("checkout.error.paymentethod.dob.invalid");
            }
        }

        if (telephoneNumberRequired && StringUtils.isEmpty(form.getTelephoneNumber())) {
            errors.reject("checkout.error.paymentmethod.telephonenumber.invalid");
        }

        if (!form.getUseAdyenDeliveryAddress() && form.getBillingAddress()!=null) {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.titleCode", "address.title.invalid");
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.firstName", "address.firstName.invalid");
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.lastName", "address.lastName.invalid");
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.line1", "address.line1.invalid");
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.townCity", "address.townCity.invalid");
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.postcode", "address.postcode.invalid");
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.countryIso", "address.country.invalid");
        }
    }
}
