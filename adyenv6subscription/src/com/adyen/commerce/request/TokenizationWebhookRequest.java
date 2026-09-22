package com.adyen.commerce.request;

public class TokenizationWebhookRequest {
    private String createdAt;
    private String eventId;
    private String environment;
    private String type;
    private TokenizationWebhookData data;

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public TokenizationWebhookData getData() {
        return data;
    }

    public void setData(TokenizationWebhookData data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "TokenizationWebhookRequest{" +
                "createdAt='" + createdAt + '\'' +
                ", eventId='" + eventId + '\'' +
                ", environment='" + environment + '\'' +
                ", type='" + type + '\'' +
                ", data=" + data +
                '}';
    }
}

