package com.adyen.commerce.validators;

import com.adyen.commerce.request.PlaceOrderRequest;
import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.PaymentRequest;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import java.util.Set;

public class PaymentRequestValidator implements Validator {


    private final boolean cardHolderNameRequired;

    private final boolean showRememberTheseDetails;

    private final Set<String> storedCards;

    public PaymentRequestValidator(Set<String> storedCards, boolean showRememberTheseDetails, boolean cardHolderNameRequired) {
        this.cardHolderNameRequired = cardHolderNameRequired;
        this.showRememberTheseDetails = showRememberTheseDetails;
        this.storedCards = storedCards;
    }

    @Override
    public boolean supports(final Class<?> aClass) {
        return PlaceOrderRequest.class.equals(aClass);
    }


    @Override
    public void validate(Object o, Errors errors) {
        PlaceOrderRequest placeOrderRequest = (PlaceOrderRequest) o;

        PaymentRequest paymentRequest = placeOrderRequest.getPaymentRequest();
        if (paymentRequest == null || paymentRequest.getPaymentMethod() == null) {
            errors.reject("checkout.error.paymentmethod.invalid");
        }

        if (paymentRequest.getPaymentMethod().getActualInstance() instanceof CardDetails cardDetails) {
            //Check selectedReference in case of oneClick
            if (CardDetails.TypeEnum.GIFTCARD.equals(cardDetails.getType())) {
                if (StringUtils.isEmpty(cardDetails.getEncryptedSecurityCode()) || StringUtils.isEmpty(cardDetails.getEncryptedCardNumber())) {
                    errors.reject("checkout.error.paymentmethod.cse.missing");
                }
            } else if (StringUtils.isNotBlank(cardDetails.getStoredPaymentMethodId())) {
                if (storedCards == null || !storedCards.contains(cardDetails.getStoredPaymentMethodId())) {
                    errors.reject("checkout.error.paymentmethod.recurringDetailReference.invalid");
                }
                if (StringUtils.isEmpty(cardDetails.getEncryptedSecurityCode())) {
                    errors.reject("checkout.error.paymentmethod.cse.missing");
                }
            } else if(StringUtils.isNotEmpty(cardDetails.getSrcScheme())){
                if (StringUtils.isEmpty(cardDetails.getSrcCorrelationId()) || StringUtils.isEmpty(cardDetails.getSrcDigitalCardId())) {
                    errors.reject("checkout.error.paymentmethod.cse.missing");
                }

            } else if(StringUtils.isNotEmpty(cardDetails.getEncryptedCardNumber())) {
                if (StringUtils.isEmpty(cardDetails.getEncryptedExpiryMonth())
                        || StringUtils.isEmpty(cardDetails.getEncryptedExpiryYear())
                        || (cardHolderNameRequired && StringUtils.isEmpty(cardDetails.getHolderName()))) {
                    errors.reject("checkout.error.paymentmethod.cse.missing");
                }
            }

            //Check remember these details
            if (BooleanUtils.isTrue(paymentRequest.getEnableOneClick())) {
                if (!showRememberTheseDetails) {
                    errors.reject("checkout.error.paymentethod.rememberdetails.invalid");
                }
            }

        }

        if (!placeOrderRequest.isUseAdyenDeliveryAddress() && placeOrderRequest.getBillingAddress() != null) {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.firstName", "address.firstName.invalid");
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.lastName", "address.lastName.invalid");
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.line1", "address.line1.invalid");
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.townCity", "address.townCity.invalid");
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.postcode", "address.postcode.invalid");
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.countryIso", "address.country.invalid");

            validateCountrySpecificFields(errors);
        }

    }

    protected void validateCountrySpecificFields(Errors errors) {
        String countryIso = (String) errors.getFieldValue("billingAddress.countryIso");
        if (countryIso != null) {
            switch (countryIso) {
                case "US", "CA", "JP", "CN":
                    ValidationUtils.rejectIfEmptyOrWhitespace(errors, "billingAddress.regionIso", "address.regionIso.invalid");
                    break;
                default:
                    break;
            }
        }
    }
}
