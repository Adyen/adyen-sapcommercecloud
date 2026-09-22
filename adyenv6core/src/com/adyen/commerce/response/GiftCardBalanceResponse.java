package com.adyen.commerce.response;

import com.adyen.model.checkout.Amount;
import java.math.BigDecimal;

/**
 * Response DTO for gift card balance check operations
 */
public class GiftCardBalanceResponse {
    private Amount balance;
    private Amount transactionLimit;
    private String partialPaymentId;
    private BigDecimal chargedAmount;
    private BigDecimal remainingAmount;
    
    // Getters and setters
    public Amount getBalance() { 
        return balance; 
    }
    
    public void setBalance(Amount balance) { 
        this.balance = balance; 
    }
    
    public Amount getTransactionLimit() { 
        return transactionLimit; 
    }
    
    public void setTransactionLimit(Amount transactionLimit) { 
        this.transactionLimit = transactionLimit; 
    }
    
    public String getPartialPaymentId() { 
        return partialPaymentId; 
    }
    
    public void setPartialPaymentId(String partialPaymentId) { 
        this.partialPaymentId = partialPaymentId; 
    }
    
    public BigDecimal getChargedAmount() { 
        return chargedAmount; 
    }
    
    public void setChargedAmount(BigDecimal chargedAmount) { 
        this.chargedAmount = chargedAmount; 
    }
    
    public BigDecimal getRemainingAmount() { 
        return remainingAmount; 
    }
    
    public void setRemainingAmount(BigDecimal remainingAmount) { 
        this.remainingAmount = remainingAmount; 
    }
}