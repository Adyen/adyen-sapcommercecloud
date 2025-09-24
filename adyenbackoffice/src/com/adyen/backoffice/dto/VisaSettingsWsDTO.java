package com.adyen.backoffice.dto;

public class VisaSettingsWsDTO {
    
    private TransactionDescriptionWsDTO transactionDescription;
    
    public TransactionDescriptionWsDTO getTransactionDescription() {
        return transactionDescription;
    }
    
    public void setTransactionDescription(TransactionDescriptionWsDTO transactionDescription) {
        this.transactionDescription = transactionDescription;
    }
}