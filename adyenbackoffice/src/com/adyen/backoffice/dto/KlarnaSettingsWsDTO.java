package com.adyen.backoffice.dto;

public class KlarnaSettingsWsDTO {
    
    private Boolean autoCapture;
    private String disputeEmail;
    private String region;
    private String supportEmail;
    
    public Boolean getAutoCapture() {
        return autoCapture;
    }
    
    public void setAutoCapture(Boolean autoCapture) {
        this.autoCapture = autoCapture;
    }
    
    public String getDisputeEmail() {
        return disputeEmail;
    }
    
    public void setDisputeEmail(String disputeEmail) {
        this.disputeEmail = disputeEmail;
    }
    
    public String getRegion() {
        return region;
    }
    
    public void setRegion(String region) {
        this.region = region;
    }
    
    public String getSupportEmail() {
        return supportEmail;
    }
    
    public void setSupportEmail(String supportEmail) {
        this.supportEmail = supportEmail;
    }
}