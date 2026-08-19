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
package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.enums.RecurringContractMode;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.user.CustomerModel;
import org.junit.Before;
import org.junit.Test;

import static com.adyen.v6.constants.Adyenv6coreConstants.CARD_TYPE_DEBIT;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_BCMC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SCHEME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The JSP / BCMC path. It has no component to send {@code storePaymentMethod}, so the shopper consent
 * can only reach it through {@code CartData.adyenRememberTheseDetails}.
 */
@UnitTest
public class CreditCardPaymentHandlerTest {

    private CreditCardPaymentHandler testObj;

    private CustomerModel registeredCustomer;

    @Before
    public void setUp() {
        testObj = new CreditCardPaymentHandler();
        registeredCustomer = mock(CustomerModel.class);
        when(registeredCustomer.getType()).thenReturn(CustomerType.REGISTERED);
    }

    @Test
    public void handlesCardAndBcmcOnly() {
        assertTrue(testObj.canHandle(PAYMENT_METHOD_CC));
        assertTrue(testObj.canHandle(PAYMENT_METHOD_BCMC));
        assertFalse(testObj.canHandle(PAYMENT_METHOD_SCHEME));
        assertFalse(testObj.canHandle("klarna"));
    }

    @Test
    public void recurringStoresTheCardWithoutTheCheckbox() {
        PaymentRequest request = new PaymentRequest();

        testObj.updatePaymentRequest(request, cardCart(false), RecurringContractMode.RECURRING,
                registeredCustomer, Boolean.FALSE, Boolean.FALSE);

        assertTrue(request.getStorePaymentMethod());
        assertEquals(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE, request.getRecurringProcessingModel());
        assertEquals(PaymentRequest.ShopperInteractionEnum.ECOMMERCE, request.getShopperInteraction());
        assertNoLegacyFields(request);
    }

    @Test
    public void oneClickStoresTheCardOnlyWithTheCheckbox() {
        PaymentRequest withoutConsent = new PaymentRequest();
        testObj.updatePaymentRequest(withoutConsent, cardCart(false), RecurringContractMode.ONECLICK,
                registeredCustomer, Boolean.FALSE, Boolean.FALSE);
        assertFalse(withoutConsent.getStorePaymentMethod());

        PaymentRequest withConsent = new PaymentRequest();
        testObj.updatePaymentRequest(withConsent, cardCart(true), RecurringContractMode.ONECLICK,
                registeredCustomer, Boolean.FALSE, Boolean.FALSE);
        assertTrue(withConsent.getStorePaymentMethod());

        assertNoLegacyFields(withoutConsent);
        assertNoLegacyFields(withConsent);
    }

    @Test
    public void noneNeverStoresTheCard() {
        PaymentRequest request = new PaymentRequest();

        testObj.updatePaymentRequest(request, cardCart(true), RecurringContractMode.NONE,
                registeredCustomer, Boolean.FALSE, Boolean.FALSE);

        assertFalse(request.getStorePaymentMethod());
        assertNoLegacyFields(request);
    }

    @Test
    public void debitCardFollowsTheSameRecurringRulesAsCreditCard() {
        PaymentRequest request = new PaymentRequest();
        CartData cartData = cardCart(false);
        cartData.setAdyenCardType(CARD_TYPE_DEBIT);
        cartData.setAdyenCardBrand("visadankort");

        testObj.updatePaymentRequest(request, cartData, RecurringContractMode.RECURRING,
                registeredCustomer, Boolean.FALSE, Boolean.FALSE);

        assertTrue(request.getStorePaymentMethod());
        assertEquals("true", request.getAdditionalData().get("overwriteBrand"));
        assertNoLegacyFields(request);
    }

    @Test
    public void cardDetailsAreStillBuiltFromTheCart() {
        PaymentRequest request = new PaymentRequest();

        testObj.updatePaymentRequest(request, cardCart(true), RecurringContractMode.ONECLICK,
                registeredCustomer, Boolean.FALSE, Boolean.FALSE);

        CardDetails cardDetails = (CardDetails) request.getPaymentMethod().getActualInstance();
        assertEquals("test_number", cardDetails.getEncryptedCardNumber());
        assertEquals("test_month", cardDetails.getEncryptedExpiryMonth());
        assertEquals("test_year", cardDetails.getEncryptedExpiryYear());
        assertEquals(CardDetails.TypeEnum.CARD, cardDetails.getType());
    }

    @Test
    public void debitCardDetailsCarryTheBrandInsteadOfTheType() {
        PaymentRequest request = new PaymentRequest();
        CartData cartData = cardCart(false);
        cartData.setAdyenCardType(CARD_TYPE_DEBIT);
        cartData.setAdyenCardBrand("visadankort");

        testObj.updatePaymentRequest(request, cartData, RecurringContractMode.NONE,
                registeredCustomer, Boolean.FALSE, Boolean.FALSE);

        CardDetails cardDetails = (CardDetails) request.getPaymentMethod().getActualInstance();
        assertEquals("visadankort", cardDetails.getBrand());
        assertNull(cardDetails.getType());
    }

    @Test
    public void installmentsAreCarriedOver() {
        PaymentRequest request = new PaymentRequest();
        CartData cartData = cardCart(false);
        cartData.setAdyenInstallments(3);

        testObj.updatePaymentRequest(request, cartData, RecurringContractMode.NONE,
                registeredCustomer, Boolean.FALSE, Boolean.FALSE);

        assertEquals(Integer.valueOf(3), request.getInstallments().getValue());
    }

    @Test
    public void guestIsNotTokenizedUnlessTheStoreAllowsIt() {
        CustomerModel guest = mock(CustomerModel.class);
        when(guest.getType()).thenReturn(CustomerType.GUEST);

        PaymentRequest denied = new PaymentRequest();
        testObj.updatePaymentRequest(denied, cardCart(true), RecurringContractMode.RECURRING,
                guest, Boolean.FALSE, Boolean.FALSE);
        assertFalse(denied.getStorePaymentMethod());

        PaymentRequest allowed = new PaymentRequest();
        testObj.updatePaymentRequest(allowed, cardCart(true), RecurringContractMode.RECURRING,
                guest, Boolean.FALSE, Boolean.TRUE);
        assertTrue(allowed.getStorePaymentMethod());
    }

    @Test
    public void threeDS2DataIsAddedWhenAllowed() {
        PaymentRequest request = new PaymentRequest();

        testObj.updatePaymentRequest(request, cardCart(false), RecurringContractMode.NONE,
                registeredCustomer, Boolean.TRUE, Boolean.FALSE);

        assertEquals(PaymentRequest.ChannelEnum.WEB, request.getChannel());
    }

    private void assertNoLegacyFields(PaymentRequest request) {
        assertNull("enableRecurring", request.getEnableRecurring());
        assertNull("enableOneClick", request.getEnableOneClick());
    }

    private CartData cardCart(boolean rememberTheseDetails) {
        CartData cartData = new CartData();
        cartData.setAdyenPaymentMethod(PAYMENT_METHOD_CC);
        cartData.setAdyenEncryptedCardNumber("test_number");
        cartData.setAdyenEncryptedExpiryMonth("test_month");
        cartData.setAdyenEncryptedExpiryYear("test_year");
        cartData.setAdyenEncryptedSecurityCode("test_cvc");
        cartData.setAdyenCardHolder("John Doe");
        cartData.setAdyenRememberTheseDetails(rememberTheseDetails);
        return cartData;
    }
}
