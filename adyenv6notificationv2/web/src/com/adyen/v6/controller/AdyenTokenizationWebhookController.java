package com.adyen.v6.controller;

import com.adyen.v6.security.AdyenNotificationAuthenticationProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Controller
@RequestMapping(value = "/adyen/v6/tokenization/{baseSiteId}")
public class AdyenTokenizationWebhookController {

    @Resource(name = "adyenNotificationAuthenticationProvider")
    private AdyenNotificationAuthenticationProvider adyenNotificationAuthenticationProvider;

    @PostMapping
    @ResponseBody
    public ResponseEntity<Void> onReceive(@PathVariable final String baseSiteId, @RequestBody final String tokenizationWebhookRequest, final HttpServletRequest request) {

        if (!adyenNotificationAuthenticationProvider.authenticate(request, tokenizationWebhookRequest, baseSiteId)) {
            throw new AccessDeniedException("Request authentication failed");
        }

        return ResponseEntity.ok().build();
    }
}
