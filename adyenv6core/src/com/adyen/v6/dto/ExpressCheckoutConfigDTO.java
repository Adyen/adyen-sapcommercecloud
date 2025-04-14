package com.adyen.v6.dto;

import com.adyen.model.checkout.Amount;

import java.math.BigDecimal;

public class ExpressCheckoutConfigDTO {
    private String applePayMerchantId;
    private String applePayMerchantName;
    private String googlePayMerchantId;
    private String googlePayGatewayMerchantId;
    private String payPalIntent;
    private String shopperLocale;
    private String environmentMode;
    private String clientKey;
    private String merchantAccount;
    private Amount amount;
    private BigDecimal amountDecimal;
    private String dfUrl;
    private String checkoutShopperHost;
    private ExpressPaymentConfigDto expressPaymentConfig;

    public String getApplePayMerchantId() {
        return applePayMerchantId;
    }

    public void setApplePayMerchantId(String applePayMerchantId) {
        this.applePayMerchantId = applePayMerchantId;
    }

    public String getApplePayMerchantName() {
        return applePayMerchantName;
    }

    public void setApplePayMerchantName(String applePayMerchantName) {
        this.applePayMerchantName = applePayMerchantName;
    }

    public String getGooglePayMerchantId() {
        return googlePayMerchantId;
    }

    public void setGooglePayMerchantId(String googlePayMerchantId) {
        this.googlePayMerchantId = googlePayMerchantId;
    }

    public String getGooglePayGatewayMerchantId() {
        return googlePayGatewayMerchantId;
    }

    public void setGooglePayGatewayMerchantId(String googlePayGatewayMerchantId) {
        this.googlePayGatewayMerchantId = googlePayGatewayMerchantId;
    }

    public String getPayPalIntent() {
        return payPalIntent;
    }

    public void setPayPalIntent(String payPalIntent) {
        this.payPalIntent = payPalIntent;
    }

    public String getShopperLocale() {
        return shopperLocale;
    }

    public void setShopperLocale(String shopperLocale) {
        this.shopperLocale = shopperLocale;
    }

    public String getEnvironmentMode() {
        return environmentMode;
    }

    public void setEnvironmentMode(String environmentMode) {
        this.environmentMode = environmentMode;
    }

    public String getClientKey() {
        return clientKey;
    }

    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }

    public String getMerchantAccount() {
        return merchantAccount;
    }

    public void setMerchantAccount(String merchantAccount) {
        this.merchantAccount = merchantAccount;
    }

    public Amount getAmount() {
        return amount;
    }

    public void setAmount(Amount amount) {
        this.amount = amount;
    }

    public BigDecimal getAmountDecimal() {
        return amountDecimal;
    }

    public void setAmountDecimal(BigDecimal amountDecimal) {
        this.amountDecimal = amountDecimal;
    }

    public String getDfUrl() {
        return dfUrl;
    }

    public void setDfUrl(String dfUrl) {
        this.dfUrl = dfUrl;
    }

    public String getCheckoutShopperHost() {
        return checkoutShopperHost;
    }

    public void setCheckoutShopperHost(String checkoutShopperHost) {
        this.checkoutShopperHost = checkoutShopperHost;
    }

    public ExpressPaymentConfigDto getExpressPaymentConfig() {
        return expressPaymentConfig;
    }

    public void setExpressPaymentConfig(ExpressPaymentConfigDto expressPaymentConfig) {
        this.expressPaymentConfig = expressPaymentConfig;
    }
}
