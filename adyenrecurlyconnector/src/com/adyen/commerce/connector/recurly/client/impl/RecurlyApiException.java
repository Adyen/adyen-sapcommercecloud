package com.adyen.commerce.connector.recurly.client.impl;

public class RecurlyApiException extends RuntimeException
{
    private final int statusCode;
    private final String responseBody;

    public RecurlyApiException(
            final int statusCode,
            final String responseBody)
    {
        super(buildMessage(statusCode, responseBody));
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public RecurlyApiException(
            final int statusCode,
            final String responseBody,
            final Throwable cause)
    {
        super(buildMessage(statusCode, responseBody), cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public String getResponseBody()
    {
        return responseBody;
    }

    private static String buildMessage(
            final int statusCode,
            final String responseBody)
    {
        return "Recurly API request failed with HTTP status "
                + statusCode
                + formatResponseBody(responseBody);
    }

    private static String formatResponseBody(final String responseBody)
    {
        if (responseBody == null || responseBody.isBlank())
        {
            return "";
        }

        return ". Response body: " + responseBody;
    }
}