package com.adyen.backoffice.dto;

public class AmexSettingsWsDTO {
    
    private String midNumber;
    private Boolean reuseMidNumber;
    private String serviceLevel;
    
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
}