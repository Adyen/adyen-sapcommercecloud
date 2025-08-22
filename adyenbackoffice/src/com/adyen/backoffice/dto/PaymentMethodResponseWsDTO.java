package com.adyen.backoffice.dto;

import java.util.List;

public class PaymentMethodResponseWsDTO {
    
    private LinksWsDTO _links;
    private List<PaymentMethodWsDTO> data;
    private Integer itemsTotal;
    private Integer pagesTotal;
    private List<String> typesWithErrors;
    
    public LinksWsDTO getLinks() {
        return _links;
    }
    
    public void setLinks(LinksWsDTO links) {
        this._links = links;
    }
    
    public List<PaymentMethodWsDTO> getData() {
        return data;
    }
    
    public void setData(List<PaymentMethodWsDTO> data) {
        this.data = data;
    }
    
    public Integer getItemsTotal() {
        return itemsTotal;
    }
    
    public void setItemsTotal(Integer itemsTotal) {
        this.itemsTotal = itemsTotal;
    }
    
    public Integer getPagesTotal() {
        return pagesTotal;
    }
    
    public void setPagesTotal(Integer pagesTotal) {
        this.pagesTotal = pagesTotal;
    }
    
    public List<String> getTypesWithErrors() {
        return typesWithErrors;
    }
    
    public void setTypesWithErrors(List<String> typesWithErrors) {
        this.typesWithErrors = typesWithErrors;
    }
}