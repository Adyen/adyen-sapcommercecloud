package com.adyen.v6.dto;


import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.CreateCheckoutSessionResponse;
import com.adyen.model.checkout.PaymentMethod;
import com.adyen.model.checkout.StoredPaymentMethod;
import com.adyen.v6.enums.AdyenCardTypeEnum;

import java.util.List;
import java.util.Map;

public class CheckoutConfigDTOBuilder {

    private final CheckoutConfigDTO checkoutConfigDTO;

    public CheckoutConfigDTOBuilder() {
        checkoutConfigDTO = new CheckoutConfigDTO();
    }

    public CheckoutConfigDTOBuilder setAlternativePaymentMethods(List<PaymentMethod> alternativePaymentMethods) {
        checkoutConfigDTO.setAlternativePaymentMethods(alternativePaymentMethods);
        return this;
    }

    public CheckoutConfigDTOBuilder setPaymentMethods(List<PaymentMethod> paymentMethods) {
        checkoutConfigDTO.setPaymentMethods(paymentMethods);
        return this;
    }

    public CheckoutConfigDTOBuilder setConnectedTerminalList(List<String> connectedTerminalList) {
        checkoutConfigDTO.setConnectedTerminalList(connectedTerminalList);
        return this;
    }

    public CheckoutConfigDTOBuilder setStoredPaymentMethodList(List<StoredPaymentMethod> storedPaymentMethodList) {
        checkoutConfigDTO.setStoredPaymentMethodList(storedPaymentMethodList);
        return this;
    }

    public CheckoutConfigDTOBuilder setIssuerLists(Map<String, String> issuerLists) {
        checkoutConfigDTO.setIssuerLists(issuerLists);
        return this;
    }

    public CheckoutConfigDTOBuilder setCreditCardLabel(String creditCardLabel) {
        checkoutConfigDTO.setCreditCardLabel(creditCardLabel);
        return this;
    }

    public CheckoutConfigDTOBuilder setAllowedCards(List<AdyenCardTypeEnum> allowedCards) {
        List<String> mappedAllowedCards = allowedCards.stream().map(AdyenCardTypeEnum::toString).toList();
        checkoutConfigDTO.setAllowedCards(mappedAllowedCards);
        return this;
    }

    public CheckoutConfigDTOBuilder setAmount(Amount amount) {
        checkoutConfigDTO.setAmount(amount);
        return this;
    }

    public CheckoutConfigDTOBuilder setAdyenClientKey(String adyenClientKey) {
        checkoutConfigDTO.setAdyenClientKey(adyenClientKey);
        return this;
    }

    public CheckoutConfigDTOBuilder setAdyenPaypalMerchantId(String adyenPaypalMerchantId) {
        checkoutConfigDTO.setAdyenPaypalMerchantId(adyenPaypalMerchantId);
        return this;
    }

    public CheckoutConfigDTOBuilder setDeviceFingerPrintUrl(String deviceFingerPrintUrl) {
        checkoutConfigDTO.setDeviceFingerPrintUrl(deviceFingerPrintUrl);
        return this;
    }

    public CheckoutConfigDTOBuilder setSessionData(CreateCheckoutSessionResponse sessionData) {
        checkoutConfigDTO.setSessionData(sessionData);
        return this;
    }

    public CheckoutConfigDTOBuilder setSelectedPaymentMethod(String selectedPaymentMethod) {
        checkoutConfigDTO.setSelectedPaymentMethod(selectedPaymentMethod);
        return this;
    }

    public CheckoutConfigDTOBuilder setShowRememberTheseDetails(boolean showRememberTheseDetails) {
        checkoutConfigDTO.setShowRememberTheseDetails(showRememberTheseDetails);
        return this;
    }

    public CheckoutConfigDTOBuilder setCheckoutShopperHost(String checkoutShopperHost) {
        checkoutConfigDTO.setCheckoutShopperHost(checkoutShopperHost);
        return this;
    }

    public CheckoutConfigDTOBuilder setEnvironmentMode(String environmentMode) {
        checkoutConfigDTO.setEnvironmentMode(environmentMode);
        return this;
    }

    public CheckoutConfigDTOBuilder setShopperLocale(String shopperLocale) {
        checkoutConfigDTO.setShopperLocale(shopperLocale);
        return this;
    }

    public CheckoutConfigDTOBuilder setOpenInvoiceMethods(List<String> openInvoiceMethods) {
        checkoutConfigDTO.setOpenInvoiceMethods(openInvoiceMethods);
        return this;
    }

    public CheckoutConfigDTOBuilder setShowSocialSecurityNumber(boolean showSocialSecurityNumber) {
        checkoutConfigDTO.setShowSocialSecurityNumber(showSocialSecurityNumber);
        return this;
    }

    public CheckoutConfigDTOBuilder setShowBoleto(boolean showBoleto) {
        checkoutConfigDTO.setShowBoleto(showBoleto);
        return this;
    }

    public CheckoutConfigDTOBuilder setShowComboCard(boolean showComboCard) {
        checkoutConfigDTO.setShowComboCard(showComboCard);
        return this;
    }

    public CheckoutConfigDTOBuilder setShowPos(boolean showPos) {
        checkoutConfigDTO.setShowPos(showPos);
        return this;
    }

    public CheckoutConfigDTOBuilder setImmediateCapture(boolean immediateCapture) {
        checkoutConfigDTO.setImmediateCapture(immediateCapture);
        return this;
    }

    public CheckoutConfigDTOBuilder setCountryCode(String countryCode) {
        checkoutConfigDTO.setCountryCode(countryCode);
        return this;
    }

    public CheckoutConfigDTOBuilder setCardHolderNameRequired(boolean cardHolderNameRequired) {
        checkoutConfigDTO.setCardHolderNameRequired(cardHolderNameRequired);
        return this;
    }

    public CheckoutConfigDTOBuilder setSepaDirectDebit(boolean sepaDirectDebit) {
        checkoutConfigDTO.setSepaDirectDebit(sepaDirectDebit);
        return this;
    }

    public CheckoutConfigDTOBuilder setMerchantDisplayName(String merchantDisplayName) {
        checkoutConfigDTO.setMerchantDisplayName(merchantDisplayName);
        return this;
    }

    public CheckoutConfigDTOBuilder setShopperEmail(String shopperEmail) {
        checkoutConfigDTO.setShopperEmail(shopperEmail);
        return this;
    }

    public CheckoutConfigDTO build() {
        return checkoutConfigDTO;
    }
}