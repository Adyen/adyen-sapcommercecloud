package com.adyen.commerce.event;

import com.adyen.commerce.data.TokenWebhookRequestData;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;

public class TokenizationEvent extends AbstractEvent {
    private final TokenWebhookRequestData data;

    public TokenizationEvent(final TokenWebhookRequestData data) {
        this.data = data;
    }

    public TokenWebhookRequestData getData() {
        return data;
    }
}
