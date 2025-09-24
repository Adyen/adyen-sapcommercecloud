package com.adyen.backoffice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.adyen.backoffice.dto.PaymentMethodSettingsWsDTO;
import com.adyen.backoffice.service.AdyenManagementService;

@Controller
@RequestMapping("/api/merchants/{merchantId}/payment-method-settings")
public class PaymentMethodSettingsController {

    @Autowired
    private AdyenManagementService adyenManagementService;

    /**
     * Retrieves the full settings for a specific payment method.
     * 
     * @param merchantId The ID of the merchant
     * @param paymentMethodId The unique identifier of the payment method
     * @return ResponseEntity containing the payment method settings
     */
    @GetMapping("/{paymentMethodId}")
    public ResponseEntity<PaymentMethodSettingsWsDTO> getPaymentMethodSettings(
            @PathVariable String merchantId,
            @PathVariable String paymentMethodId) {
        
        PaymentMethodSettingsWsDTO settings = adyenManagementService.getPaymentMethodSettings(merchantId, paymentMethodId);
        
        return ResponseEntity.ok(settings);
    }
}