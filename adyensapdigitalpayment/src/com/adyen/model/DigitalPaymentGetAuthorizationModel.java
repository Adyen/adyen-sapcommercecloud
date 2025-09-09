package com.adyen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalPaymentGetAuthorizationModel {

    @JsonProperty("AuthorizationByAcquirer")
    private String authorizationByAcquirer;
    @JsonProperty("AuthorizationByDigitalPaytSrvc")
    private String authorizationByDigitalPaytSrvc;
    @JsonProperty("AuthorizationByPaytSrvcPrvdr")
    private String authorizationByPaytSrvcPrvdr;
    @JsonProperty("AuthorizationCurrency")
    private String authorizationCurrency;

    @JsonProperty("AuthorizationDateTime")
    private String authorizationDateTime;
    @JsonProperty("AuthorizationExpirationDateTme")
    private String authorizationExpirationDateTme;
    @JsonProperty("AuthorizationExpirationDateTime")
    private String authorizationExpirationDateTime;

    @JsonProperty("AuthorizedAmountInAuthznCrcy")
    private String authorizedAmountInAuthznCrcy;
    @JsonProperty("DetailedAuthorizationStatus")
    private String detailedAuthorizationStatus;
    @JsonProperty("MerchantAlias")
    private String merchantAlias;

    @JsonProperty("StatusDescription")
    private String statusDescription;

    @JsonProperty("DgtlPaytAuthznRelationID")
    private String dgtlPaytAuthznRelationID;

    @JsonProperty("DigitalPaymentFraudRisk")
    private String digitalPaymentFraudRisk;


    public String getAuthorizationByAcquirer() {
        return authorizationByAcquirer;
    }

    public void setAuthorizationByAcquirer(String authorizationByAcquirer) {
        this.authorizationByAcquirer = authorizationByAcquirer;
    }

    public String getAuthorizationByDigitalPaytSrvc() {
        return authorizationByDigitalPaytSrvc;
    }

    public void setAuthorizationByDigitalPaytSrvc(String authorizationByDigitalPaytSrvc) {
        this.authorizationByDigitalPaytSrvc = authorizationByDigitalPaytSrvc;
    }

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

    public String getAuthorizationDateTime() {
        return authorizationDateTime;
    }

    public void setAuthorizationDateTime(String authorizationDateTime) {
        this.authorizationDateTime = authorizationDateTime;
    }

    public String getAuthorizationExpirationDateTme() {
        return authorizationExpirationDateTme;
    }

    public void setAuthorizationExpirationDateTme(String authorizationExpirationDateTme) {
        this.authorizationExpirationDateTme = authorizationExpirationDateTme;
    }

    public String getAuthorizedAmountInAuthznCrcy() {
        return authorizedAmountInAuthznCrcy;
    }

    public void setAuthorizedAmountInAuthznCrcy(String authorizedAmountInAuthznCrcy) {
        this.authorizedAmountInAuthznCrcy = authorizedAmountInAuthznCrcy;
    }

    public String getDetailedAuthorizationStatus() {
        return detailedAuthorizationStatus;
    }

    public void setDetailedAuthorizationStatus(String detailedAuthorizationStatus) {
        this.detailedAuthorizationStatus = detailedAuthorizationStatus;
    }

    public String getMerchantAlias() {
        return merchantAlias;
    }

    public void setMerchantAlias(String merchantAlias) {
        this.merchantAlias = merchantAlias;
    }

    public String getAuthorizationExpirationDateTime() {
        return authorizationExpirationDateTime;
    }

    public void setAuthorizationExpirationDateTime(String authorizationExpirationDateTime) {
        this.authorizationExpirationDateTime = authorizationExpirationDateTime;
    }

    public String getDgtlPaytAuthznRelationID() {
        return dgtlPaytAuthznRelationID;
    }

    public void setDgtlPaytAuthznRelationID(String dgtlPaytAuthznRelationID) {
        this.dgtlPaytAuthznRelationID = dgtlPaytAuthznRelationID;
    }

    public String getDigitalPaymentFraudRisk() {
        return digitalPaymentFraudRisk;
    }

    public void setDigitalPaymentFraudRisk(String digitalPaymentFraudRisk) {
        this.digitalPaymentFraudRisk = digitalPaymentFraudRisk;
    }

    public String getStatusDescription() {
        return statusDescription;
    }

    public void setStatusDescription(String statusDescription) {
        this.statusDescription = statusDescription;
    }


}
