package com.adyen.backoffice.dto;

import java.util.List;

public class ApplePaySettingsWsDTO {
    
    private List<String> domains;
    
    public List<String> getDomains() {
        return domains;
    }
    
    public void setDomains(List<String> domains) {
        this.domains = domains;
    }
}