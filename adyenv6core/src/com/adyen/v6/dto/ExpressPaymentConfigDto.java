package com.adyen.v6.dto;

public class ExpressPaymentConfigDto {
    private boolean googlePayExpressEnabledOnCart;
    private boolean applePayExpressEnabledOnCart;
    private boolean paypalExpressEnabledOnCart;
    private boolean amazonPayExpressEnabledOnCart;
    private boolean googlePayExpressEnabledOnProduct;
    private boolean applePayExpressEnabledOnProduct;
    private boolean paypalExpressEnabledOnProduct;
    private boolean amazonPayExpressEnabledOnProduct;


    public boolean isGooglePayExpressEnabledOnCart() {
        return googlePayExpressEnabledOnCart;
    }

    public void setGooglePayExpressEnabledOnCart(boolean googlePayExpressEnabledOnCart) {
        this.googlePayExpressEnabledOnCart = googlePayExpressEnabledOnCart;
    }

    public boolean isApplePayExpressEnabledOnCart() {
        return applePayExpressEnabledOnCart;
    }

    public void setApplePayExpressEnabledOnCart(boolean applePayExpressEnabledOnCart) {
        this.applePayExpressEnabledOnCart = applePayExpressEnabledOnCart;
    }

    public boolean isPaypalExpressEnabledOnCart() {
        return paypalExpressEnabledOnCart;
    }

    public void setPaypalExpressEnabledOnCart(boolean paypalExpressEnabledOnCart) {
        this.paypalExpressEnabledOnCart = paypalExpressEnabledOnCart;
    }

    public boolean isAmazonPayExpressEnabledOnCart() {
        return amazonPayExpressEnabledOnCart;
    }

    public void setAmazonPayExpressEnabledOnCart(boolean amazonPayExpressEnabledOnCart) {
        this.amazonPayExpressEnabledOnCart = amazonPayExpressEnabledOnCart;
    }

    public boolean isGooglePayExpressEnabledOnProduct() {
        return googlePayExpressEnabledOnProduct;
    }

    public void setGooglePayExpressEnabledOnProduct(boolean googlePayExpressEnabledOnProduct) {
        this.googlePayExpressEnabledOnProduct = googlePayExpressEnabledOnProduct;
    }

    public boolean isApplePayExpressEnabledOnProduct() {
        return applePayExpressEnabledOnProduct;
    }

    public void setApplePayExpressEnabledOnProduct(boolean applePayExpressEnabledOnProduct) {
        this.applePayExpressEnabledOnProduct = applePayExpressEnabledOnProduct;
    }

    public boolean isPaypalExpressEnabledOnProduct() {
        return paypalExpressEnabledOnProduct;
    }

    public void setPaypalExpressEnabledOnProduct(boolean paypalExpressEnabledOnProduct) {
        this.paypalExpressEnabledOnProduct = paypalExpressEnabledOnProduct;
    }

    public boolean isAmazonPayExpressEnabledOnProduct() {
        return amazonPayExpressEnabledOnProduct;
    }

    public void setAmazonPayExpressEnabledOnProduct(boolean amazonPayExpressEnabledOnProduct) {
        this.amazonPayExpressEnabledOnProduct = amazonPayExpressEnabledOnProduct;
    }
}