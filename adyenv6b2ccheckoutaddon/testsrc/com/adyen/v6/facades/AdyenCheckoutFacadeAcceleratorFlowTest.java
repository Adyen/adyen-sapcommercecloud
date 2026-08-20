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
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.facades;

import com.adyen.commerce.facades.impl.DefaultAdyenCheckoutFacade;
import com.adyen.commerce.services.AdyenCartRestorationService;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.repository.OrderRepository;
import com.adyen.v6.service.AdyenCheckoutApiService;
import com.adyen.v6.service.AdyenOrderService;
import com.adyen.v6.service.AdyenShopperIpResolverService;
import com.adyen.v6.service.AdyenTransactionService;
import com.adyen.v6.service.ThreeDSAuthorizationService;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.c2l.LanguageModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.order.CartFactory;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.model.RequestInfo.ACCEPT_HEADER;
import static com.adyen.v6.model.RequestInfo.USER_AGENT_HEADER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the accelerator storefront's own entry points into {@link DefaultAdyenCheckoutFacade}: card
 * authorisation, the 3DS return leg, and the session-cart lock/restore pair around them.
 *
 * <p>The name deliberately avoids {@code AdyenCheckoutFacadeTest}. adyenv6core already has a class with
 * that fully qualified name, and while both existed a test run only ever reported the core one's
 * methods - so nothing here was being executed.</p>
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenCheckoutFacadeAcceleratorFlowTest {

    private static final String CART_CODE = "cart-code";
    private static final String ORDER_CODE = "order-code";
    private static final String PSP_REFERENCE = "pspRef";
    private static final String SHOPPER_IP = "203.0.113.7";
    private static final String REQUEST_URL = "https://shop.local/checkout/payment";
    private static final String REQUEST_URI = "/checkout/payment";

    @Spy
    @InjectMocks
    private DefaultAdyenCheckoutFacade adyenCheckoutFacade = new DefaultAdyenCheckoutFacade();

    @Mock
    private BaseStoreService baseStoreService;
    @Mock
    private SessionService sessionService;
    @Mock
    private CartService cartService;
    @Mock
    private CartFactory cartFactory;
    @Mock
    private CheckoutFacade checkoutFacade;
    @Mock
    private CheckoutCustomerStrategy checkoutCustomerStrategy;
    @Mock
    private ModelService modelService;
    @Mock
    private CommonI18NService commonI18NService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    @Mock
    private AdyenTransactionService adyenTransactionService;
    @Mock
    private AdyenOrderService adyenOrderService;
    @Mock
    private AdyenShopperIpResolverService adyenShopperIpResolverService;
    @Mock
    private ThreeDSAuthorizationService threeDSAuthorizationService;
    @Mock
    private AdyenCartRestorationService adyenCartRestorationService;

    @Mock
    private AdyenCheckoutApiService adyenCheckoutApiService;
    @Mock
    private BaseStoreModel baseStore;
    @Mock
    private LanguageModel language;
    @Mock
    private HttpServletRequest request;
    @Mock
    private CartData cartData;
    @Mock
    private CartModel cartModel;
    @Mock
    private CartModel replacementCartModel;
    @Mock
    private OrderModel orderModel;
    @Mock
    private OrderData orderData;

    private final PaymentDetailsRequest detailsRequest = new PaymentDetailsRequest();

    private PaymentResponse paymentResponse;

    @Before
    public void setUp() throws Exception {
        when(baseStoreService.getCurrentBaseStore()).thenReturn(baseStore);
        when(adyenPaymentServiceFactory.createAdyenCheckoutApiService(baseStore)).thenReturn(adyenCheckoutApiService);

        when(cartService.getSessionCart()).thenReturn(cartModel);
        when(cartModel.getCode()).thenReturn(CART_CODE);
        when(cartFactory.createCart()).thenReturn(replacementCartModel);
        when(checkoutFacade.placeOrder()).thenReturn(orderData);
        when(orderData.getCode()).thenReturn(ORDER_CODE);
        when(orderRepository.getOrderModel(ORDER_CODE)).thenReturn(orderModel);

        when(cartData.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_CC);
        when(commonI18NService.getCurrentLanguage()).thenReturn(language);
        when(language.getIsocode()).thenReturn("en");
        when(adyenShopperIpResolverService.resolveShopperIp(request)).thenReturn(SHOPPER_IP);
        when(request.getHeader(USER_AGENT_HEADER)).thenReturn("userAgent");
        when(request.getHeader(ACCEPT_HEADER)).thenReturn("acceptHeader");
        when(request.getRequestURL()).thenReturn(new StringBuffer(REQUEST_URL));
        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        // No action and no additionalData: this is the plain card response, and the branches that read them
        // belong to other flows.
        paymentResponse = new PaymentResponse();
        paymentResponse.setPspReference(PSP_REFERENCE);
        when(adyenCheckoutApiService.processPaymentRequest(any(), any(), any(), any())).thenReturn(paymentResponse);
    }

    @Test
    public void testAuthorizeCardPayment() throws Exception {
        paymentResponse.setResultCode(PaymentResponse.ResultCodeEnum.AUTHORISED);

        assertSame(orderData, adyenCheckoutFacade.authorisePayment(request, cartData));

        verify(adyenCheckoutApiService).processPaymentRequest(eq(cartData), isNull(), any(RequestInfo.class), isNull());
        verify(adyenTransactionService).authorizeOrderModel(cartModel, CART_CODE, PSP_REFERENCE);
        verify(checkoutFacade).placeOrder();
        // The cast selects the AbstractOrderModel overload - the only one implemented, and the one the
        // facade calls; the deprecated OrderModel forwarder would be matched as a different method.
        verify(adyenOrderService).updatePaymentInfo(eq((AbstractOrderModel) orderModel), any(), any());
        verify(adyenOrderService).storeFraudReport(orderModel, PSP_REFERENCE, null);
    }

    @Test
    public void testAuthorizeCardPaymentRedirect() throws Exception {
        paymentResponse.setResultCode(PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER);

        final AdyenNonAuthorizedPaymentException thrown = assertThrows(AdyenNonAuthorizedPaymentException.class,
                () -> adyenCheckoutFacade.authorisePayment(request, cartData));

        assertSame(paymentResponse, thrown.getPaymentsResponse());
        verify(cartModel).setStatus(OrderStatus.PAYMENT_PENDING);
        verify(cartModel).setStatusInfo(PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER.getValue());
        verify(modelService).save(cartModel);
        verify(checkoutFacade).placeOrder();
        // The shopper leaves the site at this point, so the session must not keep handing out the cart the
        // pending order was placed from.
        verify(cartService).setSessionCart(replacementCartModel);
    }

    @Test
    public void testAuthorizeCardPaymentRefused() throws Exception {
        paymentResponse.setResultCode(PaymentResponse.ResultCodeEnum.REFUSED);

        final AdyenNonAuthorizedPaymentException thrown = assertThrows(AdyenNonAuthorizedPaymentException.class,
                () -> adyenCheckoutFacade.authorisePayment(request, cartData));

        assertSame(paymentResponse, thrown.getPaymentsResponse());
        verify(checkoutFacade, never()).placeOrder();
        verify(adyenTransactionService, never()).authorizeOrderModel(any(), any(), any());
    }

    @Test
    public void testHandle3DResponse() throws Exception {
        when(threeDSAuthorizationService.handle3DSResponse(detailsRequest)).thenReturn(orderData);

        assertSame(orderData, adyenCheckoutFacade.handle3DSResponse(detailsRequest));

        verify(adyenCartRestorationService, never()).restoreCartFromOrderCodeInSession();
    }

    @Test
    public void testHandle3DResponseRefused() throws Exception {
        final PaymentDetailsResponse refusedResponse = new PaymentDetailsResponse();
        final AdyenNonAuthorizedPaymentException refused = new AdyenNonAuthorizedPaymentException(refusedResponse);
        when(threeDSAuthorizationService.handle3DSResponse(detailsRequest)).thenThrow(refused);

        final AdyenNonAuthorizedPaymentException thrown = assertThrows(AdyenNonAuthorizedPaymentException.class,
                () -> adyenCheckoutFacade.handle3DSResponse(detailsRequest));

        // Rethrown as-is: the storefront renders the refusal from the Adyen response it carries, so
        // re-wrapping it would lose that.
        assertSame(refused, thrown);
        assertSame(refusedResponse, thrown.getPaymentsDetailsResponse());
        verify(adyenCartRestorationService).restoreCartFromOrderCodeInSession();
    }

    @Test
    public void testHandle3DResponseUnexpectedFailure() throws Exception {
        when(threeDSAuthorizationService.handle3DSResponse(detailsRequest))
                .thenThrow(new IllegalStateException("3DS lookup blew up"));

        final AdyenNonAuthorizedPaymentException thrown = assertThrows(AdyenNonAuthorizedPaymentException.class,
                () -> adyenCheckoutFacade.handle3DSResponse(detailsRequest));

        assertEquals("3DS lookup blew up", thrown.getMessage());
        verify(adyenCartRestorationService).restoreCartFromOrderCodeInSession();
    }

    @Test
    public void testRestoreSessionCart() throws InvalidCartException {
        when(adyenCartRestorationService.restoreSessionCart()).thenReturn(cartModel);

        assertSame(cartModel, adyenCheckoutFacade.restoreSessionCart());
    }

    @Test
    public void testRestoreSessionCartWithoutALockedCart() throws InvalidCartException {
        final InvalidCartException expected = new InvalidCartException("Cart does not exist!");
        when(adyenCartRestorationService.restoreSessionCart()).thenThrow(expected);

        assertSame(expected, assertThrows(InvalidCartException.class, () -> adyenCheckoutFacade.restoreSessionCart()));
    }

    @Test
    public void testLockSessionCart() {
        adyenCheckoutFacade.lockSessionCart();

        verify(adyenCartRestorationService).lockSessionCart();
    }
}
