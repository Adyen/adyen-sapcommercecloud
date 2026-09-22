package com.adyen.v6.dto;

import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.PaymentMethod;
import com.adyen.model.checkout.StoredPaymentMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class CheckoutConfigDTO {
    @Deprecated
    private List<PaymentMethod> alternativePaymentMethods;
    private List<PaymentMethod> paymentMethods;
    private List<StoredPaymentMethod> storedPaymentMethodList;
    @Deprecated
    private Map<String, String> issuerLists;
    @Deprecated
    private String creditCardLabel;
    @Deprecated
    private List<String> allowedCards;
    private Amount amount;
    private String adyenClientKey;
    private String adyenPaypalMerchantId;
    private String deviceFingerPrintUrl;
    private String selectedPaymentMethod;
    private boolean showRememberTheseDetails;
    private String checkoutShopperHost;
    private String environmentMode;
    private String shopperLocale;
    private List<String> openInvoiceMethods;
    private boolean showSocialSecurityNumber;
    private boolean showBoleto;
    private boolean showComboCard;
    private boolean immediateCapture;
    private String countryCode;
    private boolean cardHolderNameRequired;
    private boolean sepaDirectDebit;
    private BigDecimal amountDecimal;
    private ExpressPaymentConfigDto expressPaymentConfig;
    private String merchantDisplayName;
    private String shopperEmail;
    private String clickToPayLocale;
    private InstallmentOptionsDTO installmentOptions;
    private boolean skipCvcForOneClick;

    // Getters and setters for the new field
    public ExpressPaymentConfigDto getExpressPaymentConfig() {
        return expressPaymentConfig;
    }

    public void setExpressPaymentConfig(ExpressPaymentConfigDto expressPaymentConfigDto) {
        this.expressPaymentConfig = expressPaymentConfigDto;
    }

    public List<PaymentMethod> getAlternativePaymentMethods() {
        return alternativePaymentMethods;
    }

    public void setAlternativePaymentMethods(List<PaymentMethod> alternativePaymentMethods) {
        this.alternativePaymentMethods = alternativePaymentMethods;
    }

    public List<StoredPaymentMethod> getStoredPaymentMethodList() {
        return storedPaymentMethodList;
    }

    public void setStoredPaymentMethodList(List<StoredPaymentMethod> storedPaymentMethodList) {
        this.storedPaymentMethodList = storedPaymentMethodList;
    }

    public Map<String, String> getIssuerLists() {
        return issuerLists;
    }

    public void setIssuerLists(Map<String, String> issuerLists) {
        this.issuerLists = issuerLists;
    }

    public String getCreditCardLabel() {
        return creditCardLabel;
    }

    public void setCreditCardLabel(String creditCardLabel) {
        this.creditCardLabel = creditCardLabel;
    }

    public List<String> getAllowedCards() {
        return allowedCards;
    }

    public void setAllowedCards(List<String> allowedCards) {
        this.allowedCards = allowedCards;
    }

    public Amount getAmount() {
        return amount;
    }

    public void setAmount(Amount amount) {
        this.amount = amount;
    }

    public String getAdyenClientKey() {
        return adyenClientKey;
    }

    public void setAdyenClientKey(String adyenClientKey) {
        this.adyenClientKey = adyenClientKey;
    }

    public String getAdyenPaypalMerchantId() {
        return adyenPaypalMerchantId;
    }

    public void setAdyenPaypalMerchantId(String adyenPaypalMerchantId) {
        this.adyenPaypalMerchantId = adyenPaypalMerchantId;
    }

    public String getDeviceFingerPrintUrl() {
        return deviceFingerPrintUrl;
    }

    public void setDeviceFingerPrintUrl(String deviceFingerPrintUrl) {
        this.deviceFingerPrintUrl = deviceFingerPrintUrl;
    }

    public String getSelectedPaymentMethod() {
        return selectedPaymentMethod;
    }

    public void setSelectedPaymentMethod(String selectedPaymentMethod) {
        this.selectedPaymentMethod = selectedPaymentMethod;
    }

    public boolean isShowRememberTheseDetails() {
        return showRememberTheseDetails;
    }

    public void setShowRememberTheseDetails(boolean showRememberTheseDetails) {
        this.showRememberTheseDetails = showRememberTheseDetails;
    }

    public String getCheckoutShopperHost() {
        return checkoutShopperHost;
    }

    public void setCheckoutShopperHost(String checkoutShopperHost) {
        this.checkoutShopperHost = checkoutShopperHost;
    }

    public String getEnvironmentMode() {
        return environmentMode;
    }

    public void setEnvironmentMode(String environmentMode) {
        this.environmentMode = environmentMode;
    }

    public String getShopperLocale() {
        return shopperLocale;
    }

    public void setShopperLocale(String shopperLocale) {
        this.shopperLocale = shopperLocale;
    }

    public List<String> getOpenInvoiceMethods() {
        return openInvoiceMethods;
    }

    public void setOpenInvoiceMethods(List<String> openInvoiceMethods) {
        this.openInvoiceMethods = openInvoiceMethods;
    }

    public boolean isShowSocialSecurityNumber() {
        return showSocialSecurityNumber;
    }

    public void setShowSocialSecurityNumber(boolean showSocialSecurityNumber) {
        this.showSocialSecurityNumber = showSocialSecurityNumber;
    }

    public boolean isShowBoleto() {
        return showBoleto;
    }

    public void setShowBoleto(boolean showBoleto) {
        this.showBoleto = showBoleto;
    }

    public boolean isShowComboCard() {
        return showComboCard;
    }

    public void setShowComboCard(boolean showComboCard) {
        this.showComboCard = showComboCard;
    }

    public boolean isImmediateCapture() {
        return immediateCapture;
    }

    public void setImmediateCapture(boolean immediateCapture) {
        this.immediateCapture = immediateCapture;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public boolean isCardHolderNameRequired() {
        return cardHolderNameRequired;
    }

    public void setCardHolderNameRequired(boolean cardHolderNameRequired) {
        this.cardHolderNameRequired = cardHolderNameRequired;
    }

    public boolean isSepaDirectDebit() {
        return sepaDirectDebit;
    }

    public void setSepaDirectDebit(boolean sepadirectdebit) {
        this.sepaDirectDebit = sepadirectdebit;
    }

    public List<PaymentMethod> getPaymentMethods() {
        return paymentMethods;
    }

    public void setPaymentMethods(List<PaymentMethod> paymentMethods) {
        this.paymentMethods = paymentMethods;
    }

    public BigDecimal getAmountDecimal() {
        return amountDecimal;
    }

    public void setAmountDecimal(BigDecimal amountDecimal) {
        this.amountDecimal = amountDecimal;
    }

    public String getMerchantDisplayName() {
        return merchantDisplayName;
    }

    public void setMerchantDisplayName(String merchantDisplayName) {
        this.merchantDisplayName = merchantDisplayName;
    }

    public String getShopperEmail() {
        return shopperEmail;
    }

    public void setShopperEmail(String shopperEmail) {
        this.shopperEmail = shopperEmail;
    }

    public String getClickToPayLocale() {
        return clickToPayLocale;
    }

    public void setClickToPayLocale(String clickToPayLocale) {
        this.clickToPayLocale = clickToPayLocale;
    }

    public InstallmentOptionsDTO getInstallmentOptions() {
        return installmentOptions;
    }

    public void setInstallmentOptions(InstallmentOptionsDTO installmentOptions) {
        this.installmentOptions = installmentOptions;
    }

    public boolean isSkipCvcForOneClick() {
        return skipCvcForOneClick;
    }

    public void setSkipCvcForOneClick(boolean skipCvcForOneClick) {
        this.skipCvcForOneClick = skipCvcForOneClick;
    }
}