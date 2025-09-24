package com.adyen.backoffice.dto;

public class PayPalSettingsWsDTO {
    
    private String intent;
    private String landingPage;
    private String userAction;
    private String paymentType;
    private Boolean enableExpressCheckout;
    private String merchantId;
    private String clientId;
    
    public String getIntent() {
        return intent;
    }
    
    public void setIntent(String intent) {
        this.intent = intent;
    }
    
    public String getLandingPage() {
        return landingPage;
    }
    
    public void setLandingPage(String landingPage) {
        this.landingPage = landingPage;
    }
    
    public String getUserAction() {
        return userAction;
    }
    
    public void setUserAction(String userAction) {
        this.userAction = userAction;
    }
    
    public String getPaymentType() {
        return paymentType;
    }
    
    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }
    
    public Boolean getEnableExpressCheckout() {
        return enableExpressCheckout;
    }
    
    public void setEnableExpressCheckout(Boolean enableExpressCheckout) {
        this.enableExpressCheckout = enableExpressCheckout;
    }
    
    public String getMerchantId() {
        return merchantId;
    }
    
    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}