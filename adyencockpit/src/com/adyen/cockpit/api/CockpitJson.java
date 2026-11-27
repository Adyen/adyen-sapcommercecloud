package com.adyen.cockpit.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The one ObjectMapper of the cockpit API. It ignores unknown properties so that a newer SPA can send a field
 * an older backend does not know yet. An ObjectMapper is thread-safe once configured, so it is shared.
 */
public final class CockpitJson
{
	private static final ObjectMapper MAPPER = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private CockpitJson()
	{
	}

	public static ObjectMapper mapper()
	{
		return MAPPER;
	}
}
