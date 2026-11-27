package com.adyen.cockpit.api.page;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One page of a listing (ADR 0002), the same shape as {@code Page<T>} in the SPA. {@code next} is present
 * exactly when there is a further page; a client passes it back unchanged. {@code totalItems} is present
 * only for providers that report it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Page<T>(List<T> items, int pageNumber, int pageSize, Long totalItems, String next)
{
	public Page
	{
		items = items == null ? List.of() : List.copyOf(items);
	}
}
