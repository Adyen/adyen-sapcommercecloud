package com.adyen.v6.controllers.checkout.dto;

import de.hybris.platform.commercefacades.product.data.PriceData;

public class CartDataDTO {
    private String code;
    private PriceData totalPriceWithTax;

    // Getters and Setters
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public PriceData getTotalPriceWithTax() {
        return totalPriceWithTax;
    }

    public void setTotalPriceWithTax(PriceData totalPriceWithTax) {
        this.totalPriceWithTax = totalPriceWithTax;
    }
}