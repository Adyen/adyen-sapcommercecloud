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
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the whole {@link RecurringContractMode} x payment path x shopper consent matrix and asserts
 * the fields of the outgoing {@link PaymentRequest}.
 */
@UnitTest
public class RecurringContractHelperTest {

    private static final String STORED_PAYMENT_METHOD_ID = "8415995487014051";

    private static final List<RecurringContractMode> ALL_MODES = Arrays.asList(
            RecurringContractMode.NONE,
            RecurringContractMode.ONECLICK,
            RecurringContractMode.ONECLICK_RECURRING,
            RecurringContractMode.RECURRING);

    // ---------------------------------------------------------------- NONE

    @Test
    public void noneNeverStores() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, consentingCart(), RecurringContractMode.NONE,
                registeredCustomer(), Boolean.TRUE);

        assertNotStored(request);
    }

    @Test
    public void noneOverridesConsentSentByTheComponent() {
        PaymentRequest request = cardRequest();
        request.setStorePaymentMethod(true);

        RecurringContractHelper.applyRecurringContract(request, new CartData(), RecurringContractMode.NONE,
                registeredCustomer(), Boolean.TRUE);

        assertNotStored(request);
    }

    @Test
    public void nullModeNeverStores() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, consentingCart(), null,
                registeredCustomer(), Boolean.TRUE);

        assertNotStored(request);
    }

    // ------------------------------------------------------------ ONECLICK

    @Test
    public void oneClickStoresOnlyWithShopperConsent() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, new CartData(), RecurringContractMode.ONECLICK,
                registeredCustomer(), Boolean.FALSE);

        assertNotStored(request);
    }

    @Test
    public void oneClickStoresWhenTheCartCarriesTheConsent() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, consentingCart(), RecurringContractMode.ONECLICK,
                registeredCustomer(), Boolean.FALSE);

        assertStored(request);
    }

    @Test
    public void oneClickStoresWhenTheComponentCarriesTheConsent() {
        PaymentRequest request = cardRequest();
        request.setStorePaymentMethod(true);

        RecurringContractHelper.applyRecurringContract(request, new CartData(), RecurringContractMode.ONECLICK,
                registeredCustomer(), Boolean.FALSE);

        assertStored(request);
    }

    // ----------------------------------------------------------- RECURRING

    @Test
    public void recurringStoresWithoutAnyShopperConsent() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, new CartData(), RecurringContractMode.RECURRING,
                registeredCustomer(), Boolean.FALSE);

        assertStored(request);
    }

    @Test
    public void recurringStoresWithShopperConsent() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, consentingCart(), RecurringContractMode.RECURRING,
                registeredCustomer(), Boolean.FALSE);

        assertStored(request);
    }

    // ------------------------------------------------- ONECLICK_RECURRING

    @Test
    public void oneClickRecurringStoresWithoutAnyShopperConsent() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, new CartData(),
                RecurringContractMode.ONECLICK_RECURRING, registeredCustomer(), Boolean.FALSE);

        assertStored(request);
    }

    // --------------------------------------------------------------- guest

    @Test
    public void guestDoesNotStoreWhenGuestTokenizationIsOff() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, consentingCart(), RecurringContractMode.RECURRING,
                guestCustomer(), Boolean.FALSE);

        assertNotStored(request);
    }

    @Test
    public void guestDoesNotStoreWhenGuestTokenizationIsUnset() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, consentingCart(), RecurringContractMode.RECURRING,
                guestCustomer(), null);

        assertNotStored(request);
    }

    @Test
    public void guestStoresWhenGuestTokenizationIsOn() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, new CartData(), RecurringContractMode.RECURRING,
                guestCustomer(), Boolean.TRUE);

        assertStored(request);
    }

    @Test
    public void guestStillNeedsConsentInOneClickMode() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, new CartData(), RecurringContractMode.ONECLICK,
                guestCustomer(), Boolean.TRUE);

        assertNotStored(request);
    }

    @Test
    public void registeredCustomerIsNotAffectedByTheGuestFlag() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, new CartData(), RecurringContractMode.RECURRING,
                registeredCustomer(), Boolean.FALSE);

        assertStored(request);
    }

    // ------------------------------------------------ stored card (re-use)

    @Test
    public void storedCardIsNeverStoredAgain() {
        for (RecurringContractMode mode : ALL_MODES) {
            PaymentRequest request = storedCardRequest();

            RecurringContractHelper.applyRecurringContract(request, consentingCart(), mode,
                    registeredCustomer(), Boolean.TRUE);

            assertFalse("mode " + mode, request.getStorePaymentMethod());
            assertNoLegacyFields(request);
        }
    }

    /**
     * Paying with an existing token is a card-on-file transaction in its own right. Dropping the contract
     * along with the deprecated flags would have sent saved-card payments out as one-off e-commerce ones.
     */
    @Test
    public void storedCardStillDeclaresTheCardOnFileContract() {
        for (RecurringContractMode mode : ALL_MODES) {
            PaymentRequest request = storedCardRequest();

            RecurringContractHelper.applyRecurringContract(request, consentingCart(), mode,
                    registeredCustomer(), Boolean.TRUE);

            assertEquals("mode " + mode, PaymentRequest.RecurringProcessingModelEnum.CARDONFILE,
                    request.getRecurringProcessingModel());
            assertEquals("mode " + mode, PaymentRequest.ShopperInteractionEnum.ECOMMERCE,
                    request.getShopperInteraction());
        }
    }

    @Test
    public void storedCardDoesNotOverrideAContractSetUpstream() {
        PaymentRequest request = storedCardRequest();
        request.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);

        RecurringContractHelper.applyRecurringContract(request, consentingCart(), RecurringContractMode.RECURRING,
                registeredCustomer(), Boolean.TRUE);

        assertEquals(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION, request.getRecurringProcessingModel());
    }

    @Test
    public void storedCardIsRecognisedByTheLegacyRecurringDetailReference() {
        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod(new CheckoutPaymentMethod(
                new CardDetails().recurringDetailReference(STORED_PAYMENT_METHOD_ID)));

        RecurringContractHelper.applyRecurringContract(request, consentingCart(), RecurringContractMode.RECURRING,
                registeredCustomer(), Boolean.TRUE);

        assertFalse(request.getStorePaymentMethod());
    }

    // ------------------------------------------------------- field hygiene

    @Test
    public void existingRecurringProcessingModelIsNotOverwritten() {
        PaymentRequest request = cardRequest();
        request.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);

        RecurringContractHelper.applyRecurringContract(request, new CartData(), RecurringContractMode.RECURRING,
                registeredCustomer(), Boolean.FALSE);

        assertTrue(request.getStorePaymentMethod());
        assertEquals(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION, request.getRecurringProcessingModel());
    }

    @Test
    public void existingShopperInteractionIsNotOverwritten() {
        PaymentRequest request = cardRequest();
        request.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.CONTAUTH);

        RecurringContractHelper.applyRecurringContract(request, new CartData(), RecurringContractMode.RECURRING,
                registeredCustomer(), Boolean.FALSE);

        assertEquals(PaymentRequest.ShopperInteractionEnum.CONTAUTH, request.getShopperInteraction());
    }

    @Test
    public void applyingTwiceYieldsTheSameResult() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, new CartData(), RecurringContractMode.ONECLICK,
                registeredCustomer(), Boolean.FALSE);
        RecurringContractHelper.applyRecurringContract(request, new CartData(), RecurringContractMode.ONECLICK,
                registeredCustomer(), Boolean.FALSE);

        assertNotStored(request);
    }

    /**
     * The Checkout API refuses a request that carries storePaymentMethod next to enableRecurring or
     * enableOneClick, so no combination of inputs may ever produce one.
     */
    @Test
    public void noCombinationEverSetsTheDeprecatedFields() {
        for (RecurringContractMode mode : ALL_MODES) {
            for (boolean consent : new boolean[]{true, false}) {
                for (boolean guest : new boolean[]{true, false}) {
                    for (Boolean guestTokenization : new Boolean[]{Boolean.TRUE, Boolean.FALSE, null}) {
                        PaymentRequest request = cardRequest();
                        CartData cartData = new CartData();
                        cartData.setAdyenRememberTheseDetails(consent);

                        RecurringContractHelper.applyRecurringContract(request, cartData, mode,
                                guest ? guestCustomer() : registeredCustomer(), guestTokenization);

                        assertNoLegacyFields(request);
                    }
                }
            }
        }
    }

    @Test
    public void nullPaymentRequestIsIgnored() {
        RecurringContractHelper.applyRecurringContract(null, consentingCart(), RecurringContractMode.RECURRING,
                registeredCustomer(), Boolean.TRUE);
    }

    @Test
    public void nullCustomerIsTreatedAsRegistered() {
        PaymentRequest request = cardRequest();

        RecurringContractHelper.applyRecurringContract(request, new CartData(), RecurringContractMode.RECURRING,
                null, Boolean.FALSE);

        assertStored(request);
    }

    // -------------------------------------------------------------- helpers

    private void assertStored(PaymentRequest request) {
        assertTrue("storePaymentMethod", request.getStorePaymentMethod());
        assertEquals(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE, request.getRecurringProcessingModel());
        assertEquals(PaymentRequest.ShopperInteractionEnum.ECOMMERCE, request.getShopperInteraction());
        assertNoLegacyFields(request);
    }

    private void assertNotStored(PaymentRequest request) {
        assertFalse("storePaymentMethod", request.getStorePaymentMethod());
        assertNull("recurringProcessingModel", request.getRecurringProcessingModel());
        assertNull("shopperInteraction", request.getShopperInteraction());
        assertNoLegacyFields(request);
    }

    private void assertNoLegacyFields(PaymentRequest request) {
        assertNull("enableRecurring", request.getEnableRecurring());
        assertNull("enableOneClick", request.getEnableOneClick());
    }

    private PaymentRequest cardRequest() {
        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod(new CheckoutPaymentMethod(
                new CardDetails().encryptedCardNumber("test_4111111111111111").type(CardDetails.TypeEnum.SCHEME)));
        return request;
    }

    private PaymentRequest storedCardRequest() {
        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod(new CheckoutPaymentMethod(
                new CardDetails().storedPaymentMethodId(STORED_PAYMENT_METHOD_ID)));
        return request;
    }

    private CartData consentingCart() {
        CartData cartData = new CartData();
        cartData.setAdyenRememberTheseDetails(true);
        return cartData;
    }

    private CustomerModel registeredCustomer() {
        CustomerModel customerModel = mock(CustomerModel.class);
        when(customerModel.getType()).thenReturn(CustomerType.REGISTERED);
        return customerModel;
    }

    private CustomerModel guestCustomer() {
        CustomerModel customerModel = mock(CustomerModel.class);
        when(customerModel.getType()).thenReturn(CustomerType.GUEST);
        return customerModel;
    }
}
