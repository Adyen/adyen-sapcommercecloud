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
package com.adyen.commerce.facades.impl;


import com.adyen.commerce.data.PaymentMethodsCartData;
import com.adyen.commerce.services.AdyenPaymentMethodConfigService;
import com.adyen.model.checkout.*;
import com.adyen.model.recurring.Recurring;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.StorefrontType;
import com.adyen.v6.controllers.dtos.PaymentResultDTO;
import com.adyen.v6.dto.*;
import com.adyen.v6.enums.AdyenCardTypeEnum;
import com.adyen.v6.enums.AdyenRegions;
import com.adyen.v6.enums.RecurringContractMode;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.commerce.facades.AdyenCheckoutFacade;
import com.adyen.commerce.facades.AdyenExpressCheckoutFacade;
import com.adyen.commerce.facades.AdyenOrderFacade;
import com.adyen.v6.converters.ExpressPaymentConfigConverter;
import com.adyen.commerce.factory.AdyenPaymentInfoFactory;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.commerce.populators.AdyenCheckoutModelPopulator;
import com.adyen.v6.forms.AddressForm;
import com.adyen.v6.forms.AdyenPaymentForm;
import com.adyen.v6.forms.validation.AdyenPaymentFormValidator;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.repository.OrderRepository;
import com.adyen.v6.service.*;
import com.adyen.commerce.services.AdyenCartRestorationService;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;
import com.adyen.v6.util.AmountUtil;
import com.google.gson.Gson;
import de.hybris.platform.commercefacades.i18n.I18NFacade;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commercefacades.product.ProductOption;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.user.data.RegionData;
import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.commercewebservicescommons.dto.order.PaymentDetailsWsDTO;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.c2l.CountryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
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
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.Errors;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.adyen.v6.constants.Adyenv6coreConstants.*;

/**
 * Adyen Checkout Facade for initiating payments using CC or APM
 */
public class DefaultAdyenCheckoutFacade implements AdyenCheckoutFacade {

    public static final String DETAILS = "details";
    private static final String RECURRING_RECURRING_DETAIL_REFERENCE = "recurring.recurringDetailReference";
    public static final String EXPRESS_PAYMENT_CONFIG = "expressPaymentConfig";

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
    private ProductFacade productFacade;
    private CommerceCartService commerceCartService;
    private ThreeDSAuthorizationService threeDSAuthorizationService;
    private AdyenShopperIpResolverService adyenShopperIpResolverService;
    private AdyenInstallmentsConfigurationService adyenInstallmentsConfigurationService;
    private AdyenPaymentInfoFactory adyenPaymentInfoFactory;
    private AdyenCartRestorationService adyenCartRestorationService;
    private AdyenCheckoutModelPopulator adyenCheckoutModelPopulator;
    private AdyenPaymentMethodConfigService adyenPaymentMethodConfigService;
    private ExpressPaymentConfigConverter expressPaymentConfigConverter;

    protected static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(DefaultAdyenCheckoutFacade.class);

    private static final String ECOMMERCE_SHOPPER_INTERACTION = "Ecommerce";
    private static final String IS_CARD_HOLDER_NAME_REQUIRED_PROPERTY = "adyen.card.holderName.required";

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
    // Session attribute keys
    public static final String MODEL_SELECTED_PAYMENT_METHOD = "selectedPaymentMethod";
    public static final String MODEL_PAYMENT_METHODS = "paymentMethods";
    public static final String MODEL_CLIENT_KEY = "clientKey";
    public static final String MODEL_CHECKOUT_SHOPPER_HOST = "checkoutShopperHost";
    public static final String MODEL_ENVIRONMENT_MODE = "environmentMode";
    public static final String MODEL_ISSUER_LISTS = "issuerLists";
    public static final String CHECKOUT_SHOPPER_HOST_TEST = "checkoutshopper-test.adyen.com";
    public static final String CHECKOUT_SHOPPER_HOST_LIVE = "checkoutshopper-live.adyen.com";
    public static final String CHECKOUT_SHOPPER_HOST_LIVE_IN = "checkoutshopper-live-in.adyen.com";
    private static final Long ZERO_AUTH_VALUE = 0L;

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
            return TEST_ENV;
        }
        if (AdyenRegions.IN.equals(baseStoreService.getCurrentBaseStore().getAdyenRegion())) {
            return "live-in";
        }
        return LIVE_ENV;
    }

    @Override
    public String getClientKey() {
        return baseStoreService.getCurrentBaseStore().getAdyenClientKey();
    }

    @Override
    public void lockSessionCart() {
        adyenCartRestorationService.lockSessionCart();
    }

    @Override
    public CartModel restoreSessionCart() throws InvalidCartException {
        return adyenCartRestorationService.restoreSessionCart();
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
        final AddressModel billingAddress = getModelService().create(AddressModel.class);

        final AddressData addressData = new AddressData();
        addressData.setTitleCode(paymentDetails.getBillingAddress().getTitleCode());
        addressData.setFirstName(paymentDetails.getBillingAddress().getFirstName());
        addressData.setLastName(paymentDetails.getBillingAddress().getLastName());
        addressData.setLine1(paymentDetails.getBillingAddress().getLine1());
        addressData.setLine2(paymentDetails.getBillingAddress().getLine2());
        addressData.setTown(paymentDetails.getBillingAddress().getTown());
        addressData.setPostalCode(paymentDetails.getBillingAddress().getPostalCode());
        addressData.setBillingAddress(true);

        if (paymentDetails.getBillingAddress().getCountry() != null) {
            final CountryData countryData = getI18NFacade().getCountryForIsocode(paymentDetails.getBillingAddress().getCountry().getIsocode());
            addressData.setCountry(countryData);
        }
        if (paymentDetails.getBillingAddress().getRegion() != null
                && paymentDetails.getBillingAddress().getRegion().getIsocode() != null) {
            final RegionData regionData = getI18NFacade().getRegion(
                    paymentDetails.getBillingAddress().getCountry().getIsocode(),
                    paymentDetails.getBillingAddress().getRegion().getIsocode());
            addressData.setRegion(regionData);
        }

        getAddressReverseConverter().convert(addressData, billingAddress);

        return billingAddress;
    }

    @Override
    public PaymentDetailsResponse handleRedirectPayload(PaymentCompletionDetails details)
            throws AdyenNonAuthorizedPaymentException, InvalidCartException, CalculationException {
        PaymentDetailsResponse response;
        try {
            response = getAdyenPaymentService().getPaymentDetailsFromPayload(details);
        } catch (Exception e) {
            LOGGER.error("Failed to get payment details from payload", e);
            restoreCartFromOrderCodeInSession();
            throw new AdyenNonAuthorizedPaymentException(e.getMessage());
        }

        String orderCode = response.getMerchantReference();
        OrderModel orderModel = retrievePendingOrder(orderCode);
        threeDSAuthorizationService.updateOrderPaymentStatusAndInfo(orderModel, response);

        if (!(PaymentDetailsResponse.ResultCodeEnum.AUTHORISED.equals(response.getResultCode())
                || PaymentDetailsResponse.ResultCodeEnum.RECEIVED.equals(response.getResultCode()))) {
            restoreCartFromOrder(orderCode);
        }

        return response;
    }


    @Override
    public OrderData authorisePayment(final HttpServletRequest request, final CartData cartData)
            throws AdyenNonAuthorizedPaymentException, InvalidCartException, ApiException, IOException {
        CheckoutCustomerStrategy checkoutCustomerStrategy = getCheckoutCustomerStrategy();

        CustomerModel customer = checkoutCustomerStrategy.getCurrentUserForCheckout();

        updateCartWithSessionData(cartData);
        String adyenPaymentMethod = cartData.getAdyenPaymentMethod();

        String shopperIp = adyenShopperIpResolverService.resolveShopperIp(request);

        RequestInfo requestInfo = new RequestInfo(request, shopperIp);
        requestInfo.setStorefrontType(StorefrontType.ACCELERATOR);
        requestInfo.setShopperLocale(getShopperLocale());

        PaymentResponse paymentResponse;
        try {
            paymentResponse = getAdyenPaymentService().processPaymentRequest(cartData, null, requestInfo, customer);
        } catch (ApiException | IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Payment request failed", e);
        }
        PaymentResponse.ResultCodeEnum resultCode = paymentResponse.getResultCode();
        PaymentResponseAction action = paymentResponse.getAction();


        LOGGER.info("Authorize payment with result code: {} action: {}", resultCode, action != null ? action.getSchemaType() : "null");

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
    public OrderData handleResultcomponentPayment(final PaymentResultDTO paymentResultDTO) throws InvalidCartException {
        if (PaymentResponse.ResultCodeEnum.PENDING.getValue().equals(paymentResultDTO.getResultCode()) ||
                PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER.getValue().equals(paymentResultDTO.getResultCode())) {
            LOGGER.info("Placing pending order");
            return placePendingOrder(paymentResultDTO.getResultCode()); // NOSONAR
        }
        if (PaymentResponse.ResultCodeEnum.AUTHORISED.getValue().equals(paymentResultDTO.getResultCode())) {
            LOGGER.info("Placing authorized order");
            return placeAuthorisedOrder(PaymentResponse.ResultCodeEnum.AUTHORISED);
        }
        return null;
    }

    @Override
    public PaymentResponse componentPayment(final HttpServletRequest request, final CartData cartData, PaymentRequest paymentRequest)
            throws AdyenNonAuthorizedPaymentException, InvalidCartException, ApiException, IOException {
        updateCartWithSessionData(cartData);

        String shopperIp = adyenShopperIpResolverService.resolveShopperIp(request);

        RequestInfo requestInfo = new RequestInfo(request, shopperIp);
        requestInfo.setStorefrontType(StorefrontType.ACCELERATOR);
        requestInfo.setShopperLocale(getShopperLocale());

        PaymentResponse paymentResponse;
        try {
            paymentResponse = getAdyenPaymentService().processPaymentRequest(cartData, paymentRequest, requestInfo, getCheckoutCustomerStrategy().getCurrentUserForCheckout());
        } catch (ApiException | IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Payment request failed", e);
        }
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
    public PaymentDetailsResponse componentDetails(PaymentDetailsRequest detailsRequest)
            throws ApiException, IOException, InvalidCartException {
        PaymentDetailsResponse response;
        try {
            response = getAdyenPaymentService().getPaymentDetailsFromPayload(detailsRequest);
        } catch (ApiException | IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Payment details request failed", e);
        }
        String orderCode = response.getMerchantReference();
        OrderModel orderModel = retrievePendingOrder(orderCode);
        threeDSAuthorizationService.updateOrderPaymentStatusAndInfo(orderModel, response);

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
    public OrderData handle3DSResponse(PaymentDetailsRequest paymentsDetailsRequest)
            throws AdyenNonAuthorizedPaymentException, InvalidCartException, CalculationException {
        try {
            return threeDSAuthorizationService.handle3DSResponse(paymentsDetailsRequest);
        } catch (AdyenNonAuthorizedPaymentException e) {
            restoreCartFromOrderCodeInSession();
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to handle component result", e);
            restoreCartFromOrderCodeInSession();
            throw new AdyenNonAuthorizedPaymentException(e.getMessage());
        }
    }

    public OrderData placePendingOrder() throws InvalidCartException {
        return placePendingOrder(PaymentDetailsResponse.ResultCodeEnum.PENDING.getValue());
    }

    @Override
    public CheckoutConfigDTO getConfig() {
        CheckoutConfigDTOBuilder checkoutConfigDTOBuilder = new CheckoutConfigDTOBuilder();
        Amount zeroAuthAmount = new Amount();
        zeroAuthAmount.setValue(ZERO_AUTH_VALUE);
        zeroAuthAmount.setCurrency(baseStoreService.getCurrentBaseStore().getDefaultCurrency().getIsocode());
        CustomerModel customerModel = getCheckoutCustomerStrategy().getCurrentUserForCheckout();
        List<PaymentMethod> paymentMethods = List.of();
        BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();
        try {
            paymentMethods = getAdyenPaymentService().getPaymentMethodsResponse(BigDecimal.ZERO, baseStore.getDefaultCurrency().getIsocode(), customerModel.getDefaultShipmentAddress().getCountry().getIsocode(), getShopperLocale(), customerModel.getCustomerID(), "").getPaymentMethods();
        } catch (IOException | ApiException e) {
            LOGGER.warn("Payment methods couldn't be fetched "  + e);
        }
        PaymentMethod cardPaymentMethod = paymentMethods.stream().filter(paymentMethod -> PAYMENT_METHOD_SCHEME.equals(paymentMethod.getType())).findAny().orElse(null);

        if (cardPaymentMethod != null) {
            List<String> allowedCards = baseStore.getAdyenAllowedCards().stream().map(AdyenCardTypeEnum::getCode).toList();

            List<String> cardBrands = cardPaymentMethod.getBrands();

            allowedCards = allowedCards.stream()
                    .filter(cardBrands::contains)
                    .toList();

            cardPaymentMethod.setBrands(allowedCards);
        } else
            cardPaymentMethod = new PaymentMethod();

        checkoutConfigDTOBuilder
                .setPaymentMethods(List.of(cardPaymentMethod))
                .setAdyenClientKey(baseStore.getAdyenClientKey())
                .setAmount(zeroAuthAmount).setEnvironmentMode(getEnvironmentMode())
                .setShopperLocale(getShopperLocale())
                .setShowSocialSecurityNumber(showSocialSecurityNumber())
                .setCountryCode(customerModel.getDefaultShipmentAddress() != null ? customerModel.getDefaultShipmentAddress().getCountry().getIsocode() : "")
                .setCardHolderNameRequired(getHolderNameRequired())
                .setAdyenPaypalMerchantId(baseStore.getAdyenPaypalMerchantId())
                .setShopperEmail(customerModel.getContactEmail());

        return checkoutConfigDTOBuilder.build();
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
        LOGGER.debug("Create order from paymentsResponse: {}", paymentsResponse.getPspReference());

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
        return placeOrderWithStatus(OrderStatus.PAYMENT_PENDING, resultCode);
    }

    protected OrderData placeAuthorisedOrder(PaymentResponse.ResultCodeEnum resultCode) throws InvalidCartException {
        return placeOrderWithStatus(OrderStatus.PAYMENT_AUTHORIZED, resultCode.getValue());
    }

    /**
     * Common implementation for placing an order with a given payment status.
     * Used by both {@link #placePendingOrder(String)} and {@link #placeAuthorisedOrder(PaymentResponse.ResultCodeEnum)}.
     */
    private OrderData placeOrderWithStatus(final OrderStatus status, final String statusInfo) throws InvalidCartException {
        CartModel cartModel = getCartService().getSessionCart();
        cartModel.setStatus(status);
        cartModel.setStatusInfo(statusInfo);

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
    public void initializeCheckoutData(final Model model) throws ApiException {
        adyenCheckoutModelPopulator.populateCheckoutData(model, getCheckoutConfig());
    }

    public CheckoutConfigDTO getReactCheckoutConfig() throws ApiException {
        final CartData cartData = getCheckoutFacade().getCheckoutCart();
        final AdyenCheckoutApiService adyenPaymentService = getAdyenPaymentService();
        final CustomerModel customerModel = getCheckoutCustomerStrategy().getCurrentUserForCheckout();
        final CartModel cartModel = cartService.getSessionCart();

        final String shopperConversionId = UUID.randomUUID().toString();
        cartModel.setAdyenShopperConversionId(shopperConversionId);
        Assert.notNull(cartModel.getDeliveryAddress(), "Delivery address is required");

        PaymentMethodsResponse response = new PaymentMethodsResponse();
        try {
            response = getPaymentMethods(adyenPaymentService, cartData, customerModel,
                    adyenPaymentMethodConfigService.getExcludedPaymentMethods(),
                    adyenPaymentMethodConfigService.getAllowedPaymentMethods(),
                    shopperConversionId);
        } catch (ApiException | IOException e) {
            LOGGER.error("Failed to retrieve payment methods", e);
        }

        final CheckoutConfigDTOBuilder builder = buildBaseCheckoutConfig(
                cartData, response, cartModel, baseStoreService.getCurrentBaseStore(), customerModel, adyenPaymentService);

        builder.setInstallmentOptions(adyenInstallmentsConfigurationService.getInstallmentOptionsForCountry());

        return builder.build();
    }

    @Deprecated
    public CheckoutConfigDTO getCheckoutConfig() throws ApiException {
        final CartData cartData = getCheckoutFacade().getCheckoutCart();
        final AdyenCheckoutApiService adyenCheckoutApiService = getAdyenPaymentService();
        final CustomerModel customerModel = getCheckoutCustomerStrategy().getCurrentUserForCheckout();
        final CartModel cartModel = cartService.getSessionCart();

        final String shopperConversionId = UUID.randomUUID().toString();
        cartModel.setAdyenShopperConversionId(shopperConversionId);
        Assert.notNull(cartModel.getDeliveryAddress(), "Delivery address is required");

        PaymentMethodsResponse response = new PaymentMethodsResponse();
        try {
            response = getPaymentMethods(adyenCheckoutApiService, cartData, customerModel, shopperConversionId);
        } catch (ApiException | IOException e) {
            LOGGER.error("Failed to retrieve payment methods for legacy checkout", e);
        }

        List<PaymentMethod> alternativePaymentMethods = response.getPaymentMethods() != null
                ? response.getPaymentMethods() : Collections.emptyList();

        // Build issuer lists inline
        final Map<String, String> issuerLists = new HashMap<>();
        final Gson issuerGson = new Gson();
        alternativePaymentMethods.stream()
                .filter(pm -> !pm.getType().isEmpty() && ISSUER_PAYMENT_METHODS.contains(pm.getType()))
                .forEach(pm -> issuerLists.put(pm.getType(), issuerGson.toJson(pm.getIssuers())));

        // Detect SEPA inline
        final boolean sepaDirectDebit = alternativePaymentMethods.stream()
                .anyMatch(pm -> !pm.getType().isEmpty() && PAYMENT_METHOD_SEPA_DIRECTDEBIT.contains(pm.getType()));

        adyenPaymentMethodConfigService.getAmazonPayMethod(alternativePaymentMethods)
                .ifPresent(pm -> {
                    Map<String, String> amazonPayConfiguration = pm.getConfiguration();
                    if (!CollectionUtils.isEmpty(amazonPayConfiguration)) {
                        cartModel.setAdyenAmazonPayConfiguration(amazonPayConfiguration);
                    }
                });

        final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();
        String creditCardLabel = null;
        List<AdyenCardTypeEnum> allowedCards = null;
        final PaymentMethod cardsPaymentMethod = alternativePaymentMethods.stream()
                .filter(pm -> PAYMENT_METHOD_SCHEME.equals(pm.getType()))
                .findAny().orElse(null);
        if (cardsPaymentMethod != null) {
            creditCardLabel = cardsPaymentMethod.getName();
            final List<String> cardBrands = cardsPaymentMethod.getBrands();
            allowedCards = baseStore.getAdyenAllowedCards().stream()
                    .filter(card -> cardBrands.contains(card.getCode()))
                    .toList();
        }

        alternativePaymentMethods = alternativePaymentMethods.stream()
                .filter(pm -> !pm.getType().isEmpty() && !adyenPaymentMethodConfigService.isHiddenPaymentMethod(pm))
                .toList();

        final CheckoutConfigDTOBuilder builder = buildBaseCheckoutConfig(
                cartData, response, cartModel, baseStore, customerModel, adyenCheckoutApiService);

        builder.setAlternativePaymentMethods(alternativePaymentMethods)
                .setIssuerLists(issuerLists)
                .setCreditCardLabel(creditCardLabel)
                .setAllowedCards(allowedCards)
                .setSepaDirectDebit(sepaDirectDebit)
                .setCountryCode(cartData.getDeliveryAddress().getCountry().getIsocode());

        return builder.build();
    }
    /**
     * Builds a {@link CheckoutConfigDTOBuilder} pre-populated with fields common to both
     * {@link #getReactCheckoutConfig()} and {@link #getCheckoutConfig()}.
     * Callers are responsible for setting method-specific fields and calling {@code build()}.
     */
    protected CheckoutConfigDTOBuilder buildBaseCheckoutConfig(
            final CartData cartData,
            final PaymentMethodsResponse response,
            final CartModel cartModel,
            final BaseStoreModel baseStore,
            final CustomerModel customerModel,
            final AdyenCheckoutApiService adyenPaymentService) {

        List<PaymentMethod> paymentMethods = response.getPaymentMethods() != null
                ? response.getPaymentMethods() : Collections.emptyList();

        // Apple Pay config
        final Map<String, String> applePayConfig = adyenPaymentMethodConfigService.getApplePayConfig(paymentMethods);
        if (!CollectionUtils.isEmpty(applePayConfig)) {
            cartModel.setAdyenApplePayMerchantName(applePayConfig.get("merchantName"));
            cartModel.setAdyenApplePayMerchantIdentifier(applePayConfig.get("merchantId"));
        }

        // Filter allowed card brands
        final PaymentMethod cardsPaymentMethod = paymentMethods.stream()
                .filter(pm -> PAYMENT_METHOD_SCHEME.equals(pm.getType()))
                .findAny().orElse(null);
        if (cardsPaymentMethod != null) {
            final List<String> cardBrands = cardsPaymentMethod.getBrands();
            final List<String> allowedCardCodes = baseStore.getAdyenAllowedCards().stream()
                    .map(AdyenCardTypeEnum::getCode)
                    .filter(cardBrands::contains)
                    .toList();
            cardsPaymentMethod.setBrands(allowedCardCodes);
        }

        // Stored one-click cards
        List<StoredPaymentMethod> storedPaymentMethodList = null;
        if (showRememberDetails()) {
            storedPaymentMethodList = getStoredOneClickPaymentMethods(response);
            final Set<String> recurringDetailReferences = storedPaymentMethodList != null
                    ? storedPaymentMethodList.stream().map(StoredPaymentMethod::getId).collect(Collectors.toSet())
                    : new HashSet<>();
            cartModel.setAdyenStoredCards(recurringDetailReferences);
        }

        modelService.save(cartModel);

        final Amount amount = AmountUtil.createAmount(
                cartData.getTotalPriceWithTax().getValue(),
                cartData.getTotalPriceWithTax().getCurrencyIso());

        final String countryCode = cartData.getDeliveryAddress() != null
                && cartData.getDeliveryAddress().getCountry() != null
                ? cartData.getDeliveryAddress().getCountry().getIsocode() : "";

        final CheckoutConfigDTOBuilder builder = new CheckoutConfigDTOBuilder()
                .setPaymentMethods(paymentMethods)
                .setStoredPaymentMethodList(storedPaymentMethodList)
                .setAmount(amount)
                .setAdyenClientKey(baseStore.getAdyenClientKey())
                .setAdyenPaypalMerchantId(baseStore.getAdyenPaypalMerchantId())
                .setDeviceFingerPrintUrl(adyenPaymentService.getDeviceFingerprintUrl())
                .setSelectedPaymentMethod(cartData.getAdyenPaymentMethod())
                .setShowRememberTheseDetails(showRememberDetails())
                .setCheckoutShopperHost(getCheckoutShopperHost())
                .setEnvironmentMode(getEnvironmentMode())
                .setShopperLocale(getShopperLocale())
                .setOpenInvoiceMethods(OPENINVOICE_METHODS_API)
                .setShowSocialSecurityNumber(showSocialSecurityNumber())
                .setShowBoleto(showBoleto())
                .setShowComboCard(showComboCard())
                .setImmediateCapture(isImmediateCapture())
                .setCountryCode(countryCode)
                .setCardHolderNameRequired(getHolderNameRequired())
                .setAmountDecimal(cartData.getTotalPriceWithTax().getValue())
                .setMerchantDisplayName(baseStore.getName())
                .setShopperEmail(customerModel.getContactEmail())
                .setClickToPayLocale(baseStore.getClickToPayLocale())
                .setSkipCvcForOneClick(baseStore.getAdyenSkipCvcForOneClick());

        if (baseStore.getExpressPaymentConfig() != null) {
            builder.setExpressPaymentConfig(expressPaymentConfigConverter.convert(baseStore.getExpressPaymentConfig()));
        }

        return builder;
    }

    protected PaymentMethodsResponse getPaymentMethods(AdyenCheckoutApiService adyenPaymentService, CartData cartData, CustomerModel customerModel, String shopperConversionId) throws IOException, ApiException {
        return adyenPaymentService.getPaymentMethodsResponse(cartData.getTotalPriceWithTax().getValue(),
                cartData.getTotalPriceWithTax().getCurrencyIso(),
                cartData.getDeliveryAddress().getCountry().getIsocode(),
                getShopperLocale(),
                customerModel.getCustomerID(),
                shopperConversionId);
    }

    protected PaymentMethodsResponse getPaymentMethods(AdyenCheckoutApiService adyenPaymentService, CartData cartData, CustomerModel customerModel, List<String> excludedPaymentMethods, List<String> allowedPaymentMethods, String shopperConversionId) throws IOException, ApiException {
        if (adyenPaymentService == null || cartData == null || customerModel == null) {
            throw new IllegalArgumentException("Required parameters cannot be null");
        }

        BigDecimal totalPrice = cartData.getTotalPriceWithTax() != null ? cartData.getTotalPriceWithTax().getValue() : BigDecimal.ZERO;
        String currencyIso = cartData.getTotalPriceWithTax() != null ? cartData.getTotalPriceWithTax().getCurrencyIso() : StringUtils.EMPTY;
        String countryIso = cartData.getDeliveryAddress() != null && cartData.getDeliveryAddress().getCountry() != null ? cartData.getDeliveryAddress().getCountry().getIsocode() : StringUtils.EMPTY;
        String customerID = customerModel.getCustomerID() != null ? customerModel.getCustomerID() : StringUtils.EMPTY;

        return adyenPaymentService.getPaymentMethodsResponse(totalPrice, currencyIso, countryIso, getShopperLocale(), customerID, excludedPaymentMethods, allowedPaymentMethods, shopperConversionId);
    }


    @Override
    public void initializeSummaryData(final Model model) throws ApiException {
        final CartData cartData = getCheckoutFacade().getCheckoutCart();
        final AdyenCheckoutApiService adyenCheckoutApiService = getAdyenPaymentService();
        final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

        final Amount amount = AmountUtil.createAmount(cartData.getTotalPriceWithTax().getValue(), cartData.getTotalPriceWithTax().getCurrencyIso());
        final Gson gson = new Gson();
        final String shopperLocale = getShopperLocale();
        final String countryCode = Objects.nonNull(cartData.getDeliveryAddress()) &&
                Objects.nonNull(cartData.getDeliveryAddress().getCountry()) ?
                cartData.getDeliveryAddress().getCountry().getIsocode() : null;

        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_SELECTED_PAYMENT_METHOD, cartData.getAdyenPaymentMethod());
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_DF_URL, adyenCheckoutApiService.getDeviceFingerprintUrl());
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_CHECKOUT_SHOPPER_HOST, getCheckoutShopperHost());
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_ENVIRONMENT_MODE, getEnvironmentMode());
        model.addAttribute(SHOPPER_LOCALE, shopperLocale);
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_CLIENT_KEY, baseStore.getAdyenClientKey());
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_MERCHANT_ACCOUNT, adyenMerchantAccountStrategy.getWebMerchantAccount());
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_AMOUNT, amount);
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_IMMEDIATE_CAPTURE, isImmediateCapture());
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_PAYPAL_MERCHANT_ID, baseStore.getAdyenPaypalMerchantId());
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_APPLEPAY_MERCHANT_IDENTIFIER, cartData.getAdyenApplePayMerchantIdentifier());
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_APPLEPAY_MERCHANT_NAME, cartData.getAdyenApplePayMerchantName());
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_AMAZONPAY_CONFIGURATION, gson.toJson(cartData.getAdyenAmazonPayConfiguration()));
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_GIFT_CARD_BRAND, cartData.getAdyenGiftCardBrand());
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_COUNTRY_CODE, countryCode);
        model.addAttribute(AdyenCheckoutModelPopulator.MODEL_DELIVERY_ADDRESS, gson.toJson(cartData.getDeliveryAddress()));
        model.addAttribute(AdyenCheckoutModelPopulator.LOCALE, gson.toJson(adyenPaymentMethodConfigService.resolveAmazonPayLocale(cartData.getAdyenAmazonPayConfiguration(), shopperLocale)));
    }

    public void initializeExpressCheckoutCartPageData(final Model model) throws ApiException, CalculationException {
        adyenCheckoutModelPopulator.populateExpressCheckoutData(model, initializeExpressCheckoutCartPageDataOCC());
    }

    public void initializeExpressCheckoutPDPData(final Model model, final String productCode) throws ApiException {
        adyenCheckoutModelPopulator.populateExpressCheckoutData(model, initializeExpressCheckoutPDPDataOCC(productCode));
    }

    public ExpressCheckoutConfigDTO initializeExpressCheckoutCartPageDataOCC() throws ApiException, CalculationException {
        removeDeliveryModeFromSessionCart();

        final CartData cartData = getCheckoutFacade().getCheckoutCart();
        if (cartData != null && cartData.getTotalPriceWithTax() != null && cartData.getTotalPriceWithTax().getCurrencyIso() != null) {
            final String currencyIso = cartData.getTotalPriceWithTax().getCurrencyIso();
            BigDecimal amountValue = cartData.getTotalPriceWithTax().getValue();
            BigDecimal expressDeliveryModeValue = getExpressDeliveryModeValue(currencyIso);
            amountValue = amountValue.add(expressDeliveryModeValue);

            return initializeExpressCheckoutDataInternal(amountValue, currencyIso);
        }

        throw new IllegalArgumentException("CartData is null or empty");
    }

    public ExpressCheckoutConfigDTO initializeExpressCheckoutPDPDataOCC(String productCode) throws ApiException {
        final ProductData productData = productFacade.getProductForCodeAndOptions(productCode, Arrays.asList(ProductOption.BASIC, ProductOption.PRICE));

        if (productData != null) {
            final String currencyIso = productData.getPrice().getCurrencyIso();
            BigDecimal amountValue = productData.getPrice().getValue();

            return initializeExpressCheckoutDataInternal(amountValue, currencyIso);
        }
        throw new IllegalArgumentException("ProductData is null or empty");
    }

    protected ExpressCheckoutConfigDTO initializeExpressCheckoutDataInternal(BigDecimal amountValue, String currency) throws ApiException {
        final BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();
        final ExpressCheckoutConfigDTOBuilder expressCheckoutConfigDTOBuilder = new ExpressCheckoutConfigDTOBuilder();

        try {
            PaymentMethodsResponse paymentMethodsResponse = getAdyenPaymentService().getPaymentMethodsResponse(amountValue,
                    currency,
                    null,
                    getShopperLocale(),
                    null,
                    null);

            Map<String, String> applePayConfig = adyenPaymentMethodConfigService.getApplePayConfig(paymentMethodsResponse.getPaymentMethods());
            if (!CollectionUtils.isEmpty(applePayConfig)) {
                expressCheckoutConfigDTOBuilder.setApplePayMerchantId(applePayConfig.get("merchantId"))
                        .setApplePayMerchantName(applePayConfig.get("merchantName"));
            } else {
                LOGGER.warn("Empty apple pay config");
            }

            Map<String, String> payPalConfig = adyenPaymentMethodConfigService.getPayPalConfig(paymentMethodsResponse.getPaymentMethods());
            if (!CollectionUtils.isEmpty(payPalConfig)) {
                expressCheckoutConfigDTOBuilder.setPayPalIntent(payPalConfig.get("intent"));
            } else {
                LOGGER.warn("Empty PayPal config");
            }

            Map<String, String> googlePayConfig = adyenPaymentMethodConfigService.getGooglePayConfig(paymentMethodsResponse.getPaymentMethods());
            if (!CollectionUtils.isEmpty(googlePayConfig)) {
                expressCheckoutConfigDTOBuilder.setGooglePayMerchantId(googlePayConfig.get("merchantId"))
                        .setGooglePayGatewayMerchantId(googlePayConfig.get("gatewayMerchantId"));
            } else {
                LOGGER.warn("Empty GooglePay config");
            }

        } catch (IOException e) {
            LOGGER.error("Payment methods request failed", e);
        }

        final Amount amount = AmountUtil.createAmount(amountValue, currency);

        expressCheckoutConfigDTOBuilder.setShopperLocale(getShopperLocale())
                .setEnvironmentMode(getEnvironmentMode())
                .setClientKey(baseStore.getAdyenClientKey())
                .setMerchantAccount(adyenMerchantAccountStrategy.getWebMerchantAccount())
                .setAmount(amount)
                .setAmountDecimal(amountValue)
                .setDfUrl(getAdyenPaymentService().getDeviceFingerprintUrl())
                .setCheckoutShopperHost(getCheckoutShopperHost());

        if (baseStore.getExpressPaymentConfig() != null) {
            expressCheckoutConfigDTOBuilder.setExpressPaymentConfigDto(expressPaymentConfigConverter.convert(baseStore.getExpressPaymentConfig()));
        }

        return expressCheckoutConfigDTOBuilder.build();
    }

    protected void removeDeliveryModeFromSessionCart() throws CalculationException {
        if (cartService.hasSessionCart()) {
            CartModel sessionCart = cartService.getSessionCart();
            sessionCart.setDeliveryMode(null);
            modelService.save(sessionCart);

            CommerceCartParameter commerceCartParameter = new CommerceCartParameter();
            commerceCartParameter.setCart(sessionCart);
            commerceCartService.recalculateCart(commerceCartParameter);
        }
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
        if (baseStore.getAdyenBoleto() == null || !baseStore.getAdyenBoleto()) {
            return false;
        }

        CartData cartData = getCheckoutFacade().getCheckoutCart();
        String currency = cartData.getTotalPriceWithTax().getCurrencyIso();
        String country = cartData.getDeliveryAddress().getCountry().getIsocode();

        return "BRL".equals(currency) && "BR".equals(country);
    }

    @Override
    public boolean showComboCard() {
        CartData cartData = getCheckoutFacade().getCheckoutCart();
        String currency = cartData.getTotalPriceWithTax().getCurrencyIso();
        return "BRL".equals(currency);
    }

    @Override
    public boolean showRememberDetails() {
        BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();

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
        if (cart == null) {
            return showSocialSecurityNumber;
        }

        final AddressData deliveryAddress = cart.getDeliveryAddress();
        if (deliveryAddress == null || deliveryAddress.getCountry() == null) {
            return showSocialSecurityNumber;
        }

        String countryCode = deliveryAddress.getCountry().getIsocode();
        if (PAYMENT_METHODS_ALLOW_SOCIAL_SECURITY_NUMBER.contains(cart.getAdyenPaymentMethod()) && OPENINVOICE_METHODS_ALLOW_SOCIAL_SECURITY_NUMBER.contains(countryCode)) {
            showSocialSecurityNumber = true;
        }
        return showSocialSecurityNumber;
    }

    @Override
    public PaymentInfoModel createPaymentInfo(final CartModel cartModel, final AdyenPaymentForm adyenPaymentForm) {
        return adyenPaymentInfoFactory.createFromPaymentForm(cartModel, adyenPaymentForm);
    }

    public PaymentInfoModel createPaymentInfo(final CartModel cartModel, final PaymentDetailsWsDTO paymentDetails) {
        return adyenPaymentInfoFactory.createFromPaymentDetails(cartModel, paymentDetails);
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

        cartModel.setAdyenDfValue(adyenPaymentForm.getDfValue());

        transactionTemplate.execute(transactionStatus -> {
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

    protected AddressData convertToAddressData(final AddressForm addressForm) {
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
        addressData.setCompanyName(addressForm.getCompanyName());
        addressData.setTaxNumber(addressForm.getTaxNumber());
        addressData.setRegistrationNumber(addressForm.getRegistrationNumber());

        if (addressForm.getRegionIso() != null && !StringUtils.isEmpty(addressForm.getRegionIso())) {
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

    public AdyenCheckoutApiService getAdyenPaymentService() {
        return adyenPaymentServiceFactory.createAdyenCheckoutApiService(baseStoreService.getCurrentBaseStore());
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
    public OrderData handleComponentResult(String resultCode, String merchantReference)
            throws AdyenNonAuthorizedPaymentException, InvalidCartException, CalculationException {

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

    protected OrderModel retrievePendingOrder(final String orderCode) throws InvalidCartException {
        if (orderCode == null || orderCode.isEmpty()) {
            throw new InvalidCartException("Could not retrieve pending order: missing orderCode!");
        }

        final OrderModel orderModel = getOrderRepository().getOrderModel(orderCode);
        if (orderModel == null) {
            throw new InvalidCartException("Order '" + orderCode + "' does not exist!");
        }

        getSessionService().removeAttribute(SESSION_PENDING_ORDER_CODE);
        threeDSAuthorizationService.clear3DSSessionTokens();

        return orderModel;
    }

    protected void restoreCartFromOrder(final String orderCode) throws CalculationException, InvalidCartException {
        adyenCartRestorationService.restoreCartFromOrder(orderCode);
    }

    @Override
    public void restoreCartFromOrderOCC(final String orderCode) throws CalculationException, InvalidCartException {
        adyenCartRestorationService.restoreCartFromOrderOCC(orderCode);
    }

    @Override
    public void restoreCartFromOrderCodeInSession() throws InvalidCartException, CalculationException {
        adyenCartRestorationService.restoreCartFromOrderCodeInSession();
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

    protected String generateCcPaymentInfoCode(final CartModel cartModel) {
        return cartModel.getCode() + "_" + UUID.randomUUID();
    }

    protected PaymentMethodsCartData getPaymentMethodsCartData(final CartModel cartModel) {
        final PaymentMethodsCartData paymentMethodsCartData = new PaymentMethodsCartData();
        paymentMethodsCartData.setAdyenDfValue(cartModel.getAdyenDfValue());
        paymentMethodsCartData.setAdyenStoredCards(cartModel.getAdyenStoredCards());
        paymentMethodsCartData.setAdyenApplePayMerchantName(cartModel.getAdyenApplePayMerchantName());
        paymentMethodsCartData.setAdyenAmazonPayConfiguration(cartModel.getAdyenAmazonPayConfiguration());
        return paymentMethodsCartData;
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

    protected Converter<CountryModel, CountryData> getCountryConverter() {
        return countryConverter;
    }

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

    public void setProductFacade(ProductFacade productFacade) {
        this.productFacade = productFacade;
    }

    public void setCommerceCartService(CommerceCartService commerceCartService) {
        this.commerceCartService = commerceCartService;
    }

    public ThreeDSAuthorizationService getThreeDSAuthorizationService() {
        return threeDSAuthorizationService;
    }

    public void setThreeDSAuthorizationService(ThreeDSAuthorizationService threeDSAuthorizationService) {
        this.threeDSAuthorizationService = threeDSAuthorizationService;
    }

    public void setAdyenShopperIpResolverService(AdyenShopperIpResolverService adyenShopperIpResolverService) {
        this.adyenShopperIpResolverService = adyenShopperIpResolverService;
    }

    public AdyenInstallmentsConfigurationService getAdyenInstallmentsConfigurationService() {
        return adyenInstallmentsConfigurationService;
    }

    public void setAdyenInstallmentsConfigurationService(AdyenInstallmentsConfigurationService adyenInstallmentsConfigurationService) {
        this.adyenInstallmentsConfigurationService = adyenInstallmentsConfigurationService;
    }

    public AdyenPaymentInfoFactory getAdyenPaymentInfoFactory() {
        return adyenPaymentInfoFactory;
    }

    public void setAdyenPaymentInfoFactory(AdyenPaymentInfoFactory adyenPaymentInfoFactory) {
        this.adyenPaymentInfoFactory = adyenPaymentInfoFactory;
    }

    public AdyenCartRestorationService getAdyenCartRestorationService() {
        return adyenCartRestorationService;
    }

    public void setAdyenCartRestorationService(AdyenCartRestorationService adyenCartRestorationService) {
        this.adyenCartRestorationService = adyenCartRestorationService;
    }

    public AdyenCheckoutModelPopulator getAdyenCheckoutModelPopulator() {
        return adyenCheckoutModelPopulator;
    }

    public void setAdyenCheckoutModelPopulator(AdyenCheckoutModelPopulator adyenCheckoutModelPopulator) {
        this.adyenCheckoutModelPopulator = adyenCheckoutModelPopulator;
    }

    public AdyenPaymentMethodConfigService getAdyenPaymentMethodConfigService() {
        return adyenPaymentMethodConfigService;
    }

    public void setAdyenPaymentMethodConfigService(AdyenPaymentMethodConfigService adyenPaymentMethodConfigService) {
        this.adyenPaymentMethodConfigService = adyenPaymentMethodConfigService;
    }

    public ExpressPaymentConfigConverter getExpressPaymentConfigConverter() {
        return expressPaymentConfigConverter;
    }

    public void setExpressPaymentConfigConverter(ExpressPaymentConfigConverter expressPaymentConfigConverter) {
        this.expressPaymentConfigConverter = expressPaymentConfigConverter;
    }
}