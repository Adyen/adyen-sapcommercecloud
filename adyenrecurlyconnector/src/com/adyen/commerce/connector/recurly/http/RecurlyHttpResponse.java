package com.adyen.commerce.connector.recurly.http;

public record RecurlyHttpResponse(int statusCode, String body) {
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
}
