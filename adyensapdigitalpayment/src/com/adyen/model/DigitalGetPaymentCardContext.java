/*
 * Copyright (c) 2022 SAP SE or an SAP affiliate company. All rights reserved.
 */
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
