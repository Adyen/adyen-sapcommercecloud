package com.adyen.backoffice.dto;

public class StoreDataWsDTO {
    private String id;
    private StoreAddressWsDTO address;
    private String description;
    private String merchantId;
    private String phoneNumber;
    private String reference;
    private String status;
    private LinksWsDTO _links;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public StoreAddressWsDTO getAddress() {
        return address;
    }

    public void setAddress(StoreAddressWsDTO address) {
        this.address = address;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LinksWsDTO getLinks() {
        return _links;
    }

    public void setLinks(LinksWsDTO links) {
        this._links = links;
    }
}