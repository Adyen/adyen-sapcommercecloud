package com.adyen.commerce.request;

import com.adyen.model.checkout.Amount;

/**
 * Request DTO for gift card balance check operations
 */
public class GiftCardBalanceRequest {
    private String cardNumber;
    private String pin;
    private Amount amount;
    private String brand;
    private String type;
    
    // Getters and setters
    public String getCardNumber() { 
        return cardNumber; 
    }
    
    public void setCardNumber(String cardNumber) { 
        this.cardNumber = cardNumber; 
    }
    
    public String getPin() { 
        return pin; 
    }
    
    public void setPin(String pin) { 
        this.pin = pin; 
    }
    
    public Amount getAmount() { 
        return amount; 
    }
    
    public void setAmount(Amount amount) { 
        this.amount = amount; 
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}