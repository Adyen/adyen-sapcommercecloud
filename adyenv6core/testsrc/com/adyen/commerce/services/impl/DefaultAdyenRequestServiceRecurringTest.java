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

import com.adyen.model.checkout.ApplicationInfo;
import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.enums.RecurringContractMode;
import com.adyen.v6.model.RequestInfo;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SCHEME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Both the full and the partial payment entry points funnel into the same payment method logic, so the
 * gift card + card flow must end up with the same tokenization fields as a plain card checkout.
 */
@UnitTest
public class DefaultAdyenRequestServiceRecurringTest {

    private static final String MERCHANT_ACCOUNT = "TestMerchant";
    private static final String CURRENCY = "EUR";

    private DefaultAdyenRequestService testObj;

    private CustomerModel registeredCustomer;

    @Before
    public void setUp() {
        BaseStoreService baseStoreService = mock(BaseStoreService.class);
        CartService cartService = mock(CartService.class);

        Configuration configuration = mock(Configuration.class);
        when(configuration.containsKey("is3DS2allowed")).thenReturn(false);
        ConfigurationService configurationService = mock(ConfigurationService.class);
        when(configurationService.getConfiguration()).thenReturn(configuration);

        ApplicationInfoService applicationInfoService = mock(ApplicationInfoService.class);
        when(applicationInfoService.createApplicationInfo(any(RequestInfo.class))).thenReturn(new ApplicationInfo());

        testObj = new DefaultAdyenRequestService(baseStoreService, cartService, configurationService,
                new PaymentMethodHandlerFactory(), applicationInfoService, Collections.emptyList());

        registeredCustomer = mock(CustomerModel.class);
        when(registeredCustomer.getType()).thenReturn(CustomerType.REGISTERED);
        when(registeredCustomer.getCustomerID()).thenReturn("test-customer");
    }

    @Test
    public void schemeSettingsCarryOnlyTheStoreFlag() {
        PaymentRequest origin = new PaymentRequest();
        origin.setStorePaymentMethod(true);
        PaymentRequest target = new PaymentRequest();

        testObj.copySchemePaymentSettings(target, origin);

        assertTrue(target.getStorePaymentMethod());
        assertNull(target.getEnableRecurring());
        assertNull(target.getEnableOneClick());
    }

    @Test
    public void recurringTokenizesTheFullPayment() {
        PaymentRequest result = testObj.createPaymentsRequest(MERCHANT_ACCOUNT, schemeCart(), componentRequest(),
                new RequestInfo(), registeredCustomer, RecurringContractMode.RECURRING, Boolean.FALSE, null);

        assertTokenized(result);
    }

    @Test
    public void recurringTokenizesThePartialPayment() {
        PaymentRequest result = testObj.createPartialPaymentRequest(MERCHANT_ACCOUNT, schemeCart(), componentRequest(),
                new RequestInfo(), registeredCustomer, RecurringContractMode.RECURRING, Boolean.FALSE,
                new BigDecimal("12.34"), CURRENCY);

        assertTokenized(result);
    }

    @Test
    public void noneTokenizesNeitherPayment() {
        PaymentRequest full = testObj.createPaymentsRequest(MERCHANT_ACCOUNT, schemeCart(), componentRequest(),
                new RequestInfo(), registeredCustomer, RecurringContractMode.NONE, Boolean.FALSE, null);
        PaymentRequest partial = testObj.createPartialPaymentRequest(MERCHANT_ACCOUNT, schemeCart(), componentRequest(),
                new RequestInfo(), registeredCustomer, RecurringContractMode.NONE, Boolean.FALSE,
                new BigDecimal("12.34"), CURRENCY);

        assertFalse(full.getStorePaymentMethod());
        assertFalse(partial.getStorePaymentMethod());
        assertNoLegacyFields(full);
        assertNoLegacyFields(partial);
    }

    @Test
    public void theJspCardPathIsTokenizedTheSameWay() {
        CartData cartData = schemeCart();
        cartData.setAdyenPaymentMethod(PAYMENT_METHOD_CC);
        cartData.setAdyenEncryptedCardNumber("test_number");
        cartData.setAdyenEncryptedExpiryMonth("test_month");
        cartData.setAdyenEncryptedExpiryYear("test_year");

        PaymentRequest result = testObj.createPaymentsRequest(MERCHANT_ACCOUNT, cartData, null,
                new RequestInfo(), registeredCustomer, RecurringContractMode.RECURRING, Boolean.FALSE, null);

        assertTokenized(result);
    }

    private void assertTokenized(PaymentRequest request) {
        assertTrue("storePaymentMethod", request.getStorePaymentMethod());
        assertEquals(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE, request.getRecurringProcessingModel());
        assertEquals(PaymentRequest.ShopperInteractionEnum.ECOMMERCE, request.getShopperInteraction());
        assertNoLegacyFields(request);
    }

    private void assertNoLegacyFields(PaymentRequest request) {
        assertNull("enableRecurring", request.getEnableRecurring());
        assertNull("enableOneClick", request.getEnableOneClick());
    }

    private CartData schemeCart() {
        CartData cartData = new CartData();
        cartData.setCode("00001234");
        cartData.setAdyenPaymentMethod(PAYMENT_METHOD_SCHEME);
        cartData.setTotalPriceWithTax(price());
        return cartData;
    }

    private PaymentRequest componentRequest() {
        PaymentRequest origin = new PaymentRequest();
        origin.setReference("00001234");
        origin.setPaymentMethod(new CheckoutPaymentMethod(
                new CardDetails().encryptedCardNumber("test_4111111111111111").type(CardDetails.TypeEnum.SCHEME)));
        return origin;
    }

    private PriceData price() {
        PriceData priceData = new PriceData();
        priceData.setValue(new BigDecimal("99.99"));
        priceData.setCurrencyIso(CURRENCY);
        return priceData;
    }
}
