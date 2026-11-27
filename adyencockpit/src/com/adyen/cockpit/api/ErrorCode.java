package com.adyen.cockpit.api;

/**
 * Every error code of the cockpit API with the HTTP status it answers (ADR 0002). Provider failures never
 * answer 401 or 403: the SPA reads 401 as an ended Backoffice session and 403 as the user's own missing
 * permission.
 */
public enum ErrorCode
{
	INVALID_REQUEST(400),
	REASON_REQUIRED(400),
	CONFIRMATION_REQUIRED(400),
	/** Reserved: the retyped {@code confirmation.storeName} does not match the store; not thrown yet. */
	STORE_NAME_MISMATCH(400),
	PERMISSION_DENIED(403),
	UNKNOWN_ENDPOINT(404),
	UNKNOWN_STORE(404),
	OPERATION_IN_PROGRESS(409),
	/**
	 * A write based on a stale version of a setting. {@code details.modifiedBy} is the uid of the Backoffice
	 * user who saved last, {@code details.modifiedAt} an ISO-8601 instant. Not thrown by any route yet.
	 */
	CONCURRENT_MODIFICATION(409),
	PAYLOAD_TOO_LARGE(413),
	UNTRUSTED_WRITE(415),
	REQUEST_REJECTED(422),
	INTERNAL_ERROR(500),
	PROVIDER_UNAUTHORISED(502),
	PROVIDER_FORBIDDEN(502),
	PROVIDER_NOT_FOUND(502),
	PROVIDER_VALIDATION(502),
	PROVIDER_REJECTED(502),
	PROVIDER_UNREACHABLE(502),
	PROVIDER_MISCONFIGURED(500),
	PROVIDER_RATE_LIMITED(503),
	PROVIDER_TIMEOUT(504);

	private final int status;

	ErrorCode(final int status)
	{
		this.status = status;
	}

	public int status()
	{
		return status;
	}
}
