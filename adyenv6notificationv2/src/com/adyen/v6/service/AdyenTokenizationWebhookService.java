package com.adyen.v6.service;

import com.adyen.v6.request.TokenizationWebhookRequest;

public interface AdyenTokenizationWebhookService {

    void onRequest(TokenizationWebhookRequest tokenizationWebhookRequest);

}
