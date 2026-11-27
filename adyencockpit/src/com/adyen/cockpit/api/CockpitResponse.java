package com.adyen.cockpit.api;

/**
 * What a route answers: a status and the object Jackson serialises as the body. A refusal is not built here
 * but thrown as a {@link CockpitApiException}, so every error leaves through one envelope. A 422 whose body
 * is the expected result (a failed credential check, a partial provisioning report) is a response, not an error.
 */
public record CockpitResponse(int status, Object body)
{
	public static CockpitResponse ok(final Object body)
	{
		return new CockpitResponse(200, body);
	}

	public static CockpitResponse of(final int status, final Object body)
	{
		return new CockpitResponse(status, body);
	}
}
