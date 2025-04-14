package com.adyen.v6.dto;

import com.adyen.model.checkout.Amount;

import java.math.BigDecimal;

public class ExpressCheckoutConfigDTOBuilder {
    private final ExpressCheckoutConfigDTO expressCheckoutConfigDTO;

    public ExpressCheckoutConfigDTOBuilder() {
        this.expressCheckoutConfigDTO = new ExpressCheckoutConfigDTO();
    }

    public ExpressCheckoutConfigDTOBuilder setApplePayMerchantId(String applePayMerchantId) {
        expressCheckoutConfigDTO.setApplePayMerchantId(applePayMerchantId);
        return this;
    }

    public ExpressCheckoutConfigDTOBuilder setApplePayMerchantName(String applePayMerchantName) {
        expressCheckoutConfigDTO.setApplePayMerchantName(applePayMerchantName);
        return this;
    }

    public ExpressCheckoutConfigDTOBuilder setGooglePayMerchantId(String googlePayMerchantId) {
        expressCheckoutConfigDTO.setGooglePayMerchantId(googlePayMerchantId);
        return this;
    }

    public ExpressCheckoutConfigDTOBuilder setGooglePayGatewayMerchantId(String googlePayGatewayMerchantId) {
        expressCheckoutConfigDTO.setGooglePayGatewayMerchantId(googlePayGatewayMerchantId);
        return this;
    }

    public ExpressCheckoutConfigDTOBuilder setPayPalIntent(String payPalIntent) {
        expressCheckoutConfigDTO.setPayPalIntent(payPalIntent);
        return this;
    }

    public ExpressCheckoutConfigDTOBuilder setShopperLocale(String shopperLocale) {
        expressCheckoutConfigDTO.setShopperLocale(shopperLocale);
        return this;
    }

    public ExpressCheckoutConfigDTOBuilder setEnvironmentMode(String environmentMode) {
        expressCheckoutConfigDTO.setEnvironmentMode(environmentMode);
        return this;
    }

    public ExpressCheckoutConfigDTOBuilder setClientKey(String clientKey) {
        expressCheckoutConfigDTO.setClientKey(clientKey);
        return this;
    }

    public ExpressCheckoutConfigDTOBuilder setMerchantAccount(String merchantAccount) {
        expressCheckoutConfigDTO.setMerchantAccount(merchantAccount);
        return this;
    }

    public ExpressCheckoutConfigDTOBuilder setAmount(Amount amount) {
        expressCheckoutConfigDTO.setAmount(amount);
        return this;
    }

    public ExpressCheckoutConfigDTOBuilder setAmountDecimal(BigDecimal amountDecimal) {
        expressCheckoutConfigDTO.setAmountDecimal(amountDecimal);
        return this;
    }

    public ExpressCheckoutConfigDTOBuilder setDfUrl(String dfUrl) {
        expressCheckoutConfigDTO.setDfUrl(dfUrl);
        return this;
    }

    public ExpressCheckoutConfigDTOBuilder setCheckoutShopperHost(String checkoutShopperHost) {
        expressCheckoutConfigDTO.setCheckoutShopperHost(checkoutShopperHost);
        return this;
    }

    public ExpressCheckoutConfigDTOBuilder setExpressPaymentConfigDto(ExpressPaymentConfigDto expressPaymentConfigDto) {
        expressCheckoutConfigDTO.setExpressPaymentConfig(expressPaymentConfigDto);
        return this;
    }

    public ExpressCheckoutConfigDTO build() {
        return expressCheckoutConfigDTO;
    }
}