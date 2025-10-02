package com.adyen.backoffice.dto;

import java.util.List;

public class CardSettingsWsDTO {
    
    private Boolean enableRealTimeUpdate;
    private Boolean enableRecurring;
    private Boolean enableOneClick;
    private Boolean enableManualCapture;
    private List<String> supportedBrands;
    private String acquirer;
    private String acquirerCountry;
    private String processingType;
    private TransactionDescriptionWsDTO transactionDescription;
    
    public Boolean getEnableRealTimeUpdate() {
        return enableRealTimeUpdate;
    }
    
    public void setEnableRealTimeUpdate(Boolean enableRealTimeUpdate) {
        this.enableRealTimeUpdate = enableRealTimeUpdate;
    }
    
    public Boolean getEnableRecurring() {
        return enableRecurring;
    }
    
    public void setEnableRecurring(Boolean enableRecurring) {
        this.enableRecurring = enableRecurring;
    }
    
    public Boolean getEnableOneClick() {
        return enableOneClick;
    }
    
    public void setEnableOneClick(Boolean enableOneClick) {
        this.enableOneClick = enableOneClick;
    }
    
    public Boolean getEnableManualCapture() {
        return enableManualCapture;
    }
    
    public void setEnableManualCapture(Boolean enableManualCapture) {
        this.enableManualCapture = enableManualCapture;
    }
    
    public List<String> getSupportedBrands() {
        return supportedBrands;
    }
    
    public void setSupportedBrands(List<String> supportedBrands) {
        this.supportedBrands = supportedBrands;
    }
    
    public String getAcquirer() {
        return acquirer;
    }
    
    public void setAcquirer(String acquirer) {
        this.acquirer = acquirer;
    }
    
    public String getAcquirerCountry() {
        return acquirerCountry;
    }
    
    public void setAcquirerCountry(String acquirerCountry) {
        this.acquirerCountry = acquirerCountry;
    }
    
    public String getProcessingType() {
        return processingType;
    }
    
    public void setProcessingType(String processingType) {
        this.processingType = processingType;
    }
    
    public TransactionDescriptionWsDTO getTransactionDescription() {
        return transactionDescription;
    }
    
    public void setTransactionDescription(TransactionDescriptionWsDTO transactionDescription) {
        this.transactionDescription = transactionDescription;
    }
}