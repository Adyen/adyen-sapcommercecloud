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
package com.adyen.v6.factory;

import com.adyen.builders.terminal.TerminalAPIRequestBuilder;
import com.adyen.model.checkout.*;
import com.adyen.model.nexo.AmountsReq;
import com.adyen.model.nexo.DocumentQualifierType;
import com.adyen.model.nexo.MessageCategoryType;
import com.adyen.model.nexo.MessageReference;
import com.adyen.model.nexo.PaymentTransaction;
import com.adyen.model.nexo.SaleData;
import com.adyen.model.nexo.TransactionIdentification;
import com.adyen.model.nexo.TransactionStatusRequest;
import com.adyen.model.recurring.DisableRequest;
import com.adyen.model.recurring.Recurring;
import com.adyen.model.recurring.RecurringDetailsRequest;
import com.adyen.model.terminal.SaleToAcquirerData;
import com.adyen.model.terminal.TerminalAPIRequest;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.enums.RecurringContractMode;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.util.AdyenUtil;
import com.adyen.v6.util.AmountUtil;
import com.google.gson.Gson;
import de.hybris.platform.commercefacades.order.data.CCPaymentInfoData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.util.TaxValue;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.adyen.v6.constants.Adyenv6coreConstants.AFTERPAY;
import static com.adyen.v6.constants.Adyenv6coreConstants.CARD_TYPE_DEBIT;
import static com.adyen.v6.constants.Adyenv6coreConstants.OPENINVOICE_METHODS_API;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYBRIGHT;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_BCMC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_BOLETO;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_FACILPAY_PREFIX;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_KLARNA;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_PIX;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SCHEME;
import static com.adyen.v6.constants.Adyenv6coreConstants.PLUGIN_NAME;
import static com.adyen.v6.constants.Adyenv6coreConstants.PLUGIN_VERSION;
import static com.adyen.v6.constants.Adyenv6coreConstants.RATEPAY;

/**
 * Factory class to create Adyen API requests, do not add new code to this class.
 * This class is deprecated and will be removed in future versions.
 * Please use the new {@link com.adyen.commerce.services.AdyenRequestService} instead.
 * @deprecated since v13.1. Please use the new {@link com.adyen.commerce.services.AdyenRequestService} instead.
 * @see com.adyen.commerce.services.AdyenRequestService
 */
@Deprecated(since = "v13.1", forRemoval = true)
public class AdyenRequestFactory {
    private static final Logger LOG = Logger.getLogger(AdyenRequestFactory.class);

    private static final String PLATFORM_NAME = "Hybris";
    private static final String PLATFORM_VERSION_PROPERTY = "build.version.api";
    private static final String IS_3DS2_ALLOWED_PROPERTY = "is3DS2allowed";
    private static final String ALLOW_3DS2_PROPERTY = "allow3DS2";
    private static final String OVERWRITE_BRAND_PROPERTY = "overwriteBrand";
    private static final String DUAL_BRANDED_NOT_SELECTED_FLOW_PAYMENT_TYPE = "scheme";

    protected final ConfigurationService configurationService;


    public AdyenRequestFactory(final ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public PaymentRequest createPaymentsRequest(final String merchantAccount,
                                                final CartData cartData,
                                                final PaymentRequest originPaymentsRequest,
                                                final RequestInfo requestInfo,
                                                final CustomerModel customerModel,
                                                final RecurringContractMode recurringContractMode,
                                                final Boolean guestUserTokenizationEnabled) {

        final String adyenPaymentMethod = cartData.getAdyenPaymentMethod();
        final Boolean is3DS2allowed = is3DS2Allowed();
        final PaymentRequest paymentsRequest = new PaymentRequest();

        //Update payment request for generic information for all payment method types
        setCommonInfoOnPaymentRequest(merchantAccount, cartData, requestInfo, customerModel, paymentsRequest);
        paymentsRequest.setApplicationInfo(createApplicationInfo());

        setRiskData(paymentsRequest, cartData, originPaymentsRequest);

        paymentsRequest.setReturnUrl(cartData.getAdyenReturnUrl());
        paymentsRequest.setRedirectFromIssuerMethod(RequestMethod.POST.toString());
        paymentsRequest.setRedirectToIssuerMethod(RequestMethod.POST.toString());
        if (originPaymentsRequest != null) {
            paymentsRequest.setPaymentMethod(originPaymentsRequest.getPaymentMethod());
        }

        //For credit cards
        if (PAYMENT_METHOD_CC.equals(adyenPaymentMethod) || PAYMENT_METHOD_BCMC.equals(adyenPaymentMethod)) {
            if (CARD_TYPE_DEBIT.equals(cartData.getAdyenCardType())) {
                updatePaymentRequestForDC(paymentsRequest, cartData, recurringContractMode);
            } else {
                updatePaymentRequestForCC(paymentsRequest, cartData, recurringContractMode);
            }
            if (is3DS2allowed) {
                enhanceForThreeDS2(paymentsRequest, cartData);
            }
            if (customerModel.getType() == CustomerType.GUEST && guestUserTokenizationEnabled) {
                paymentsRequest.setEnableOneClick(false);
            }
        } else if (PAYMENT_METHOD_SCHEME.equals(adyenPaymentMethod) && originPaymentsRequest != null) {
            paymentsRequest.setEnableOneClick(originPaymentsRequest.getEnableOneClick());
            paymentsRequest.setEnableRecurring(originPaymentsRequest.getEnableRecurring());
            paymentsRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE);
            paymentsRequest.setStorePaymentMethod(originPaymentsRequest.getStorePaymentMethod());
            if (is3DS2allowed) {
                enhanceForThreeDS2(paymentsRequest, cartData);
            }
            if (customerModel.getType() == CustomerType.GUEST && guestUserTokenizationEnabled) {
                paymentsRequest.setEnableOneClick(false);
            }
        }
        //For one click
        else if (AdyenUtil.isOneClick(adyenPaymentMethod)) {
            Optional.ofNullable(cartData.getAdyenSelectedReference())
                    .filter(StringUtils::isNotEmpty)
                    .map(selectedReference -> new CheckoutPaymentMethod(getCardDetails(cartData, selectedReference)))
                    .ifPresent(paymentsRequest::paymentMethod);

            paymentsRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE);

            if (is3DS2allowed) {
                enhanceForThreeDS2(paymentsRequest, cartData);
            }
        }
        //For Pix APM
        else if (PAYMENT_METHOD_PIX.equals(cartData.getAdyenPaymentMethod())) {
            setPixData(paymentsRequest, cartData);
        }
        //Set Boleto parameters
        else if (cartData.getAdyenPaymentMethod().indexOf(PAYMENT_METHOD_BOLETO) == 0) {
            setBoletoData(paymentsRequest, cartData);
        }
        //For alternate payment methods like iDeal, Paypal etc.
        else {
            updatePaymentRequestForAlternateMethod(paymentsRequest, cartData);
        }

        return paymentsRequest;
    }

    private void setRiskData(PaymentRequest paymentsRequest, CartData cartData, PaymentRequest originPaymentsRequest) {
        // drop-in
        if (originPaymentsRequest != null && originPaymentsRequest.getRiskData() != null) {
            paymentsRequest.setRiskData(originPaymentsRequest.getRiskData());
            return;
        }

        // components
        if (StringUtils.isNotEmpty(cartData.getRiskData())) {
            RiskData riskData = new RiskData();
            riskData.setClientData(cartData.getRiskData());
            paymentsRequest.setRiskData(riskData);
        }
    }

    protected CardDetails getCardDetails(CartData cartData, String selectedReference) {
        final CardDetails paymentMethodDetails = new CardDetails();
        paymentMethodDetails.encryptedSecurityCode(cartData.getAdyenEncryptedSecurityCode());
        paymentMethodDetails.recurringDetailReference(selectedReference);
        Optional.ofNullable(cartData.getAdyenCardBrand()).ifPresent(paymentMethodDetails::brand);
        return paymentMethodDetails;
    }

    protected PaymentRequest enhanceForThreeDS2(final PaymentRequest paymentsRequest, final CartData cartData) {
        final BrowserInfo browserInfo = Optional.ofNullable(new Gson().fromJson(cartData.getAdyenBrowserInfo(), BrowserInfo.class))
                .orElse(new BrowserInfo())
                .acceptHeader(paymentsRequest.getBrowserInfo().getAcceptHeader())
                .userAgent(paymentsRequest.getBrowserInfo().getUserAgent());

        paymentsRequest.setAdditionalData(Optional.ofNullable(paymentsRequest.getAdditionalData()).orElse(new HashMap<>()));
        paymentsRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentsRequest.setBrowserInfo(browserInfo);

        return paymentsRequest;
    }

    protected ApplicationInfo createApplicationInfo() {
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        final CommonField version = new CommonField().name(PLUGIN_NAME).version(PLUGIN_VERSION);

        ExternalPlatform externalPlatform = new ExternalPlatform();

        externalPlatform.setName(PLATFORM_NAME);
        externalPlatform.setVersion(getPlatformVersion());
        externalPlatform.setIntegrator(Adyenv6coreConstants.INTEGRATOR);

        applicationInfo.setExternalPlatform(externalPlatform);
        applicationInfo.setMerchantApplication(version);
        applicationInfo.setAdyenPaymentSource(version);
        return applicationInfo;
    }

    protected void setCommonInfoOnPaymentRequest(final String merchantAccount, final CartData cartData,
                                                 final RequestInfo requestInfo, final CustomerModel customerModel,
                                                 final PaymentRequest paymentsRequest) {

        //Get details from CartData to set in PaymentRequest.
        final String amount = String.valueOf(cartData.getTotalPriceWithTax().getValue());
        final String currency = cartData.getTotalPriceWithTax().getCurrencyIso();
        final String reference = cartData.getCode();
        final AddressData billingAddress = cartData.getPaymentInfo() != null ? cartData.getPaymentInfo().getBillingAddress() : null;
        final AddressData deliveryAddress = cartData.getDeliveryAddress();

        //Get details from HttpServletRequest to set in PaymentRequest.
        final String userAgent = requestInfo.getUserAgent();
        final String acceptHeader = requestInfo.getAcceptHeader();
        final String shopperIP = requestInfo.getShopperIp();
        final String origin = requestInfo.getOrigin();
        final String shopperLocale = requestInfo.getShopperLocale();

        paymentsRequest
                .amount(AmountUtil.createAmount(cartData.getTotalPriceWithTax().getValue(), currency))
                .reference(reference)
                .merchantAccount(merchantAccount)
                .browserInfo(new BrowserInfo().userAgent(userAgent).acceptHeader(acceptHeader))
                .shopperIP(shopperIP)
                .origin(origin)
                .shopperLocale(shopperLocale)
                .shopperReference(customerModel.getCustomerID())
                .shopperEmail(customerModel.getContactEmail())
                .deliveryAddress(convertToDeliveryAddress(deliveryAddress))
                .billingAddress(convertToBillingAddress(billingAddress))
                .telephoneNumber(billingAddress != null ? billingAddress.getPhone() : "")
                .setCountryCode(getCountryCode(cartData));
    }

    protected void updatePaymentRequestForCC(final PaymentRequest paymentsRequest, final CartData cartData, final RecurringContractMode recurringContractMode) {
        final Recurring recurringContract = getRecurringContractType(recurringContractMode);
        Recurring.ContractEnum contract = null;
        if (recurringContract != null) {
            contract = recurringContract.getContract();
        }

        final String encryptedCardNumber = cartData.getAdyenEncryptedCardNumber();
        final String encryptedExpiryMonth = cartData.getAdyenEncryptedExpiryMonth();
        final String encryptedExpiryYear = cartData.getAdyenEncryptedExpiryYear();

        if (Recurring.ContractEnum.RECURRING.equals(contract)) {
            paymentsRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE);
            paymentsRequest.setEnableRecurring(true);
            if (Boolean.TRUE.equals(cartData.getAdyenRememberTheseDetails())) {
                paymentsRequest.setEnableOneClick(true);
            }
        } else if (Recurring.ContractEnum.ONECLICK.equals(contract) && Boolean.TRUE.equals(cartData.getAdyenRememberTheseDetails())) {
            paymentsRequest.setEnableOneClick(true);
        } else if (Recurring.ContractEnum.RECURRING.equals(contract)) {
            paymentsRequest.setEnableRecurring(true);
        }

        if (StringUtils.isNotEmpty(encryptedCardNumber) && StringUtils.isNotEmpty(encryptedExpiryMonth) && StringUtils.isNotEmpty(encryptedExpiryYear)) {
            paymentsRequest.setPaymentMethod(new CheckoutPaymentMethod(new CardDetails()
                    .encryptedCardNumber(encryptedCardNumber)
                    .encryptedExpiryMonth(encryptedExpiryMonth)
                    .encryptedExpiryYear(encryptedExpiryYear)
                    .encryptedSecurityCode(cartData.getAdyenEncryptedSecurityCode())
                    .holderName(cartData.getAdyenCardHolder())));
        }

        if (cartData.getAdyenInstallments() != null) {
            Installments installmentObj = new Installments();
            installmentObj.setValue(cartData.getAdyenInstallments());
            paymentsRequest.setInstallments(installmentObj);
        }
    }

    protected void updatePaymentRequestForDC(final PaymentRequest paymentsRequest, final CartData cartData, final RecurringContractMode recurringContractMode) {
        final Recurring recurringContract = getRecurringContractType(recurringContractMode);
        Recurring.ContractEnum contract = null;
        if (recurringContract != null) {
            contract = recurringContract.getContract();
        }

        final String encryptedCardNumber = cartData.getAdyenEncryptedCardNumber();
        final String encryptedExpiryMonth = cartData.getAdyenEncryptedExpiryMonth();
        final String encryptedExpiryYear = cartData.getAdyenEncryptedExpiryYear();
        final String cardBrand = cartData.getAdyenCardBrand();

        if ((Recurring.ContractEnum.RECURRING.equals(contract) || Recurring.ContractEnum.ONECLICK.equals(contract))
                && cartData.getAdyenRememberTheseDetails()) {
            paymentsRequest.setEnableOneClick(true);
        }

        if (StringUtils.isNotEmpty(encryptedCardNumber) && StringUtils.isNotEmpty(encryptedExpiryMonth) && StringUtils.isNotEmpty(encryptedExpiryYear)) {
            paymentsRequest.setPaymentMethod(new CheckoutPaymentMethod(new CardDetails()
                    .encryptedCardNumber(encryptedCardNumber)
                    .encryptedExpiryMonth(encryptedExpiryMonth)
                    .encryptedExpiryYear(encryptedExpiryYear)
                    .encryptedSecurityCode(cartData.getAdyenEncryptedSecurityCode())
                    .brand(cardBrand)
                    .holderName(cartData.getAdyenCardHolder())));
        }

        paymentsRequest.putAdditionalDataItem(OVERWRITE_BRAND_PROPERTY, "true");
    }

    protected void updatePaymentRequestForAlternateMethod(final PaymentRequest paymentsRequest, final CartData cartData) {
        final String adyenPaymentMethod = cartData.getAdyenPaymentMethod();

        paymentsRequest.setShopperName(getShopperNameFromAddress(cartData.getDeliveryAddress()));
        paymentsRequest.setReturnUrl(cartData.getAdyenReturnUrl());

        if (adyenPaymentMethod.startsWith(PAYMENT_METHOD_KLARNA)
                || adyenPaymentMethod.startsWith(PAYMENT_METHOD_FACILPAY_PREFIX)
                || OPENINVOICE_METHODS_API.contains(adyenPaymentMethod)
                || adyenPaymentMethod.contains(RATEPAY)) {
            setOpenInvoiceData(paymentsRequest, cartData);
        }
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

    public RecurringDetailsRequest createListRecurringDetailsRequest(final String merchantAccount, final String customerId) {
        return new RecurringDetailsRequest().merchantAccount(merchantAccount).shopperReference(customerId);
    }

    /**
     * Creates a request to disable a recurring contract
     */
    public DisableRequest createDisableRequest(final String merchantAccount, final String customerId, final String recurringReference) {
        return new DisableRequest().merchantAccount(merchantAccount).shopperReference(customerId).recurringDetailReference(recurringReference);
    }

    public TerminalAPIRequest createTerminalAPIRequestForStatus(final CartData cartData, String originalServiceId) {
        TransactionStatusRequest transactionStatusRequest = new TransactionStatusRequest();
        transactionStatusRequest.setReceiptReprintFlag(true);

        MessageReference messageReference = new MessageReference();
        messageReference.setMessageCategory(MessageCategoryType.PAYMENT);
        messageReference.setSaleID(cartData.getStore());
        messageReference.setServiceID(originalServiceId);

        transactionStatusRequest.setMessageReference(messageReference);
        transactionStatusRequest.getDocumentQualifier().add(DocumentQualifierType.CASHIER_RECEIPT);
        transactionStatusRequest.getDocumentQualifier().add(DocumentQualifierType.CUSTOMER_RECEIPT);

        String serviceId = Long.toString(System.currentTimeMillis() % 10000000000L);

        TerminalAPIRequestBuilder builder = new TerminalAPIRequestBuilder(cartData.getStore(), serviceId, cartData.getAdyenTerminalId());
        builder.withTransactionStatusRequest(transactionStatusRequest);

        return builder.build();
    }

    public TerminalAPIRequest createTerminalAPIRequest(final CartData cartData, CustomerModel customer, RecurringContractMode recurringContractMode, String serviceId) throws Exception {
        com.adyen.model.nexo.PaymentRequest paymentRequest = new com.adyen.model.nexo.PaymentRequest();

        SaleData saleData = new SaleData();
        TransactionIdentification transactionIdentification = new TransactionIdentification();
        transactionIdentification.setTransactionID(cartData.getCode());
        XMLGregorianCalendar timestamp = DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar());
        transactionIdentification.setTimeStamp(timestamp);
        saleData.setSaleTransactionID(transactionIdentification);

        //Set recurring contract, if exists
        if (customer != null) {
            String shopperReference = customer.getCustomerID();
            String shopperEmail = customer.getContactEmail();
            Recurring recurringContract = getRecurringContractType(recurringContractMode);
            SaleToAcquirerData saleToAcquirerData = new SaleToAcquirerData();

            if (recurringContract != null && StringUtils.isNotEmpty(shopperReference) && StringUtils.isNotEmpty(shopperEmail)) {
                saleToAcquirerData.setShopperEmail(shopperEmail);
                saleToAcquirerData.setShopperReference(shopperReference);
                saleToAcquirerData.setRecurringContract(recurringContract.getContract().toString());
            }
            com.adyen.model.applicationinfo.ApplicationInfo applicationInfo = saleToAcquirerData.getApplicationInfo();
            com.adyen.model.applicationinfo.CommonField version = new com.adyen.model.applicationinfo.CommonField().name(PLUGIN_NAME).version(PLUGIN_VERSION);

            com.adyen.model.applicationinfo.ExternalPlatform externalPlatform = new com.adyen.model.applicationinfo.ExternalPlatform();

            externalPlatform.setName(PLATFORM_NAME);
            externalPlatform.setVersion(getPlatformVersion());
            externalPlatform.setIntegrator(Adyenv6coreConstants.INTEGRATOR);

            applicationInfo.setExternalPlatform(externalPlatform);
            applicationInfo.setMerchantApplication(version);
            applicationInfo.setAdyenPaymentSource(version);
            saleData.setSaleToAcquirerData(saleToAcquirerData);
        }

        paymentRequest.setSaleData(saleData);

        PaymentTransaction paymentTransaction = new PaymentTransaction();
        AmountsReq amountsReq = new AmountsReq();
        amountsReq.setCurrency(cartData.getTotalPriceWithTax().getCurrencyIso());
        amountsReq.setRequestedAmount(cartData.getTotalPriceWithTax().getValue());
        paymentTransaction.setAmountsReq(amountsReq);

        paymentRequest.setPaymentTransaction(paymentTransaction);

        TerminalAPIRequestBuilder builder = new TerminalAPIRequestBuilder(cartData.getStore(), serviceId, cartData.getAdyenTerminalId());
        builder.withPaymentRequest(paymentRequest);

        return builder.build();
    }

    /**
     * Set Address Data into API
     */
    protected DeliveryAddress convertToDeliveryAddress(AddressData addressData) {
        if (addressData == null) {
            LOG.warn("Null address data");
            return null;
        }

        DeliveryAddress address = new DeliveryAddress();

        // set defaults because all fields are required into the API
        address.setCity("NA");
        address.setCountry("NA");
        address.setHouseNumberOrName("NA");
        address.setPostalCode("NA");
        address.setStateOrProvince("NA");
        address.setStreet("NA");

        // set the actual values if they are available
        if (addressData.getTown() != null && !addressData.getTown().isEmpty()) {
            address.setCity(addressData.getTown());
        }

        if (addressData.getCountry() != null && !addressData.getCountry().getIsocode().isEmpty()) {
            address.setCountry(addressData.getCountry().getIsocode());
        }

        if (addressData.getLine1() != null && !addressData.getLine1().isEmpty()) {
            address.setStreet(addressData.getLine1());
        }

        if (addressData.getLine2() != null && !addressData.getLine2().isEmpty()) {
            address.setHouseNumberOrName(addressData.getLine2());
        }

        if (addressData.getPostalCode() != null && !address.getPostalCode().isEmpty()) {
            address.setPostalCode(addressData.getPostalCode());
        }

        //State value will be updated later for boleto in boleto specific method.
        if (addressData.getRegion() != null && StringUtils.isNotEmpty(addressData.getRegion().getIsocodeShort())) {
            address.setStateOrProvince(addressData.getRegion().getIsocodeShort());
        } else if (addressData.getRegion() != null && StringUtils.isNotEmpty(addressData.getRegion().getIsocode())) {
            address.setStateOrProvince(addressData.getRegion().getIsocode());
        }

        return address;
    }

    protected BillingAddress convertToBillingAddress(AddressData addressData) {
        if (addressData == null) {
            LOG.warn("Null address data");
            return null;
        }

        BillingAddress address = new BillingAddress();

        // set defaults because all fields are required into the API
        address.setCity("NA");
        address.setCountry("NA");
        address.setHouseNumberOrName("NA");
        address.setPostalCode("NA");
        address.setStateOrProvince("NA");
        address.setStreet("NA");

        // set the actual values if they are available
        if (StringUtils.isNotEmpty(addressData.getTown())) {
            address.setCity(addressData.getTown());
        }

        if (addressData.getCountry() != null && StringUtils.isNotEmpty(addressData.getCountry().getIsocode())) {
            address.setCountry(addressData.getCountry().getIsocode());
        }

        if (StringUtils.isNotEmpty(addressData.getLine1())) {
            address.setStreet(addressData.getLine1());
        }

        if (StringUtils.isNotEmpty(addressData.getLine2())) {
            address.setHouseNumberOrName(addressData.getLine2());
        }

        if (StringUtils.isNotEmpty(addressData.getPostalCode())) {
            address.setPostalCode(addressData.getPostalCode());
        }

        //State value will be updated later for boleto in boleto specific method.
        if (addressData.getRegion() != null && StringUtils.isNotEmpty(addressData.getRegion().getIsocodeShort())) {
            address.setStateOrProvince(addressData.getRegion().getIsocodeShort());
        } else if (addressData.getRegion() != null && StringUtils.isNotEmpty(addressData.getRegion().getIsocode())) {
            address.setStateOrProvince(addressData.getRegion().getIsocode());
        }

        return address;
    }

    /**
     * Return Recurring object from RecurringContractMode
     */
    protected Recurring getRecurringContractType(RecurringContractMode recurringContractMode) {
        Recurring recurringContract = new Recurring();

        //If recurring contract is disabled, return null
        if (recurringContractMode == null || RecurringContractMode.NONE.equals(recurringContractMode)) {
            return null;
        }

        String recurringMode = recurringContractMode.getCode();
        Recurring.ContractEnum contractEnum = Recurring.ContractEnum.valueOf(recurringMode);

        recurringContract.contract(contractEnum);

        return recurringContract;
    }

    /**
     * Return the recurringContract. If the user did not want to save the card don't send it as ONECLICK
     */
    protected Recurring getRecurringContractType(RecurringContractMode recurringContractMode, final Boolean enableOneClick) {
        Recurring recurringContract = getRecurringContractType(recurringContractMode);

        //If recurring contract is disabled, return null
        if (recurringContract == null) {
            return null;
        }

        // if user want to save his card use the configured recurring contract type
        if (enableOneClick != null && enableOneClick) {
            return recurringContract;
        }

        Recurring.ContractEnum contractEnum = recurringContract.getContract();
        /*
         * If save card is not checked do the folllowing changes:
         * NONE => NONE
         * ONECLICK => NONE
         * ONECLICK,RECURRING => RECURRING
         * RECURRING => RECURRING
         */
        if (Recurring.ContractEnum.RECURRING.equals(contractEnum) || Recurring.ContractEnum.RECURRING.equals(contractEnum)) {
            return recurringContract.contract(Recurring.ContractEnum.RECURRING);
        }

        return null;
    }

    /**
     * Get shopper name and gender
     */
    protected Name getShopperNameFromAddress(AddressData addressData) {
        Name shopperName = new Name();

        shopperName.setFirstName(addressData.getFirstName());
        shopperName.setLastName(addressData.getLastName());

        return shopperName;
    }


    /*
     * Set the required fields for using the OpenInvoice API
     */
    public void setOpenInvoiceData(PaymentRequest paymentsRequest, CartData cartData) {
        paymentsRequest.setShopperName(getShopperNameFromAddress(cartData.getDeliveryAddress()));

        // set date of birth
        if (cartData.getAdyenDob() != null) {
            java.util.Date date = cartData.getAdyenDob();
            OffsetDateTime offsetDateTime = date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
            paymentsRequest.setDateOfBirth(offsetDateTime);
        }

        if (cartData.getAdyenSocialSecurityNumber() != null && !cartData.getAdyenSocialSecurityNumber().isEmpty()) {
            paymentsRequest.setSocialSecurityNumber(cartData.getAdyenSocialSecurityNumber());
        }

        if (cartData.getAdyenDfValue() != null && !cartData.getAdyenDfValue().isEmpty()) {
            paymentsRequest.setDeviceFingerprint(cartData.getAdyenDfValue());
        }

        if (AFTERPAY.equals(cartData.getAdyenPaymentMethod())) {
            paymentsRequest.setShopperEmail(cartData.getAdyenShopperEmail());
            paymentsRequest.setTelephoneNumber(cartData.getAdyenShopperTelephone());
            paymentsRequest.setShopperName(getAfterPayShopperName(cartData));
        } else if (PAYBRIGHT.equals(cartData.getAdyenPaymentMethod())) {
            paymentsRequest.setTelephoneNumber(cartData.getAdyenShopperTelephone());
        }

        // set the invoice lines
        List<LineItem> invoiceLines = new ArrayList<>();
        String currency = cartData.getTotalPriceWithTax().getCurrencyIso();

        for (OrderEntryData entry : cartData.getEntries()) {
            if (entry.getQuantity() == 0L) {
                // skip zero quantities
                continue;
            }

            String description = "NA";
            if (entry.getProduct().getName() != null && !entry.getProduct().getName().isEmpty()) {
                description = entry.getProduct().getName();
            }

            // Tax of total price (included quantity)
            Double tax = entry.getTaxValues().stream().map(TaxValue::getAppliedValue).reduce(0.0, Double::sum);

            // Calculate Tax per quantitiy
            if (tax > 0) {
                tax = tax / entry.getQuantity().intValue();
            }

            final Double percentage = entry.getTaxValues().stream().map(TaxValue::getValue).reduce(0.0, Double::sum) * 100;

            final LineItem invoiceLine = new LineItem();

            invoiceLine.setDescription(description);

            /*
             * The price for one item in the invoice line, represented in minor units.
             * The due amount for the item, VAT excluded.
             */
            final Amount itemAmount = AmountUtil.createAmount(entry.getBasePrice().getValue(), currency);

            if (cartData.isNet()) {
                invoiceLine.setAmountExcludingTax(itemAmount.getValue());
                invoiceLine.setTaxAmount(tax.longValue());
            } else {
                invoiceLine.setAmountIncludingTax(itemAmount.getValue());
            }

            // The VAT percentage for one item in the invoice line, represented in minor units.
            invoiceLine.setTaxPercentage(percentage.longValue());

            invoiceLine.setQuantity(entry.getQuantity());

            if (entry.getProduct() != null && !entry.getProduct().getCode().isEmpty()) {
                invoiceLine.setId(entry.getProduct().getCode());
            }

            LOG.debug("InvoiceLine Product:" + invoiceLine);
            invoiceLines.add(invoiceLine);
        }

        // Add delivery costs
        if (cartData.getDeliveryCost() != null) {
            final LineItem invoiceLine = new LineItem();
            invoiceLine.setDescription("Delivery Costs");

            final Amount deliveryAmount = AmountUtil.createAmount(cartData.getDeliveryCost().getValue(), currency);

            if (cartData.isNet()) {
                final Double taxAmount = cartData.getEntries().stream()
                        .map(OrderEntryData::getTaxValues)
                        .flatMap(Collection::stream)
                        .map(TaxValue::getAppliedValue)
                        .collect(Collectors.toList())
                        .stream()
                        .reduce(0.0, Double::sum);
                invoiceLine.setAmountExcludingTax(deliveryAmount.getValue());
                invoiceLine.setTaxAmount(cartData.getTotalTax().getValue().longValue());
            } else {
                invoiceLine.setAmountIncludingTax(deliveryAmount.getValue());
            }

            final Double percentage = cartData.getEntries().stream()
                    .findFirst()
                    .map(OrderEntryData::getTaxValues)
                    .stream()
                    .flatMap(Collection::stream)
                    .map(TaxValue::getValue)
                    .reduce(0.0, Double::sum) * 100;

            invoiceLine.setTaxPercentage(percentage.longValue());
            invoiceLine.setQuantity(1L);
            LOG.debug("InvoiceLine DeliveryCosts:" + invoiceLine);
            invoiceLines.add(invoiceLine);
        }

        paymentsRequest.setLineItems(invoiceLines);
    }

    protected Name getAfterPayShopperName(final CartData cartData) {
        return new Name()
                .firstName(cartData.getAdyenFirstName())
                .lastName(cartData.getAdyenLastName());
    }

    /**
     * Set Boleto payment request data
     */
    protected void setBoletoData(final PaymentRequest paymentsRequest, final CartData cartData) {
        paymentsRequest.setSocialSecurityNumber(cartData.getAdyenSocialSecurityNumber());

        final Name shopperName = new Name()
                .firstName(cartData.getAdyenFirstName())
                .lastName(cartData.getAdyenLastName());

        paymentsRequest.setShopperName(shopperName);

        if (paymentsRequest.getBillingAddress() != null) {
            String stateOrProvinceBilling = paymentsRequest.getBillingAddress().getStateOrProvince();
            if (!StringUtils.isEmpty(stateOrProvinceBilling) && stateOrProvinceBilling.length() > 2) {
                String shortStateOrProvince = stateOrProvinceBilling.substring(stateOrProvinceBilling.length() - 2);
                paymentsRequest.getBillingAddress().setStateOrProvince(shortStateOrProvince);
            }
        }
        if (paymentsRequest.getDeliveryAddress() != null) {
            String stateOrProvinceDelivery = paymentsRequest.getDeliveryAddress().getStateOrProvince();
            if (!StringUtils.isEmpty(stateOrProvinceDelivery) && stateOrProvinceDelivery.length() > 2) {
                String shortStateOrProvince = stateOrProvinceDelivery.substring(stateOrProvinceDelivery.length() - 2);
                paymentsRequest.getDeliveryAddress().setStateOrProvince(shortStateOrProvince);
            }
        }
    }


    protected void setPixData(final PaymentRequest paymentsRequest, final CartData cartData) {
        final List<LineItem> invoiceLines = cartData.getEntries().stream()
                .filter(cartEntry -> cartEntry.getQuantity() > 0)
                .map(cartEntry ->
                        new LineItem()
                                .amountIncludingTax(cartEntry.getBasePrice().getValue().longValue())
                                .id(Optional.ofNullable(cartEntry.getProduct().getName())
                                        .filter(StringUtils::isNotEmpty)
                                        .orElse("NA")
                                )
                )
                .collect(Collectors.toList());

        paymentsRequest.setSocialSecurityNumber(cartData.getAdyenSocialSecurityNumber());
        paymentsRequest.setShopperName(new Name()
                .firstName(cartData.getAdyenFirstName())
                .lastName(cartData.getAdyenLastName())
        );
        paymentsRequest.setLineItems(invoiceLines);
    }

    protected String getPlatformVersion() {
        return getConfigurationService().getConfiguration().getString(PLATFORM_VERSION_PROPERTY);
    }

    protected Boolean is3DS2Allowed() {
        final Configuration configuration = getConfigurationService().getConfiguration();
        if (configuration.containsKey(IS_3DS2_ALLOWED_PROPERTY)) {
            return configuration.getBoolean(IS_3DS2_ALLOWED_PROPERTY);
        }
        return false;
    }

    public ConfigurationService getConfigurationService() {
        return configurationService;
    }


}


