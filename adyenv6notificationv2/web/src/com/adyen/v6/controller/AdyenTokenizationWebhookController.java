package com.adyen.v6.controller;

import com.adyen.v6.request.TokenizationWebhookRequest;
import com.adyen.v6.security.AdyenNotificationAuthenticationProvider;
import com.adyen.v6.service.AdyenTokenizationWebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Controller
@RequestMapping(value = "/adyen/v6/tokenization/{baseSiteId}")
public class AdyenTokenizationWebhookController {

    private static ObjectMapper objectMapper;

    {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Resource(name = "adyenNotificationAuthenticationProvider")
    private AdyenNotificationAuthenticationProvider adyenNotificationAuthenticationProvider;

    @Autowired
    private AdyenTokenizationWebhookService adyenTokenizationWebhookService;

    @PostMapping
    @ResponseBody
    public ResponseEntity<Void> onReceive(@PathVariable final String baseSiteId, @RequestBody final String tokenizationWebhookRequest, final HttpServletRequest request) throws JsonProcessingException {

        if (!adyenNotificationAuthenticationProvider.authenticate(request, tokenizationWebhookRequest, baseSiteId)) {
            throw new AccessDeniedException("Request authentication failed");
        }

        TokenizationWebhookRequest tokenizationRequest = objectMapper.readValue(tokenizationWebhookRequest, TokenizationWebhookRequest.class);

        adyenTokenizationWebhookService.onRequest(tokenizationRequest);

        return ResponseEntity.ok().build();
    }
}
