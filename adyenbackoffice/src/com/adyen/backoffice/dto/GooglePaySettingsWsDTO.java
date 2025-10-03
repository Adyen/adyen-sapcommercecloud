package com.adyen.backoffice.dto;

import java.util.List;

public class GooglePaySettingsWsDTO {
    
    private String merchantId;
    private String merchantName;
    private List<String> allowedAuthMethods;
    private List<String> allowedCardNetworks;
    private Boolean assuranceDetailsRequired;
    private Boolean billingAddressRequired;
    private String billingAddressParametersFormat;
    
    public String getMerchantId() {
        return merchantId;
    }
    
    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }
    
    public String getMerchantName() {
        return merchantName;
    }
    
    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }
    
    public List<String> getAllowedAuthMethods() {
        return allowedAuthMethods;
    }
    
    public void setAllowedAuthMethods(List<String> allowedAuthMethods) {
        this.allowedAuthMethods = allowedAuthMethods;
    }
    
    public List<String> getAllowedCardNetworks() {
        return allowedCardNetworks;
    }
    
    public void setAllowedCardNetworks(List<String> allowedCardNetworks) {
        this.allowedCardNetworks = allowedCardNetworks;
    }
    
    public Boolean getAssuranceDetailsRequired() {
        return assuranceDetailsRequired;
    }
    
    public void setAssuranceDetailsRequired(Boolean assuranceDetailsRequired) {
        this.assuranceDetailsRequired = assuranceDetailsRequired;
    }
    
    public Boolean getBillingAddressRequired() {
        return billingAddressRequired;
    }
    
    public void setBillingAddressRequired(Boolean billingAddressRequired) {
        this.billingAddressRequired = billingAddressRequired;
    }
    
    public String getBillingAddressParametersFormat() {
        return billingAddressParametersFormat;
    }
    
    public void setBillingAddressParametersFormat(String billingAddressParametersFormat) {
        this.billingAddressParametersFormat = billingAddressParametersFormat;
    }
}