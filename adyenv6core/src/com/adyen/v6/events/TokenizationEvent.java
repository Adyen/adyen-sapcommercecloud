package com.adyen.v6.events;

import com.adyen.commerce.data.TokenWebhookRequestData;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;

public class TokenizationEvent extends AbstractEvent {
    private final TokenWebhookRequestData data;

    public TokenizationEvent(final TokenWebhookRequestData data) {
        this.data = data;

//        this.data = new TokenWebhookRequestData();
//
//        this.data.setEventId(data.getEventId());
//        this.data.setEventType(data.getEventType());
//        this.data.setOperation(data.getOperation());
//        this.data.setEnvironment(data.getEnvironment());
//        this.data.setShopperReference(data.getShopperReference());
//        this.data.setStoredPaymentMethodId(data.getStoredPaymentMethodId());
//        this.data.setMerchantAccount(data.getMerchantAccount());
//        this.data.setCreatedAt(data.getCreatedAt());
//        this.data.setPaymentType(data.getPaymentType());
    }

    public TokenWebhookRequestData getData() {
        return data;
    }
}
