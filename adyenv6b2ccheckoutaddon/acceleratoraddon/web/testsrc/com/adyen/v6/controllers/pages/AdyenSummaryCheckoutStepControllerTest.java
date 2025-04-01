package com.adyen.v6.controllers.pages;

import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.terminal.TerminalAPIResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.AdyenControllerConstants;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.acceleratorfacades.flow.CheckoutFlowFacade;
import de.hybris.platform.acceleratorfacades.order.AcceleratorCheckoutFacade;
import de.hybris.platform.acceleratorservices.enums.CheckoutPciOptionEnum;
import de.hybris.platform.acceleratorservices.storefront.util.PageTitleResolver;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.Breadcrumb;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.checkout.steps.CheckoutGroup;
import de.hybris.platform.acceleratorstorefrontcommons.checkout.steps.CheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.forms.PlaceOrderForm;
import de.hybris.platform.cms2.data.PagePreviewCriteriaData;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.cms2.model.pages.ContentPageModel;
import de.hybris.platform.cms2.model.site.CMSSiteModel;
import de.hybris.platform.cms2.servicelayer.services.CMSPageService;
import de.hybris.platform.cms2.servicelayer.services.CMSPreviewService;
import de.hybris.platform.cms2.servicelayer.services.CMSSiteService;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.CartModificationData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.site.BaseSiteService;
import org.apache.commons.configuration.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.adyen.v6.constants.AdyenControllerConstants.CART_PREFIX;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_PAYPAL;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_POS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenSummaryCheckoutStepControllerTest {

    private static final String PAYLOAD = "payload";
    private static final String PAYLOAD_VALUE = "value";
    private static final String REDIRECT_RESULT = "redirectResult";
    private static final String MERCHANT_REFERENCE = "merchantReference";
    private static final String REDIRECT_PREFIX = "redirect:";
    private static final String REDIRECT_URL_ORDER_CONFIRMATION = REDIRECT_PREFIX + "/checkout/orderConfirmation/";
    private static final String ORDER_CODE = "orderCode";
    private static final String SECURITY_CODE = "securityCode";
    private static final String CONTENT_PAGE_MODEL_ID = "contentPageModelId";
    private static final String CONTENT_PAGE_MODEL_TITLE = "contentPageModelTitle";
    private static final String CONTENT_PAGE_MODEL_KEYWORDS = "contentPageModelKeywords";
    private static final String CONTENT_PAGE_MODEL_DESCRIPTION = "contentPageModelDescription";
    private static final String MULTI_CHECKOUT_SUMMARY_CMS_PAGE_LABEL_MOCK = "multiStepCheckoutSummary";
    private static final String CHECKOUT_FLOW_GROUP_FOR_CHECKOUT_MOCK = "checkoutFlowGroupForCheckout";
    private static final String CURRENT_CONTROLLER = "summary";
    private static final String PROGRESS_BAR_ID = "progressBarId";
    private static final String RATEPAY = "ratepay";
    private static final String POS_TOTAL_TIMEOUT_KEY = "pos.totaltimeout";
    private static final String MOCK_BASESITE_URL = "mockBasesiteURL";
    private static final String ACTION_URL = "actionURL";

    @InjectMocks
    @Spy
    private AdyenSummaryCheckoutStepController testObj;

    @Mock
    private AdyenCheckoutFacade adyenCheckoutFacadeMock;
    @Mock
    private AcceleratorCheckoutFacade checkoutFacadeMock;
    @Mock
    private CheckoutFlowFacade checkoutFlowFacadeMock;
    @Mock
    private OrderFacade orderFacadeMock;
    @Mock
    private CartFacade cartFacadeMock;
    @Mock
    private CheckoutCustomerStrategy checkoutCustomerStrategyMock;
    @Mock
    private CMSPageService cmsPageServiceMock;
    @Mock
    private CMSSiteService cmsSiteServiceMock;
    @Mock
    private BaseSiteService baseSiteServiceMock;
    @Mock
    private CMSPreviewService cmsPreviewServiceMock;
    @Mock
    private SiteBaseUrlResolutionService siteBaseUrlResolutionServiceMock;
    @Mock
    private ConfigurationService configurationServiceMock;
    @Mock
    private PageTitleResolver pageTitleResolverMock;
    @Mock
    private ResourceBreadcrumbBuilder resourceBreadcrumbBuilderMock;
    @Mock
    private HttpServletRequest requestMock;
    @Mock
    private PaymentDetailsResponse responseMock;
    @Mock
    private RedirectAttributes redirectModelMock;
    @Mock
    private OrderData orderDataMock;
    @Mock
    private CartData cartDataMock;
    @Mock
    private PagePreviewCriteriaData pagePreviewCriteriaDataMock;
    @Mock
    private CartModificationData cartModificationDataMock;
    @Mock
    private PlaceOrderForm placeOrderFormMock;
    @Mock
    private Model modelMock;
    @Mock
    private ContentPageModel contentPageModelMock;
    @Mock
    private CMSSiteModel cmsSiteModelMock;
    @Mock
    private CheckoutStep checkoutStepMock;
    @Mock
    private Configuration configurationMock;
    @Mock
    private HashMap<String, String> details;
    @Mock
    private PaymentResponse paymentsResponseMock;
    @Mock
    private TerminalAPIResponse terminalApiResponseMock;
    private List<CartModificationData> modifications;

    @Before
    public void setUp(){
        details = new HashMap<>(Map.of(REDIRECT_RESULT, PAYLOAD_VALUE));
        modifications = new ArrayList<>();
    }

    @Test
    public void handleRedirectPayload_shouldReturnResponse_whenRequestContainsPayload() throws Exception{
        mockElementsUsedInTestsForHandleRedirectPayload();
        
        when(requestMock.getParameter(PAYLOAD)).thenReturn(PAYLOAD_VALUE);

        final String result =testObj.handleAdyenResponse(requestMock, redirectModelMock);

        assertThat(result).isEqualTo(REDIRECT_URL_ORDER_CONFIRMATION + orderDataMock.getCode());
    }

    @Test
    public void handleRedirectPayload_shouldThrowException_whenRequestNotContainsPayload() throws Exception{
        mockElementsUsedInTestsForHandleRedirectPayload();
        
        when(requestMock.getParameter(PAYLOAD)).thenReturn(null);
        
        final String result = testObj.handleAdyenResponse(requestMock, redirectModelMock);
        
        assertThat(result).isEqualTo(REDIRECT_PREFIX + CART_PREFIX);
    }
    @Test
    public void placeOrder_shouldGoBackToStep_whenFormIsInvalid() throws CMSItemNotFoundException, CommerceCartModificationException, JsonProcessingException {
        mockElementsUsedInTestsForPlaceOrder();
        when(checkoutFlowFacadeMock.hasNoDeliveryAddress()).thenReturn(true);
        
        final String result = testObj.placeOrder(placeOrderFormMock, modelMock, requestMock, redirectModelMock);

        assertThat(result).isEqualTo(AdyenControllerConstants.Views.Pages.MultiStepCheckout.CheckoutSummaryPage);
    }

    @Test
    public void placeOrder_shouldRedirectToCart_whenFormIsValidButCartIsNot() throws CMSItemNotFoundException, CommerceCartModificationException, JsonProcessingException {
        mockElementsUsedInTestsForPlaceOrder();
        mockFormValidationOK();
        modifications.add(cartModificationDataMock);
        mockCartValidationOK();

        final String result = testObj.placeOrder(placeOrderFormMock, modelMock, requestMock, redirectModelMock);

        assertThat(result).isEqualTo(REDIRECT_PREFIX + CART_PREFIX);
    }

    @Test
    public void placeOrder_shouldGoToConfirmationPage_whenFormCartAreBothValidAndPaymentAuthorizedRatepay() throws Exception {
        mockElementsUsedInTestsForPlaceOrder();
        mockFormValidationOK();
        mockCartValidationOK();
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(RATEPAY);
        when(adyenCheckoutFacadeMock.authorisePayment(requestMock, cartDataMock)).thenReturn(orderDataMock);
        mockAnonymousCheckoutAndOrderGuid();

        final String result = testObj.placeOrder(placeOrderFormMock, modelMock, requestMock, redirectModelMock);

        assertThat(result).isEqualTo(REDIRECT_URL_ORDER_CONFIRMATION + ORDER_CODE);
    }

    @Test
    public void placeOrder_shouldGoBackToStep_whenApiExceptionIsThrownRatepay() throws Exception {
        mockElementsUsedInTestsForPlaceOrder();
        mockFormValidationOK();
        mockCartValidationOK();
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(RATEPAY);
        when(adyenCheckoutFacadeMock.authorisePayment(requestMock, cartDataMock)).thenThrow(new ApiException("", 1));

        final String result = testObj.placeOrder(placeOrderFormMock, modelMock, requestMock, redirectModelMock);

        assertThat(result).isEqualTo(AdyenControllerConstants.Views.Pages.MultiStepCheckout.CheckoutSummaryPage);
    }

    @Test
    public void placeOrder_shouldGoBackToStep_whenAnotherExceptionIsThrownRatepay() throws Exception {
        mockElementsUsedInTestsForPlaceOrder();
        mockFormValidationOK();
        mockCartValidationOK();
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(RATEPAY);
        when(adyenCheckoutFacadeMock.authorisePayment(requestMock, cartDataMock)).thenThrow(new Exception());

        final String result = testObj.placeOrder(placeOrderFormMock, modelMock, requestMock, redirectModelMock);

        assertThat(result).isEqualTo(AdyenControllerConstants.Views.Pages.MultiStepCheckout.CheckoutSummaryPage);
    }

    @Test
    public void placeOrder_shouldGoToConfirmationPage_whenFormCartAreBothValidAndPaymentAuthorizedPOS() throws Exception {
        mockElementsUsedInTestsForPlaceOrder();
        mockFormValidationOK();
        mockCartValidationOK();
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_POS);
        when(adyenCheckoutFacadeMock.initiatePosPayment(requestMock, cartDataMock)).thenReturn(orderDataMock);
        mockAnonymousCheckoutAndOrderGuid();

        final String result = testObj.placeOrder(placeOrderFormMock, modelMock, requestMock, redirectModelMock);

        assertThat(result).isEqualTo(REDIRECT_URL_ORDER_CONFIRMATION + ORDER_CODE);
    }

    @Test
    public void placeOrder_shouldGoToConfirmationPage_whenFormCartAreBothValidAndSocketTimeoutExceptionThrownButPaymentStatusIsOKPOS() throws Exception {
        mockElementsUsedInTestsForPlaceOrder();
        mockFormValidationOK();
        mockCartValidationOK();
        mockAnonymousCheckoutAndOrderGuid();
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_POS);
        when(adyenCheckoutFacadeMock.initiatePosPayment(requestMock, cartDataMock)).thenThrow(new SocketTimeoutException());
        when(configurationServiceMock.getConfiguration()).thenReturn(configurationMock);
        when(configurationMock.containsKey(POS_TOTAL_TIMEOUT_KEY)).thenReturn(true);
        when(configurationMock.getInt(POS_TOTAL_TIMEOUT_KEY)).thenReturn(130);
        when(adyenCheckoutFacadeMock.checkPosPaymentStatus(requestMock, cartDataMock)).thenReturn(orderDataMock);

        final String result = testObj.placeOrder(placeOrderFormMock, modelMock, requestMock, redirectModelMock);

        assertThat(result).isEqualTo(REDIRECT_URL_ORDER_CONFIRMATION + ORDER_CODE);
    }

    @Test
    public void placeOrder_shouldGoBackToStep_whenSocketTimeoutExceptionAfterAnotherSocketTimeoutExceptionAreThrownPOS() throws Exception {
        mockElementsUsedInTestsForPlaceOrder();
        mockFormValidationOK();
        mockCartValidationOK();
        mockAnonymousCheckoutAndOrderGuid();
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_POS);
        when(adyenCheckoutFacadeMock.initiatePosPayment(requestMock, cartDataMock)).thenThrow(new SocketTimeoutException());
        when(configurationServiceMock.getConfiguration()).thenReturn(configurationMock);
        when(configurationMock.containsKey(POS_TOTAL_TIMEOUT_KEY)).thenReturn(true);
        when(configurationMock.getInt(POS_TOTAL_TIMEOUT_KEY)).thenReturn(130);
        when(adyenCheckoutFacadeMock.checkPosPaymentStatus(requestMock, cartDataMock)).thenThrow(new SocketTimeoutException());
        final String result = testObj.placeOrder(placeOrderFormMock, modelMock, requestMock, redirectModelMock);

        assertThat(result).isEqualTo(AdyenControllerConstants.Views.Pages.MultiStepCheckout.CheckoutSummaryPage);
    }

    @Test
    public void placeOrder_shouldGoBackToStep_whenGenericExceptionAfterSocketTimeoutExceptionAreThrownPOS() throws Exception {
        mockElementsUsedInTestsForPlaceOrder();
        mockFormValidationOK();
        mockCartValidationOK();
        mockAnonymousCheckoutAndOrderGuid();
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_POS);
        when(adyenCheckoutFacadeMock.initiatePosPayment(requestMock, cartDataMock)).thenThrow(new SocketTimeoutException());
        when(configurationServiceMock.getConfiguration()).thenReturn(configurationMock);
        when(configurationMock.containsKey(POS_TOTAL_TIMEOUT_KEY)).thenReturn(true);
        when(configurationMock.getInt(POS_TOTAL_TIMEOUT_KEY)).thenReturn(130);
        when(adyenCheckoutFacadeMock.checkPosPaymentStatus(requestMock, cartDataMock)).thenThrow(new Exception());
        final String result = testObj.placeOrder(placeOrderFormMock, modelMock, requestMock, redirectModelMock);

        assertThat(result).isEqualTo(AdyenControllerConstants.Views.Pages.MultiStepCheckout.CheckoutSummaryPage);
    }

    @Test
    public void placeOrder_shouldGoBackToStep_whenApiExceptionIsThrownPOS() throws Exception {
        mockElementsUsedInTestsForPlaceOrder();
        mockFormValidationOK();
        mockCartValidationOK();
        mockAnonymousCheckoutAndOrderGuid();
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_POS);
        when(adyenCheckoutFacadeMock.initiatePosPayment(requestMock, cartDataMock)).thenThrow(new ApiException("", 1));
        final String result = testObj.placeOrder(placeOrderFormMock, modelMock, requestMock, redirectModelMock);

        assertThat(result).isEqualTo(AdyenControllerConstants.Views.Pages.MultiStepCheckout.CheckoutSummaryPage);
    }

    @Test
    public void placeOrder_shouldGoBackToStep_whenGenericExceptionIsThrownPOS() throws Exception {
        mockElementsUsedInTestsForPlaceOrder();
        mockFormValidationOK();
        mockCartValidationOK();
        mockAnonymousCheckoutAndOrderGuid();
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_POS);
        when(adyenCheckoutFacadeMock.initiatePosPayment(requestMock, cartDataMock)).thenThrow(new Exception());
        final String result = testObj.placeOrder(placeOrderFormMock, modelMock, requestMock, redirectModelMock);

        assertThat(result).isEqualTo(AdyenControllerConstants.Views.Pages.MultiStepCheckout.CheckoutSummaryPage);
    }

    @Test
    public void placeOrder_shouldGoToConfirmationPage_whenFormCartAreBothValidAndPaymentAuthorizedAnotherPaymentMethod() throws Exception {
        mockElementsUsedInTestsForPlaceOrder();
        mockFormValidationOK();
        mockCartValidationOK();
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_PAYPAL);
        when(adyenCheckoutFacadeMock.authorisePayment(requestMock, cartDataMock)).thenReturn(orderDataMock);
        mockAnonymousCheckoutAndOrderGuid();
        when(baseSiteServiceMock.getCurrentBaseSite()).thenReturn(cmsSiteModelMock);
        when(siteBaseUrlResolutionServiceMock.getWebsiteUrlForSite(anyObject(), anyBoolean(), anyString())).thenReturn(MOCK_BASESITE_URL);
        mockAnonymousCheckoutAndOrderGuid();

        final String result = testObj.placeOrder(placeOrderFormMock, modelMock, requestMock, redirectModelMock);

        assertThat(result).isEqualTo(REDIRECT_URL_ORDER_CONFIRMATION + ORDER_CODE);
    }

    @Test
    public void placeOrder_shouldGoBackToStep_whenApiExceptionIsThrownAnotherPaymentMethod() throws Exception {
        mockElementsUsedInTestsForPlaceOrder();
        mockFormValidationOK();
        mockCartValidationOK();
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_PAYPAL);
        when(adyenCheckoutFacadeMock.authorisePayment(requestMock, cartDataMock)).thenThrow(new ApiException("", 1));

        final String result = testObj.placeOrder(placeOrderFormMock, modelMock, requestMock, redirectModelMock);

        assertThat(result).isEqualTo(AdyenControllerConstants.Views.Pages.MultiStepCheckout.CheckoutSummaryPage);
    }
    
    private void mockAnonymousCheckoutAndOrderGuid() {
        when(checkoutCustomerStrategyMock.isAnonymousCheckout()).thenReturn(true);
        when(orderDataMock.getGuid()).thenReturn(ORDER_CODE);
    }

    private void mockFormValidationOK() {
        when(checkoutFlowFacadeMock.hasNoDeliveryAddress()).thenReturn(false);
        when(checkoutFlowFacadeMock.hasNoDeliveryMode()).thenReturn(false);
        when(checkoutFlowFacadeMock.hasNoPaymentInfo()).thenReturn(false);
        when(checkoutFlowFacadeMock.getSubscriptionPciOption()).thenReturn(CheckoutPciOptionEnum.HOP);
        when(placeOrderFormMock.isTermsCheck()).thenReturn(true);
        when(checkoutFacadeMock.containsTaxValues()).thenReturn(true);
        when(cartDataMock.isCalculated()).thenReturn(true);
    }

    private void mockCartValidationOK() throws CommerceCartModificationException {
        when(cartFacadeMock.validateCartData()).thenReturn(modifications);
    }

    private void mockElementsUsedInTestsForHandleRedirectPayload() throws Exception {
        when(requestMock.getParameter(PAYLOAD)).thenReturn(PAYLOAD_VALUE);
        when(responseMock.getMerchantReference()).thenReturn(MERCHANT_REFERENCE);
        when(responseMock.getResultCode()).thenReturn(PaymentDetailsResponse.ResultCodeEnum.AUTHORISED);
        when(orderDataMock.getCode()).thenReturn(ORDER_CODE);
        when(adyenCheckoutFacadeMock.handleRedirectPayload((any()))).thenReturn(responseMock);
        when(orderFacadeMock.getOrderDetailsForCodeWithoutUser(responseMock.getMerchantReference())).thenReturn(orderDataMock);
        when(checkoutCustomerStrategyMock.isAnonymousCheckout()).thenReturn(false);
    }
    private void mockElementsUsedInTestsForPlaceOrder() throws CMSItemNotFoundException {
        contentPageModelMock.setTitle(CONTENT_PAGE_MODEL_TITLE);
        contentPageModelMock.setKeywords(CONTENT_PAGE_MODEL_KEYWORDS);
        contentPageModelMock.setDescription(CONTENT_PAGE_MODEL_DESCRIPTION);

        final List<Breadcrumb> breadcrumbs = new ArrayList<>();
        final CheckoutGroup checkoutGroup = new CheckoutGroup();
        final Map<String, CheckoutGroup> checkoutGroupMap = Map.of(CHECKOUT_FLOW_GROUP_FOR_CHECKOUT_MOCK, checkoutGroup);
        final Map<String, CheckoutStep> checkoutStepMap = Map.of(CURRENT_CONTROLLER, checkoutStepMock);
        
        checkoutGroup.setCheckoutStepMap(checkoutStepMap);
        
        when(placeOrderFormMock.getSecurityCode()).thenReturn(SECURITY_CODE);
        when(checkoutFacadeMock.getCheckoutCart()).thenReturn(cartDataMock);
        when(checkoutFlowFacadeMock.getCheckoutCart()).thenReturn(cartDataMock);
        when(cartDataMock.getEntries()).thenReturn(null);
        when(cmsPageServiceMock.getHomepage(anyObject())).thenReturn(contentPageModelMock);
        when(cmsSiteServiceMock.getCurrentSite()).thenReturn(cmsSiteModelMock);
        when(cmsSiteServiceMock.getStartPageLabelOrId(cmsSiteModelMock)).thenReturn(CONTENT_PAGE_MODEL_ID);
        when(cmsPreviewServiceMock.getPagePreviewCriteria()).thenReturn(pagePreviewCriteriaDataMock);
        when(cmsPageServiceMock.getPageForLabelOrId(MULTI_CHECKOUT_SUMMARY_CMS_PAGE_LABEL_MOCK, pagePreviewCriteriaDataMock)).
                thenReturn(contentPageModelMock);
        when(pageTitleResolverMock.resolveContentPageTitle(CONTENT_PAGE_MODEL_TITLE)).thenReturn(CONTENT_PAGE_MODEL_TITLE);
        when(resourceBreadcrumbBuilderMock.getBreadcrumbs(anyObject())).thenReturn(breadcrumbs);
        when(checkoutFacadeMock.getCheckoutFlowGroupForCheckout()).thenReturn(CHECKOUT_FLOW_GROUP_FOR_CHECKOUT_MOCK);
        doReturn(checkoutGroupMap).when(testObj).getCheckoutFlowGroupMap();
        when(checkoutStepMock.previousStep()).thenReturn(REDIRECT_PREFIX);
        when(checkoutStepMock.nextStep()).thenReturn(REDIRECT_PREFIX);
        when(checkoutStepMock.currentStep()).thenReturn(REDIRECT_PREFIX);
        when(checkoutStepMock.getProgressBarId()).thenReturn(PROGRESS_BAR_ID);
    }
}
