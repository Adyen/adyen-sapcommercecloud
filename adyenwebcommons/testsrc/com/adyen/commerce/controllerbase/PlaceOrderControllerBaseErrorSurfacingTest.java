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
package com.adyen.commerce.controllerbase;

import com.adyen.commerce.data.AdyenPartialPaymentOrderData;
import com.adyen.commerce.exception.AdyenControllerException;
import com.adyen.commerce.facades.AdyenCheckoutApiFacade;
import com.adyen.commerce.facades.AdyenCheckoutFacade;
import com.adyen.commerce.request.PlaceOrderRequest;
import com.adyen.commerce.services.impl.RecurringContractHelper;
import com.adyen.commerce.services.impl.RecurringContractHelper.TokenizationNotSupportedException;
import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.enums.AdyenPartialPaymentStatus;
import com.adyen.v6.service.AdyenPartialPaymentService;
import com.adyen.v6.service.AdyenShopperIpResolverService;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.acceleratorfacades.flow.CheckoutFlowFacade;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.site.BaseSiteService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static com.adyen.commerce.constants.AdyenwebcommonsConstants.CHECKOUT_ERROR_AUTHORIZATION_FAILED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "You picked a method that cannot fund renewals" is a choice the shopper can correct, so it has to arrive
 * as its own message key. Every other failure keeps arriving as the generic authorization error - a catch
 * that widened would hide real breakage behind a misleading "pick another payment method".
 */
@UnitTest
public class PlaceOrderControllerBaseErrorSurfacingTest {

    private static final String GIFT_CARD_PSP = "GIFTCARD-PSP-REFERENCE";

    private AdyenCheckoutApiFacade adyenCheckoutApiFacade;
    private CartFacade cartFacade;
    private AdyenShopperIpResolverService adyenShopperIpResolverService;
    private HttpServletRequest request;
    private CartData cartData;
    private PlaceOrderControllerBase controller;

    // Collaborators the paths under test never reach; the base class still demands them.
    private final CheckoutFlowFacade checkoutFlowFacade = mock(CheckoutFlowFacade.class);
    private final BaseSiteService baseSiteService = mock(BaseSiteService.class);
    private final SiteBaseUrlResolutionService siteBaseUrlResolutionService = mock(SiteBaseUrlResolutionService.class);
    private final AdyenCheckoutFacade adyenCheckoutFacade = mock(AdyenCheckoutFacade.class);
    private final CheckoutCustomerStrategy checkoutCustomerStrategy = mock(CheckoutCustomerStrategy.class);
    private final AdyenPartialPaymentService adyenPartialPaymentService = mock(AdyenPartialPaymentService.class);

    @Before
    public void setUp() {
        adyenCheckoutApiFacade = mock(AdyenCheckoutApiFacade.class);
        cartFacade = mock(CartFacade.class);
        adyenShopperIpResolverService = mock(AdyenShopperIpResolverService.class);
        request = mock(HttpServletRequest.class);
        cartData = new CartData();

        when(cartFacade.getSessionCart()).thenReturn(cartData);
        when(request.getRequestURL()).thenReturn(new StringBuffer("https://shop.local/checkout/place-order"));
        when(request.getRequestURI()).thenReturn("/checkout/place-order");

        controller = new TestPlaceOrderController();
    }

    @Test
    public void tokenizationRefusalKeepsItsOwnMessageKey() throws Exception {
        when(adyenCheckoutApiFacade.placeOrderWithPayment(any(), any(), any(), any()))
                .thenThrow(new TokenizationNotSupportedException("klarna cannot leave a reusable token behind"));

        AdyenControllerException thrown = assertThrows(AdyenControllerException.class,
                () -> controller.placeOrderOCC(newPlaceOrderRequest(), request));

        assertEquals(RecurringContractHelper.PAYMENT_METHOD_NOT_SUPPORTED, thrown.getErrorResponse().getErrorCode());
    }

    @Test
    public void anyOtherFailureStillArrivesAsTheGenericAuthorizationError() throws Exception {
        when(adyenCheckoutApiFacade.placeOrderWithPayment(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("something genuinely broke"));

        AdyenControllerException thrown = assertThrows(AdyenControllerException.class,
                () -> controller.placeOrderOCC(newPlaceOrderRequest(), request));

        assertEquals(CHECKOUT_ERROR_AUTHORIZATION_FAILED, thrown.getErrorResponse().getErrorCode());
    }

    /**
     * The remaining-amount leg runs with a gift card already authorized. The refusal happens while the
     * request is being assembled, so nothing was charged and the hold must not be written off as a failure -
     * doing so would take away the retry the message is asking the shopper to make.
     */
    @Test
    public void tokenizationRefusalOnTheRemainingAmountDoesNotRecordAPaymentFailure() throws Exception {
        cartData.setAdyenPartialPaymentOrders(Collections.singletonList(authorizedGiftCardPayment()));
        when(adyenCheckoutApiFacade.placeOrderWithPayment(any(), any(), any(), any(), any()))
                .thenThrow(new TokenizationNotSupportedException("klarna cannot leave a reusable token behind"));

        PlaceOrderRequest placeOrderRequest = newPlaceOrderRequest();
        placeOrderRequest.setPartialPaymentId(GIFT_CARD_PSP);

        AdyenControllerException thrown = assertThrows(AdyenControllerException.class,
                () -> controller.placeOrderOCC(placeOrderRequest, request));

        assertEquals(RecurringContractHelper.PAYMENT_METHOD_NOT_SUPPORTED, thrown.getErrorResponse().getErrorCode());
        verify(adyenCheckoutApiFacade, never()).updatePartialPaymentStatus(any(), eq(AdyenPartialPaymentStatus.FAILED));
    }

    @Test
    public void anyOtherFailureOnTheRemainingAmountStillWritesOffThePartialPayment() throws Exception {
        AdyenPartialPaymentOrderData partialPayment = authorizedGiftCardPayment();
        cartData.setAdyenPartialPaymentOrders(Collections.singletonList(partialPayment));
        when(adyenCheckoutApiFacade.placeOrderWithPayment(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("something genuinely broke"));

        PlaceOrderRequest placeOrderRequest = newPlaceOrderRequest();
        placeOrderRequest.setPartialPaymentId(GIFT_CARD_PSP);

        AdyenControllerException thrown = assertThrows(AdyenControllerException.class,
                () -> controller.placeOrderOCC(placeOrderRequest, request));

        assertEquals(CHECKOUT_ERROR_AUTHORIZATION_FAILED, thrown.getErrorResponse().getErrorCode());
        verify(adyenCheckoutApiFacade).updatePartialPaymentStatus(partialPayment, AdyenPartialPaymentStatus.FAILED);
    }

    // -------------------------------------------------------------- helpers

    private AdyenPartialPaymentOrderData authorizedGiftCardPayment() {
        AdyenPartialPaymentOrderData partialPayment = new AdyenPartialPaymentOrderData();
        partialPayment.setPspReference(GIFT_CARD_PSP);
        partialPayment.setStatus("AUTHORIZED");
        return partialPayment;
    }

    private PlaceOrderRequest newPlaceOrderRequest() {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(
                new CardDetails().encryptedCardNumber("test_4111111111111111").type(CardDetails.TypeEnum.SCHEME)));

        PlaceOrderRequest placeOrderRequest = new PlaceOrderRequest();
        placeOrderRequest.setPaymentRequest(paymentRequest);
        return placeOrderRequest;
    }

    /**
     * Form validation and cart validation are covered by their own tests and are stubbed out here so that
     * these ones only exercise what happens to an exception raised while the payment is being made.
     */
    private class TestPlaceOrderController extends PlaceOrderControllerBase {

        @Override
        protected void preHandleAndValidateRequest(PlaceOrderRequest placeOrderRequest, String adyenPaymentMethod) {
            // stubbed, see javadoc
        }

        @Override
        protected boolean isCartValid() {
            return true;
        }

        @Override
        public String getPaymentRedirectReturnUrl() {
            return "https://shop.local/checkout/adyen-response";
        }

        @Override
        public AdyenCheckoutApiFacade getAdyenCheckoutApiFacade() {
            return adyenCheckoutApiFacade;
        }

        @Override
        public CheckoutFlowFacade getCheckoutFlowFacade() {
            return checkoutFlowFacade;
        }

        @Override
        public CartFacade getCartFacade() {
            return cartFacade;
        }

        @Override
        public BaseSiteService getBaseSiteService() {
            return baseSiteService;
        }

        @Override
        public SiteBaseUrlResolutionService getSiteBaseUrlResolutionService() {
            return siteBaseUrlResolutionService;
        }

        @Override
        public AdyenCheckoutFacade getAdyenCheckoutFacade() {
            return adyenCheckoutFacade;
        }

        @Override
        public CheckoutCustomerStrategy getCheckoutCustomerStrategy() {
            return checkoutCustomerStrategy;
        }

        @Override
        public AdyenPartialPaymentService getAdyenPartialPaymentService() {
            return adyenPartialPaymentService;
        }

        @Override
        public AdyenShopperIpResolverService getAdyenShopperIpResolverService() {
            return adyenShopperIpResolverService;
        }
    }
}
