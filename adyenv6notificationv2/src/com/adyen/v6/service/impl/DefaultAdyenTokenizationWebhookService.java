package com.adyen.v6.service.impl;

import com.adyen.commerce.data.TokenWebhookRequestData;
import com.adyen.v6.events.TokenizationEvent;
import com.adyen.v6.request.TokenizationWebhookRequest;
import com.adyen.v6.service.AdyenTokenizationWebhookService;
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
