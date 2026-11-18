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
 *  Copyright (c) 2025 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.validators;

import com.adyen.commerce.request.PlaceOrderRequest;
import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;
import de.hybris.bootstrap.annotations.UnitTest;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The "shopper asked to remember the card although the store does not offer it" check reads the same
 * field the component actually sends.
 */
@UnitTest
public class PaymentRequestValidatorTest {

    private static final String REMEMBER_DETAILS_ERROR = "checkout.error.paymentethod.rememberdetails.invalid";

    private static final String CSE_MISSING_ERROR = "checkout.error.paymentmethod.cse.missing";

    @Test
    public void storingIsRejectedWhenTheStoreDoesNotOfferIt() {
        Errors errors = validate(newCardRequest(true), false);

        assertTrue(hasError(errors, REMEMBER_DETAILS_ERROR));
    }

    @Test
    public void storingIsAcceptedWhenTheStoreOffersIt() {
        Errors errors = validate(newCardRequest(true), true);

        assertFalse(hasError(errors, REMEMBER_DETAILS_ERROR));
    }

    @Test
    public void notStoringIsAlwaysAccepted() {
        assertFalse(hasError(validate(newCardRequest(false), false), REMEMBER_DETAILS_ERROR));
        assertFalse(hasError(validate(newCardRequest(null), false), REMEMBER_DETAILS_ERROR));
    }

    @Test
    public void aValidCardWithoutStoringPassesCleanly() {
        Errors errors = validate(newCardRequest(false), true);

        assertEquals(0, errors.getErrorCount());
    }

    @Test
    public void storedCardWithoutCvcIsRejectedWhenCvcIsNotSkipped() {
        Errors errors = validateStored(storedCardRequest("pm-1"), Set.of("pm-1"), false);

        assertTrue(hasError(errors, CSE_MISSING_ERROR));
    }

    @Test
    public void storedCardWithoutCvcIsAcceptedWhenCvcIsSkipped() {
        Errors errors = validateStored(storedCardRequest("pm-1"), Set.of("pm-1"), true);

        assertEquals(0, errors.getErrorCount());
    }

    @Test
    public void unknownStoredCardIsRejectedEvenWhenCvcIsSkipped() {
        Errors errors = validateStored(storedCardRequest("pm-other"), Set.of("pm-1"), true);

        assertTrue(hasError(errors, "checkout.error.paymentmethod.recurringDetailReference.invalid"));
    }

    private Errors validateStored(PlaceOrderRequest placeOrderRequest, Set<String> storedCards, boolean skipCvc) {
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(placeOrderRequest, "placeOrderRequest");
        new PaymentRequestValidator(storedCards, false, false, skipCvc).validate(placeOrderRequest, errors);
        return errors;
    }

    private PlaceOrderRequest storedCardRequest(String storedPaymentMethodId) {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(new CardDetails()
                .storedPaymentMethodId(storedPaymentMethodId)
                .type(CardDetails.TypeEnum.SCHEME)));
        PlaceOrderRequest placeOrderRequest = new PlaceOrderRequest();
        placeOrderRequest.setPaymentRequest(paymentRequest);
        placeOrderRequest.setUseAdyenDeliveryAddress(true);
        return placeOrderRequest;
    }

    private Errors validate(PlaceOrderRequest placeOrderRequest, boolean showRememberTheseDetails) {
        return validate(placeOrderRequest, showRememberTheseDetails, Collections.emptySet());
    }

    private Errors validate(PlaceOrderRequest placeOrderRequest, boolean showRememberTheseDetails,
                            Set<String> storedCards) {
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(placeOrderRequest, "placeOrderRequest");
        new PaymentRequestValidator(storedCards, showRememberTheseDetails, false)
                .validate(placeOrderRequest, errors);
        return errors;
    }

    private boolean hasError(Errors errors, String code) {
        return errors.getAllErrors().stream().anyMatch(error -> code.equals(error.getCode()));
    }

    private PlaceOrderRequest newCardRequest(Boolean storePaymentMethod) {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(new CardDetails()
                .encryptedCardNumber("test_4111111111111111")
                .encryptedExpiryMonth("test_03")
                .encryptedExpiryYear("test_2030")
                .encryptedSecurityCode("test_737")
                .type(CardDetails.TypeEnum.SCHEME)));
        paymentRequest.setStorePaymentMethod(storePaymentMethod);

        PlaceOrderRequest placeOrderRequest = new PlaceOrderRequest();
        placeOrderRequest.setPaymentRequest(paymentRequest);
        placeOrderRequest.setUseAdyenDeliveryAddress(true);
        return placeOrderRequest;
    }
}
