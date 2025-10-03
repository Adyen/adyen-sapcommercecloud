package com.adyen.backoffice.dto;

public class JcbSettingsWsDTO {
    
    private String midNumber;
    private Boolean reuseMidNumber;
    private String serviceLevel;
    private TransactionDescriptionWsDTO transactionDescription;
    
    public String getMidNumber() {
        return midNumber;
    }
    
    public void setMidNumber(String midNumber) {
        this.midNumber = midNumber;
    }
    
    public Boolean getReuseMidNumber() {
        return reuseMidNumber;
    }
    
    public void setReuseMidNumber(Boolean reuseMidNumber) {
        this.reuseMidNumber = reuseMidNumber;
    }
    
    public String getServiceLevel() {
        return serviceLevel;
    }
    
    public void setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
    }
    
    public TransactionDescriptionWsDTO getTransactionDescription() {
        return transactionDescription;
    }
    
    public void setTransactionDescription(TransactionDescriptionWsDTO transactionDescription) {
        this.transactionDescription = transactionDescription;
    }
}