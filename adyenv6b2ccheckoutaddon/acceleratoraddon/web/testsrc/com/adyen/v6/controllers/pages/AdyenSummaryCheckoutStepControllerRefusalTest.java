package com.adyen.v6.controllers.pages;

import static com.adyen.v6.constants.AdyenControllerConstants.CART_PREFIX;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_PAYPAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.adyen.commerce.facades.AdyenCheckoutFacade;
import com.adyen.v6.helpers.AdyenUrlHelper;
import com.adyen.v6.service.PaymentAuthorisationRefusedException;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessage;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.commercefacades.order.data.CartData;
import jakarta.servlet.http.HttpServletRequest;

/**
 * A payment guard refused before Adyen was called (here: a subscription the customer already has). The cart is what
 * has to change, so the customer goes back to it with the refusal's own message key as a flash error - not to the
 * summary page with the generic authorization failure the catch-all would show.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenSummaryCheckoutStepControllerRefusalTest
{
    private static final String REDIRECT_TO_CART = "redirect:" + CART_PREFIX;

    @InjectMocks
    @Spy
    private AdyenSummaryCheckoutStepController testObj;

    @Mock
    private AdyenCheckoutFacade adyenCheckoutFacadeMock;
    @Mock
    private AdyenUrlHelper adyenUrlHelperMock;
    @Mock
    private HttpServletRequest requestMock;
    @Mock
    private RedirectAttributes redirectModelMock;
    @Mock
    private Model modelMock;
    @Mock
    private CartData cartDataMock;

    @Before
    public void setUp() throws Exception
    {
        lenient().doReturn(null).when(testObj).lookupMessage(any());
        when(adyenCheckoutFacadeMock.authorisePayment(requestMock, cartDataMock))
                .thenThrow(new PaymentAuthorisationRefusedException("subscriptionAlreadyOwned"));
    }

    @Test
    public void aRefusedStandardPaymentGoesBackToTheCartWithTheRefusalMessage() throws Exception
    {
        doReturn(adyenUrlHelperMock).when(testObj).getAdyenUrlService();
        when(adyenUrlHelperMock.getReturnUrl(any())).thenReturn("https://shop.local/return");
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_PAYPAL);

        final String result = testObj.handleStandardPayment(requestMock, cartDataMock, modelMock, redirectModelMock);

        assertThat(result).isEqualTo(REDIRECT_TO_CART);
        assertFlashErrorMessage("checkout.error.subscriptionAlreadyOwned");
    }

    @Test
    public void theSubscriptionAddonMessageWinsWhenItResolves() throws Exception
    {
        doReturn("You already have it. <a href=\"my-account/subscriptions\">My Subscriptions</a>")
                .when(testObj).lookupMessage("text.cart.subscription.payment.subscriptionAlreadyOwned");

        final String result = testObj.handleRatepayPayment(requestMock, cartDataMock, redirectModelMock);

        assertThat(result).isEqualTo(REDIRECT_TO_CART);
        assertFlashErrorMessage("text.cart.subscription.payment.subscriptionAlreadyOwned");
    }

    @Test
    public void anUnresolvableAddonKeyFallsBackToTheCheckoutErrorKey() throws Exception
    {
        // some message sources echo the code back instead of returning null
        doReturn("text.cart.subscription.payment.subscriptionAlreadyOwned")
                .when(testObj).lookupMessage("text.cart.subscription.payment.subscriptionAlreadyOwned");

        testObj.handleRatepayPayment(requestMock, cartDataMock, redirectModelMock);

        assertFlashErrorMessage("checkout.error.subscriptionAlreadyOwned");
    }

    @Test
    public void aRefusedRatepayPaymentGoesBackToTheCartWithTheRefusalMessage() throws Exception
    {

        final String result = testObj.handleRatepayPayment(requestMock, cartDataMock, redirectModelMock);

        assertThat(result).isEqualTo(REDIRECT_TO_CART);
        assertFlashErrorMessage("checkout.error.subscriptionAlreadyOwned");
    }

    private void assertFlashErrorMessage(final String expectedCode)
    {
        final ArgumentCaptor<Object> flash = ArgumentCaptor.forClass(Object.class);
        verify(redirectModelMock).addFlashAttribute(eq(GlobalMessages.ERROR_MESSAGES_HOLDER), flash.capture());
        final List<?> messages = (List<?>) flash.getValue();
        assertThat(messages).hasSize(1);
        assertThat(((GlobalMessage) messages.get(0)).getCode()).isEqualTo(expectedCode);
    }
}
