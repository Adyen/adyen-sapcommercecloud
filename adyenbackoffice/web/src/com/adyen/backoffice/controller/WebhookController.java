package com.adyen.backoffice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.adyen.backoffice.dto.WebhookResponseWsDTO;
import com.adyen.backoffice.service.AdyenManagementService;

@Controller
@RequestMapping("/api/webhooks")
public class WebhookController {

    @Autowired
    private AdyenManagementService adyenManagementService;

    @GetMapping("/merchants/{merchantId}")
    public ResponseEntity<WebhookResponseWsDTO> getWebhooksByMerchantId(
            @PathVariable String merchantId,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer pageNumber) {
        
        WebhookResponseWsDTO response = adyenManagementService.getWebhooksByMerchantId(merchantId, pageSize, pageNumber);
        
        return ResponseEntity.ok(response);
    }
}