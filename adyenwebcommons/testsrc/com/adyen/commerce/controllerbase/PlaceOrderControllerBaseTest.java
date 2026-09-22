package com.adyen.commerce.controllerbase;

import com.adyen.commerce.exception.AdyenControllerException;
import com.adyen.commerce.request.PlaceOrderRequest;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.forms.AddressForm;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

@UnitTest
public class PlaceOrderControllerBaseTest {

    private static final String KONBINI_PAYMENT_METHOD = "econtext_stores";
    private static final String MISSING_TELEPHONE_ERROR =
            "checkout.error.konbini.telephone.missing";
    private static final String TELEPHONE_NUMBER = "+81 11 1111 1111";

    private PlaceOrderControllerBase testObj;
    private PlaceOrderRequest placeOrderRequest;
    private PaymentRequest paymentRequest;
    private CartData cartData;

    @Before
    public void setUp() {
        testObj = mock(PlaceOrderControllerBase.class, CALLS_REAL_METHODS);
        paymentRequest = new PaymentRequest();
        placeOrderRequest = new PlaceOrderRequest();
        placeOrderRequest.setPaymentRequest(paymentRequest);
        cartData = new CartData();
    }

    @Test
    public void shouldAcceptTelephoneSubmittedInPaymentRequest() {
        paymentRequest.setTelephoneNumber(TELEPHONE_NUMBER);

        testObj.validateKonbiniTelephone(
                placeOrderRequest,
                KONBINI_PAYMENT_METHOD,
                cartData
        );
    }

    @Test
    public void shouldAcceptTelephoneFromBillingAddress() {
        AddressForm billingAddress = new AddressForm();
        billingAddress.setPhoneNumber(TELEPHONE_NUMBER);
        placeOrderRequest.setBillingAddress(billingAddress);

        testObj.validateKonbiniTelephone(
                placeOrderRequest,
                KONBINI_PAYMENT_METHOD,
                cartData
        );
    }

    @Test
    public void shouldAcceptTelephoneFromShippingAddress() {
        AddressData shippingAddress = new AddressData();
        shippingAddress.setPhone(TELEPHONE_NUMBER);
        cartData.setDeliveryAddress(shippingAddress);

        testObj.validateKonbiniTelephone(
                placeOrderRequest,
                KONBINI_PAYMENT_METHOD,
                cartData
        );
    }

    @Test
    public void shouldRejectMissingTelephone() {
        assertMissingTelephoneError();
    }

    @Test
    public void shouldRejectBlankTelephoneValues() {
        paymentRequest.setTelephoneNumber(" ");

        AddressForm billingAddress = new AddressForm();
        billingAddress.setPhoneNumber("  ");
        placeOrderRequest.setBillingAddress(billingAddress);

        AddressData shippingAddress = new AddressData();
        shippingAddress.setPhone("\t");
        cartData.setDeliveryAddress(shippingAddress);

        assertMissingTelephoneError();
    }

    @Test
    public void shouldIgnoreMissingTelephoneForOtherPaymentMethods() {
        testObj.validateKonbiniTelephone(
                placeOrderRequest,
                "scheme",
                cartData
        );
    }

    @Test
    public void shouldValidateEveryEcontextVariant() {
        assertMissingTelephoneError("econtext");
        assertMissingTelephoneError("econtext_atm");
        assertMissingTelephoneError("econtext_online");
        assertMissingTelephoneError("econtext_seven_eleven");
        assertMissingTelephoneError("econtext_stores");
    }

    private void assertMissingTelephoneError() {
        assertMissingTelephoneError(KONBINI_PAYMENT_METHOD);
    }

    private void assertMissingTelephoneError(String paymentMethod) {
        try {
            testObj.validateKonbiniTelephone(
                    placeOrderRequest,
                    paymentMethod,
                    cartData
            );
            fail("Expected AdyenControllerException");
        } catch (AdyenControllerException exception) {
            assertEquals(
                    MISSING_TELEPHONE_ERROR,
                    exception.getErrorResponse().getErrorCode()
            );
        }
    }
}
