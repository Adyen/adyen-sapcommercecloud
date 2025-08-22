package com.adyen.backoffice.dto;

import java.util.List;
import java.util.Map;

public class PaymentMethodWsDTO {
    
    private String id;
    private String type;
    private String name;
    private String description;
    private Boolean enabled;
    private List<String> currencies;
    private List<String> countries;
    private Map<String, Object> configuration;
    private String storeId;
    private String businessLineId;
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public List<String> getCurrencies() {
        return currencies;
    }
    
    public void setCurrencies(List<String> currencies) {
        this.currencies = currencies;
    }
    
    public List<String> getCountries() {
        return countries;
    }
    
    public void setCountries(List<String> countries) {
        this.countries = countries;
    }
    
    public Map<String, Object> getConfiguration() {
        return configuration;
    }
    
    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }
    
    public String getStoreId() {
        return storeId;
    }
    
    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }
    
    public String getBusinessLineId() {
        return businessLineId;
    }
    
    public void setBusinessLineId(String businessLineId) {
        this.businessLineId = businessLineId;
    }
}