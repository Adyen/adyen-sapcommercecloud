package com.adyen.backoffice.dto;

import java.util.List;

public class MerchantResponseWsDTO {
    
    private LinksWsDTO _links;
    private Integer itemsTotal;
    private Integer pagesTotal;
    private List<MerchantDataWsDTO> data;

    public LinksWsDTO getLinks() {
        return _links;
    }

    public void setLinks(LinksWsDTO links) {
        this._links = links;
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

    public List<MerchantDataWsDTO> getData() {
        return data;
    }

    public void setData(List<MerchantDataWsDTO> data) {
        this.data = data;
    }
}