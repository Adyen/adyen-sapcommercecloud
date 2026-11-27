package com.adyen.cockpit.api;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The body of every error response. {@code field} and {@code details} are left out when empty.
 * {@code message} is for a merchant: it never carries an exception text, a submitted value or a provider's
 * error body.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String field,
		@JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> details, String correlationId)
{
}
