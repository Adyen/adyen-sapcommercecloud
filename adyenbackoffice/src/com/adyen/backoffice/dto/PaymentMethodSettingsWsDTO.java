package com.adyen.backoffice.dto;

import java.util.List;
import java.util.Map;

public class PaymentMethodSettingsWsDTO {
    
    private String id;
    private String type;
    private String name;
    private String description;
    private Boolean enabled;
    private List<String> currencies;
    private List<String> countries;
    private String storeId;
    private String businessLineId;
    private String merchantId;
    private String shopperInteraction;
    private Map<String, Object> configuration;
    private Map<String, Object> verificationSettings;
    private Map<String, Object> fundingSource;
    private Map<String, Object> cardholderName;
    private Map<String, Object> installmentOptions;
    private Map<String, Object> reference;
    private Map<String, Object> shopperStatement;
    private Map<String, Object> surcharge;
    private Map<String, Object> additionalSettings;
    
    // Specific payment method settings
    private ApplePaySettingsWsDTO applePay;
    private GooglePaySettingsWsDTO googlePay;
    private PayPalSettingsWsDTO paypal;
    private CardSettingsWsDTO card;
    private VisaSettingsWsDTO visa;
    private AmexSettingsWsDTO amex;
    private KlarnaSettingsWsDTO klarna;
    private JcbSettingsWsDTO jcb;
    private SepaDirectDebitSettingsWsDTO sepadirectdebit;
    
    // Common fields for all payment methods
    private String processingType;
    private Boolean allowed;
    private String merchantReference;
    private String verificationStatus;
    private List<String> storeIds;
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public List<String> getCurrencies() {
        return currencies;
    }
    
    public void setCurrencies(List<String> currencies) {
        this.currencies = currencies;
    }
    
    public List<String> getCountries() {
        return countries;
    }
    
    public void setCountries(List<String> countries) {
        this.countries = countries;
    }
    
    public String getStoreId() {
        return storeId;
    }
    
    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }
    
    public String getBusinessLineId() {
        return businessLineId;
    }
    
    public void setBusinessLineId(String businessLineId) {
        this.businessLineId = businessLineId;
    }
    
    public String getMerchantId() {
        return merchantId;
    }
    
    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }
    
    public String getShopperInteraction() {
        return shopperInteraction;
    }
    
    public void setShopperInteraction(String shopperInteraction) {
        this.shopperInteraction = shopperInteraction;
    }
    
    public Map<String, Object> getConfiguration() {
        return configuration;
    }
    
    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }
    
    public Map<String, Object> getVerificationSettings() {
        return verificationSettings;
    }
    
    public void setVerificationSettings(Map<String, Object> verificationSettings) {
        this.verificationSettings = verificationSettings;
    }
    
    public Map<String, Object> getFundingSource() {
        return fundingSource;
    }
    
    public void setFundingSource(Map<String, Object> fundingSource) {
        this.fundingSource = fundingSource;
    }
    
    public Map<String, Object> getCardholderName() {
        return cardholderName;
    }
    
    public void setCardholderName(Map<String, Object> cardholderName) {
        this.cardholderName = cardholderName;
    }
    
    public Map<String, Object> getInstallmentOptions() {
        return installmentOptions;
    }
    
    public void setInstallmentOptions(Map<String, Object> installmentOptions) {
        this.installmentOptions = installmentOptions;
    }
    
    public Map<String, Object> getReference() {
        return reference;
    }
    
    public void setReference(Map<String, Object> reference) {
        this.reference = reference;
    }
    
    public Map<String, Object> getShopperStatement() {
        return shopperStatement;
    }
    
    public void setShopperStatement(Map<String, Object> shopperStatement) {
        this.shopperStatement = shopperStatement;
    }
    
    public Map<String, Object> getSurcharge() {
        return surcharge;
    }
    
    public void setSurcharge(Map<String, Object> surcharge) {
        this.surcharge = surcharge;
    }
    
    public Map<String, Object> getAdditionalSettings() {
        return additionalSettings;
    }
    
    public void setAdditionalSettings(Map<String, Object> additionalSettings) {
        this.additionalSettings = additionalSettings;
    }
    
    public ApplePaySettingsWsDTO getApplePay() {
        return applePay;
    }
    
    public void setApplePay(ApplePaySettingsWsDTO applePay) {
        this.applePay = applePay;
    }
    
    public GooglePaySettingsWsDTO getGooglePay() {
        return googlePay;
    }
    
    public void setGooglePay(GooglePaySettingsWsDTO googlePay) {
        this.googlePay = googlePay;
    }
    
    public PayPalSettingsWsDTO getPaypal() {
        return paypal;
    }
    
    public void setPaypal(PayPalSettingsWsDTO paypal) {
        this.paypal = paypal;
    }
    
    public CardSettingsWsDTO getCard() {
        return card;
    }
    
    public void setCard(CardSettingsWsDTO card) {
        this.card = card;
    }
    
    public VisaSettingsWsDTO getVisa() {
        return visa;
    }
    
    public void setVisa(VisaSettingsWsDTO visa) {
        this.visa = visa;
    }
    
    public AmexSettingsWsDTO getAmex() {
        return amex;
    }
    
    public void setAmex(AmexSettingsWsDTO amex) {
        this.amex = amex;
    }
    
    public KlarnaSettingsWsDTO getKlarna() {
        return klarna;
    }
    
    public void setKlarna(KlarnaSettingsWsDTO klarna) {
        this.klarna = klarna;
    }
    
    public JcbSettingsWsDTO getJcb() {
        return jcb;
    }
    
    public void setJcb(JcbSettingsWsDTO jcb) {
        this.jcb = jcb;
    }
    
    public SepaDirectDebitSettingsWsDTO getSepadirectdebit() {
        return sepadirectdebit;
    }
    
    public void setSepadirectdebit(SepaDirectDebitSettingsWsDTO sepadirectdebit) {
        this.sepadirectdebit = sepadirectdebit;
    }
    
    public String getProcessingType() {
        return processingType;
    }
    
    public void setProcessingType(String processingType) {
        this.processingType = processingType;
    }
    
    public Boolean getAllowed() {
        return allowed;
    }
    
    public void setAllowed(Boolean allowed) {
        this.allowed = allowed;
    }
    
    public String getMerchantReference() {
        return merchantReference;
    }
    
    public void setMerchantReference(String merchantReference) {
        this.merchantReference = merchantReference;
    }
    
    public String getVerificationStatus() {
        return verificationStatus;
    }
    
    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }
    
    public List<String> getStoreIds() {
        return storeIds;
    }
    
    public void setStoreIds(List<String> storeIds) {
        this.storeIds = storeIds;
    }
}