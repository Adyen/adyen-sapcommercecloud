package com.adyen.commerce.response;

/**
 * Response DTO for partial payment order creation
 */
public class PartialPaymentOrderResponse {
    private String orderData;
    private String pspReference;
    private String resultCode;
    
    // Getters and setters
    public String getOrderData() { 
        return orderData; 
    }
    
    public void setOrderData(String orderData) { 
        this.orderData = orderData; 
    }
    
    public String getPspReference() { 
        return pspReference; 
    }
    
    public void setPspReference(String pspReference) { 
        this.pspReference = pspReference; 
    }
    
    public String getResultCode() { 
        return resultCode; 
    }
    
    public void setResultCode(String resultCode) { 
        this.resultCode = resultCode; 
    }
}