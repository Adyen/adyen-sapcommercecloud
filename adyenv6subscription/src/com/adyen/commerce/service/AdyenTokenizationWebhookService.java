package com.adyen.commerce.service;


import com.adyen.commerce.request.TokenizationWebhookRequest;

public interface AdyenTokenizationWebhookService {

    void onRequest(TokenizationWebhookRequest tokenizationWebhookRequest);

}
