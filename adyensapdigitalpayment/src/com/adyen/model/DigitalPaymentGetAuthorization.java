package com.adyen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalPaymentGetAuthorization {

    @JsonProperty("AuthorizationByPaytSrvcPrvdr")
    private String authorizationByPaytSrvcPrvdr;
    @JsonProperty("AuthorizationCurrency")
    private String authorizationCurrency;
    @JsonProperty("AuthorizedAmountInAuthznCrcy")
    private String authorizedAmountInAuthznCrcy;
    @JsonProperty("DigitalPaymentCommerceType")
    private String digitalPaymentCommerceType;
    @JsonProperty("DigitalPaymentSessionType")
    private String digitalPaymentSessionType;
    @JsonProperty("MerchantAccount")
    private String merchantAccount;
    @JsonProperty("PaytCardByDigitalPaymentSrvc")
    private String paytCardByDigitalPaymentSrvc;

    @JsonProperty("PaymentServiceProvider")
    private String paymentServiceProvider;
    @JsonProperty("PaytCardByPaytServiceProvider")
    private String paytCardByPaytServiceProvider;
    @JsonProperty("PaytCardRegnLifeCycleType")
    private String paytCardRegnLifeCycleType;

    @JsonProperty("DigitalPaymentAuthorizationType")
    private String digitalPaymentAuthorizationType;

    @JsonProperty("PaymentCardContext")
    private DigitalGetPaymentCardContext paymentCardContext;



    public String getAuthorizationByPaytSrvcPrvdr() {
        return authorizationByPaytSrvcPrvdr;
    }

    public void setAuthorizationByPaytSrvcPrvdr(String authorizationByPaytSrvcPrvdr) {
        this.authorizationByPaytSrvcPrvdr = authorizationByPaytSrvcPrvdr;
    }

    public String getAuthorizationCurrency() {
        return authorizationCurrency;
    }

    public void setAuthorizationCurrency(String authorizationCurrency) {
        this.authorizationCurrency = authorizationCurrency;
    }

    public String getAuthorizedAmountInAuthznCrcy() {
        return authorizedAmountInAuthznCrcy;
    }

    public void setAuthorizedAmountInAuthznCrcy(String authorizedAmountInAuthznCrcy) {
        this.authorizedAmountInAuthznCrcy = authorizedAmountInAuthznCrcy;
    }

    public String getDigitalPaymentCommerceType() {
        return digitalPaymentCommerceType;
    }

    public void setDigitalPaymentCommerceType(String digitalPaymentCommerceType) {
        this.digitalPaymentCommerceType = digitalPaymentCommerceType;
    }

    public String getDigitalPaymentSessionType() {
        return digitalPaymentSessionType;
    }

    public void setDigitalPaymentSessionType(String digitalPaymentSessionType) {
        this.digitalPaymentSessionType = digitalPaymentSessionType;
    }

    public String getMerchantAccount() {
        return merchantAccount;
    }

    public void setMerchantAccount(String merchantAccount) {
        this.merchantAccount = merchantAccount;
    }

    public String getPaytCardByDigitalPaymentSrvc() {
        return paytCardByDigitalPaymentSrvc;
    }

    public void setPaytCardByDigitalPaymentSrvc(String paytCardByDigitalPaymentSrvc) {
        this.paytCardByDigitalPaymentSrvc = paytCardByDigitalPaymentSrvc;
    }

    public String getPaymentServiceProvider() {
        return paymentServiceProvider;
    }

    public void setPaymentServiceProvider(String paymentServiceProvider) {
        this.paymentServiceProvider = paymentServiceProvider;
    }

    public String getPaytCardByPaytServiceProvider() {
        return paytCardByPaytServiceProvider;
    }

    public void setPaytCardByPaytServiceProvider(String paytCardByPaytServiceProvider) {
        this.paytCardByPaytServiceProvider = paytCardByPaytServiceProvider;
    }

    public String getPaytCardRegnLifeCycleType() {
        return paytCardRegnLifeCycleType;
    }

    public void setPaytCardRegnLifeCycleType(String paytCardRegnLifeCycleType) {
        this.paytCardRegnLifeCycleType = paytCardRegnLifeCycleType;
    }

    public String getDigitalPaymentAuthorizationType() {
        return digitalPaymentAuthorizationType;
    }

    public void setDigitalPaymentAuthorizationType(String digitalPaymentAuthorizationType) {
        this.digitalPaymentAuthorizationType = digitalPaymentAuthorizationType;
    }

    public DigitalGetPaymentCardContext getPaymentCardContext() {
        return paymentCardContext;
    }

    public void setPaymentCardContext(DigitalGetPaymentCardContext paymentCardContext) {
        this.paymentCardContext = paymentCardContext;
    }
}
