package com.adyen.v6.request;

public class GooglePayExpressPDPRequest extends GooglePayExpressCartRequest {
    private String productCode;
    private String cartId;

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getCartId() {
        return cartId;
    }
    public void setCartId(String cartId) {
        this.cartId = cartId;
    }
}
