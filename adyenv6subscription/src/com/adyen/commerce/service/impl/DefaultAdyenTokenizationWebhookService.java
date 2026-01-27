package com.adyen.commerce.service.impl;

import com.adyen.commerce.data.TokenWebhookRequestData;
import com.adyen.commerce.event.TokenizationEvent;
import com.adyen.commerce.request.TokenizationWebhookRequest;
import com.adyen.commerce.service.AdyenTokenizationWebhookService;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.event.EventService;
import org.apache.log4j.Logger;

public class DefaultAdyenTokenizationWebhookService implements AdyenTokenizationWebhookService {
    private static final Logger LOG = Logger.getLogger(DefaultAdyenTokenizationWebhookService.class);

    private EventService eventService;
    private Converter<TokenizationWebhookRequest, TokenWebhookRequestData> tokenWebhookRequestConverter;

    public void onRequest(TokenizationWebhookRequest tokenizationWebhookRequest) {
        LOG.debug("Processing tokenization webhook request: " + tokenizationWebhookRequest);

        TokenWebhookRequestData requestData = tokenWebhookRequestConverter.convert(tokenizationWebhookRequest);

        TokenizationEvent tokenizationEvent = new TokenizationEvent(requestData);

        eventService.publishEvent(tokenizationEvent);
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setTokenWebhookRequestConverter(Converter<TokenizationWebhookRequest, TokenWebhookRequestData> tokenWebhookRequestConverter) {
        this.tokenWebhookRequestConverter = tokenWebhookRequestConverter;
    }
}
