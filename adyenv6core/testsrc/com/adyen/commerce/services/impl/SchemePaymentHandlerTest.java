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
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.enums.RecurringContractMode;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.user.CustomerModel;
import org.junit.Before;
import org.junit.Test;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SCHEME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The component path. Here {@code storePaymentMethod} arrives on the request the browser sent and is
 * weighed against the store configuration.
 */
@UnitTest
public class SchemePaymentHandlerTest {

    private SchemePaymentHandler testObj;

    private CustomerModel registeredCustomer;

    @Before
    public void setUp() {
        testObj = new SchemePaymentHandler();
        registeredCustomer = mock(CustomerModel.class);
        when(registeredCustomer.getType()).thenReturn(CustomerType.REGISTERED);
    }

    @Test
    public void handlesSchemeOnly() {
        assertTrue(testObj.canHandle(PAYMENT_METHOD_SCHEME));
        assertFalse(testObj.canHandle(PAYMENT_METHOD_CC));
        assertFalse(testObj.canHandle(null));
    }

    @Test
    public void recurringStoresEvenWhenTheComponentDidNotAskForIt() {
        PaymentRequest request = schemeRequest();

        testObj.updatePaymentRequest(request, new CartData(), RecurringContractMode.RECURRING,
                registeredCustomer, Boolean.FALSE, Boolean.FALSE);

        assertTrue(request.getStorePaymentMethod());
        assertEquals(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE, request.getRecurringProcessingModel());
        assertEquals(PaymentRequest.ShopperInteractionEnum.ECOMMERCE, request.getShopperInteraction());
        assertNoLegacyFields(request);
    }

    @Test
    public void oneClickKeepsTheConsentSentByTheComponent() {
        PaymentRequest request = schemeRequest();
        request.setStorePaymentMethod(true);

        testObj.updatePaymentRequest(request, new CartData(), RecurringContractMode.ONECLICK,
                registeredCustomer, Boolean.FALSE, Boolean.FALSE);

        assertTrue(request.getStorePaymentMethod());
        assertNoLegacyFields(request);
    }

    @Test
    public void oneClickDropsTheStoreWhenTheComponentDidNotAskForIt() {
        PaymentRequest request = schemeRequest();
        request.setStorePaymentMethod(false);

        testObj.updatePaymentRequest(request, new CartData(), RecurringContractMode.ONECLICK,
                registeredCustomer, Boolean.FALSE, Boolean.FALSE);

        assertFalse(request.getStorePaymentMethod());
        assertNull(request.getRecurringProcessingModel());
        assertNoLegacyFields(request);
    }

    @Test
    public void noneOverridesTheConsentSentByTheComponent() {
        PaymentRequest request = schemeRequest();
        request.setStorePaymentMethod(true);

        testObj.updatePaymentRequest(request, new CartData(), RecurringContractMode.NONE,
                registeredCustomer, Boolean.FALSE, Boolean.FALSE);

        assertFalse(request.getStorePaymentMethod());
        assertNoLegacyFields(request);
    }

    @Test
    public void payingWithAStoredCardDoesNotStoreItAgain() {
        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod(new CheckoutPaymentMethod(
                new CardDetails().storedPaymentMethodId("8415995487014051").encryptedSecurityCode("test_cvc")));

        testObj.updatePaymentRequest(request, new CartData(), RecurringContractMode.RECURRING,
                registeredCustomer, Boolean.FALSE, Boolean.FALSE);

        assertFalse(request.getStorePaymentMethod());
        assertNoLegacyFields(request);
    }

    @Test
    public void guestIsNotTokenizedUnlessTheStoreAllowsIt() {
        CustomerModel guest = mock(CustomerModel.class);
        when(guest.getType()).thenReturn(CustomerType.GUEST);

        PaymentRequest denied = schemeRequest();
        denied.setStorePaymentMethod(true);
        testObj.updatePaymentRequest(denied, new CartData(), RecurringContractMode.RECURRING,
                guest, Boolean.FALSE, Boolean.FALSE);
        assertFalse(denied.getStorePaymentMethod());

        PaymentRequest allowed = schemeRequest();
        allowed.setStorePaymentMethod(true);
        testObj.updatePaymentRequest(allowed, new CartData(), RecurringContractMode.RECURRING,
                guest, Boolean.FALSE, Boolean.TRUE);
        assertTrue(allowed.getStorePaymentMethod());
    }

    @Test
    public void threeDS2DataIsAddedWhenAllowed() {
        PaymentRequest request = schemeRequest();

        testObj.updatePaymentRequest(request, new CartData(), RecurringContractMode.NONE,
                registeredCustomer, Boolean.TRUE, Boolean.FALSE);

        assertEquals(PaymentRequest.ChannelEnum.WEB, request.getChannel());
    }

    private void assertNoLegacyFields(PaymentRequest request) {
        assertNull("enableRecurring", request.getEnableRecurring());
        assertNull("enableOneClick", request.getEnableOneClick());
    }

    private PaymentRequest schemeRequest() {
        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod(new CheckoutPaymentMethod(
                new CardDetails().encryptedCardNumber("test_4111111111111111").type(CardDetails.TypeEnum.SCHEME)));
        return request;
    }
}
