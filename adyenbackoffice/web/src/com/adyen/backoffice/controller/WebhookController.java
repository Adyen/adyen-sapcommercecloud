package com.adyen.backoffice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.adyen.backoffice.dto.WebhookCreateRequestWsDTO;
import com.adyen.backoffice.dto.WebhookDataWsDTO;
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

    @PostMapping("/merchants/{merchantId}")
    public ResponseEntity<WebhookDataWsDTO> createWebhook(
            @PathVariable String merchantId,
            @RequestBody WebhookCreateRequestWsDTO webhookRequest) {
        
        WebhookDataWsDTO response = adyenManagementService.createWebhook(merchantId, webhookRequest);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}