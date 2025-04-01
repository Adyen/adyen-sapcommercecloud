package com.adyen.v6.controllers.pages.payments;

import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.facades.AdyenAmazonPayFacade;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.acceleratorfacades.flow.CheckoutFlowFacade;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;

import static com.adyen.v6.constants.AdyenControllerConstants.AMAZON_RETURN_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenAmazonpayControllerTest {

    private static final String ORDER_CODE = "orderCode";
    private static final String AMAZON_PAY_TOKEN = "amazonPayToken";
    private static final String SUMMARY_PAGE_REDIRECT = "summaryPageRedirect";
    private static final String AMAZON_CHECKOUT_SESSION_ID = "amazonCheckoutSessionId";
    private static final String REDIRECT_CHECKOUT_ORDER_CONFIRMATION_PAGE = "redirect:/checkout/orderConfirmation/orderCode";
    private static final String REDIRECT_3DS = "addon:/adyenv6b2ccheckoutaddon/pages/checkout/multi/3ds_payment";
    private static final String REDIRECT_RESULT = "redirectResult";
    private static final String URL = "url";

    @Spy
    @InjectMocks
    private AdyenAmazonpayController testObj;

    @Mock
    private OrderFacade orderFacadeMock;
    @Mock
    private CheckoutFlowFacade checkoutFlowFacadeMock;
    @Mock
    private AdyenCheckoutFacade adyenCheckoutFacadeMock;
    @Mock
    private AdyenAmazonPayFacade adyenAmazonPayFacadeMock;
    @Mock
    private CheckoutCustomerStrategy checkoutCustomerStrategyMock;

    @Mock
    private Model modelMock;
    @Mock
    private CartData cartDataMock;
    @Mock
    private OrderData orderDataMock;
    @Mock
    private HttpServletRequest requestMock;
    @Mock
    private PaymentResponse paymentResponseMock;
    @Mock
    private RedirectAttributes redirectModelMock;

    @Test
    public void placeOrder_shouldRedirectToSummaryPage_whenCheckoutSessionIdAndRedirectResultAreEmpty() throws CMSItemNotFoundException, CommerceCartModificationException {
        doReturn(SUMMARY_PAGE_REDIRECT).when(testObj).enterStep(modelMock, redirectModelMock);

        final String result = testObj.placeOrder(modelMock, redirectModelMock, requestMock, StringUtils.EMPTY);

        assertThat(result).isEqualTo(SUMMARY_PAGE_REDIRECT);
    }

    @Test
    public void placeOrder_shouldRedirectToSummaryPage_whenCheckoutSessionIdAndRedirectResultAreNull() throws CMSItemNotFoundException, CommerceCartModificationException {
        doReturn(SUMMARY_PAGE_REDIRECT).when(testObj).enterStep(modelMock, redirectModelMock);

        final String result = testObj.placeOrder(modelMock, redirectModelMock, requestMock, null);

        assertThat(result).isEqualTo(SUMMARY_PAGE_REDIRECT);
    }

    @Test
    public void placeOrder_shouldRedirectToOrderPlaced_whenCheckoutSessionIdIsValid() throws Exception {
        when(orderDataMock.getCode()).thenReturn(ORDER_CODE);
        when(paymentResponseMock.getMerchantReference()).thenReturn(ORDER_CODE);
        when(checkoutFlowFacadeMock.getCheckoutCart()).thenReturn(cartDataMock);
        when(checkoutCustomerStrategyMock.isAnonymousCheckout()).thenReturn(Boolean.FALSE);
        when(orderFacadeMock.getOrderDetailsForCodeWithoutUser(ORDER_CODE)).thenReturn(orderDataMock);
        when(adyenAmazonPayFacadeMock.getAmazonPayToken(AMAZON_CHECKOUT_SESSION_ID)).thenReturn(AMAZON_PAY_TOKEN);
        when(adyenCheckoutFacadeMock.componentPayment(eq(requestMock), eq(cartDataMock), isA(PaymentRequest.class))).thenReturn(paymentResponseMock);
        when(adyenAmazonPayFacadeMock.getReturnUrl(AMAZON_RETURN_URL)).thenReturn(URL);

        final String result = testObj.placeOrder(modelMock, redirectModelMock, requestMock, AMAZON_CHECKOUT_SESSION_ID);


        assertThat(result).isEqualTo(REDIRECT_CHECKOUT_ORDER_CONFIRMATION_PAGE);
    }

    @Test
    public void placeOrder_shouldRedirectTo3DSValidation_whenCheckoutSessionIdIsValidAndResponseCodeIsRedirectShopper() throws Exception {
        when(orderDataMock.getCode()).thenReturn(ORDER_CODE);
        when(paymentResponseMock.getMerchantReference()).thenReturn(ORDER_CODE);
        when(paymentResponseMock.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER);
        when(checkoutFlowFacadeMock.getCheckoutCart()).thenReturn(cartDataMock);
        when(checkoutCustomerStrategyMock.isAnonymousCheckout()).thenReturn(Boolean.FALSE);
        when(orderFacadeMock.getOrderDetailsForCodeWithoutUser(ORDER_CODE)).thenReturn(orderDataMock);
        when(adyenAmazonPayFacadeMock.getAmazonPayToken(AMAZON_CHECKOUT_SESSION_ID)).thenReturn(AMAZON_PAY_TOKEN);
        when(adyenCheckoutFacadeMock.componentPayment(eq(requestMock), eq(cartDataMock), isA(PaymentRequest.class))).thenReturn(paymentResponseMock);
        when(adyenAmazonPayFacadeMock.getReturnUrl(AMAZON_RETURN_URL)).thenReturn(URL);
        when(adyenCheckoutFacadeMock.getClientKey()).thenReturn("clientKey");
        when(adyenCheckoutFacadeMock.getCheckoutShopperHost()).thenReturn("host");
        when(adyenCheckoutFacadeMock.getEnvironmentMode()).thenReturn("environment");
        when(adyenCheckoutFacadeMock.getShopperLocale()).thenReturn("shopperLocale");

        final String result = testObj.placeOrder(modelMock, redirectModelMock, requestMock, AMAZON_CHECKOUT_SESSION_ID);


        assertThat(result).isEqualTo(REDIRECT_3DS);
    }

    @Test
    public void placeOrder_shouldRedirectToSummaryPage_whenAnExceptionIsThrownDuringThePaymentProcess() throws Exception {
        when(paymentResponseMock.getMerchantReference()).thenReturn(ORDER_CODE);
        when(checkoutFlowFacadeMock.getCheckoutCart()).thenReturn(cartDataMock);
        doReturn(SUMMARY_PAGE_REDIRECT).when(testObj).enterStep(modelMock, redirectModelMock);
        when(adyenAmazonPayFacadeMock.getAmazonPayToken(AMAZON_CHECKOUT_SESSION_ID)).thenReturn(AMAZON_PAY_TOKEN);
        when(adyenCheckoutFacadeMock.componentPayment(eq(requestMock), eq(cartDataMock), isA(PaymentRequest.class))).thenThrow(Exception.class);
        when(adyenAmazonPayFacadeMock.getReturnUrl(AMAZON_RETURN_URL)).thenReturn(URL);

        final String result = testObj.placeOrder(modelMock, redirectModelMock, requestMock, AMAZON_CHECKOUT_SESSION_ID);

        assertThat(result).isEqualTo(SUMMARY_PAGE_REDIRECT);
    }

}
