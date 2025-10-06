package com.adyen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DigitalGetPaymentCardContext {


    @JsonProperty("shopperReference")
    private String shopperReference;


    public String getShopperReference() {
        return shopperReference;
    }

    public void setShopperReference(String shopperReference) {
        this.shopperReference = shopperReference;
    }

}
