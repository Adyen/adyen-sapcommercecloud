package com.adyen.commerce.facades.impl;

import com.adyen.v6.forms.AddressForm;
import com.adyen.v6.forms.AdyenPaymentForm;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.order.CartService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import static com.adyen.v6.constants.Adyenv6coreConstants.CHECKOUT_ERROR_KONBINI_TELEPHONE_MISSING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultAdyenCheckoutFacadeKonbiniTest {

    private static final String KONBINI_PAYMENT_METHOD = "econtext_stores";
    private static final String TELEPHONE_NUMBER = "+81 11 1111 1111";

    @Mock
    private CartService cartService;
    @Mock
    private CartModel cartModel;
    @Mock
    private AddressModel shippingAddress;

    private DefaultAdyenCheckoutFacade testObj;
    private AdyenPaymentForm paymentForm;
    private Errors errors;

    @Before
    public void setUp() {
        testObj = new DefaultAdyenCheckoutFacade();
        paymentForm = new AdyenPaymentForm();
        paymentForm.setPaymentMethod(KONBINI_PAYMENT_METHOD);
        errors = new BeanPropertyBindingResult(paymentForm, "adyenPaymentForm");
    }

    @Test
    public void shouldRejectKonbiniWithoutBillingOrShippingTelephone() {
        testObj.validateKonbiniTelephone(paymentForm, cartModel, errors);

        assertMissingTelephoneError();
    }

    @Test
    public void shouldRejectBlankBillingAndShippingTelephone() {
        AddressForm billingAddress = new AddressForm();
        billingAddress.setPhoneNumber(" ");
        paymentForm.setBillingAddress(billingAddress);
        when(cartModel.getDeliveryAddress()).thenReturn(shippingAddress);
        when(shippingAddress.getPhone1()).thenReturn("\t");

        testObj.validateKonbiniTelephone(paymentForm, cartModel, errors);

        assertMissingTelephoneError();
    }

    @Test
    public void shouldAcceptKonbiniWithBillingTelephone() {
        AddressForm billingAddress = new AddressForm();
        billingAddress.setPhoneNumber(TELEPHONE_NUMBER);
        paymentForm.setBillingAddress(billingAddress);

        testObj.validateKonbiniTelephone(paymentForm, cartModel, errors);

        assertFalse(errors.hasErrors());
    }

    @Test
    public void shouldAcceptKonbiniWithShippingTelephone() {
        when(cartModel.getDeliveryAddress()).thenReturn(shippingAddress);
        when(shippingAddress.getPhone1()).thenReturn(TELEPHONE_NUMBER);

        testObj.validateKonbiniTelephone(paymentForm, cartModel, errors);

        assertFalse(errors.hasErrors());
    }

    @Test
    public void shouldIgnoreMissingTelephoneForOtherPaymentMethods() {
        paymentForm.setPaymentMethod("scheme");

        testObj.validateKonbiniTelephone(paymentForm, cartModel, errors);

        assertFalse(errors.hasErrors());
    }

    @Test
    public void shouldApplyKonbiniValidationWhileHandlingPaymentForm() {
        DefaultAdyenCheckoutFacade facade = spy(new DefaultAdyenCheckoutFacade());
        facade.setCartService(cartService);
        when(cartService.getSessionCart()).thenReturn(cartModel);
        doReturn(false).when(facade).showRememberDetails();
        doReturn(false).when(facade).showSocialSecurityNumber();
        doReturn(false).when(facade).getHolderNameRequired();

        facade.handlePaymentForm(paymentForm, errors);

        assertMissingTelephoneError();
        verify(cartModel, never()).setAdyenDfValue(paymentForm.getDfValue());
    }

    private void assertMissingTelephoneError() {
        assertTrue(errors.hasErrors());
        assertEquals(
                CHECKOUT_ERROR_KONBINI_TELEPHONE_MISSING,
                errors.getGlobalError().getCode()
        );
    }
}
