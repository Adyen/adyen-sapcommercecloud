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
package com.adyen.v6.controllers.pages;

import com.adyen.constants.ApiConstants.RefusalReason;
import com.adyen.model.checkout.CheckoutRedirectAction;
import com.adyen.model.checkout.PaymentCompletionDetails;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.checkout.PaymentResponseAction;
import com.adyen.model.payment.PaymentResult;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.AdyenControllerConstants;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.adyen.v6.util.AdyenUtil;
import com.adyen.v6.util.TerminalAPIUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.hybris.platform.acceleratorservices.enums.CheckoutPciOptionEnum;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.PreValidateCheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.checkout.steps.CheckoutStep;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.checkout.steps.AbstractCheckoutStepController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.acceleratorstorefrontcommons.forms.PlaceOrderForm;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commercefacades.order.data.CCPaymentInfoData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.ProductOption;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.site.BaseSiteService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpHeaders;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static com.adyen.model.checkout.PaymentResponse.ResultCodeEnum.CHALLENGESHOPPER;
import static com.adyen.model.checkout.PaymentResponse.ResultCodeEnum.ERROR;
import static com.adyen.model.checkout.PaymentResponse.ResultCodeEnum.IDENTIFYSHOPPER;
import static com.adyen.model.checkout.PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER;
import static com.adyen.model.checkout.PaymentResponse.ResultCodeEnum.REFUSED;
import static com.adyen.v6.constants.AdyenControllerConstants.CART_PREFIX;
import static com.adyen.v6.constants.AdyenControllerConstants.SELECT_PAYMENT_METHOD_PREFIX;
import static com.adyen.v6.constants.AdyenControllerConstants.SUMMARY_CHECKOUT_PREFIX;
import static com.adyen.v6.constants.Adyenv6coreConstants.AFTERPAY_TOUCH;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_APPLEPAY;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_BCMC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_BOLETO;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_MULTIBANCO;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_ONECLICK;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_POS;
import static com.adyen.v6.constants.Adyenv6coreConstants.RATEPAY;
import static com.adyen.v6.constants.Adyenv6coreConstants.SHOPPER_LOCALE;
import static com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade.MODEL_CHECKOUT_SHOPPER_HOST;
import static com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade.MODEL_CLIENT_KEY;
import static com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade.MODEL_ENVIRONMENT_MODE;

@Controller
@RequestMapping(value = SUMMARY_CHECKOUT_PREFIX)
@SuppressWarnings("java:S3776")
public class AdyenSummaryCheckoutStepController extends AbstractCheckoutStepController {
    private static final Logger LOGGER = Logger.getLogger(AdyenSummaryCheckoutStepController.class);

    private static final String SUMMARY = "summary";
    private static final String AUTHORISE_3D_SECURE_PAYMENT_URL = "/authorise-3d-adyen-response";
    protected static final String CHECKOUT_RESULT_URL = "/checkout-adyen-response";
    private static final String REDIRECT_RESULT = "redirectResult";
    private static final String ACTION = "action";
    private static final String PAYLOAD = "payload";
    private static final String POS_TOTALTIMEOUT_KEY = "pos.totaltimeout";
    private static final String CHECKOUT_ERROR_AUTHORIZATION_FAILED = "checkout.error.authorization.failed";
    private static final String PAYMENT_NOT_SUPPORTED = "checkout.error.payment.not.supported";
    private static final String CART_NOT_VALID = "checkout.error.cart.not.valid";
    private static final String REDIRECTING_TO_CONFIRMATION = "Redirecting to confirmation!";
    private static final String API_EXCEPTION_START_MESSAGE = "API exception ";
    private static final String HANDLING_ADYEN_NON_AUTHORIZED_PAYMENT_EXCEPTION = "Handling AdyenNonAuthorizedPaymentException";
    private static final String REDIRECTING_TO_CART_PAGE = "Redirecting to cart page...";
    private static final String CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_REFUSED = "checkout.error.authorization.payment.refused";
    private static final String CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_CANCELLED = "checkout.error.authorization.payment.cancelled";
    private static final String CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_ERROR = "checkout.error.authorization.payment.error";
    private static final int POS_TOTAL_TIMEOUT_DEFAULT = 130;
    private static final String NON_AUTHORIZED_ERROR = "Handling AdyenNonAuthorizedPaymentException. Checking PaymentResponse.";

    @Resource(name = "siteBaseUrlResolutionService")
    private SiteBaseUrlResolutionService siteBaseUrlResolutionService;

    @Resource(name = "baseSiteService")
    private BaseSiteService baseSiteService;

    @Resource(name = "adyenCheckoutFacade")
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Resource(name = "orderFacade")
    private OrderFacade orderFacade;

    @Resource(name = "configurationService")
    private ConfigurationService configurationService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @GetMapping(value = "/view")
    @RequireHardLogIn
    @Override
    @PreValidateCheckoutStep(checkoutStep = SUMMARY)
    public String enterStep(final Model model, final RedirectAttributes redirectAttributes) throws CMSItemNotFoundException, // NOSONAR
            CommerceCartModificationException {

        final CartData cartData = getCheckoutFacade().getCheckoutCart();
        if (cartData.getEntries() != null && !cartData.getEntries().isEmpty()) {
            for (final OrderEntryData entry : cartData.getEntries()) {
                final String productCode = entry.getProduct().getCode();
                final ProductData product = getProductFacade().getProductForCodeAndOptions(productCode, Arrays.asList(ProductOption.BASIC, ProductOption.PRICE));
                entry.setProduct(product);
            }
        }

        model.addAttribute("cartData", cartData);
        model.addAttribute("allItems", cartData.getEntries());
        model.addAttribute("deliveryAddress", cartData.getDeliveryAddress());
        model.addAttribute("deliveryMode", cartData.getDeliveryMode());

        model.addAttribute(new PlaceOrderForm());

        storeCmsPageInModel(model, getContentPageForLabelOrId(MULTI_CHECKOUT_SUMMARY_CMS_PAGE_LABEL));
        setUpMetaDataForContentPage(model, getContentPageForLabelOrId(MULTI_CHECKOUT_SUMMARY_CMS_PAGE_LABEL));
        model.addAttribute(WebConstants.BREADCRUMBS_KEY, getResourceBreadcrumbBuilder().getBreadcrumbs("checkout.multi.summary.breadcrumb"));
        model.addAttribute("metaRobots", "noindex,nofollow");
        setCheckoutStepLinksForModel(model, getCheckoutStep());

        try {
            adyenCheckoutFacade.initializeSummaryData(model);
        } catch (ApiException e) {
            LOGGER.error("Initialize summary data failed. ", e);

        }

        return AdyenControllerConstants.Views.Pages.MultiStepCheckout.CheckoutSummaryPage;
    }

    protected String getCountryCode(final CartData cartData) {
        //Identify country code based on shopper's delivery address
        return Optional.ofNullable(cartData.getPaymentInfo())
                .map(CCPaymentInfoData::getBillingAddress)
                .map(billingAddress -> Optional.ofNullable(billingAddress).or(() -> Optional.ofNullable(cartData.getDeliveryAddress())))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(AddressData::getCountry)
                .map(CountryData::getIsocode)
                .orElse("");
    }

    @PostMapping({"/placeOrder"})
    @RequireHardLogIn
    public String placeOrder(@ModelAttribute("placeOrderForm") final PlaceOrderForm placeOrderForm,
                             final Model model,
                             final HttpServletRequest request,
                             final RedirectAttributes redirectModel) throws CMSItemNotFoundException, CommerceCartModificationException, JsonProcessingException {
        if (validateOrderForm(placeOrderForm, model)) {
            return enterStep(model, redirectModel);
        }

        //Validate the cart
        if (validateCart(redirectModel)) {
            // Invalid cart. Bounce back to the cart page.
            return REDIRECT_PREFIX + CART_PREFIX;
        }

        final CartData cartData = getCheckoutFlowFacade().getCheckoutCart();

        String errorMessage = CHECKOUT_ERROR_AUTHORIZATION_FAILED;

        String adyenPaymentMethod = cartData.getAdyenPaymentMethod();
        if (adyenPaymentMethod.equals(RATEPAY)) {
            try {
                OrderData orderData = adyenCheckoutFacade.authorisePayment(request, cartData);
                LOGGER.debug(REDIRECTING_TO_CONFIRMATION);
                return redirectToOrderConfirmationPage(orderData);
            } catch (ApiException e) {
                LOGGER.error(API_EXCEPTION_START_MESSAGE + e.getError(), e);
            } catch (AdyenNonAuthorizedPaymentException e) {
                LOGGER.info(HANDLING_ADYEN_NON_AUTHORIZED_PAYMENT_EXCEPTION);
                PaymentResult paymentResult = e.getPaymentResult();
                if (Objects.nonNull(paymentResult)) {
                    if (PaymentResult.ResultCodeEnum.REFUSED.equals(paymentResult.getResultCode())) {
                        errorMessage = getErrorMessageByRefusalReason(paymentResult.getRefusalReason());
                        LOGGER.info("Payment " + paymentResult.getPspReference() + " is refused " + errorMessage);
                    }
                    if (PaymentResult.ResultCodeEnum.ERROR.equals(paymentResult.getResultCode())) {
                        LOGGER.error("Payment " + paymentResult.getPspReference() + " result is error, reason:  "
                                + paymentResult.getRefusalReason());
                    }
                }

            } catch (Exception e) {
                LOGGER.error(ExceptionUtils.getStackTrace(e));
            }
        } else if (PAYMENT_METHOD_POS.equals(adyenPaymentMethod)) {
            try {
                String originalServiceId = Long.toString(System.currentTimeMillis() % 10000000000L);
                request.setAttribute("originalServiceId", originalServiceId);
                Long paymentStartTime = System.currentTimeMillis();
                request.setAttribute("paymentStartTime", paymentStartTime);
                OrderData orderData = adyenCheckoutFacade.initiatePosPayment(request, cartData);
                LOGGER.debug(REDIRECTING_TO_CONFIRMATION);
                return redirectToOrderConfirmationPage(orderData);
            } catch (SocketTimeoutException e) {
                try {
                    LOGGER.debug("POS request timed out. Checking POS Payment status ");
                    int totalTimeout = POS_TOTAL_TIMEOUT_DEFAULT;
                    if (configurationService.getConfiguration().containsKey(POS_TOTALTIMEOUT_KEY)) {
                        totalTimeout = configurationService.getConfiguration().getInt(POS_TOTALTIMEOUT_KEY);
                    }
                    request.setAttribute("totalTimeout", totalTimeout);
                    OrderData orderData = adyenCheckoutFacade.checkPosPaymentStatus(request, cartData);
                    LOGGER.debug(REDIRECTING_TO_CONFIRMATION);
                    return redirectToOrderConfirmationPage(orderData);
                } catch (AdyenNonAuthorizedPaymentException nx) {
                    errorMessage = TerminalAPIUtil.getErrorMessageForNonAuthorizedPosPayment(nx.getTerminalApiResponse());
                    LOGGER.warn("AdyenNonAuthorizedPaymentException " + errorMessage + " pspReference: " + nx.getPaymentResult().getPspReference());
                } catch (SocketTimeoutException to) {
                    LOGGER.error("POS Status request timed out. Returning error message.");
                    errorMessage = "checkout.error.authorization.pos.configuration";
                } catch (Exception ex) {
                    LOGGER.error("Exception", ex);
                }
            } catch (ApiException e) {
                LOGGER.error(API_EXCEPTION_START_MESSAGE + e.getError(), e);
            } catch (AdyenNonAuthorizedPaymentException e) {
                errorMessage = TerminalAPIUtil.getErrorMessageForNonAuthorizedPosPayment(e.getTerminalApiResponse());
                LOGGER.warn("AdyenNonAuthorizedPaymentException" + errorMessage + " pspReference: " + e.getPaymentResult().getPspReference());
            } catch (Exception e) {
                LOGGER.error("Exception", e);
            }
        } else {
            try {
                cartData.setAdyenReturnUrl(getReturnUrl(cartData.getAdyenPaymentMethod()));
                OrderData orderData = adyenCheckoutFacade.authorisePayment(request, cartData);
                //In case of Boleto, show link to pdf
                if (PAYMENT_METHOD_BOLETO.equals(cartData.getAdyenPaymentMethod())) {
                    addBoletoMessage(redirectModel, orderData.getCode());
                } else if (PAYMENT_METHOD_MULTIBANCO.equals(cartData.getAdyenPaymentMethod())) {
                    addMultibancoMessage(redirectModel, orderData.getCode());
                }
                return redirectToOrderConfirmationPage(orderData);
            } catch (ApiException e) {
                LOGGER.error(API_EXCEPTION_START_MESSAGE, e);
            } catch (AdyenNonAuthorizedPaymentException e) {
                LOGGER.info(NON_AUTHORIZED_ERROR);
                PaymentResponse paymentsResponse = e.getPaymentsResponse();
                if (REDIRECTSHOPPER == paymentsResponse.getResultCode()) {
                    if (is3DSPaymentMethod(adyenPaymentMethod)) {
                        LOGGER.debug("PaymentResponse resultCode is REDIRECTSHOPPER, redirecting shopper to 3DS flow");
                        return redirectTo3DSValidation(model, paymentsResponse, cartData);
                    }
                    if (AFTERPAY_TOUCH.equals(adyenPaymentMethod)) {
                        LOGGER.debug("PaymentResponse resultCode is REDIRECTSHOPPER, redirecting shopper to afterpaytouch page");

                        return REDIRECT_PREFIX + ((CheckoutRedirectAction) paymentsResponse.getAction().getActualInstance()).getUrl();
                    }
                    LOGGER.debug("PaymentResponse resultCode is REDIRECTSHOPPER, redirecting shopper to local payment method page");
                    return REDIRECT_PREFIX + ((CheckoutRedirectAction) paymentsResponse.getAction().getActualInstance()).getUrl();
                }
                if (REFUSED == paymentsResponse.getResultCode()) {
                    LOGGER.info("PaymentResponse is REFUSED, pspReference: " + paymentsResponse.getPspReference());
                    errorMessage = getErrorMessageByRefusalReason(paymentsResponse.getRefusalReason());
                }
                if (CHALLENGESHOPPER == paymentsResponse.getResultCode() || IDENTIFYSHOPPER == paymentsResponse.getResultCode()) {
                    LOGGER.debug("PaymentResponse is " + paymentsResponse.getResultCode() + ", redirecting to 3DS2 flow");
                    return redirectTo3DSValidation(model, paymentsResponse);
                }
                if (ERROR == paymentsResponse.getResultCode()) {
                    LOGGER.error("PaymentResponse is ERROR, reason: " + paymentsResponse.getRefusalReason() + " pspReference: " + paymentsResponse.getPspReference());
                }
            } catch (Exception e) {
                LOGGER.error(ExceptionUtils.getStackTrace(e));
            }
        }
        LOGGER.debug("Redirecting to summary view");
        GlobalMessages.addErrorMessage(model, errorMessage);
        return enterStep(model, redirectModel);
    }

    @GetMapping(value = AUTHORISE_3D_SECURE_PAYMENT_URL)
    @RequireHardLogIn
    public String authorise3DS1Payment(final RedirectAttributes redirectModel,
                                       final HttpServletRequest request)  throws IOException, ApiException {
        try {
            String redirectResult = request.getParameter(REDIRECT_RESULT);
            PaymentDetailsRequest paymentDetailsRequest = new PaymentDetailsRequest();
            if (redirectResult != null && !redirectResult.isEmpty()) {
                PaymentCompletionDetails details = new PaymentCompletionDetails();
                details.setRedirectResult(redirectResult);
                paymentDetailsRequest.details(details);
            } else {
                String payload = request.getParameter(PAYLOAD);
                if (payload != null && !payload.isEmpty()) {
                    PaymentCompletionDetails details = new PaymentCompletionDetails();
                    details.setPayload(payload);
                    paymentDetailsRequest.details(details);
                }
            }
            OrderData orderData = adyenCheckoutFacade.handle3DSResponse(paymentDetailsRequest);
            LOGGER.debug("Redirecting to confirmation");
            return redirectToOrderConfirmationPage(orderData);
        } catch (AdyenNonAuthorizedPaymentException e) {
            LOGGER.info(HANDLING_ADYEN_NON_AUTHORIZED_PAYMENT_EXCEPTION);
            String errorMessage = CHECKOUT_ERROR_AUTHORIZATION_FAILED;
            PaymentDetailsResponse response = e.getPaymentsDetailsResponse();
            if (response != null) {
                if (PaymentDetailsResponse.ResultCodeEnum.REFUSED.equals(response.getResultCode())) {
                    LOGGER.info("PaymentResponse " + response.getPspReference() + " is REFUSED: " + response);
                    errorMessage = getErrorMessageByRefusalReason(response.getRefusalReason());
                }
                if (PaymentDetailsResponse.ResultCodeEnum.ERROR.equals(response.getResultCode())) {
                    LOGGER.error("Payment " + response.getPspReference() + " result is error, reason:  "
                            + response.getRefusalReason());
                }
            }
            LOGGER.debug("Redirecting to select payment method..");
            return redirectToSelectPaymentMethodWithError(redirectModel, errorMessage);
        } catch (CalculationException | InvalidCartException e) {
            LOGGER.error(e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }

        LOGGER.warn(REDIRECTING_TO_CART_PAGE);
        return REDIRECT_PREFIX + CART_PREFIX;
    }

    @PostMapping(value = AUTHORISE_3D_SECURE_PAYMENT_URL)
    @RequireHardLogIn
    public String authorise3DSPayment(final RedirectAttributes redirectModel,
                                      final HttpServletRequest request) {
        PaymentDetailsRequest paymentDetailsRequest = new PaymentDetailsRequest();
        try {
            OrderData orderData = adyenCheckoutFacade.handle3DSResponse(paymentDetailsRequest);
            LOGGER.debug("Redirecting to confirmation");
            return redirectToOrderConfirmationPage(orderData);
        } catch (AdyenNonAuthorizedPaymentException e) {
            LOGGER.debug(NON_AUTHORIZED_ERROR);
            String errorMessage = CHECKOUT_ERROR_AUTHORIZATION_FAILED;
            PaymentDetailsResponse response = e.getPaymentsDetailsResponse();
            if (response != null) {
                if (REFUSED.equals(response.getResultCode())) {
                    LOGGER.info("PaymentResponse " + response.getPspReference() + " is REFUSED: " + response);
                    errorMessage = getErrorMessageByRefusalReason(response.getRefusalReason());
                }
                if (ERROR.equals(response.getResultCode())) {
                    LOGGER.error("Payment " + response.getPspReference() + " result is error, reason:  "
                            + response.getRefusalReason());
                }
            }
            return redirectToSelectPaymentMethodWithError(redirectModel, errorMessage);
        } catch (CalculationException | InvalidCartException e) {
            LOGGER.warn(e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }

        LOGGER.warn(REDIRECTING_TO_CART_PAGE);
        return REDIRECT_PREFIX + CART_PREFIX;
    }

    @GetMapping(value = CHECKOUT_RESULT_URL)
    @RequireHardLogIn
    public String handleAdyenResponse(final HttpServletRequest request, final RedirectAttributes redirectModel) {
        String redirectResult = request.getParameter(REDIRECT_RESULT);
        PaymentCompletionDetails details = new PaymentCompletionDetails();

        if (redirectResult != null && !redirectResult.isEmpty()) {
            details.setRedirectResult(redirectResult);
        } else if (StringUtils.isNotEmpty(request.getParameter(PAYLOAD))) {
            details.setPayload(request.getParameter(PAYLOAD));
        }

        try {
            PaymentDetailsResponse response = adyenCheckoutFacade.handleRedirectPayload(details);

            switch (response.getResultCode()) {
                case AUTHORISED, RECEIVED:
                    LOGGER.debug("Redirecting to order confirmation");
                    OrderData orderData = orderFacade.getOrderDetailsForCodeWithoutUser(response.getMerchantReference());
                    if (orderData == null) {
                        LOGGER.error("Order " + response.getMerchantReference() + " not found");
                        throw new Exception("Order not found");
                    }
                    return redirectToOrderConfirmationPage(orderData);
                case REFUSED:
                    LOGGER.info("PaymentResponse " + response.getPspReference() + " is REFUSED");
                    return redirectToSelectPaymentMethodWithError(redirectModel, CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_REFUSED);
                case CANCELLED:
                    LOGGER.info("PaymentResponse " + response.getPspReference() + " is CANCELLED");
                    return redirectToSelectPaymentMethodWithError(redirectModel, CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_CANCELLED);
                default:
                    LOGGER.error("PaymentResponse " + response.getPspReference() + " - error occurred");
                    return redirectToSelectPaymentMethodWithError(redirectModel, CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_ERROR);
            }
        } catch (CalculationException | InvalidCartException e) {
            LOGGER.warn(e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }

        LOGGER.warn(REDIRECTING_TO_CART_PAGE);
        return REDIRECT_PREFIX + CART_PREFIX;
    }

    /**
     * Adds a flash message containing the Boleto pdf
     */
    private void addBoletoMessage(RedirectAttributes redirectModel, final String orderCode) {
        //Use OrderFacade to force execution of AbstractOrder populators
        OrderData orderData = orderFacade.getOrderDetailsForCodeWithoutUser(orderCode);

        GlobalMessages.addFlashMessage(redirectModel,
                GlobalMessages.INFO_MESSAGES_HOLDER,
                "Boleto PDf: <a target=\"_blank\" href=\"" + orderData.getAdyenBoletoUrl() + "\" title=\"Boleto PDF\">Download</a>");
    }


    /**
     * Adds a flash message containing the Multibanco response fields
     */
    private void addMultibancoMessage(RedirectAttributes redirectModel, final String orderCode) {
        //Use OrderFacade to force execution of AbstractOrder populators

        OrderData orderData = orderFacade.getOrderDetailsForCodeWithoutUser(orderCode);

        GlobalMessages.addFlashMessage(redirectModel,
                GlobalMessages.INFO_MESSAGES_HOLDER,
                "<p> Multibanco order summary "
                        + "</p>"
                        + "<p> Amount: "
                        + orderData.getAdyenMultibancoAmount()
                        + "</p>"
                        + "<p> Entity: "
                        + orderData.getAdyenMultibancoEntity()
                        + "</p>"
                        + "<p> Deadline: "
                        + orderData.getAdyenMultibancoDeadline()
                        + "</p>"
                        + "<p> Reference: "
                        + orderData.getAdyenMultibancoReference()
                        + "</p>");
    }


    private boolean is3DSPaymentMethod(String adyenPaymentMethod) {
        return adyenPaymentMethod.equals(PAYMENT_METHOD_CC) || adyenPaymentMethod.equals(PAYMENT_METHOD_BCMC) || AdyenUtil.isOneClick(adyenPaymentMethod);
    }

    private String redirectToSelectPaymentMethodWithError(final RedirectAttributes redirectModel, final String messageKey) {
        GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, messageKey);

        final CartData cartData = getCheckoutFacade().getCheckoutCart();
        if (cartData == null || cartData.getDeliveryAddress() == null) {
            LOGGER.debug("Redirecting to cart with error: " + messageKey);
            return REDIRECT_PREFIX + CART_PREFIX;
        }

        LOGGER.debug("Redirecting to payment method with error: " + messageKey);
        return REDIRECT_PREFIX + SELECT_PAYMENT_METHOD_PREFIX;
    }

    protected String redirectTo3DSValidation(Model model, PaymentResponse paymentsResponse, CartData cartData) throws JsonProcessingException {
        PaymentResponseAction action = paymentsResponse.getAction();
        model.addAttribute(MODEL_CLIENT_KEY, adyenCheckoutFacade.getClientKey());
        model.addAttribute(MODEL_CHECKOUT_SHOPPER_HOST, adyenCheckoutFacade.getCheckoutShopperHost());
        model.addAttribute(MODEL_ENVIRONMENT_MODE, adyenCheckoutFacade.getEnvironmentMode());
        model.addAttribute(SHOPPER_LOCALE, adyenCheckoutFacade.getShopperLocale());
        model.addAttribute("countryCode", getCountryCode(cartData));
        model.addAttribute(ACTION, ( ((CheckoutRedirectAction) action.getActualInstance()).toJson()));
        return AdyenControllerConstants.Views.Pages.MultiStepCheckout.Validate3DSPaymentPage;
    }

    protected String redirectTo3DSValidation(Model model, PaymentResponse paymentsResponse) throws JsonProcessingException {
        PaymentResponseAction action = paymentsResponse.getAction();
        model.addAttribute(MODEL_CLIENT_KEY, adyenCheckoutFacade.getClientKey());
        model.addAttribute(MODEL_CHECKOUT_SHOPPER_HOST, adyenCheckoutFacade.getCheckoutShopperHost());
        model.addAttribute(MODEL_ENVIRONMENT_MODE, adyenCheckoutFacade.getEnvironmentMode());
        model.addAttribute(SHOPPER_LOCALE, adyenCheckoutFacade.getShopperLocale());
        model.addAttribute(ACTION, ( ((CheckoutRedirectAction) action.getActualInstance()).toJson()));
        return AdyenControllerConstants.Views.Pages.MultiStepCheckout.Validate3DSPaymentPage;
    }

    private String getReturnUrl(String adyenPaymentMethod) {
        String url;
        if (is3DSPaymentMethod(adyenPaymentMethod)) {
            url = SUMMARY_CHECKOUT_PREFIX + AUTHORISE_3D_SECURE_PAYMENT_URL;
        } else {
            url = SUMMARY_CHECKOUT_PREFIX + CHECKOUT_RESULT_URL;
        }
        BaseSiteModel currentBaseSite = baseSiteService.getCurrentBaseSite();

        return siteBaseUrlResolutionService.getWebsiteUrlForSite(currentBaseSite, true, url);
    }

    protected String getErrorMessageByRefusalReason(String refusalReason) {
        String errorMessage = CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_REFUSED;
        if (refusalReason != null) {
            switch (refusalReason) {
                case RefusalReason.TRANSACTION_NOT_PERMITTED:
                    errorMessage = "checkout.error.authorization.transaction.not.permitted";
                    break;
                case RefusalReason.CVC_DECLINED:
                    errorMessage = "checkout.error.authorization.cvc.declined";
                    break;
                case RefusalReason.RESTRICTED_CARD:
                    errorMessage = "checkout.error.authorization.restricted.card";
                    break;
                case RefusalReason.PAYMENT_DETAIL_NOT_FOUND:
                    errorMessage = "checkout.error.authorization.payment.detail.not.found";
                    break;
                default:
                    errorMessage = CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_REFUSED;
            }
        }
        return errorMessage;
    }

    /**
     * Validates the order form before to filter out invalid order states
     *
     * @param placeOrderForm The spring form of the order being submitted
     * @param model          A spring Model
     * @return True if the order form is invalid and false if everything is valid.
     */
    protected boolean validateOrderForm(final PlaceOrderForm placeOrderForm, final Model model) {
        final String securityCode = placeOrderForm.getSecurityCode();
        boolean invalid = false;

        if (getCheckoutFlowFacade().hasNoDeliveryAddress()) {
            GlobalMessages.addErrorMessage(model, "checkout.deliveryAddress.notSelected");
            invalid = true;
        }

        if (getCheckoutFlowFacade().hasNoDeliveryMode()) {
            GlobalMessages.addErrorMessage(model, "checkout.deliveryMethod.notSelected");
            invalid = true;
        }

        if (getCheckoutFlowFacade().hasNoPaymentInfo()) {
            GlobalMessages.addErrorMessage(model, "checkout.paymentMethod.notSelected");
            invalid = true;
        } else {
            // Only require the Security Code to be entered on the summary page if the SubscriptionPciOption is set to Default.
            if (CheckoutPciOptionEnum.DEFAULT.equals(getCheckoutFlowFacade().getSubscriptionPciOption()) && StringUtils.isBlank(securityCode)) {
                GlobalMessages.addErrorMessage(model, "checkout.paymentMethod.noSecurityCode");
                invalid = true;
            }
        }

        if (!placeOrderForm.isTermsCheck()) {
            GlobalMessages.addErrorMessage(model, "checkout.error.terms.not.accepted");
            invalid = true;
            return invalid;
        }
        final CartData cartData = getCheckoutFacade().getCheckoutCart();

        if (!getCheckoutFacade().containsTaxValues()) {
            LOGGER.error(String.format("Cart %s does not have any tax values, which means the tax cacluation was not properly done, placement of order can't continue", cartData.getCode()));
            GlobalMessages.addErrorMessage(model, "checkout.error.tax.missing");
            invalid = true;
        }

        if (!cartData.isCalculated()) {
            LOGGER.error(String.format("Cart %s has a calculated flag of FALSE, placement of order can't continue", cartData.getCode()));
            GlobalMessages.addErrorMessage(model, "checkout.error.cart.notcalculated");
            invalid = true;
        }

        return invalid;
    }

    protected String validateOrderFormJson(final PlaceOrderForm placeOrderForm) {
        final String securityCode = placeOrderForm.getSecurityCode();

        if (getCheckoutFlowFacade().hasNoDeliveryAddress()) {
            return "checkout.deliveryAddress.notSelected";
        }

        if (getCheckoutFlowFacade().hasNoDeliveryMode()) {
            return "checkout.deliveryMethod.notSelected";
        }

        if (getCheckoutFlowFacade().hasNoPaymentInfo()) {
            return "checkout.paymentMethod.notSelected";

        } else {
            // Only require the Security Code to be entered on the summary page if the SubscriptionPciOption is set to Default.
            if (CheckoutPciOptionEnum.DEFAULT.equals(getCheckoutFlowFacade().getSubscriptionPciOption()) && StringUtils.isBlank(securityCode)) {
                return "checkout.paymentMethod.noSecurityCode";
            }
        }

        if (!placeOrderForm.isTermsCheck()) {
            return "checkout.error.terms.not.accepted";
        }

        final CartData cartData = getCheckoutFacade().getCheckoutCart();

        if (!getCheckoutFacade().containsTaxValues()) {
            LOGGER.error(String.format("Cart %s does not have any tax values, which means the tax cacluation was not properly done, placement of order can't continue", cartData.getCode()));
            return "checkout.error.tax.missing";

        }

        if (!cartData.isCalculated()) {
            LOGGER.error(String.format("Cart %s has a calculated flag of FALSE, placement of order can't continue", cartData.getCode()));
            return "checkout.error.cart.notcalculated";

        }

        return StringUtils.EMPTY;
    }

    @PostMapping(value = "/component-result-express")
    public String handleComponentResultExpress(final HttpServletRequest request, final RedirectAttributes redirectAttributes,
                                               @RequestHeader(value = HttpHeaders.REFERER, required = false) final String referrer) throws Exception {
        String resultCode = request.getParameter("resultCode");
        String merchantReference = request.getParameter("merchantReference");
        String isResultError = request.getParameter("isResultError");

        LOGGER.debug("isResultError=" + isResultError + "\nresultData=" + resultCode);

        if (isValidResult(resultCode, isResultError)) {
            try {
                OrderData orderData = adyenCheckoutFacade.handleComponentResult(resultCode,merchantReference);
                return redirectToOrderConfirmationPage(orderData);
            } catch (AdyenNonAuthorizedPaymentException e) {
                GlobalMessages.addFlashMessage(redirectAttributes, GlobalMessages.ERROR_MESSAGES_HOLDER, CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_ERROR);
                return "redirect:" + referrer;
            }
        }

        GlobalMessages.addFlashMessage(redirectAttributes, GlobalMessages.ERROR_MESSAGES_HOLDER, CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_ERROR);
        return "redirect:" + referrer;
    }


    @PostMapping(value = "/component-result")
    @RequireHardLogIn
    public String handleComponentResult(final HttpServletRequest request,
                                        final Model model,
                                        final RedirectAttributes redirectAttributes)
            throws CMSItemNotFoundException, InvalidCartException, CalculationException, CommerceCartModificationException, JsonProcessingException {
        String resultCode = request.getParameter("resultCode");
        String merchantReference = request.getParameter("merchantReference");
        String isResultError = request.getParameter("isResultError");

        LOGGER.debug("isResultError=" + isResultError + "\nresultData=" + resultCode);

        String errorMessageKey = CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_ERROR;

        if (isValidResult(resultCode, isResultError)) {
            try {
                OrderData orderData = adyenCheckoutFacade.handleComponentResult(resultCode,merchantReference);
                return redirectToOrderConfirmationPage(orderData);
            } catch (AdyenNonAuthorizedPaymentException e) {
                LOGGER.debug(HANDLING_ADYEN_NON_AUTHORIZED_PAYMENT_EXCEPTION);
                PaymentResponse paymentsResponse = e.getPaymentsResponse();
                if (paymentsResponse != null && paymentsResponse.getResultCode() != null) {
                    switch (paymentsResponse.getResultCode()) {
                        case REDIRECTSHOPPER:
                            LOGGER.debug("Component PaymentResponse resultCode is REDIRECTSHOPPER, redirecting shopper to 3DS flow");
                            return redirectTo3DSValidation(model, paymentsResponse);
                        case REFUSED:
                            LOGGER.info("PaymentResponse " + paymentsResponse.getPspReference() + " is REFUSED");
                            errorMessageKey = CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_REFUSED;
                            break;
                        case CANCELLED:
                            LOGGER.info("PaymentResponse " + paymentsResponse.getPspReference() + " is CANCELLED");
                            errorMessageKey = CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_CANCELLED;
                            break;
                        default:
                            LOGGER.error("PaymentResponse " + paymentsResponse.getPspReference() + " - error occurred");
                            break;
                    }
                }
            } catch (InvalidCartException e) {
                LOGGER.error("Error retrieving order", e);
            } catch (Exception e) {
                LOGGER.error("Unexpected error while validating component payment result", e);
            }
        } else {
            if (StringUtils.isNotBlank(resultCode)) {
                errorMessageKey = resultCode;
                adyenCheckoutFacade.restoreCartFromOrderCodeInSession();
            }
        }

        return redirectToOrderSummaryWithError(model, redirectAttributes, errorMessageKey);
    }

    private String redirectToOrderSummaryWithError(final Model model,
                                                   final RedirectAttributes redirectAttributes,
                                                   final String messageKey) throws CommerceCartModificationException, CMSItemNotFoundException {
        final CartData cartData = getCheckoutFacade().getCheckoutCart();
        if (cartData == null || cartData.getAdyenPaymentMethod() == null
                || PAYMENT_METHOD_APPLEPAY.equals(cartData.getAdyenPaymentMethod())) {
            return redirectToSelectPaymentMethodWithError(redirectAttributes, messageKey);
        }

        LOGGER.debug("Redirecting to summary view with error: " + messageKey);
        GlobalMessages.addErrorMessage(model, messageKey);
        return enterStep(model, redirectAttributes);
    }

    private boolean isValidResult(String resultCode, String isResultError) {
        return (StringUtils.isBlank(isResultError) || !Boolean.parseBoolean(isResultError))
                && StringUtils.isNotBlank(resultCode);
    }

    @GetMapping(value = "/back")
    @RequireHardLogIn
    @Override
    public String back(final RedirectAttributes redirectAttributes) {
        return getCheckoutStep().previousStep();
    }

    @GetMapping(value = "/next")
    @RequireHardLogIn
    @Override
    public String next(final RedirectAttributes redirectAttributes) {
        return getCheckoutStep().nextStep();
    }

    protected CheckoutStep getCheckoutStep() {
        return getCheckoutStep(SUMMARY);
    }
}
