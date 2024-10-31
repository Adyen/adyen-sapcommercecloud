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
package com.adyen.v6.facades.impl;


import com.adyen.commerce.data.PaymentMethodsCartData;
import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.CreateCheckoutSessionResponse;
import com.adyen.model.checkout.PaymentCompletionDetails;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.PaymentMethod;
import com.adyen.model.checkout.PaymentMethodsResponse;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.checkout.PaymentResponseAction;
import com.adyen.model.checkout.StoredPaymentMethod;
import com.adyen.model.nexo.ErrorConditionType;
import com.adyen.model.nexo.ResultType;
import com.adyen.model.recurring.Recurring;
import com.adyen.model.terminal.TerminalAPIResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.controllers.dtos.PaymentResultDTO;
import com.adyen.v6.converters.PosPaymentResponseConverter;
import com.adyen.v6.dto.CheckoutConfigDTO;
import com.adyen.v6.dto.CheckoutConfigDTOBuilder;
import com.adyen.v6.enums.AdyenCardTypeEnum;
import com.adyen.v6.enums.AdyenRegions;
import com.adyen.v6.enums.RecurringContractMode;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.adyen.v6.facades.AdyenExpressCheckoutFacade;
import com.adyen.v6.facades.AdyenOrderFacade;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.forms.AddressForm;
import com.adyen.v6.forms.AdyenPaymentForm;
import com.adyen.v6.forms.validation.AdyenPaymentFormValidator;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.repository.OrderRepository;
import com.adyen.v6.service.AdyenBusinessProcessService;
import com.adyen.v6.service.AdyenCheckoutApiService;
import com.adyen.v6.service.AdyenOrderService;
import com.adyen.v6.service.AdyenTransactionService;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;
import com.adyen.v6.util.AmountUtil;
import com.adyen.v6.util.TerminalAPIUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import de.hybris.platform.commercefacades.i18n.I18NFacade;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.user.data.RegionData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.commercewebservicescommons.dto.order.PaymentDetailsWsDTO;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.c2l.CountryModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.TitleModel;
import de.hybris.platform.deliveryzone.model.ZoneDeliveryModeValueModel;
import de.hybris.platform.order.CalculationService;
import de.hybris.platform.order.CartFactory;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.keygenerator.KeyGenerator;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.adyen.constants.ApiConstants.ThreeDS2Property.THREEDS2_CHALLENGE_TOKEN;
import static com.adyen.constants.ApiConstants.ThreeDS2Property.THREEDS2_FINGERPRINT_TOKEN;
import static com.adyen.v6.constants.Adyenv6coreConstants.ISSUER_PAYMENT_METHODS;
import static com.adyen.v6.constants.Adyenv6coreConstants.OPENINVOICE_METHODS_ALLOW_SOCIAL_SECURITY_NUMBER;
import static com.adyen.v6.constants.Adyenv6coreConstants.OPENINVOICE_METHODS_API;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYBRIGHT;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHODS_ALLOW_SOCIAL_SECURITY_NUMBER;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_AMAZONPAY;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_APPLEPAY;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_BOLETO;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_KLARNA;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_ONLINEBANKING_IN;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_ONLINEBANKING_PL;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SCHEME;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SEPA_DIRECTDEBIT;
import static com.adyen.v6.constants.Adyenv6coreConstants.SHOPPER_LOCALE;
import static de.hybris.platform.order.impl.DefaultCartService.SESSION_CART_PARAMETER_NAME;

/**
 * Adyen Checkout Facade for initiating payments using CC or APM
 */
public class DefaultAdyenCheckoutFacade implements AdyenCheckoutFacade {

    public static final String DETAILS = "details";
    private static final String LOCALE = "locale";
    public static final String SESSION_DATA = "sessionData";
    private static final String REGION = "region";
    private static final String US_LOCALE = "en_US";
    private static final String GB_LOCALE = "en_GB";
    private static final String DE_LOCALE = "de_DE";
    private static final String FR_LOCALE = "fr_FR";
    private static final String IT_LOCALE = "it_IT";
    private static final String ES_LOCALE = "es_ES";
    private static final String US = "US";
    private static final String RECURRING_RECURRING_DETAIL_REFERENCE = "recurring.recurringDetailReference";
    private static final String EXCLUDED_PAYMENT_METHODS_CONFIG = "adyen.payment-methods.excluded";

    private BaseStoreService baseStoreService;
    private SessionService sessionService;
    private CartService cartService;
    private OrderFacade orderFacade;
    private CheckoutFacade checkoutFacade;
    private AdyenTransactionService adyenTransactionService;
    private OrderRepository orderRepository;
    private AdyenOrderService adyenOrderService;
    private CheckoutCustomerStrategy checkoutCustomerStrategy;
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    private ModelService modelService;
    private CommonI18NService commonI18NService;
    private KeyGenerator keyGenerator;
    private FlexibleSearchService flexibleSearchService;
    private Converter<AddressData, AddressModel> addressReverseConverter;
    private PosPaymentResponseConverter posPaymentResponseConverter;
    private Converter<CountryModel, CountryData> countryConverter;
    private Converter<OrderModel, OrderData> orderConverter;
    private CartFactory cartFactory;
    private CalculationService calculationService;
    private Populator<AddressModel, AddressData> addressPopulator;
    private AdyenBusinessProcessService adyenBusinessProcessService;
    private TransactionOperations transactionTemplate;
    private AdyenExpressCheckoutFacade adyenExpressCheckoutFacade;
    private UserFacade userFacade;
    private I18NFacade i18NFacade;
    private ConfigurationService configurationService;
    private AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;
    private AdyenOrderFacade adyenOrderFacade;

    public static final Logger LOGGER = Logger.getLogger(DefaultAdyenCheckoutFacade.class);

    public static final String SESSION_LOCKED_CART = "adyen_cart";
    public static final String SESSION_PENDING_ORDER_CODE = "adyen_pending_order_code";
    public static final String SESSION_PAYMENT_METHODS_CART_DATA = "adyen_payment_methods_cart_data";
    public static final String SESSION_CSE_TOKEN = "adyen_cse_token";
    public static final String SESSION_SF_CARD_NUMBER = "encryptedCardNumber";
    public static final String SESSION_SF_EXPIRY_MONTH = "encryptedExpiryMonth";
    public static final String SESSION_SF_EXPIRY_YEAR = "encryptedExpiryYear";
    public static final String SESSION_SF_SECURITY_CODE = "encryptedSecurityCode";
    public static final String SESSION_CARD_BRAND = "cardBrand";
    public static final String SESSION_ADYEN_RISK_DATA = "adyenRiskData";
    public static final String MODEL_SELECTED_PAYMENT_METHOD = "selectedPaymentMethod";
    public static final String MODEL_PAYMENT_METHODS = "paymentMethods";
    public static final String MODEL_CREDIT_CARD_LABEL = "creditCardLabel";
    public static final String MODEL_ALLOWED_CARDS = "allowedCards";
    public static final String MODEL_REMEMBER_DETAILS = "showRememberTheseDetails";
    public static final String MODEL_STORED_CARDS = "storedCards";
    public static final String MODEL_DF_URL = "dfUrl";
    public static final String MODEL_CLIENT_KEY = "clientKey";
    public static final String MODEL_MERCHANT_ACCOUNT = "merchantAccount";
    public static final String MODEL_CHECKOUT_SHOPPER_HOST = "checkoutShopperHost";
    public static final String DF_VALUE = "dfValue";
    public static final String MODEL_OPEN_INVOICE_METHODS = "openInvoiceMethods";
    public static final String MODEL_SHOW_SOCIAL_SECURITY_NUMBER = "showSocialSecurityNumber";
    public static final String MODEL_SHOW_BOLETO = "showBoleto";
    public static final String MODEL_SHOW_POS = "showPos";
    public static final String MODEL_SHOW_COMBO_CARD = "showComboCard";
    public static final String CHECKOUT_SHOPPER_HOST_TEST = "checkoutshopper-test.adyen.com";
    public static final String CHECKOUT_SHOPPER_HOST_LIVE = "checkoutshopper-live.adyen.com";
    public static final String CHECKOUT_SHOPPER_HOST_LIVE_IN = "checkoutshopper-live-in.adyen.com";
    public static final String MODEL_ISSUER_LISTS = "issuerLists";
    public static final String MODEL_CONNECTED_TERMINAL_LIST = "connectedTerminalList";
    public static final String MODEL_ENVIRONMENT_MODE = "environmentMode";
    public static final String MODEL_AMOUNT = "amount";
    public static final String MODEL_AMOUNT_DECIMAL= "amountDecimal";
    public static final String MODEL_IMMEDIATE_CAPTURE = "immediateCapture";
    public static final String MODEL_PAYPAL_MERCHANT_ID = "paypalMerchantId";
    public static final String MODEL_COUNTRY_CODE = "countryCode";
    public static final String MODEL_APPLEPAY_MERCHANT_IDENTIFIER = "applePayMerchantIdentifier";
    public static final String MODEL_APPLEPAY_MERCHANT_NAME = "applePayMerchantName";
    public static final String MODEL_AMAZONPAY_CONFIGURATION = "amazonPayConfiguration";
    public static final String MODEL_DELIVERY_ADDRESS = "deliveryAddress";
    public static final String ECOMMERCE_SHOPPER_INTERACTION = "Ecommerce";
    public static final String MODEL_CARD_HOLDER_NAME_REQUIRED = "cardHolderNameRequired";
    public static final String IS_CARD_HOLDER_NAME_REQUIRED_PROPERTY = "isCardHolderNameRequired";

    public DefaultAdyenCheckoutFacade() {
    }

    @Override
    public String getCheckoutShopperHost() {
        final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

        if (Boolean.TRUE.equals(baseStore.getAdyenTestMode())) {
            return CHECKOUT_SHOPPER_HOST_TEST;
        }

        if (AdyenRegions.IN.equals(baseStore.getAdyenRegion())) {
            return CHECKOUT_SHOPPER_HOST_LIVE_IN;
        }
        return CHECKOUT_SHOPPER_HOST_LIVE;
    }


    @Override
    public String getEnvironmentMode() {
        if (Boolean.TRUE.equals(baseStoreService.getCurrentBaseStore().getAdyenTestMode())) {
            return "test";
        }
        if (AdyenRegions.IN.equals(baseStoreService.getCurrentBaseStore().getAdyenRegion())) {
            return "live-in";
        }
        return "live";
    }

    @Override
    public String getClientKey() {
        return baseStoreService.getCurrentBaseStore().getAdyenClientKey();
    }

    @Override
    public void lockSessionCart() {
        getSessionService().setAttribute(SESSION_LOCKED_CART, cartService.getSessionCart());
        getSessionService().removeAttribute(SESSION_CART_PARAMETER_NAME);

        //Refresh session for registered users
        if (!getCheckoutCustomerStrategy().isAnonymousCheckout()) {
            getCartService().getSessionCart();
        }
    }

    @Override
    public CartModel restoreSessionCart() throws InvalidCartException {
        CartModel cartModel = getSessionService().getAttribute(SESSION_LOCKED_CART);
        if (cartModel == null) {
            throw new InvalidCartException("Cart does not exist!");
        }

        getCartService().setSessionCart(cartModel);
        getSessionService().removeAttribute(SESSION_LOCKED_CART);
        getSessionService().removeAttribute(THREEDS2_FINGERPRINT_TOKEN);
        getSessionService().removeAttribute(THREEDS2_CHALLENGE_TOKEN);
        getSessionService().removeAttribute(PAYMENT_METHOD);

        return cartModel;
    }

    @Override
    public PaymentDetailsWsDTO addPaymentDetails(PaymentDetailsWsDTO paymentDetails) {
        CartModel cartModel = cartService.getSessionCart();

        transactionTemplate.execute(transactionStatus -> {
            final AddressModel billingAddress = createBillingAddress(paymentDetails);

            PaymentInfoModel paymentInfo = createPaymentInfo(cartModel, paymentDetails);
            paymentInfo.setBillingAddress(billingAddress);
            billingAddress.setOwner(paymentInfo);

            modelService.save(paymentInfo);

            cartModel.setPaymentInfo(paymentInfo);
            modelService.save(cartModel);
            return null;
        });

        return paymentDetails;
    }


    protected AddressModel createBillingAddress(PaymentDetailsWsDTO paymentDetails) {
        String titleCode = paymentDetails.getBillingAddress().getTitleCode();
        final AddressModel billingAddress = getModelService().create(AddressModel.class);
        if (StringUtils.isNotBlank(titleCode)) {
            final TitleModel title = new TitleModel();
            title.setCode(titleCode);
            billingAddress.setTitle(getFlexibleSearchService().getModelByExample(title));
        }
        billingAddress.setFirstname(paymentDetails.getBillingAddress().getFirstName());
        billingAddress.setLastname(paymentDetails.getBillingAddress().getLastName());
        billingAddress.setLine1(paymentDetails.getBillingAddress().getLine1());
        billingAddress.setLine2(paymentDetails.getBillingAddress().getLine2());
        billingAddress.setTown(paymentDetails.getBillingAddress().getTown());
        billingAddress.setPostalcode(paymentDetails.getBillingAddress().getPostalCode());
        billingAddress.setCountry(getCommonI18NService().getCountry(paymentDetails.getBillingAddress().getCountry().getIsocode()));

        final AddressData addressData = new AddressData();
        addressData.setTitleCode(paymentDetails.getBillingAddress().getTitleCode());
        addressData.setFirstName(billingAddress.getFirstname());
        addressData.setLastName(billingAddress.getLastname());
        addressData.setLine1(billingAddress.getLine1());
        addressData.setLine2(billingAddress.getLine2());
        addressData.setTown(billingAddress.getTown());
        addressData.setPostalCode(billingAddress.getPostalcode());
        addressData.setBillingAddress(true);

        if (paymentDetails.getBillingAddress().getCountry() != null) {
            final CountryData countryData = getI18NFacade().getCountryForIsocode(paymentDetails.getBillingAddress().getCountry().getIsocode());
            addressData.setCountry(countryData);
        }
        if (paymentDetails.getBillingAddress().getRegion().getIsocode() != null) {
            final RegionData regionData = getI18NFacade().getRegion(paymentDetails.getBillingAddress().getCountry().getIsocode(), paymentDetails.getBillingAddress().getRegion().getIsocode());
            addressData.setRegion(regionData);
        }

        getAddressReverseConverter().convert(addressData, billingAddress);

        return billingAddress;
    }

    @Override
    public PaymentDetailsResponse handleRedirectPayload(PaymentCompletionDetails details) throws Exception {
        PaymentDetailsResponse response;
        try {
            response = getAdyenPaymentService().getPaymentDetailsFromPayload(details);
        } catch (Exception e) {
            LOGGER.error(e instanceof ApiException ? e.toString() : e.getMessage());
            restoreCartFromOrderCodeInSession();
            throw new AdyenNonAuthorizedPaymentException(e.getMessage());
        }

        String orderCode = response.getMerchantReference();
        OrderModel orderModel = retrievePendingOrder(orderCode);
        updateOrderPaymentStatusAndInfo(orderModel, response);

        if (PaymentDetailsResponse.ResultCodeEnum.AUTHORISED.equals(response.getResultCode())
                && PaymentDetailsResponse.ResultCodeEnum.RECEIVED.equals(response.getResultCode())) {
            restoreCartFromOrder(orderCode);
        }

        return response;
    }

    protected void updateOrderPaymentStatusAndInfo(OrderModel orderModel, PaymentDetailsResponse paymentDetailsResponse) {

        if (PaymentDetailsResponse.ResultCodeEnum.RECEIVED != paymentDetailsResponse.getResultCode()) {
            //payment authorisation is finished, update payment info
            LOGGER.debug("payment authorisation is finished, updating payment info");

            getAdyenTransactionService().createPaymentTransactionFromResultCode(orderModel,
                    orderModel.getCode(),
                    paymentDetailsResponse.getPspReference(),
                    paymentDetailsResponse.getResultCode());
        }

        if (PaymentDetailsResponse.ResultCodeEnum.AUTHORISED == paymentDetailsResponse.getResultCode() || PaymentDetailsResponse.ResultCodeEnum.RECEIVED == paymentDetailsResponse.getResultCode()) {
            //remove PAYMENT_PENDING status, will be processed by order management
            LOGGER.info("Removing PAYMENT_PENDING status, will be processed by order management");

            orderModel.setStatus(null);
            orderModel.setStatusInfo(null);
        } else {
            //payment was not authorised, cancel pending order
            LOGGER.warn("Payment was not authorised, cancel pending order");

            orderModel.setStatus(OrderStatus.CANCELLED);
            orderModel.setStatusInfo(paymentDetailsResponse.getPspReference() + " - " + paymentDetailsResponse.getResultCode().getValue());
        }
        getModelService().save(orderModel);
        getAdyenBusinessProcessService().triggerOrderProcessEvent(orderModel, Adyenv6coreConstants.PROCESS_EVENT_ADYEN_PAYMENT_RESULT);

        String paymentType = "";
        if (paymentDetailsResponse.getPaymentMethod() != null) {
            paymentType = paymentDetailsResponse.getPaymentMethod().getType();
        }

        Map<String, String> additionalData = paymentDetailsResponse.getAdditionalData();

        getAdyenOrderService().updatePaymentInfo(orderModel, paymentType, additionalData);
        getAdyenOrderService().storeFraudReport(orderModel, paymentDetailsResponse.getPspReference(), paymentDetailsResponse.getFraudResult());
    }

    @Override
    public OrderData authorisePayment(final HttpServletRequest request, final CartData cartData) throws Exception {
        CheckoutCustomerStrategy checkoutCustomerStrategy = getCheckoutCustomerStrategy();

        CustomerModel customer = checkoutCustomerStrategy.getCurrentUserForCheckout();

        updateCartWithSessionData(cartData);
        String adyenPaymentMethod = cartData.getAdyenPaymentMethod();

        RequestInfo requestInfo = new RequestInfo(request);
        requestInfo.setShopperLocale(getShopperLocale());

        PaymentResponse paymentResponse = getAdyenPaymentService().processPaymentRequest(cartData,null, requestInfo, customer);
        PaymentResponse.ResultCodeEnum resultCode = paymentResponse.getResultCode();
        PaymentResponseAction action = paymentResponse.getAction();


        LOGGER.info("Authorize payment with result code: " + resultCode + " action: " + (action != null ? action.getSchemaType() : "null"));

        if (PaymentResponse.ResultCodeEnum.AUTHORISED == resultCode || PaymentResponse.ResultCodeEnum.PENDING == resultCode) {
            return createAuthorizedOrder(paymentResponse);
        }
        if (PaymentResponse.ResultCodeEnum.RECEIVED == resultCode) {
            return createOrderFromPaymentResponse(paymentResponse);
        }
        if (PaymentResponse.ResultCodeEnum.PRESENTTOSHOPPER == resultCode) {
            return createOrderFromPaymentResponse(paymentResponse);
        }
        if (PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER == resultCode) {
            placePendingOrder(resultCode.getValue());
            if (adyenPaymentMethod.startsWith(PAYMENT_METHOD_KLARNA)) {
                getSessionService().setAttribute(PAYMENT_METHOD, adyenPaymentMethod);
            }
        }

        throw new AdyenNonAuthorizedPaymentException(paymentResponse);
    }

    @Override
    public OrderData handleResultcomponentPayment(final PaymentResultDTO paymentResultDTO) throws Exception {
        if (PaymentResponse.ResultCodeEnum.PENDING.getValue().equals(paymentResultDTO.getResultCode()) ||
                PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER.getValue().equals(paymentResultDTO.getResultCode())) {
            LOGGER.info("Placing pending order");
            return placePendingOrder(paymentResultDTO.getResultCode());
        }
        if (PaymentResponse.ResultCodeEnum.AUTHORISED.getValue().equals(paymentResultDTO.getResultCode())) {
            LOGGER.info("Placing authorized order");
            return placeAuthorisedOrder(PaymentResponse.ResultCodeEnum.AUTHORISED);
        }
        return null;
    }

    @Override
    public PaymentResponse componentPayment(final HttpServletRequest request, final CartData cartData, PaymentRequest paymentRequest) throws Exception {
        updateCartWithSessionData(cartData);

        RequestInfo requestInfo = new RequestInfo(request);
        requestInfo.setShopperLocale(getShopperLocale());

        PaymentResponse paymentResponse = getAdyenPaymentService().processPaymentRequest(cartData, paymentRequest, requestInfo, getCheckoutCustomerStrategy().getCurrentUserForCheckout());
        if (PaymentResponse.ResultCodeEnum.PENDING == paymentResponse.getResultCode() ||
                PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER == paymentResponse.getResultCode() ||
                PaymentResponse.ResultCodeEnum.PRESENTTOSHOPPER == paymentResponse.getResultCode()) {
            LOGGER.info("Placing pending order");
            placePendingOrder(paymentResponse.getResultCode().getValue());
            return paymentResponse;
        }
        if (PaymentResponse.ResultCodeEnum.AUTHORISED == paymentResponse.getResultCode()) {
            LOGGER.info("Creating authorized order");
            createAuthorizedOrder(paymentResponse);
            return paymentResponse;
        }
        throw new AdyenNonAuthorizedPaymentException(paymentResponse);
    }

    @Override
    public PaymentDetailsResponse componentDetails(PaymentDetailsRequest detailsRequest) throws Exception {
        PaymentDetailsResponse response = getAdyenPaymentService().getPaymentDetailsFromPayload(detailsRequest);
        String orderCode = response.getMerchantReference();
        OrderModel orderModel = retrievePendingOrder(orderCode);
        updateOrderPaymentStatusAndInfo(orderModel, response);

        return response;
    }

    protected void updateCartWithSessionData(CartData cartData) {
        cartData.setAdyenCseToken(getSessionService().getAttribute(SESSION_CSE_TOKEN));
        cartData.setAdyenEncryptedCardNumber(getSessionService().getAttribute(SESSION_SF_CARD_NUMBER));
        cartData.setAdyenEncryptedExpiryMonth(getSessionService().getAttribute(SESSION_SF_EXPIRY_MONTH));
        cartData.setAdyenEncryptedExpiryYear(getSessionService().getAttribute(SESSION_SF_EXPIRY_YEAR));
        cartData.setAdyenEncryptedSecurityCode(getSessionService().getAttribute(SESSION_SF_SECURITY_CODE));
        cartData.setAdyenCardBrand(getSessionService().getAttribute(SESSION_CARD_BRAND));
        cartData.setRiskData(getSessionService().getAttribute(SESSION_ADYEN_RISK_DATA));

        getSessionService().removeAttribute(SESSION_CSE_TOKEN);
        getSessionService().removeAttribute(SESSION_SF_CARD_NUMBER);
        getSessionService().removeAttribute(SESSION_SF_EXPIRY_MONTH);
        getSessionService().removeAttribute(SESSION_SF_EXPIRY_YEAR);
        getSessionService().removeAttribute(SESSION_SF_SECURITY_CODE);
        getSessionService().removeAttribute(SESSION_CARD_BRAND);
        getSessionService().removeAttribute(PAYMENT_METHOD);
        getSessionService().removeAttribute(SESSION_ADYEN_RISK_DATA);
    }

    @Override
    public OrderData handle3DSResponse(PaymentDetailsRequest paymentsDetailsRequest) throws Exception {
        PaymentDetailsResponse paymentsDetailsResponse;
        try {
            paymentsDetailsResponse = getAdyenPaymentService().authorise3DSPayment(paymentsDetailsRequest);
        } catch (Exception e) {
            LOGGER.error(e instanceof ApiException ? e.toString() : e.getMessage());
            restoreCartFromOrderCodeInSession();
            throw new AdyenNonAuthorizedPaymentException(e.getMessage());
        }

        String orderCode = paymentsDetailsResponse.getMerchantReference();
        OrderModel orderModel = retrievePendingOrder(orderCode);
        updateOrderPaymentStatusAndInfo(orderModel, paymentsDetailsResponse);

        PaymentDetailsResponse.ResultCodeEnum resultCode = paymentsDetailsResponse.getResultCode();

        if (PaymentDetailsResponse.ResultCodeEnum.AUTHORISED.equals(resultCode) || PaymentDetailsResponse.ResultCodeEnum.RECEIVED.equals(resultCode)) {
            return getOrderConverter().convert(orderModel);
        }

        restoreCartFromOrder(orderCode);
        throw new AdyenNonAuthorizedPaymentException(paymentsDetailsResponse);
    }

    /**
     * Create order and authorized TX
     */
    protected OrderData createAuthorizedOrder(final PaymentResponse paymentsResponse) throws InvalidCartException {
        final CartModel cartModel = cartService.getSessionCart();
        final String merchantTransactionCode = cartModel.getCode();

        updateAdyenSelectedReferenceIfPresent(cartModel, paymentsResponse);

        // First save the transactions to the CartModel < AbstractOrderModel
        getAdyenTransactionService().authorizeOrderModel(cartModel, merchantTransactionCode, paymentsResponse.getPspReference());

        return createOrderFromPaymentResponse(paymentsResponse);
    }

    protected void updateAdyenSelectedReferenceIfPresent(final CartModel cartModel, final PaymentResponse paymentsResponse) {
        Map<String, String> additionalData = paymentsResponse.getAdditionalData();
        if (additionalData != null) {
            String recurringDetailReference = additionalData.get(RECURRING_RECURRING_DETAIL_REFERENCE);
            if (recurringDetailReference != null) {
                cartModel.getPaymentInfo().setAdyenSelectedReference(recurringDetailReference);
            }
        }
    }

    /**
     * Create order
     */
    protected OrderData createOrderFromPaymentResponse(final PaymentResponse paymentsResponse) throws InvalidCartException {
        LOGGER.debug("Create order from paymentsResponse: " + paymentsResponse.getPspReference());

        OrderData orderData = getCheckoutFacade().placeOrder();

        OrderModel orderModel = orderRepository.getOrderModel(orderData.getCode());

        String paymentType = "";
        if (paymentsResponse.getPaymentMethod() != null) {
            paymentType = paymentsResponse.getPaymentMethod().getType();
        }

        Map<String, String> additionalData = paymentsResponse.getAdditionalData();

        getAdyenOrderService().updatePaymentInfo(orderModel, paymentType, additionalData);
        getAdyenOrderService().storeFraudReport(orderModel, paymentsResponse.getPspReference(), paymentsResponse.getFraudResult());
        return orderData;
    }

    protected OrderData placePendingOrder(String resultCode) throws InvalidCartException {
        CartModel cartModel = getCartService().getSessionCart();
        cartModel.setStatus(OrderStatus.PAYMENT_PENDING);
        cartModel.setStatusInfo(resultCode);

        PaymentMethodsCartData paymentMethodsCartData = getPaymentMethodsCartData(cartModel);

        getModelService().save(cartModel);

        OrderData orderData = getCheckoutFacade().placeOrder();

        getSessionService().setAttribute(SESSION_PENDING_ORDER_CODE, orderData.getCode());
        getSessionService().setAttribute(SESSION_PAYMENT_METHODS_CART_DATA, paymentMethodsCartData);

        //Set new cart in session to avoid bugs (like going "back" on browser)
        CartModel newCartModel = getCartFactory().createCart();
        getCartService().setSessionCart(newCartModel);

        return orderData;
    }

    protected OrderData placeAuthorisedOrder(PaymentResponse.ResultCodeEnum resultCode) throws InvalidCartException {
        CartModel cartModel = getCartService().getSessionCart();
        cartModel.setStatus(OrderStatus.PAYMENT_AUTHORIZED);
        cartModel.setStatusInfo(resultCode.getValue());

        PaymentMethodsCartData paymentMethodsCartData = getPaymentMethodsCartData(cartModel);

        getModelService().save(cartModel);

        OrderData orderData = getCheckoutFacade().placeOrder();

        getSessionService().setAttribute(SESSION_PENDING_ORDER_CODE, orderData.getCode());
        getSessionService().setAttribute(SESSION_PAYMENT_METHODS_CART_DATA, paymentMethodsCartData);

        //Set new cart in session to avoid bugs (like going "back" on browser)
        CartModel newCartModel = getCartFactory().createCart();
        getCartService().setSessionCart(newCartModel);

        return orderData;
    }

    @Override
    public void initializeCheckoutData(Model model) throws ApiException {
        CheckoutConfigDTO checkoutConfigDTO = getCheckoutConfig();

        model.addAttribute(SESSION_DATA, checkoutConfigDTO.getSessionData());

        // current selected PaymentMethod
        model.addAttribute(MODEL_SELECTED_PAYMENT_METHOD, checkoutConfigDTO.getSelectedPaymentMethod());

        //Set payment methods
        model.addAttribute(MODEL_PAYMENT_METHODS, checkoutConfigDTO.getAlternativePaymentMethods());

        //Set allowed Credit Cards
        model.addAttribute(MODEL_CREDIT_CARD_LABEL, checkoutConfigDTO.getCreditCardLabel());
        model.addAttribute(MODEL_ALLOWED_CARDS, checkoutConfigDTO.getAllowedCards());

        model.addAttribute(MODEL_REMEMBER_DETAILS, checkoutConfigDTO.isShowRememberTheseDetails());
        model.addAttribute(MODEL_STORED_CARDS, checkoutConfigDTO.getStoredPaymentMethodList());
        model.addAttribute(MODEL_DF_URL, checkoutConfigDTO.getDeviceFingerPrintUrl());
        model.addAttribute(MODEL_CHECKOUT_SHOPPER_HOST, checkoutConfigDTO.getCheckoutShopperHost());
        model.addAttribute(MODEL_ENVIRONMENT_MODE, checkoutConfigDTO.getEnvironmentMode());
        model.addAttribute(SHOPPER_LOCALE, checkoutConfigDTO.getShopperLocale());

        // OpenInvoice Methods
        model.addAttribute(MODEL_OPEN_INVOICE_METHODS, checkoutConfigDTO.getOpenInvoiceMethods());

        // retrieve shipping Country to define if social security number needs to be shown or date of birth field for openinvoice methods
        model.addAttribute(MODEL_SHOW_SOCIAL_SECURITY_NUMBER, checkoutConfigDTO.isShowSocialSecurityNumber());

        //Include Boleto banks
        model.addAttribute(MODEL_SHOW_BOLETO, checkoutConfigDTO.isShowBoleto());

        //Enable combo card flag
        model.addAttribute(MODEL_SHOW_COMBO_CARD, checkoutConfigDTO.isShowComboCard());

        //Include POS Enable configuration
        model.addAttribute(MODEL_SHOW_POS, checkoutConfigDTO.isShowPos());
        //Include connnected terminal List for POS
        model.addAttribute(MODEL_CONNECTED_TERMINAL_LIST, checkoutConfigDTO.getConnectedTerminalList());
        //Include Issuer Lists
        model.addAttribute(MODEL_ISSUER_LISTS, checkoutConfigDTO.getIssuerLists());

        //Include information for components
        model.addAttribute(MODEL_CLIENT_KEY, checkoutConfigDTO.getAdyenClientKey());
        model.addAttribute(MODEL_AMOUNT, checkoutConfigDTO.getAmount());
        model.addAttribute(MODEL_IMMEDIATE_CAPTURE, checkoutConfigDTO.isImmediateCapture());
        model.addAttribute(MODEL_PAYPAL_MERCHANT_ID, checkoutConfigDTO.getAdyenPaypalMerchantId());
        model.addAttribute(MODEL_COUNTRY_CODE, checkoutConfigDTO.getCountryCode());
        model.addAttribute(MODEL_CARD_HOLDER_NAME_REQUIRED, checkoutConfigDTO.isCardHolderNameRequired());
        model.addAttribute(PAYMENT_METHOD_SEPA_DIRECTDEBIT, checkoutConfigDTO.isSepaDirectDebit());
    }

    public CheckoutConfigDTO getReactCheckoutConfig() throws ApiException {
        final CartData cartData = getCheckoutFacade().getCheckoutCart();
        AdyenCheckoutApiService adyenPaymentService = getAdyenPaymentService();
        List<PaymentMethod> paymentMethods;
        List<String> connectedTerminalList = null;
        List<StoredPaymentMethod> storedPaymentMethodList = null;
        BaseStoreModel baseStore;
        CustomerModel customerModel = getCheckoutCustomerStrategy().getCurrentUserForCheckout();
        PaymentMethodsResponse response = new PaymentMethodsResponse();
        CartModel cartModel = cartService.getSessionCart();

        //to remove unwanted payment methods insert them here
        List<String> excludedPaymentMethods = getExcludedPaymentMethodsFromConfiguration();
        LOGGER.info(excludedPaymentMethods.toString());

        try {
            if (showPos()) {
                connectedTerminalList = adyenPaymentService.getConnectedTerminals().getUniqueTerminalIds();
            }

            response = getPaymentMethods(adyenPaymentService, cartData, customerModel, excludedPaymentMethods);
        } catch (ApiException | IOException e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }

        paymentMethods = response.getPaymentMethods();
        if (paymentMethods == null) {
            paymentMethods = Collections.emptyList();
        }

        //apple pay
        Map<String, String> applePayConfig = getApplePayConfigFromPaymentMethods(paymentMethods);
        if (!CollectionUtils.isEmpty(applePayConfig)) {
            cartModel.setAdyenApplePayMerchantName(applePayConfig.get("merchantName"));
            cartModel.setAdyenApplePayMerchantIdentifier(applePayConfig.get("merchantId"));
        }

        baseStore = baseStoreService.getCurrentBaseStore();

        //Verify allowedCards
        PaymentMethod cardsPaymentMethod = paymentMethods.stream()
                .filter(paymentMethod -> PAYMENT_METHOD_SCHEME.equals(paymentMethod.getType()))
                .findAny().orElse(null);

        if (cardsPaymentMethod != null) {
            List<String> allowedCards = baseStore.getAdyenAllowedCards().stream().map(AdyenCardTypeEnum::getCode).toList();

            List<String> cardBrands = cardsPaymentMethod.getBrands();
            allowedCards = allowedCards.stream()
                    .filter(cardBrands::contains)
                    .toList();

            cardsPaymentMethod.setBrands(allowedCards);
        }

        if (showRememberDetails()) {
            //Include stored one-click cards
            storedPaymentMethodList = getStoredOneClickPaymentMethods(response);
            Set<String> recurringDetailReferences = new HashSet<>();
            if (storedPaymentMethodList != null) {
                recurringDetailReferences = storedPaymentMethodList.stream().map(StoredPaymentMethod::getId).collect(Collectors.toSet());
            }
            cartModel.setAdyenStoredCards(recurringDetailReferences);
        }

        modelService.save(cartModel);

        Amount amount = AmountUtil.createAmount(cartData.getTotalPriceWithTax().getValue(), cartData.getTotalPriceWithTax().getCurrencyIso());

        CheckoutConfigDTOBuilder checkoutConfigDTOBuilder = new CheckoutConfigDTOBuilder();

        return checkoutConfigDTOBuilder
                .setPaymentMethods(paymentMethods)
                .setConnectedTerminalList(connectedTerminalList)
                .setStoredPaymentMethodList(storedPaymentMethodList)
                .setAmount(amount)
                .setAdyenClientKey(baseStore.getAdyenClientKey())
                .setAdyenPaypalMerchantId(baseStore.getAdyenPaypalMerchantId())
                .setDeviceFingerPrintUrl(adyenPaymentService.getDeviceFingerprintUrl())
                .setSessionData(getAdyenSessionData())
                .setSelectedPaymentMethod(cartData.getAdyenPaymentMethod())
                .setShowRememberTheseDetails(showRememberDetails())
                .setCheckoutShopperHost(getCheckoutShopperHost())
                .setEnvironmentMode(getEnvironmentMode())
                .setShopperLocale(getShopperLocale())
                .setOpenInvoiceMethods(OPENINVOICE_METHODS_API)
                .setShowSocialSecurityNumber(showSocialSecurityNumber())
                .setShowBoleto(showBoleto())
                .setShowComboCard(showComboCard())
                .setShowPos(showPos())
                .setImmediateCapture(isImmediateCapture())
                .setCountryCode(cartData.getDeliveryAddress().getCountry().getIsocode())
                .setCardHolderNameRequired(getHolderNameRequired())
                .build();
    }

    protected List<String> getExcludedPaymentMethodsFromConfiguration() {
        String excludedPaymentMethodsConfig = configurationService.getConfiguration().getString(EXCLUDED_PAYMENT_METHODS_CONFIG);
        if (StringUtils.isEmpty(excludedPaymentMethodsConfig)) {
            return new ArrayList<>();
        }

        String[] excludedPaymentMethods = StringUtils.split(excludedPaymentMethodsConfig, ',');
        return Arrays.stream(excludedPaymentMethods).map(String::trim).toList();
    }

    @Deprecated
    public CheckoutConfigDTO getCheckoutConfig() throws ApiException {
        final CartData cartData = getCheckoutFacade().getCheckoutCart();
        AdyenCheckoutApiService adyenCheckoutApiService = getAdyenPaymentService();
        List<PaymentMethod> alternativePaymentMethods;
        List<String> connectedTerminalList = null;
        List<StoredPaymentMethod> storedPaymentMethodList = null;
        Map<String, String> issuerLists = new HashMap<>();
        BaseStoreModel baseStore;
        CustomerModel customerModel = getCheckoutCustomerStrategy().getCurrentUserForCheckout();
        PaymentMethodsResponse response = new PaymentMethodsResponse();
        CartModel cartModel = cartService.getSessionCart();

        try {
            if (showPos()) {
                connectedTerminalList = adyenCheckoutApiService.getConnectedTerminals().getUniqueTerminalIds();
            }

            response = getPaymentMethods(adyenCheckoutApiService, cartData, customerModel);
        } catch (ApiException | IOException e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }

        alternativePaymentMethods = response.getPaymentMethods();
        if (alternativePaymentMethods == null) {
            alternativePaymentMethods = Collections.emptyList();
        }

        final List<PaymentMethod> issuerPaymentMethods = getIssuerPaymentMethods(alternativePaymentMethods);
        updateIssuerList(issuerPaymentMethods, issuerLists);

        //apple pay
        Map<String, String> applePayConfig = getApplePayConfigFromPaymentMethods(alternativePaymentMethods);
        if (!CollectionUtils.isEmpty(applePayConfig)) {
            cartModel.setAdyenApplePayMerchantName(applePayConfig.get("merchantName"));
            cartModel.setAdyenApplePayMerchantIdentifier(applePayConfig.get("merchantId"));
        }

        boolean sepaDirectDebit = isSepaDirectDebit(alternativePaymentMethods);

        //amazon pay
        Optional<PaymentMethod> amazonPayMethod = getAmazonPayMethod(alternativePaymentMethods);
        amazonPayMethod.ifPresent(paymentMethod -> updateCartWithAmazonPay(paymentMethod, cartModel));

        baseStore = baseStoreService.getCurrentBaseStore();

        //Verify allowedCards
        String creditCardLabel = null;
        List<AdyenCardTypeEnum> allowedCards = null;
        PaymentMethod cardsPaymentMethod = alternativePaymentMethods.stream()
                .filter(paymentMethod -> PAYMENT_METHOD_SCHEME.equals(paymentMethod.getType()))
                .findAny().orElse(null);

        if (cardsPaymentMethod != null) {
            creditCardLabel = cardsPaymentMethod.getName();
            allowedCards = baseStore.getAdyenAllowedCards().stream().toList();

            List<String> cardBrands = cardsPaymentMethod.getBrands();
            allowedCards = allowedCards.stream()
                    .filter(adyenCardTypeEnum -> cardBrands.contains(adyenCardTypeEnum.getCode()))
                    .toList();
        }

        //Exclude cards, boleto and iDeal
        alternativePaymentMethods = alternativePaymentMethods.stream()
                .filter(paymentMethod -> !paymentMethod.getType().isEmpty() && !isHiddenPaymentMethod(paymentMethod))
                .toList();

        if (showRememberDetails()) {
            //Include stored one-click cards
            storedPaymentMethodList = getStoredOneClickPaymentMethods(response);
            Set<String> recurringDetailReferences = new HashSet<>();
            if (storedPaymentMethodList != null) {
                recurringDetailReferences = storedPaymentMethodList.stream().map(StoredPaymentMethod::getId).collect(Collectors.toSet());
            }
            cartModel.setAdyenStoredCards(recurringDetailReferences);
        }

        modelService.save(cartModel);

        Amount amount = AmountUtil.createAmount(cartData.getTotalPriceWithTax().getValue(), cartData.getTotalPriceWithTax().getCurrencyIso());

        CheckoutConfigDTOBuilder checkoutConfigDTOBuilder = new CheckoutConfigDTOBuilder();

        return checkoutConfigDTOBuilder.setAlternativePaymentMethods(alternativePaymentMethods)
                .setConnectedTerminalList(connectedTerminalList)
                .setStoredPaymentMethodList(storedPaymentMethodList)
                .setIssuerLists(issuerLists)
                .setCreditCardLabel(creditCardLabel)
                .setAllowedCards(allowedCards)
                .setAmount(amount)
                .setAdyenClientKey(baseStore.getAdyenClientKey())
                .setAdyenPaypalMerchantId(baseStore.getAdyenPaypalMerchantId())
                .setDeviceFingerPrintUrl(adyenCheckoutApiService.getDeviceFingerprintUrl())
                .setSessionData(getAdyenSessionData())
                .setSelectedPaymentMethod(cartData.getAdyenPaymentMethod())
                .setShowRememberTheseDetails(showRememberDetails())
                .setCheckoutShopperHost(getCheckoutShopperHost())
                .setEnvironmentMode(getEnvironmentMode())
                .setShopperLocale(getShopperLocale())
                .setOpenInvoiceMethods(OPENINVOICE_METHODS_API)
                .setShowSocialSecurityNumber(showSocialSecurityNumber())
                .setShowBoleto(showBoleto())
                .setShowComboCard(showComboCard())
                .setShowPos(showPos())
                .setImmediateCapture(isImmediateCapture())
                .setCountryCode(cartData.getDeliveryAddress().getCountry().getIsocode())
                .setCardHolderNameRequired(getHolderNameRequired())
                .setSepaDirectDebit(sepaDirectDebit)
                .build();
    }

    @Deprecated
    protected static boolean isSepaDirectDebit(List<PaymentMethod> alternativePaymentMethods) {
        return alternativePaymentMethods.stream().
                anyMatch(paymentMethod -> !paymentMethod.getType().isEmpty() &&
                        PAYMENT_METHOD_SEPA_DIRECTDEBIT.contains(paymentMethod.getType()));
    }

    protected static void updateCartWithAmazonPay(PaymentMethod amazonPayMethod, CartModel cartModel) {
        Map<String, String> amazonPayConfiguration = amazonPayMethod.getConfiguration();
        if (!CollectionUtils.isEmpty(amazonPayConfiguration)) {
            cartModel.setAdyenAmazonPayConfiguration(amazonPayConfiguration);
        }
    }

    protected static Optional<PaymentMethod> getAmazonPayMethod(List<PaymentMethod> alternativePaymentMethods) {
        return alternativePaymentMethods.stream()
                .filter(paymentMethod -> !paymentMethod.getType().isEmpty()
                        && PAYMENT_METHOD_AMAZONPAY.contains(paymentMethod.getType()))
                .findFirst();
    }

    @Deprecated
    protected static void updateIssuerList(List<PaymentMethod> issuerPaymentMethods, Map<String, String> issuerLists) {
        if (!CollectionUtils.isEmpty(issuerPaymentMethods)) {
            Gson gson = new Gson();
            for (PaymentMethod paymentMethod : issuerPaymentMethods) {
                issuerLists.put(paymentMethod.getType(), gson.toJson(paymentMethod.getIssuers()));
            }
        }
    }

    @Deprecated
    protected static List<PaymentMethod> getIssuerPaymentMethods(List<PaymentMethod> alternativePaymentMethods) {
        if (alternativePaymentMethods != null) {
            return alternativePaymentMethods.stream()
                    .filter(paymentMethod -> !paymentMethod.getType().isEmpty() && ISSUER_PAYMENT_METHODS.contains(paymentMethod.getType()))
                    .toList();
        }

        return Collections.emptyList();
    }

    protected PaymentMethodsResponse getPaymentMethods(AdyenCheckoutApiService adyenPaymentService, CartData cartData, CustomerModel customerModel) throws IOException, ApiException {
        return adyenPaymentService.getPaymentMethodsResponse(cartData.getTotalPriceWithTax().getValue(),
                cartData.getTotalPriceWithTax().getCurrencyIso(),
                cartData.getDeliveryAddress().getCountry().getIsocode(),
                getShopperLocale(),
                customerModel.getCustomerID());
    }

    protected PaymentMethodsResponse getPaymentMethods(AdyenCheckoutApiService adyenPaymentService, CartData cartData, CustomerModel customerModel, List<String> excludedPaymentMethods) throws IOException, ApiException {
        return adyenPaymentService.getPaymentMethodsResponse(cartData.getTotalPriceWithTax().getValue(),
                cartData.getTotalPriceWithTax().getCurrencyIso(),
                cartData.getDeliveryAddress().getCountry().getIsocode(),
                getShopperLocale(),
                customerModel.getCustomerID(), excludedPaymentMethods);
    }


    protected Map<String, String> getApplePayConfigFromPaymentMethods(List<PaymentMethod> paymentMethods) {
        if (paymentMethods != null) {
            Optional<PaymentMethod> applePayMethod = paymentMethods.stream()
                    .filter(paymentMethod -> !paymentMethod.getType().isEmpty()
                            && PAYMENT_METHOD_APPLEPAY.contains(paymentMethod.getType()))
                    .findFirst();
            if (applePayMethod.isPresent()) {
                Map<String, String> applePayConfiguration = applePayMethod.get().getConfiguration();
                if (!CollectionUtils.isEmpty(applePayConfiguration)) {
                    return applePayConfiguration;
                }
            }
        }

        return new HashMap<>();
    }

    protected CreateCheckoutSessionResponse getAdyenSessionData() throws ApiException {
        try {
            final CartData cartData = getCheckoutFacade().getCheckoutCart();
            return getAdyenPaymentService().getPaymentSessionData(cartData);
        } catch (JsonProcessingException e) {
            LOGGER.error("Processing json failed. ", e);
            return null;
        } catch (IOException e) {
            LOGGER.error("Exception during geting Adyen session data. ", e);
            return null;
        }
    }

    protected CreateCheckoutSessionResponse getAdyenSessionData(Amount amount) throws ApiException {
        try {
            return getAdyenPaymentService().getPaymentSessionData(amount);
        } catch (JsonProcessingException e) {
            LOGGER.error("Processing json failed. ", e);
            return null;
        } catch (IOException e) {
            LOGGER.error("Exception during geting Adyen session data. ", e);
            return null;
        }
    }

    @Override
    public void initializeSummaryData(Model model) throws ApiException {
        final CartData cartData = getCheckoutFacade().getCheckoutCart();
        final AdyenCheckoutApiService adyenCheckoutApiService = getAdyenPaymentService();
        final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

        final Amount amount = AmountUtil.createAmount(cartData.getTotalPriceWithTax().getValue(), cartData.getTotalPriceWithTax().getCurrencyIso());
        final Gson gson = new Gson();
        final String shopperLocale = getShopperLocale();
        final String countryCode = Objects.nonNull(cartData.getDeliveryAddress()) &&
                Objects.nonNull(cartData.getDeliveryAddress().getCountry()) ?
                cartData.getDeliveryAddress().getCountry().getIsocode() : null;

        model.addAttribute(MODEL_SELECTED_PAYMENT_METHOD, cartData.getAdyenPaymentMethod());
        model.addAttribute(MODEL_DF_URL, adyenCheckoutApiService.getDeviceFingerprintUrl());
        model.addAttribute(MODEL_CHECKOUT_SHOPPER_HOST, getCheckoutShopperHost());
        model.addAttribute(MODEL_ENVIRONMENT_MODE, getEnvironmentMode());
        model.addAttribute(SHOPPER_LOCALE, shopperLocale);

        //Include information for components
        model.addAttribute(MODEL_CLIENT_KEY, baseStore.getAdyenClientKey());
        model.addAttribute(MODEL_MERCHANT_ACCOUNT, adyenMerchantAccountStrategy.getWebMerchantAccount());
        model.addAttribute(MODEL_AMOUNT, amount);
        model.addAttribute(MODEL_IMMEDIATE_CAPTURE, isImmediateCapture());
        model.addAttribute(MODEL_PAYPAL_MERCHANT_ID, baseStore.getAdyenPaypalMerchantId());
        model.addAttribute(MODEL_APPLEPAY_MERCHANT_IDENTIFIER, cartData.getAdyenApplePayMerchantIdentifier());
        model.addAttribute(MODEL_APPLEPAY_MERCHANT_NAME, cartData.getAdyenApplePayMerchantName());
        model.addAttribute(MODEL_AMAZONPAY_CONFIGURATION, gson.toJson(cartData.getAdyenAmazonPayConfiguration()));
        model.addAttribute(MODEL_COUNTRY_CODE, countryCode);
        model.addAttribute(MODEL_DELIVERY_ADDRESS, gson.toJson(cartData.getDeliveryAddress()));
        model.addAttribute(SESSION_DATA, getAdyenSessionData());
        model.addAttribute(LOCALE, gson.toJson(setLocale(cartData.getAdyenAmazonPayConfiguration(), shopperLocale)));
    }

    public void initializeApplePayExpressCartPageData(Model model) throws ApiException {
        final CartData cartData = getCheckoutFacade().getCheckoutCart();
        if (cartData != null && cartData.getTotalPriceWithTax() != null && cartData.getTotalPriceWithTax().getCurrencyIso() != null) {
            final String currencyIso = cartData.getTotalPriceWithTax().getCurrencyIso();
            BigDecimal amountValue = cartData.getTotalPriceWithTax().getValue();
            BigDecimal expressDeliveryModeValue = getExpressDeliveryModeValue(currencyIso);
            amountValue = amountValue.add(expressDeliveryModeValue);

            initializeApplePayExpressDataInternal(amountValue, currencyIso, model);
        }
    }

    public void initializeApplePayExpressPDPData(Model model, ProductData productData) throws ApiException {
        final String currencyIso = productData.getPrice().getCurrencyIso();
        BigDecimal amountValue = productData.getPrice().getValue();
        BigDecimal expressDeliveryModeValue = getExpressDeliveryModeValue(currencyIso);

        amountValue = amountValue.add(expressDeliveryModeValue);

        initializeApplePayExpressDataInternal(amountValue, currencyIso, model);
    }

    protected void initializeApplePayExpressDataInternal(BigDecimal amountValue, String currency, Model model) throws ApiException {
        final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

        try {
            PaymentMethodsResponse paymentMethodsResponse = getAdyenPaymentService().getPaymentMethodsResponse(amountValue,
                    currency,
                    null,
                    getShopperLocale(),
                    null);

            Map<String, String> applePayConfig = getApplePayConfigFromPaymentMethods(paymentMethodsResponse.getPaymentMethods());
            if (!CollectionUtils.isEmpty(applePayConfig)) {
                model.addAttribute(MODEL_APPLEPAY_MERCHANT_IDENTIFIER, applePayConfig.get("merchantId"));
                model.addAttribute(MODEL_APPLEPAY_MERCHANT_NAME, applePayConfig.get("merchantName"));
            } else {
                LOGGER.warn("Empty apple pay config");
            }

        } catch (IOException e) {
            LOGGER.error("Payment methods request failed", e);
        }

        final Amount amount = AmountUtil.createAmount(amountValue, currency);

        model.addAttribute(SHOPPER_LOCALE, getShopperLocale());
        model.addAttribute(MODEL_ENVIRONMENT_MODE, getEnvironmentMode());
        model.addAttribute(MODEL_CLIENT_KEY, baseStore.getAdyenClientKey());
        model.addAttribute(MODEL_MERCHANT_ACCOUNT, adyenMerchantAccountStrategy.getWebMerchantAccount());
        model.addAttribute(SESSION_DATA, getAdyenSessionData(amount));
        model.addAttribute(MODEL_AMOUNT, amount);
        model.addAttribute(MODEL_AMOUNT_DECIMAL, amountValue);
        model.addAttribute(MODEL_DF_URL, getAdyenPaymentService().getDeviceFingerprintUrl());
        model.addAttribute(MODEL_CHECKOUT_SHOPPER_HOST, getCheckoutShopperHost());
    }

    protected BigDecimal getExpressDeliveryModeValue(final String currencyIso) {
        Optional<ZoneDeliveryModeValueModel> expressDeliveryModePrice = adyenExpressCheckoutFacade.getExpressDeliveryModePrice();

        BigDecimal deliveryValue = BigDecimal.ZERO;

        if (expressDeliveryModePrice.isPresent()) {
            ZoneDeliveryModeValueModel zoneDeliveryModeValueModel = expressDeliveryModePrice.get();
            if (!StringUtils.equals(zoneDeliveryModeValueModel.getCurrency().getIsocode(), currencyIso)) {
                throw new IllegalArgumentException("Delivery and product currencies are not equal");
            }
            deliveryValue = BigDecimal.valueOf(zoneDeliveryModeValueModel.getValue());
        } else {
            LOGGER.warn("Empty delivery mode price");
        }
        return deliveryValue;
    }

    protected String setLocale(final Map<String, String> map, final String shopperLocale) {
        if (Objects.nonNull(map) && !map.get(REGION).isBlank() && map.get(REGION).equals(US)) {
            return US_LOCALE;
        } else {
            switch (shopperLocale) {
                case "de":
                    return DE_LOCALE;
                case "fr":
                    return FR_LOCALE;
                case "it":
                    return IT_LOCALE;
                case "es":
                    return ES_LOCALE;
                default:
                    return GB_LOCALE;
            }
        }
    }

    protected boolean isHiddenPaymentMethod(PaymentMethod paymentMethod) {
        String paymentMethodType = paymentMethod.getType();

        if (paymentMethodType == null || paymentMethodType.isEmpty() ||
                paymentMethodType.equals("scheme") ||
                (paymentMethodType.contains("wechatpay")
                        && !paymentMethodType.equals("wechatpayWeb")) ||
                paymentMethodType.startsWith(PAYMENT_METHOD_BOLETO) ||
                paymentMethodType.contains(PAYMENT_METHOD_SEPA_DIRECTDEBIT) ||
                (ISSUER_PAYMENT_METHODS.contains(paymentMethodType) &&
                        !paymentMethodType.equals(PAYMENT_METHOD_ONLINEBANKING_IN) &&
                        !paymentMethodType.equals(PAYMENT_METHOD_ONLINEBANKING_PL))) {
            return true;
        }
        return false;
    }

    protected List<StoredPaymentMethod> getStoredOneClickPaymentMethods(PaymentMethodsResponse response) {
        List<StoredPaymentMethod> storedPaymentMethodList = null;
        if (response.getStoredPaymentMethods() != null) {
            storedPaymentMethodList = response.getStoredPaymentMethods().stream()
                    .filter(storedPaymentMethod -> storedPaymentMethod.getSupportedShopperInteractions() != null
                            && storedPaymentMethod.getSupportedShopperInteractions().contains(ECOMMERCE_SHOPPER_INTERACTION))
                    .collect(Collectors.toList());
        }

        return storedPaymentMethodList;
    }

    @Override
    public boolean showBoleto() {
        BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();
        //Check base store settings
        if (baseStore.getAdyenBoleto() == null || !baseStore.getAdyenBoleto()) {
            return false;
        }

        CartData cartData = getCheckoutFacade().getCheckoutCart();
        String currency = cartData.getTotalPriceWithTax().getCurrencyIso();
        String country = cartData.getDeliveryAddress().getCountry().getIsocode();

        //Show only on Brasil with BRL
        return "BRL".equals(currency) && "BR".equals(country);
    }

    @Override
    public boolean showComboCard() {
        CartData cartData = getCheckoutFacade().getCheckoutCart();
        String currency = cartData.getTotalPriceWithTax().getCurrencyIso();
        return "BRL".equals(currency);
    }

    @Override
    public boolean showPos() {
        BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();
        //Check base store settings for POS Enabled or not.
        if (baseStore.getAdyenPosEnabled() == null || !baseStore.getAdyenPosEnabled()) {
            return false;
        }
        return true;
    }

    @Override
    public boolean showRememberDetails() {
        BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

        /*
         * The show remember me checkout should only be shown as the
         * user is logged in and the recurirng mode is set to ONECLICK or ONECLICK,RECURRING
         */
        RecurringContractMode recurringContractMode = baseStore.getAdyenRecurringContractMode();
        if (!getCheckoutCustomerStrategy().isAnonymousCheckout()) {
            if (Recurring.ContractEnum.RECURRING.name().equals(recurringContractMode.getCode()) || Recurring.ContractEnum.ONECLICK.name().equals(recurringContractMode.getCode())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean showSocialSecurityNumber() {
        Boolean showSocialSecurityNumber = false;
        CartData cart = getCheckoutFacade().getCheckoutCart();
        final AddressData deliveryAddress = cart.getDeliveryAddress();
        String countryCode = deliveryAddress.getCountry().getIsocode();
        if (PAYMENT_METHODS_ALLOW_SOCIAL_SECURITY_NUMBER.contains(cart.getAdyenPaymentMethod()) && OPENINVOICE_METHODS_ALLOW_SOCIAL_SECURITY_NUMBER.contains(countryCode)) {
            showSocialSecurityNumber = true;
        }
        return showSocialSecurityNumber;
    }

    @Override
    public PaymentInfoModel createPaymentInfo(final CartModel cartModel, AdyenPaymentForm adyenPaymentForm) {
        final PaymentInfoModel paymentInfo = modelService.create(PaymentInfoModel.class);
        paymentInfo.setUser(cartModel.getUser());
        paymentInfo.setSaved(false);
        paymentInfo.setCode(generateCcPaymentInfoCode(cartModel));

        if (adyenPaymentForm.getUseAdyenDeliveryAddress() == true) {
            // Clone DeliveryAdress to BillingAddress
            final AddressModel clonedAddress = modelService.clone(cartModel.getDeliveryAddress());
            clonedAddress.setBillingAddress(true);
            clonedAddress.setOwner(paymentInfo);
            paymentInfo.setBillingAddress(clonedAddress);
        } else {
            AddressModel billingAddress = convertToAddressModel(adyenPaymentForm.getBillingAddress());
            paymentInfo.setBillingAddress(billingAddress);
            billingAddress.setOwner(paymentInfo);
            paymentInfo.setBillingAddress(billingAddress);
        }

        paymentInfo.setAdyenPaymentMethod(adyenPaymentForm.getPaymentMethod());
        paymentInfo.setAdyenIssuerId(adyenPaymentForm.getIssuerId());
        paymentInfo.setAdyenUPIVirtualAddress(adyenPaymentForm.getUpiVirtualAddress());
        paymentInfo.setAdyenRememberTheseDetails(adyenPaymentForm.getRememberTheseDetails());
        paymentInfo.setAdyenSelectedReference(adyenPaymentForm.getSelectedReference());

        // openinvoice fields
        paymentInfo.setAdyenDob(adyenPaymentForm.getDob());

        paymentInfo.setAdyenSocialSecurityNumber(adyenPaymentForm.getSocialSecurityNumber());

        paymentInfo.setAdyenSepaOwnerName(adyenPaymentForm.getSepaOwnerName());
        paymentInfo.setAdyenSepaIbanNumber(adyenPaymentForm.getSepaIbanNumber());

        // AfterPay fields
        paymentInfo.setAdyenTelephone(cartModel.getDeliveryAddress().getPhone1());
        paymentInfo.setAdyenShopperEmail(adyenPaymentForm.getShopperEmail());
        paymentInfo.setAdyenShopperGender(adyenPaymentForm.getGender());

        // Boleto fields
        paymentInfo.setAdyenFirstName(adyenPaymentForm.getFirstName());
        paymentInfo.setAdyenLastName(adyenPaymentForm.getLastName());

        paymentInfo.setAdyenCardHolder(adyenPaymentForm.getCardHolder());

        //required for 3DS2
        paymentInfo.setAdyenBrowserInfo(adyenPaymentForm.getBrowserInfo());

        //pos field(s)
        paymentInfo.setAdyenTerminalId(adyenPaymentForm.getTerminalId());

        //apple pay
        paymentInfo.setAdyenApplePayMerchantName(cartModel.getAdyenApplePayMerchantName());
        paymentInfo.setAdyenApplePayMerchantIdentifier(cartModel.getAdyenApplePayMerchantIdentifier());

        //combo card fields
        paymentInfo.setCardType(adyenPaymentForm.getCardType());
        paymentInfo.setCardBrand(adyenPaymentForm.getCardBrand());

        // Gift card
        paymentInfo.setAdyenGiftCardBrand(adyenPaymentForm.getGiftCardBrand());

        modelService.save(paymentInfo);

        return paymentInfo;
    }

    public PaymentInfoModel createPaymentInfo(final CartModel cartModel, PaymentDetailsWsDTO paymentDetails) {
        final PaymentInfoModel paymentInfo = modelService.create(PaymentInfoModel.class);
        paymentInfo.setUser(cartModel.getUser());
        paymentInfo.setSaved(false);
        paymentInfo.setCode(generateCcPaymentInfoCode(cartModel));

        paymentInfo.setAdyenIssuerId(paymentDetails.getIssueNumber());

        paymentInfo.setAdyenCardHolder(paymentDetails.getAccountHolderName());
        paymentInfo.setEncryptedCardNumber(paymentDetails.getEncryptedCardNumber());
        paymentInfo.setEncryptedExpiryMonth(paymentDetails.getEncryptedExpiryMonth());
        paymentInfo.setEncryptedExpiryYear(paymentDetails.getEncryptedExpiryYear());
        paymentInfo.setEncryptedSecurityCode(paymentDetails.getEncryptedSecurityCode());
        paymentInfo.setAdyenRememberTheseDetails(paymentDetails.getSaveCardData());
        paymentInfo.setAdyenPaymentMethod(paymentDetails.getAdyenPaymentMethod());
        paymentInfo.setAdyenSelectedReference(paymentDetails.getAdyenSelectedReference());
        paymentInfo.setAdyenSocialSecurityNumber(paymentDetails.getAdyenSocialSecurityNumber());
        paymentInfo.setAdyenSepaOwnerName(paymentDetails.getAdyenSepaOwnerName());
        paymentInfo.setAdyenSepaIbanNumber(paymentDetails.getAdyenSepaIbanNumber());
        paymentInfo.setAdyenFirstName(paymentDetails.getAdyenFirstName());
        paymentInfo.setAdyenLastName(paymentDetails.getAdyenLastName());
        paymentInfo.setOwner(cartModel.getOwner());
        paymentInfo.setAdyenTerminalId(paymentDetails.getTerminalId());
        paymentInfo.setAdyenInstallments(paymentDetails.getInstallments());
        return paymentInfo;
    }

    @Override
    public void handlePaymentForm(AdyenPaymentForm adyenPaymentForm, Errors errors) {

        CartModel cartModel = cartService.getSessionCart();
        boolean showRememberDetails = showRememberDetails();
        boolean showSocialSecurityNumber = showSocialSecurityNumber();
        boolean holderNameRequired = getHolderNameRequired();

        AdyenPaymentFormValidator adyenPaymentFormValidator = new AdyenPaymentFormValidator(cartModel.getAdyenStoredCards(), showRememberDetails, showSocialSecurityNumber, holderNameRequired);

        if (PAYBRIGHT.equals(adyenPaymentForm.getPaymentMethod())) {
            adyenPaymentFormValidator.setTelephoneNumberRequired(true);
        }

        adyenPaymentFormValidator.validate(adyenPaymentForm, errors);

        if (errors.hasErrors()) {
            return;
        }

        if (!checkoutCustomerStrategy.isAnonymousCheckout() && adyenPaymentForm.getBillingAddress() != null
                && adyenPaymentForm.getBillingAddress().isSaveInAddressBook()) {
            AddressData addressData = convertToAddressData(adyenPaymentForm.getBillingAddress());
            addressData.setVisibleInAddressBook(true);
            addressData.setShippingAddress(true);
            userFacade.addAddress(addressData);
        }

        //Put encrypted data to session
        if (!StringUtils.isEmpty(adyenPaymentForm.getCseToken())) {
            getSessionService().setAttribute(SESSION_CSE_TOKEN, adyenPaymentForm.getCseToken());
        }
        if (!StringUtils.isEmpty(adyenPaymentForm.getEncryptedCardNumber())) {
            getSessionService().setAttribute(SESSION_SF_CARD_NUMBER, adyenPaymentForm.getEncryptedCardNumber());
        }
        if (!StringUtils.isEmpty(adyenPaymentForm.getEncryptedExpiryMonth())) {
            getSessionService().setAttribute(SESSION_SF_EXPIRY_MONTH, adyenPaymentForm.getEncryptedExpiryMonth());
        }
        if (!StringUtils.isEmpty(adyenPaymentForm.getEncryptedExpiryYear())) {
            getSessionService().setAttribute(SESSION_SF_EXPIRY_YEAR, adyenPaymentForm.getEncryptedExpiryYear());
        }
        if (!StringUtils.isEmpty(adyenPaymentForm.getEncryptedSecurityCode())) {
            getSessionService().setAttribute(SESSION_SF_SECURITY_CODE, adyenPaymentForm.getEncryptedSecurityCode());
        }
        if (!StringUtils.isEmpty(adyenPaymentForm.getCardBrand())) {
            getSessionService().setAttribute(SESSION_CARD_BRAND, adyenPaymentForm.getCardBrand());
        }
        if (!StringUtils.isEmpty(adyenPaymentForm.getRiskData())) {
            getSessionService().setAttribute(SESSION_ADYEN_RISK_DATA, adyenPaymentForm.getRiskData());
        }

        //Update CartModel
        cartModel.setAdyenDfValue(adyenPaymentForm.getDfValue());

        transactionTemplate.execute(transactionStatus -> {
            //Create payment info
            PaymentInfoModel paymentInfo = createPaymentInfo(cartModel, adyenPaymentForm);
            cartModel.setPaymentInfo(paymentInfo);
            modelService.save(cartModel);
            return null;
        });
    }

    public AddressModel convertToAddressModel(final AddressForm addressForm) {
        final AddressData addressData = convertToAddressData(addressForm);
        final AddressModel billingAddress = getModelService().create(AddressModel.class);
        getAddressReverseConverter().convert(addressData, billingAddress);

        return billingAddress;
    }

    protected AddressData convertToAddressData(AddressForm addressForm) {
        final AddressData addressData = new AddressData();
        final CountryData countryData = getI18NFacade().getCountryForIsocode(addressForm.getCountryIso());
        addressData.setTitleCode(addressForm.getTitleCode());
        addressData.setFirstName(addressForm.getFirstName());
        addressData.setLastName(addressForm.getLastName());
        addressData.setLine1(addressForm.getLine1());
        addressData.setLine2(addressForm.getLine2());
        addressData.setTown(addressForm.getTownCity());
        addressData.setPostalCode(addressForm.getPostcode());
        addressData.setBillingAddress(true);
        addressData.setCountry(countryData);
        addressData.setPhone(addressForm.getPhoneNumber());

        if (addressForm.getRegionIso() != null && !org.apache.commons.lang.StringUtils.isEmpty(addressForm.getRegionIso())) {
            final RegionData regionData = getI18NFacade().getRegion(addressForm.getCountryIso(), addressForm.getRegionIso());
            addressData.setRegion(regionData);
        }
        return addressData;
    }

    @Override
    public String getShopperLocale() {
        if (commonI18NService.getCurrentLanguage() != null) {
            return commonI18NService.getCurrentLanguage().getIsocode();
        }

        return null;
    }

    protected String generateCcPaymentInfoCode(final CartModel cartModel) {
        return cartModel.getCode() + "_" + UUID.randomUUID();
    }

    public AdyenCheckoutApiService getAdyenPaymentService() {
        return adyenPaymentServiceFactory.createAdyenCheckoutApiService(baseStoreService.getCurrentBaseStore());
    }

    /**
     * Initiate POS Payment using Adyen Terminal API
     */
    @Override
    public OrderData initiatePosPayment(HttpServletRequest request, CartData cartData) throws Exception {
        CustomerModel customer = null;
        if (!getCheckoutCustomerStrategy().isAnonymousCheckout()) {
            customer = getCheckoutCustomerStrategy().getCurrentUserForCheckout();
        }
        //This will be used to check status later

        String serviceId = request.getAttribute("originalServiceId").toString();
        TerminalAPIResponse terminalApiResponse = getAdyenPaymentService().sendSyncPosPaymentRequest(cartData, customer, serviceId);
        ResultType resultType = TerminalAPIUtil.getPaymentResultFromStatusOrPaymentResponse(terminalApiResponse);

        if (ResultType.SUCCESS == resultType) {
            PaymentResponse paymentsResponse = getPosPaymentResponseConverter().convert(terminalApiResponse.getSaleToPOIResponse());
            String posReceipt = TerminalAPIUtil.getReceiptFromPaymentResponse(terminalApiResponse);
            if (StringUtils.isNotEmpty(posReceipt)) {
                paymentsResponse.putAdditionalDataItem("pos.receipt", posReceipt);
            }
            return createAuthorizedOrder(paymentsResponse);
        }
        throw new AdyenNonAuthorizedPaymentException(terminalApiResponse);
    }

    /**
     * Check POS Payment Status using Adyen Terminal API
     */
    @Override
    public OrderData checkPosPaymentStatus(HttpServletRequest request, CartData cartData) throws Exception {

        String originalServiceId = request.getAttribute("originalServiceId").toString();
        TerminalAPIResponse terminalApiResponse = getAdyenPaymentService().sendSyncPosStatusRequest(cartData, originalServiceId);
        ResultType statusResult = TerminalAPIUtil.getStatusResultFromStatusResponse(terminalApiResponse);

        if (statusResult != null) {
            if (statusResult == ResultType.SUCCESS) {
                //this will be success even if payment is failed. because this belongs to status call not payment call
                ResultType paymentResult = TerminalAPIUtil.getPaymentResultFromStatusOrPaymentResponse(terminalApiResponse);
                if (paymentResult == ResultType.SUCCESS) {
                    PaymentResponse paymentsResponse = getPosPaymentResponseConverter().convert(terminalApiResponse.getSaleToPOIResponse());
                    String posReceipt = TerminalAPIUtil.getReceiptFromStatusResponse(terminalApiResponse);
                    if (StringUtils.isNotEmpty(posReceipt)) {
                        paymentsResponse.putAdditionalDataItem("pos.receipt", posReceipt);
                    }
                    return createAuthorizedOrder(paymentsResponse);
                } else {
                    throw new AdyenNonAuthorizedPaymentException(terminalApiResponse);
                }
            } else {
                ErrorConditionType errorCondition = TerminalAPIUtil.getErrorConditionForStatusFromStatusResponse(terminalApiResponse);
                //If transaction is still in progress, keep retrying in 5 seconds.
                if (errorCondition == ErrorConditionType.IN_PROGRESS) {
                    TimeUnit.SECONDS.sleep(5);
                    if (isPosTimedOut(request)) {
                        throw new AdyenNonAuthorizedPaymentException(terminalApiResponse);
                    } else {
                        return checkPosPaymentStatus(request, cartData);
                    }
                } else {
                    throw new AdyenNonAuthorizedPaymentException(terminalApiResponse);
                }
            }
        }

        //probably returned SaleToPOIRequest, that means terminal unreachable, return the response as error
        throw new AdyenNonAuthorizedPaymentException(terminalApiResponse);
    }

    protected boolean isPosTimedOut(HttpServletRequest request) {
        long currentTime = System.currentTimeMillis();
        long processStartTime = (long) request.getAttribute("paymentStartTime");
        int totalTimeout = ((int) request.getAttribute("totalTimeout")) * 1000;
        long timeDiff = currentTime - processStartTime;
        if (timeDiff >= totalTimeout) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isImmediateCapture() {
        BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();
        if (baseStore.getAdyenImmediateCapture() == null) {
            return true;
        }
        return baseStore.getAdyenImmediateCapture();
    }

    @Override
    public OrderData handleComponentResult(String resultCode, String merchantReference) throws Exception {

        if (StringUtils.equals(PaymentResponse.ResultCodeEnum.AUTHORISED.getValue(), resultCode)
                || StringUtils.equals(PaymentResponse.ResultCodeEnum.RECEIVED.getValue(), resultCode)) {
            OrderModel orderModel = retrievePendingOrder(merchantReference);
            return getOrderConverter().convert(orderModel);
        }

        if (StringUtils.equals(PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER.getValue(), resultCode)) {
            restoreCartFromOrder(merchantReference);
        }
        throw new AdyenNonAuthorizedPaymentException(merchantReference);
    }

    protected OrderModel retrievePendingOrder(String orderCode) throws InvalidCartException {
        if (orderCode == null || orderCode.isEmpty()) {
            throw new InvalidCartException("Could not retrieve pending order: missing orderCode!");
        }

        OrderModel orderModel = getOrderRepository().getOrderModel(orderCode);
        if (orderModel == null) {
            throw new InvalidCartException("Order '" + orderCode + "' does not exist!");
        }

        getSessionService().removeAttribute(SESSION_PENDING_ORDER_CODE);
        getSessionService().removeAttribute(THREEDS2_FINGERPRINT_TOKEN);
        getSessionService().removeAttribute(THREEDS2_CHALLENGE_TOKEN);
        getSessionService().removeAttribute(PAYMENT_METHOD);

        return orderModel;
    }

    protected void restoreCartFromOrder(String orderCode) throws CalculationException, InvalidCartException {
        LOGGER.info("Restoring cart from order");

        OrderModel orderModel = getOrderRepository().getOrderModel(orderCode);

        if (orderModel == null) {
            LOGGER.error("Could not restore cart to session, order with code '" + orderCode + "' not found!");
            return;
        }

        restoreCartFromOrderInternal(orderModel);
    }

    public void restoreCartFromOrderOCC(String orderCode) throws CalculationException, InvalidCartException {
        LOGGER.info("Restoring cart from order");

        OrderModel orderModel = adyenOrderFacade.getOrderModelForCodeOCC(orderCode);

        if (orderModel == null) {
            LOGGER.error("Could not restore cart to session, order with code '" + orderCode + "' not found!");
            return;
        }

        restoreCartFromOrderInternal(orderModel);
    }

    protected void restoreCartFromOrderInternal(final OrderModel orderModel) throws CalculationException, InvalidCartException{

        // Get cart from session
        CartModel cartModel;
        if (getCartService().hasSessionCart()) {
            cartModel = getCartService().getSessionCart();
        }
        // Or create new cart if no cart in session
        else {
            cartModel = getCartFactory().createCart();
            getCartService().setSessionCart(cartModel);
        }

        Boolean isAnonymousCheckout = getCheckoutCustomerStrategy().isAnonymousCheckout();

        if (!isAnonymousCheckout && hasUserContextChanged(orderModel, cartModel)) {
            throw new InvalidCartException("Cart from order '" + orderModel.getCode() + "' not restored to session, since user or store in session changed.");
        }

        //Populate cart entries
        for (AbstractOrderEntryModel entryModel : orderModel.getEntries()) {
            getCartService().addNewEntry(cartModel, entryModel.getProduct(), entryModel.getQuantity(), entryModel.getUnit());
        }

        PaymentMethodsCartData paymentMethodsCartData = getSessionService().getAttribute(SESSION_PAYMENT_METHODS_CART_DATA);
        restorePaymentMethodsDataOnCart(paymentMethodsCartData, cartModel);
        getSessionService().removeAttribute(SESSION_PAYMENT_METHODS_CART_DATA);

        getModelService().save(cartModel);

        if (isAnonymousCheckout) {
            cartModel.setUser(orderModel.getUser());
            cartModel.setDeliveryAddress(orderModel.getDeliveryAddress().getOriginal());
            cartModel.setDeliveryMode(orderModel.getDeliveryMode());
            if (orderModel.getPaymentAddress() != null) {
                cartModel.setPaymentAddress(orderModel.getPaymentAddress().getOriginal());
            }
            getModelService().save(cartModel);
        } else {
            //Populate delivery address and mode
            AddressData deliveryAddressData = new AddressData();
            getAddressPopulator().populate(orderModel.getDeliveryAddress().getOriginal(), deliveryAddressData);
            getCheckoutFacade().setDeliveryAddress(deliveryAddressData);
            getCheckoutFacade().setDeliveryMode(orderModel.getDeliveryMode().getCode());
        }

        getCalculationService().calculate(cartModel);
    }

    protected boolean hasUserContextChanged(OrderModel orderModel, CartModel cartModel) {
        return !orderModel.getUser().equals(cartModel.getUser())
                || !orderModel.getStore().equals(cartModel.getStore());
    }

    @Override
    public void restoreCartFromOrderCodeInSession() throws InvalidCartException, CalculationException {
        String orderCode = getSessionService().getAttribute(SESSION_PENDING_ORDER_CODE);
        if (orderCode == null) {
            LOGGER.info("OrderCode not in session, no cart will be restored");
            return;
        }

        OrderModel orderModel = retrievePendingOrder(orderCode);

        orderModel.setStatus(OrderStatus.PROCESSING_ERROR);
        orderModel.setStatusInfo("AdyenException");
        getModelService().save(orderModel);
        getAdyenBusinessProcessService().triggerOrderProcessEvent(orderModel, Adyenv6coreConstants.PROCESS_EVENT_ADYEN_PAYMENT_RESULT);

        getSessionService().removeAttribute(SESSION_PENDING_ORDER_CODE);
        getSessionService().removeAttribute(THREEDS2_FINGERPRINT_TOKEN);
        getSessionService().removeAttribute(THREEDS2_CHALLENGE_TOKEN);
        getSessionService().removeAttribute(PAYMENT_METHOD);

        restoreCartFromOrder(orderCode);
    }

    public boolean getHolderNameRequired() {
        boolean holderNameRequired = true;
        Configuration configuration = this.configurationService.getConfiguration();
        if (configuration != null && configuration.containsKey(IS_CARD_HOLDER_NAME_REQUIRED_PROPERTY)) {
            holderNameRequired = configuration.getBoolean(IS_CARD_HOLDER_NAME_REQUIRED_PROPERTY);
        }
        return holderNameRequired;
    }

    public Set<String> getStoredCards() {
        CartModel cartModel = cartService.getSessionCart();
        return cartModel.getAdyenStoredCards();
    }

    protected PaymentMethodsCartData getPaymentMethodsCartData(final CartModel cartModel) {
        PaymentMethodsCartData paymentMethodsCartData = new PaymentMethodsCartData();
        paymentMethodsCartData.setAdyenDfValue(cartModel.getAdyenDfValue());
        paymentMethodsCartData.setAdyenStoredCards(cartModel.getAdyenStoredCards());
        paymentMethodsCartData.setAdyenApplePayMerchantName(cartModel.getAdyenApplePayMerchantName());
        paymentMethodsCartData.setAdyenAmazonPayConfiguration(cartModel.getAdyenAmazonPayConfiguration());

        return paymentMethodsCartData;
    }

    protected void restorePaymentMethodsDataOnCart(final PaymentMethodsCartData paymentMethodsCartData, final CartModel cartModel) {
        if (paymentMethodsCartData != null) {
            cartModel.setAdyenDfValue(paymentMethodsCartData.getAdyenDfValue());
            cartModel.setAdyenStoredCards(paymentMethodsCartData.getAdyenStoredCards());
            cartModel.setAdyenApplePayMerchantName(paymentMethodsCartData.getAdyenApplePayMerchantName());
            cartModel.setAdyenAmazonPayConfiguration(paymentMethodsCartData.getAdyenAmazonPayConfiguration());
        } else {
            LOGGER.warn("Empty payment methods cart data in session");
        }
    }

    public BaseStoreService getBaseStoreService() {
        return baseStoreService;
    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public CartService getCartService() {
        return cartService;
    }

    public void setCartService(CartService cartService) {
        this.cartService = cartService;
    }

    public OrderFacade getOrderFacade() {
        return orderFacade;
    }

    public void setOrderFacade(OrderFacade orderFacade) {
        this.orderFacade = orderFacade;
    }

    public CheckoutFacade getCheckoutFacade() {
        return checkoutFacade;
    }

    public void setCheckoutFacade(CheckoutFacade checkoutFacade) {
        this.checkoutFacade = checkoutFacade;
    }

    public AdyenTransactionService getAdyenTransactionService() {
        return adyenTransactionService;
    }

    public void setAdyenTransactionService(AdyenTransactionService adyenTransactionService) {
        this.adyenTransactionService = adyenTransactionService;
    }

    public OrderRepository getOrderRepository() {
        return orderRepository;
    }

    public void setOrderRepository(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public AdyenOrderService getAdyenOrderService() {
        return adyenOrderService;
    }

    public void setAdyenOrderService(AdyenOrderService adyenOrderService) {
        this.adyenOrderService = adyenOrderService;
    }

    public CheckoutCustomerStrategy getCheckoutCustomerStrategy() {
        return checkoutCustomerStrategy;
    }

    public void setCheckoutCustomerStrategy(CheckoutCustomerStrategy checkoutCustomerStrategy) {
        this.checkoutCustomerStrategy = checkoutCustomerStrategy;
    }

    public AdyenPaymentServiceFactory getAdyenPaymentServiceFactory() {
        return adyenPaymentServiceFactory;
    }

    public void setAdyenPaymentServiceFactory(AdyenPaymentServiceFactory adyenPaymentServiceFactory) {
        this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
    }

    public ModelService getModelService() {
        return modelService;
    }

    public void setModelService(ModelService modelService) {
        this.modelService = modelService;
    }

    public CommonI18NService getCommonI18NService() {
        return commonI18NService;
    }

    public void setCommonI18NService(CommonI18NService commonI18NService) {
        this.commonI18NService = commonI18NService;
    }

    public KeyGenerator getKeyGenerator() {
        return keyGenerator;
    }

    public void setKeyGenerator(KeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;
    }

    public FlexibleSearchService getFlexibleSearchService() {
        return flexibleSearchService;
    }

    public void setFlexibleSearchService(FlexibleSearchService flexibleSearchService) {
        this.flexibleSearchService = flexibleSearchService;
    }

    public Converter<AddressData, AddressModel> getAddressReverseConverter() {
        return addressReverseConverter;
    }

    public void setAddressReverseConverter(Converter<AddressData, AddressModel> addressReverseConverter) {
        this.addressReverseConverter = addressReverseConverter;
    }

    public I18NFacade getI18NFacade() {
        return i18NFacade;
    }

    public void setI18NFacade(I18NFacade i18NFacade) {
        this.i18NFacade = i18NFacade;
    }

    public PosPaymentResponseConverter getPosPaymentResponseConverter() {
        return posPaymentResponseConverter;
    }

    public void setPosPaymentResponseConverter(PosPaymentResponseConverter posPaymentResponseConverter) {
        this.posPaymentResponseConverter = posPaymentResponseConverter;
    }

    protected Converter<CountryModel, CountryData> getCountryConverter() {
        return countryConverter;
    }

    @Required
    public void setCountryConverter(final Converter<CountryModel, CountryData> countryConverter) {
        this.countryConverter = countryConverter;
    }

    public Converter<OrderModel, OrderData> getOrderConverter() {
        return orderConverter;
    }

    public void setOrderConverter(Converter<OrderModel, OrderData> orderConverter) {
        this.orderConverter = orderConverter;
    }

    public CartFactory getCartFactory() {
        return cartFactory;
    }

    public void setCartFactory(CartFactory cartFactory) {
        this.cartFactory = cartFactory;
    }

    public CalculationService getCalculationService() {
        return calculationService;
    }

    public void setCalculationService(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    public Populator<AddressModel, AddressData> getAddressPopulator() {
        return addressPopulator;
    }

    public void setAddressPopulator(Populator<AddressModel, AddressData> addressPopulator) {
        this.addressPopulator = addressPopulator;
    }

    public AdyenBusinessProcessService getAdyenBusinessProcessService() {
        return adyenBusinessProcessService;
    }

    public void setAdyenBusinessProcessService(AdyenBusinessProcessService adyenBusinessProcessService) {
        this.adyenBusinessProcessService = adyenBusinessProcessService;
    }


    public void setTransactionTemplate(TransactionOperations transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public void setAdyenExpressCheckoutFacade(AdyenExpressCheckoutFacade adyenExpressCheckoutFacade) {
        this.adyenExpressCheckoutFacade = adyenExpressCheckoutFacade;
    }

    public void setUserFacade(UserFacade userFacade) {
        this.userFacade = userFacade;
    }

    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    protected TransactionOperations getTransactionTemplate() {
        return transactionTemplate;
    }

    protected UserFacade getUserFacade() {
        return userFacade;
    }

    public void setAdyenMerchantAccountStrategy(AdyenMerchantAccountStrategy adyenMerchantAccountStrategy) {
        this.adyenMerchantAccountStrategy = adyenMerchantAccountStrategy;
    }

    public void setAdyenOrderFacade(AdyenOrderFacade adyenOrderFacade) {
        this.adyenOrderFacade = adyenOrderFacade;
    }
}
