package com.adyen.backoffice.dto;

public class TransactionDescriptionWsDTO {
    
    private String doingBusinessAsName;
    private String type;
    
    public String getDoingBusinessAsName() {
        return doingBusinessAsName;
    }
    
    public void setDoingBusinessAsName(String doingBusinessAsName) {
        this.doingBusinessAsName = doingBusinessAsName;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
}