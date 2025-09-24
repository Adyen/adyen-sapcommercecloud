package com.adyen.backoffice.dto;

public class SepaDirectDebitSettingsWsDTO {
    
    private String creditorId;
    private TransactionDescriptionWsDTO transactionDescription;
    
    public String getCreditorId() {
        return creditorId;
    }
    
    public void setCreditorId(String creditorId) {
        this.creditorId = creditorId;
    }
    
    public TransactionDescriptionWsDTO getTransactionDescription() {
        return transactionDescription;
    }
    
    public void setTransactionDescription(TransactionDescriptionWsDTO transactionDescription) {
        this.transactionDescription = transactionDescription;
    }
}