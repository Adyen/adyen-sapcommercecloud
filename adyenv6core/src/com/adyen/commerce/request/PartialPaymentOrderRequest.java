package com.adyen.commerce.request;

import com.adyen.model.checkout.Amount;
import java.util.Map;

/**
 * Request DTO for partial payment order creation
 */
public class PartialPaymentOrderRequest {
    private Amount amount;
    private Map<String, Object> paymentMethod;
    private String shopperReference;
    private String partialPaymentId; // ID from balance check response
    
    // Getters and setters
    public Amount getAmount() { 
        return amount; 
    }
    
    public void setAmount(Amount amount) { 
        this.amount = amount; 
    }
    
    public Map<String, Object> getPaymentMethod() { 
        return paymentMethod; 
    }
    
    public void setPaymentMethod(Map<String, Object> paymentMethod) { 
        this.paymentMethod = paymentMethod; 
    }
    
    public String getShopperReference() { 
        return shopperReference; 
    }
    
    public void setShopperReference(String shopperReference) { 
        this.shopperReference = shopperReference; 
    }
    
    public String getPartialPaymentId() { 
        return partialPaymentId; 
    }
    
    public void setPartialPaymentId(String partialPaymentId) { 
        this.partialPaymentId = partialPaymentId; 
    }
}