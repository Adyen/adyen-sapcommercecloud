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

import de.hybris.bootstrap.annotations.UnitTest;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_BCMC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_KLARNA;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_ONECLICK;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SCHEME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins down which handler owns which payment method - the tokenization rules differ per handler.
 */
@UnitTest
public class PaymentMethodHandlerFactoryTest {

    private PaymentMethodHandlerFactory testObj;

    @Before
    public void setUp() {
        testObj = new PaymentMethodHandlerFactory();
    }

    @Test
    public void cardGoesToTheCreditCardHandler() {
        assertHandler(PAYMENT_METHOD_CC, CreditCardPaymentHandler.class);
    }

    @Test
    public void bcmcGoesToTheCreditCardHandler() {
        assertHandler(PAYMENT_METHOD_BCMC, CreditCardPaymentHandler.class);
    }

    @Test
    public void schemeGoesToTheSchemeHandler() {
        assertHandler(PAYMENT_METHOD_SCHEME, SchemePaymentHandler.class);
    }

    @Test
    public void storedCardGoesToTheOneClickHandler() {
        assertHandler(PAYMENT_METHOD_ONECLICK + "8415995487014051", OneClickPaymentHandler.class);
    }

    @Test
    public void klarnaGoesToTheAlternativeHandler() {
        assertHandler(PAYMENT_METHOD_KLARNA, AlternativePaymentHandler.class);
    }

    @Test
    public void anUnknownMethodHasNoHandler() {
        assertFalse(testObj.getHandler("no_such_payment_method").isPresent());
    }

    private void assertHandler(String paymentMethod, Class<? extends PaymentMethodHandler> expected) {
        Optional<PaymentMethodHandler> handler = testObj.getHandler(paymentMethod);
        assertTrue(paymentMethod, handler.isPresent());
        assertEquals(paymentMethod, expected, handler.get().getClass());
    }
}
