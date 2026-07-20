package com.adyen.commerce.connector.recurly.http;

public class RecurlyHttpResponse
{
    private final int statusCode;
    private final String body;

    public RecurlyHttpResponse(
            final int statusCode,
            final String body)
    {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public String getBody()
    {
        return body;
    }

    public boolean isSuccessful()
    {
        return statusCode >= 200 && statusCode < 300;
    }
}