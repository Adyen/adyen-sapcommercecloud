package com.adyen.backoffice.dto;

import java.util.List;

public class MerchantDataWsDTO {
    private String id;
    private String name;
    private String captureDelay;
    private String defaultShopperInteraction;
    private String status;
    private String shopWebAddress;
    private String merchantCity;
    private String primarySettlementCurrency;
    private LinksWsDTO _links;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCaptureDelay() {
        return captureDelay;
    }

    public void setCaptureDelay(String captureDelay) {
        this.captureDelay = captureDelay;
    }

    public String getDefaultShopperInteraction() {
        return defaultShopperInteraction;
    }

    public void setDefaultShopperInteraction(String defaultShopperInteraction) {
        this.defaultShopperInteraction = defaultShopperInteraction;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getShopWebAddress() {
        return shopWebAddress;
    }

    public void setShopWebAddress(String shopWebAddress) {
        this.shopWebAddress = shopWebAddress;
    }

    public String getMerchantCity() {
        return merchantCity;
    }

    public void setMerchantCity(String merchantCity) {
        this.merchantCity = merchantCity;
    }

    public String getPrimarySettlementCurrency() {
        return primarySettlementCurrency;
    }

    public void setPrimarySettlementCurrency(String primarySettlementCurrency) {
        this.primarySettlementCurrency = primarySettlementCurrency;
    }

    public LinksWsDTO getLinks() {
        return _links;
    }

    public void setLinks(LinksWsDTO links) {
        this._links = links;
    }
}
