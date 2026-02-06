package com.adyen.commerce.services.impl;

import com.adyen.commerce.data.AdyenPartialPaymentOrderData;
import com.adyen.commerce.decorator.AdyenPaymentRequestDecorator;
import com.adyen.commerce.services.AdyenRequestService;
import com.adyen.commerce.services.impl.AddressConverter;
import com.adyen.commerce.util.AddressUtil;
import com.adyen.model.checkout.*;
import com.adyen.model.recurring.DisableRequest;
import com.adyen.model.recurring.RecurringDetailsRequest;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.enums.RecurringContractMode;
import com.adyen.v6.model.RequestInfo;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.util.*;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SCHEME;

public class DefaultAdyenRequestService implements AdyenRequestService {
    private static final Logger LOG = Logger.getLogger(DefaultAdyenRequestService.class);

    // Configuration constants
    protected static final String IS_3DS2_ALLOWED_PROPERTY = "is3DS2allowed";
    protected static final String L2L3_EDS_SUPPORTED_BRANDS = "adyen.l2l3eds.supported.brands";
    protected static final String L2L3_EDS_SUPPORTED_COUNTRIES = "adyen.l2l3eds.supported.countries";

    // Dependencies
    protected final BaseStoreService baseStoreService;
    protected final CartService cartService;
    protected final ConfigurationService configurationService;
    protected final PaymentMethodHandlerFactory paymentMethodHandlerFactory;
    protected final ApplicationInfoService applicationInfoService;
    protected final List<AdyenPaymentRequestDecorator> paymentRequestDecorators;

    public DefaultAdyenRequestService(BaseStoreService baseStoreService,
                                      CartService cartService,
                                      ConfigurationService configurationService,
                                      PaymentMethodHandlerFactory paymentMethodHandlerFactory,
                                      ApplicationInfoService applicationInfoService,
                                      List<AdyenPaymentRequestDecorator>  paymentRequestDecorators) {
        this.baseStoreService = baseStoreService;
        this.cartService = cartService;
        this.configurationService = configurationService;
        this.paymentMethodHandlerFactory = paymentMethodHandlerFactory;
        this.applicationInfoService = applicationInfoService;
        this.paymentRequestDecorators = paymentRequestDecorators;
    }

    @Override
    public void populateL2L3AdditionalData(final Map<String, String> additionalData, final CartData cartData) {
        validateInputs(additionalData, cartData);
        
        populateRequiredL2L3Fields(additionalData, cartData);
        populateOptionalL2L3Fields(additionalData, cartData);
        populateItemDetails(additionalData, cartData);
    }

    @Override
    public void applyAdditionalData(CartData cartData, PaymentRequest paymentsRequest) {
        if (cartData == null || paymentsRequest == null) {
            LOG.warn("Cannot apply additional data: cartData or paymentsRequest is null");
            return;
        }

        Map<String, String> additionalData = new HashMap<>();
        CartModel sessionCart = cartService.getSessionCart();
        
        if (canL23EdsBeSent(paymentsRequest, sessionCart)) {
            populateL2L3AdditionalData(additionalData, cartData);
        }

        paymentsRequest.setAdditionalData(additionalData);
    }

    @Override
    public PaymentRequest createPaymentsRequest(final String merchantAccount,
                                              final CartData cartData,
                                              final PaymentRequest originPaymentsRequest,
                                              final RequestInfo requestInfo,
                                              final CustomerModel customerModel,
                                              final RecurringContractMode recurringContractMode,
                                              final Boolean guestUserTokenizationEnabled,
                                              final AdyenPartialPaymentOrderData partialPaymentOrderData) {
        
        validatePaymentRequestInputs(merchantAccount, cartData, requestInfo, customerModel);

        PaymentRequest paymentRequest = buildBasePaymentRequest(
            merchantAccount, cartData, originPaymentsRequest, requestInfo, customerModel, partialPaymentOrderData);

        handlePaymentMethodSpecificLogic(paymentRequest, cartData, originPaymentsRequest, 
            recurringContractMode, customerModel, guestUserTokenizationEnabled);

        for (AdyenPaymentRequestDecorator paymentRequestDecorator : paymentRequestDecorators) {
            paymentRequestDecorator.decoratePaymentRequest(paymentRequest, cartData, originPaymentsRequest, requestInfo, customerModel);
        }

        return paymentRequest;
    }

    /**
     * Create payment request for partial payments with custom amount
     */
    public PaymentRequest createPartialPaymentRequest(final String merchantAccount,
                                                    final CartData cartData,
                                                    final PaymentRequest originPaymentsRequest,
                                                    final RequestInfo requestInfo,
                                                    final CustomerModel customerModel,
                                                    final RecurringContractMode recurringContractMode,
                                                    final Boolean guestUserTokenizationEnabled,
                                                    final java.math.BigDecimal customAmount,
                                                    final String currency) {
        
        validatePaymentRequestInputs(merchantAccount, cartData, requestInfo, customerModel);
        
        if (customAmount == null || customAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Custom amount must be positive for partial payments");
        }
        
        if (org.apache.commons.lang3.StringUtils.isEmpty(currency)) {
            throw new IllegalArgumentException("Currency cannot be null or empty for partial payments");
        }

        PaymentRequest paymentRequest = buildPartialPaymentRequest(
            merchantAccount, cartData, originPaymentsRequest, requestInfo, customerModel, customAmount, currency);

        handlePaymentMethodSpecificLogic(paymentRequest, cartData, originPaymentsRequest,
            recurringContractMode, customerModel, guestUserTokenizationEnabled);

        return paymentRequest;
    }


    @Override
    public void decoratePayPalSubmitPaymentRequest(final String merchantAccount, final PaymentRequest paymentRequest,
                                                           final RequestInfo requestInfo) {
        BrowserInfo browserInfo = new BrowserInfo();
        browserInfo.setAcceptHeader(requestInfo.getAcceptHeader());
        browserInfo.setUserAgent(requestInfo.getUserAgent());

        paymentRequest.setMerchantAccount(merchantAccount);
        paymentRequest.setBrowserInfo(browserInfo);
        paymentRequest.setShopperIP(requestInfo.getShopperIp());
        paymentRequest.setOrigin(requestInfo.getOrigin());
        paymentRequest.setShopperLocale(requestInfo.getShopperLocale());
        paymentRequest.setApplicationInfo(applicationInfoService.createApplicationInfo(requestInfo));
    }

    // Private helper methods
    protected void validateInputs(Map<String, String> additionalData, CartData cartData) {
        if (additionalData == null) {
            throw new IllegalArgumentException("Additional data map cannot be null");
        }
        if (cartData == null) {
            throw new IllegalArgumentException("Cart data cannot be null");
        }
    }

    protected void populateRequiredL2L3Fields(Map<String, String> additionalData, CartData cartData) {
        Optional.ofNullable(cartData.getTotalTax())
            .map(tax -> tax.getValue().toString())
            .ifPresent(value -> additionalData.put(TOTAL_TAX_AMOUNT, value));

        Optional.ofNullable(cartData.getUser())
            .map(user -> user.getUid())
            .filter(StringUtils::isNotEmpty)
            .ifPresent(uid -> additionalData.put(CUSTOMER_REFERENCE, uid));
    }

    protected void populateOptionalL2L3Fields(Map<String, String> additionalData, CartData cartData) {
        Optional.ofNullable(cartData.getDeliveryCost())
            .map(cost -> cost.getValue().toString())
            .ifPresent(value -> additionalData.put(FREIGHT_AMOUNT, value));

        populateDeliveryAddressFields(additionalData, cartData);
    }

    protected void populateDeliveryAddressFields(Map<String, String> additionalData, CartData cartData) {
        Optional.ofNullable(cartData.getDeliveryAddress())
            .ifPresent(address -> {
                Optional.ofNullable(address.getPostalCode())
                    .ifPresent(code -> additionalData.put(DESTINATION_POSTAL_CODE, code));
                
                Optional.ofNullable(address.getCountry())
                    .map(country -> country.getIsocode())
                    .ifPresent(code -> additionalData.put(DESTINATION_COUNTRY_CODE, code));
            });
    }

    protected void populateItemDetails(Map<String, String> additionalData, CartData cartData) {
        Optional.ofNullable(cartData.getEntries())
            .orElse(Collections.emptyList())
            .stream()
            .filter(Objects::nonNull)
            .filter(entry -> entry.getProduct() != null)
            .forEach(entry -> populateItemDetail(additionalData, entry));
    }

    protected void populateItemDetail(Map<String, String> additionalData, OrderEntryData entry) {
        Integer entryNumber = entry.getEntryNumber();
        
        additionalData.put(String.format(ITEM_DETAIL_PRODUCT_CODE, entryNumber),
            Optional.ofNullable(entry.getProduct().getCode()).orElse(StringUtils.EMPTY));
        
        additionalData.put(String.format(ITEM_DETAIL_DESCRIPTION, entryNumber),
            Optional.ofNullable(entry.getProduct().getName()).orElse(StringUtils.EMPTY));
        
        additionalData.put(String.format(ITEM_DETAIL_QUANTITY, entryNumber), 
            String.valueOf(entry.getQuantity()));
        
        additionalData.put(String.format(ITEM_DETAIL_UNIT_OF_MEASURE, entryNumber),
            Optional.ofNullable(entry.getUnitOfMeasure()).orElse(StringUtils.EMPTY));
        
        additionalData.put(String.format(ITEM_DETAIL_COMMODITY_CODE, entryNumber),
            Optional.ofNullable(entry.getProduct().getCommodityCode()).orElse(StringUtils.EMPTY));

        populateItemPrices(additionalData, entry);
    }

    protected void populateItemPrices(Map<String, String> additionalData, OrderEntryData entry) {
        Integer entryNumber = entry.getEntryNumber();
        
        Optional.ofNullable(entry.getTotalPrice())
            .map(price -> price.getValue())
            .map(value -> Optional.ofNullable(value).orElse(BigDecimal.ZERO))
            .ifPresent(value -> additionalData.put(String.format(ITEM_DETAIL_TOTAL_AMOUNT, entryNumber), 
                String.valueOf(value)));

        Optional.ofNullable(entry.getBasePrice())
            .map(price -> price.getValue())
            .map(value -> Optional.ofNullable(value).orElse(BigDecimal.ZERO))
            .ifPresent(value -> additionalData.put(String.format(ITEM_DETAIL_UNIT_PRICE, entryNumber), 
                String.valueOf(value)));
    }

    protected boolean canL23EdsBeSent(PaymentRequest paymentsRequest, CartModel sessionCart) {
        return isL2L3ESDEnabled() && 
               isSupportedBrand(paymentsRequest) && 
               isSupportedCountry(sessionCart);
    }

    protected boolean isL2L3ESDEnabled() {
        return Optional.ofNullable(baseStoreService.getCurrentBaseStore())
            .map(store -> store.getL2L3ESDEnabled())
            .orElse(false);
    }

    protected boolean isSupportedBrand(PaymentRequest paymentsRequest) {
        return Optional.ofNullable(paymentsRequest.getPaymentMethod())
            .map(method -> method.getActualInstance())
            .filter(instance -> instance instanceof CardDetails)
            .map(instance -> (CardDetails) instance)
            .filter(cardDetails -> cardDetails.getBrand() != null)
            .map(cardDetails -> getL2L3SupportedBrands().contains(cardDetails.getBrand()))
            .orElse(false);
    }

    protected boolean isSupportedCountry(CartModel sessionCart) {
        return Optional.ofNullable(sessionCart.getDeliveryAddress())
            .map(address -> address.getCountry())
            .map(country -> getL2L3SupportedCountries().contains(country.getIsocode()))
            .orElse(false);
    }

    protected List<String> getL2L3SupportedBrands() {
        return getConfigurationList(L2L3_EDS_SUPPORTED_BRANDS);
    }

    protected List<String> getL2L3SupportedCountries() {
        return getConfigurationList(L2L3_EDS_SUPPORTED_COUNTRIES);
    }

    protected List<String> getConfigurationList(String propertyKey) {
        String property = configurationService.getConfiguration().getString(propertyKey);
        return property != null ? Arrays.asList(property.split(",")) : Collections.emptyList();
    }

    protected PaymentRequest buildBasePaymentRequest(String merchantAccount, CartData cartData,
                                                 PaymentRequest originPaymentsRequest, RequestInfo requestInfo, 
                                                 CustomerModel customerModel, AdyenPartialPaymentOrderData partialPaymentOrderData) {
        
        PaymentRequestBuilder builder = new PaymentRequestBuilder()
            .merchantAccount(merchantAccount)
            .amount(cartData)
            .reference(cartData.getCode())
            .browserInfo(requestInfo.getUserAgent(), requestInfo.getAcceptHeader())
            .shopperDetails(customerModel)
            .requestInfo(requestInfo)
            .redirectMethods()
            .countryCode(AddressUtil.getCountryCode(getBillingAddress(cartData), cartData.getDeliveryAddress()))
            .company(createCompany(cartData))
            .shopperConversionId(cartData.getAdyenShopperConversionId());

        if (partialPaymentOrderData != null) {
            builder.amount(partialPaymentOrderData.getRemainingAmount(), partialPaymentOrderData.getCurrency().getIsocode());
        }

        // Set return URL
        String returnUrl = StringUtils.isNotEmpty(cartData.getAdyenReturnUrl()) ? 
            cartData.getAdyenReturnUrl() : 
            (originPaymentsRequest != null ? originPaymentsRequest.getReturnUrl() : null);
        builder.returnUrl(returnUrl);

        // Set addresses
        AddressData billingAddress = getBillingAddress(cartData);
        AddressData deliveryAddress = cartData.getDeliveryAddress();
        
        PaymentRequest paymentRequest = builder.build();
        paymentRequest.setDeliveryAddress(AddressConverter.convertToDeliveryAddress(deliveryAddress));
        paymentRequest.setBillingAddress(AddressConverter.convertToBillingAddress(billingAddress));
        paymentRequest.setInstallments(originPaymentsRequest != null ? originPaymentsRequest.getInstallments() : null);
        
        if (billingAddress != null) {
            paymentRequest.setTelephoneNumber(billingAddress.getPhone());
        }

        paymentRequest.setApplicationInfo(applicationInfoService.createApplicationInfo(requestInfo));
        setRiskData(paymentRequest, cartData, originPaymentsRequest);

        return paymentRequest;
    }

    protected PaymentRequest buildPartialPaymentRequest(String merchantAccount, CartData cartData,
                                                      PaymentRequest originPaymentsRequest, RequestInfo requestInfo,
                                                      CustomerModel customerModel, java.math.BigDecimal customAmount, String currency) {
        
        PaymentRequestBuilder builder = new PaymentRequestBuilder()
            .merchantAccount(merchantAccount)
            .amount(customAmount, currency)
                .reference(originPaymentsRequest.getReference())
            .browserInfo(requestInfo.getUserAgent(), requestInfo.getAcceptHeader())
            .shopperDetails(customerModel)
            .requestInfo(requestInfo)
            .redirectMethods()
            .countryCode(AddressUtil.getCountryCode(getBillingAddress(cartData), cartData.getDeliveryAddress()))
            .company(createCompany(cartData))
            .order(originPaymentsRequest.getOrder());

        // Set return URL
        String returnUrl = StringUtils.isNotEmpty(cartData.getAdyenReturnUrl()) ?
            cartData.getAdyenReturnUrl() :
            (originPaymentsRequest != null ? originPaymentsRequest.getReturnUrl() : null);
        builder.returnUrl(returnUrl);

        // Set addresses
        AddressData billingAddress = getBillingAddress(cartData);
        AddressData deliveryAddress = cartData.getDeliveryAddress();
        
        PaymentRequest paymentRequest = builder.build();
        paymentRequest.setDeliveryAddress(AddressConverter.convertToDeliveryAddress(deliveryAddress));
        paymentRequest.setBillingAddress(AddressConverter.convertToBillingAddress(billingAddress));
        
        if (billingAddress != null) {
            paymentRequest.setTelephoneNumber(billingAddress.getPhone());
        }

        paymentRequest.setApplicationInfo(applicationInfoService.createApplicationInfo(requestInfo));
        setRiskData(paymentRequest, cartData, originPaymentsRequest);

        return paymentRequest;
    }

    protected void handlePaymentMethodSpecificLogic(PaymentRequest paymentRequest, CartData cartData,
                                                PaymentRequest originPaymentsRequest, RecurringContractMode recurringContractMode,
                                                CustomerModel customerModel, Boolean guestUserTokenizationEnabled) {
        
        String paymentMethod = cartData.getAdyenPaymentMethod();
        Boolean is3DS2Allowed = is3DS2Allowed();

        // Copy payment method from origin request if available
        if (originPaymentsRequest != null && paymentRequest.getPaymentMethod() == null) {
            paymentRequest.setPaymentMethod(originPaymentsRequest.getPaymentMethod());
        }

        // Handle scheme payments specially
        if (PAYMENT_METHOD_SCHEME.equals(paymentMethod) && originPaymentsRequest != null) {
            copySchemePaymentSettings(paymentRequest, originPaymentsRequest);
        }

        // Use payment method handler
        paymentMethodHandlerFactory.getHandler(paymentMethod)
                .ifPresent(handler -> handler.updatePaymentRequest(paymentRequest, cartData,
                recurringContractMode, customerModel, is3DS2Allowed, guestUserTokenizationEnabled));
    }

    protected void copySchemePaymentSettings(PaymentRequest paymentRequest, PaymentRequest originPaymentsRequest) {
        paymentRequest.setEnableOneClick(originPaymentsRequest.getEnableOneClick());
        paymentRequest.setEnableRecurring(originPaymentsRequest.getEnableRecurring());
        paymentRequest.setStorePaymentMethod(originPaymentsRequest.getStorePaymentMethod());
    }

    protected AddressData getBillingAddress(CartData cartData) {
        return Optional.ofNullable(cartData.getPaymentInfo())
            .map(paymentInfo -> paymentInfo.getBillingAddress())
            .orElse(null);
    }

    protected Company createCompany(CartData cartData) {
        AddressData billingAddress = getBillingAddress(cartData);
        
        if (billingAddress != null && StringUtils.isNotEmpty(billingAddress.getCompanyName())) {
            Company company = new Company();
            company.setName(billingAddress.getCompanyName());
            company.setRegistrationNumber(billingAddress.getRegistrationNumber());
            company.setTaxId(billingAddress.getTaxNumber());
            return company;
        }
        return null;
    }

    protected void setRiskData(PaymentRequest paymentRequest, CartData cartData, PaymentRequest originPaymentsRequest) {
        // Priority: origin request risk data, then cart risk data
        if (originPaymentsRequest != null && originPaymentsRequest.getRiskData() != null) {
            paymentRequest.setRiskData(originPaymentsRequest.getRiskData());
        } else if (StringUtils.isNotEmpty(cartData.getRiskData())) {
            RiskData riskData = new RiskData();
            riskData.setClientData(cartData.getRiskData());
            paymentRequest.setRiskData(riskData);
        }
    }

    protected Boolean is3DS2Allowed() {
        Configuration configuration = configurationService.getConfiguration();
        return configuration.containsKey(IS_3DS2_ALLOWED_PROPERTY) ? 
            configuration.getBoolean(IS_3DS2_ALLOWED_PROPERTY) : false;
    }

    // Validation methods
    protected void validatePaymentRequestInputs(String merchantAccount, CartData cartData,
                                            RequestInfo requestInfo, CustomerModel customerModel) {
        if (StringUtils.isEmpty(merchantAccount)) {
            throw new IllegalArgumentException("Merchant account cannot be null or empty");
        }
        if (cartData == null) {
            throw new IllegalArgumentException("Cart data cannot be null");
        }
        if (requestInfo == null) {
            throw new IllegalArgumentException("Request info cannot be null");
        }
        if (customerModel == null) {
            throw new IllegalArgumentException("Customer model cannot be null");
        }
    }

    protected void validateRecurringRequestInputs(String merchantAccount, String customerId) {
        if (StringUtils.isEmpty(merchantAccount)) {
            throw new IllegalArgumentException("Merchant account cannot be null or empty");
        }
        if (StringUtils.isEmpty(customerId)) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
    }

    protected void validateDisableRequestInputs(String merchantAccount, String customerId, String recurringReference) {
        validateRecurringRequestInputs(merchantAccount, customerId);
        if (StringUtils.isEmpty(recurringReference)) {
            throw new IllegalArgumentException("Recurring reference cannot be null or empty");
        }
    }

    // Getter for configuration service (for testing)
    public ConfigurationService getConfigurationService() {
        return configurationService;
    }
}
