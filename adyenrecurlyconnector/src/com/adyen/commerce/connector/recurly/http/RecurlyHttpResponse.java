package com.adyen.commerce.connector.recurly.http;

import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_OK;

public record RecurlyHttpResponse(int statusCode, String body) {
    public boolean isSuccess() {
        return statusCode >= HTTP_OK && statusCode < HTTP_MULT_CHOICE;
    }
}
