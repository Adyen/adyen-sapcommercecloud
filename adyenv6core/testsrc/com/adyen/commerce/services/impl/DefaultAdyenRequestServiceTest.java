package com.adyen.commerce.services.impl;

import com.adyen.commerce.decorator.AdyenPaymentRequestDecorator;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.EcontextVoucherDetails;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.ShopperName;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CCPaymentInfoData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultAdyenRequestServiceTest {

    private static final String BILLING_FIRST_NAME = "Billing First";
    private static final String BILLING_LAST_NAME = "Billing Last";
    private static final String BILLING_EMAIL = "billing@example.com";
    private static final String BILLING_PHONE = "+81 11 1111 1111";
    private static final String SHIPPING_FIRST_NAME = "Shipping First";
    private static final String SHIPPING_LAST_NAME = "Shipping Last";
    private static final String SHIPPING_EMAIL = "shipping@example.com";
    private static final String SHIPPING_PHONE = "+81 22 2222 2222";
    private static final String SUBMITTED_FIRST_NAME = "Submitted First";
    private static final String SUBMITTED_LAST_NAME = "Submitted Last";
    private static final String SUBMITTED_EMAIL = "submitted@example.com";
    private static final String SUBMITTED_PHONE = "+81 33 3333 3333";

    @Mock
    private BaseStoreService baseStoreService;
    @Mock
    private CartService cartService;
    @Mock
    private ConfigurationService configurationService;
    @Mock
    private PaymentMethodHandlerFactory paymentMethodHandlerFactory;
    @Mock
    private ApplicationInfoService applicationInfoService;
    @Mock
    private Configuration configuration;
    @Mock
    private CustomerModel customerModel;

    private DefaultAdyenRequestService testObj;
    private CartData cartData;
    private PaymentRequest paymentRequest;
    private PaymentRequest originPaymentRequest;

    @Before
    public void setUp() {
        testObj = new DefaultAdyenRequestService(
                baseStoreService,
                cartService,
                configurationService,
                paymentMethodHandlerFactory,
                applicationInfoService,
                Collections.<AdyenPaymentRequestDecorator>emptyList()
        );

        cartData = new CartData();
        paymentRequest = new PaymentRequest();
        originPaymentRequest = createOriginPaymentRequest();
    }

    @Test
    public void shouldUseBillingAddressBeforeShippingAndSubmittedDetails() {
        setBillingAddress(createAddress(
                BILLING_FIRST_NAME,
                BILLING_LAST_NAME,
                BILLING_EMAIL,
                BILLING_PHONE
        ));
        cartData.setDeliveryAddress(createAddress(
                SHIPPING_FIRST_NAME,
                SHIPPING_LAST_NAME,
                SHIPPING_EMAIL,
                SHIPPING_PHONE
        ));

        testObj.populateEcontextShopperDetails(
                paymentRequest,
                cartData,
                originPaymentRequest,
                customerModel
        );

        assertShopperDetails(
                BILLING_FIRST_NAME,
                BILLING_LAST_NAME,
                BILLING_EMAIL,
                BILLING_PHONE
        );
    }

    @Test
    public void shouldUseShippingAddressWhenBillingAddressIsMissing() {
        cartData.setDeliveryAddress(createAddress(
                SHIPPING_FIRST_NAME,
                SHIPPING_LAST_NAME,
                SHIPPING_EMAIL,
                SHIPPING_PHONE
        ));

        testObj.populateEcontextShopperDetails(
                paymentRequest,
                cartData,
                originPaymentRequest,
                customerModel
        );

        assertShopperDetails(
                SHIPPING_FIRST_NAME,
                SHIPPING_LAST_NAME,
                SHIPPING_EMAIL,
                SHIPPING_PHONE
        );
    }

    @Test
    public void shouldFallbackToShippingForBlankBillingFields() {
        setBillingAddress(createAddress(" ", " ", " ", " "));
        cartData.setDeliveryAddress(createAddress(
                SHIPPING_FIRST_NAME,
                SHIPPING_LAST_NAME,
                SHIPPING_EMAIL,
                SHIPPING_PHONE
        ));

        testObj.populateEcontextShopperDetails(
                paymentRequest,
                cartData,
                originPaymentRequest,
                customerModel
        );

        assertShopperDetails(
                SHIPPING_FIRST_NAME,
                SHIPPING_LAST_NAME,
                SHIPPING_EMAIL,
                SHIPPING_PHONE
        );
    }

    @Test
    public void shouldUseSubmittedDetailsWhenAddressesAreMissing() {
        testObj.populateEcontextShopperDetails(
                paymentRequest,
                cartData,
                originPaymentRequest,
                customerModel
        );

        assertShopperDetails(
                SUBMITTED_FIRST_NAME,
                SUBMITTED_LAST_NAME,
                SUBMITTED_EMAIL,
                SUBMITTED_PHONE
        );
    }

    @Test
    public void shouldUseCustomerEmailAsLastFallback() {
        originPaymentRequest.setShopperEmail(null);
        when(customerModel.getContactEmail()).thenReturn("customer@example.com");

        testObj.populateEcontextShopperDetails(
                paymentRequest,
                cartData,
                originPaymentRequest,
                customerModel
        );

        assertEquals("customer@example.com", paymentRequest.getShopperEmail());
    }

    @Test
    public void shouldLeaveShopperDetailsEmptyWhenNoSourceContainsData() {
        testObj.populateEcontextShopperDetails(
                paymentRequest,
                cartData,
                null,
                null
        );

        assertNull(paymentRequest.getShopperName());
        assertNull(paymentRequest.getShopperEmail());
        assertNull(paymentRequest.getTelephoneNumber());
    }

    @Test
    public void shouldCopyPaymentMethodAndPopulateDetailsForEcontext() {
        cartData.setAdyenPaymentMethod("econtext_stores");
        CheckoutPaymentMethod checkoutPaymentMethod = new CheckoutPaymentMethod(
                new EcontextVoucherDetails().type(EcontextVoucherDetails.TypeEnum.ECONTEXT_STORES)
        );
        originPaymentRequest.setPaymentMethod(checkoutPaymentMethod);
        cartData.setDeliveryAddress(createAddress(
                SHIPPING_FIRST_NAME,
                SHIPPING_LAST_NAME,
                SHIPPING_EMAIL,
                SHIPPING_PHONE
        ));
        preparePaymentMethodHandling("econtext_stores");

        testObj.handlePaymentMethodSpecificLogic(
                paymentRequest,
                cartData,
                originPaymentRequest,
                null,
                customerModel,
                false
        );

        assertSame(checkoutPaymentMethod, paymentRequest.getPaymentMethod());
        assertShopperDetails(
                SHIPPING_FIRST_NAME,
                SHIPPING_LAST_NAME,
                SHIPPING_EMAIL,
                SHIPPING_PHONE
        );
    }

    @Test
    public void shouldNotPopulateEcontextDetailsForOtherPaymentMethods() {
        cartData.setAdyenPaymentMethod("paypal");
        setBillingAddress(createAddress(
                BILLING_FIRST_NAME,
                BILLING_LAST_NAME,
                BILLING_EMAIL,
                BILLING_PHONE
        ));
        preparePaymentMethodHandling("paypal");

        testObj.handlePaymentMethodSpecificLogic(
                paymentRequest,
                cartData,
                null,
                null,
                customerModel,
                false
        );

        assertNull(paymentRequest.getShopperName());
        assertNull(paymentRequest.getShopperEmail());
        assertNull(paymentRequest.getTelephoneNumber());
    }

    private void preparePaymentMethodHandling(String paymentMethod) {
        when(configurationService.getConfiguration()).thenReturn(configuration);
        when(configuration.containsKey(DefaultAdyenRequestService.IS_3DS2_ALLOWED_PROPERTY)).thenReturn(false);
        when(paymentMethodHandlerFactory.getHandler(paymentMethod)).thenReturn(Optional.empty());
    }

    private PaymentRequest createOriginPaymentRequest() {
        return new PaymentRequest()
                .shopperName(new ShopperName()
                        .firstName(SUBMITTED_FIRST_NAME)
                        .lastName(SUBMITTED_LAST_NAME))
                .shopperEmail(SUBMITTED_EMAIL)
                .telephoneNumber(SUBMITTED_PHONE);
    }

    private AddressData createAddress(
            String firstName,
            String lastName,
            String email,
            String phone) {
        AddressData address = new AddressData();
        address.setFirstName(firstName);
        address.setLastName(lastName);
        address.setEmail(email);
        address.setPhone(phone);
        return address;
    }

    private void setBillingAddress(AddressData billingAddress) {
        CCPaymentInfoData paymentInfo = new CCPaymentInfoData();
        paymentInfo.setBillingAddress(billingAddress);
        cartData.setPaymentInfo(paymentInfo);
    }

    private void assertShopperDetails(
            String firstName,
            String lastName,
            String email,
            String telephoneNumber) {
        assertEquals(firstName, paymentRequest.getShopperName().getFirstName());
        assertEquals(lastName, paymentRequest.getShopperName().getLastName());
        assertEquals(email, paymentRequest.getShopperEmail());
        assertEquals(telephoneNumber, paymentRequest.getTelephoneNumber());
    }
}
