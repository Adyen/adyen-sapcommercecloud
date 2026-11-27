package com.adyen.cockpit.api.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLHandshakeException;

import org.junit.Test;
import org.springframework.web.client.ResourceAccessException;

import com.adyen.cockpit.api.CockpitApiException;
import com.adyen.cockpit.api.ErrorCode;

import de.hybris.bootstrap.annotations.UnitTest;

@UnitTest
public class ProviderErrorClassifierTest
{
	private static final String SECRET_BODY = "{\"message\":\"key AQE-secret-123 is wrong\"}";
	private static final Provider[] ALL = Provider.values();

	private final ProviderErrorClassifier classifier = new ProviderErrorClassifier();

	private ProviderError classify(final Provider provider, final int status)
	{
		return classifier.classify(provider, status, Map.of(), null);
	}

	@Test
	public void statusMapsToItsCodeForEveryProvider()
	{
		for (final Provider provider : ALL)
		{
			assertEquals(ErrorCode.PROVIDER_UNAUTHORISED, classify(provider, 401).code());
			assertEquals(ErrorCode.PROVIDER_FORBIDDEN, classify(provider, 403).code());
			assertEquals(ErrorCode.PROVIDER_NOT_FOUND, classify(provider, 404).code());
			assertEquals(ErrorCode.PROVIDER_VALIDATION, classify(provider, 422).code());
			assertEquals(ErrorCode.PROVIDER_RATE_LIMITED, classify(provider, 429).code());
			assertEquals(ErrorCode.PROVIDER_REJECTED, classify(provider, 500).code());
			assertEquals(ErrorCode.PROVIDER_REJECTED, classify(provider, 503).code());
			assertEquals(ErrorCode.PROVIDER_REJECTED, classify(provider, 409).code());
			assertEquals(ErrorCode.PROVIDER_REJECTED, classify(provider, 400).code());
			assertEquals(provider, classify(provider, 404).provider());
			assertEquals(Integer.valueOf(404), classify(provider, 404).status());
		}
	}

	@Test
	public void rateLimitCarriesRetryAfterCaseInsensitively()
	{
		for (final Provider provider : ALL)
		{
			final ProviderError lower = classifier.classify(provider, 429, Map.of("retry-after", List.of("30")), null);
			final ProviderError mixed = classifier.classify(provider, 429, Map.of("Retry-After", List.of(" 12 ")), null);
			final ProviderError none = classifier.classify(provider, 429, Map.of("X-Other", List.of("1")), null);
			assertEquals("30", lower.retryAfter());
			assertEquals("12", mixed.retryAfter());
			assertNull(none.retryAfter());
			assertNull(classifier.classify(provider, 429, null, null).retryAfter());
		}
	}

	@Test
	public void retryAfterOnlyBelongsToTooManyRequests()
	{
		assertNull(classifier.classify(Provider.ADYEN, 500, Map.of("Retry-After", List.of("5")), null).retryAfter());
	}

	@Test
	public void chargebeeParameterErrorsAnswered400AreValidation()
	{
		for (final String body : List.of("{\"api_error_code\":\"invalid_request\",\"message\":\"x\"}",
				"{\"api_error_code\":\"param_wrong_value\"}", "{\"type\":\"invalid_request\",\"message\":\"x\"}"))
		{
			assertEquals(body, ErrorCode.PROVIDER_VALIDATION,
					classifier.classify(Provider.CHARGEBEE, 400, Map.of(), body).code());
		}
	}

	@Test
	public void chargebeeOtherBadRequestIsRejected()
	{
		assertEquals(ErrorCode.PROVIDER_REJECTED,
				classifier.classify(Provider.CHARGEBEE, 400, Map.of(), "{\"api_error_code\":\"payment_processing_failed\"}").code());
	}

	@Test
	public void chargebeeRateLimitBodyIsRateLimited()
	{
		final ProviderError error = classifier.classify(Provider.CHARGEBEE, 429, Map.of("Retry-After", List.of("7")),
				"{\"api_error_code\":\"api_request_limit_exceeded\"}");
		assertEquals(ErrorCode.PROVIDER_RATE_LIMITED, error.code());
		assertEquals("7", error.retryAfter());
	}

	@Test
	public void recurlyValidationBodyIsValidationEvenOn400()
	{
		assertEquals(ErrorCode.PROVIDER_VALIDATION, classifier.classify(Provider.RECURLY, 400, Map.of(),
				"{\"error\":{\"type\":\"validation\",\"message\":\"x\"}}").code());
		assertEquals(ErrorCode.PROVIDER_VALIDATION,
				classifier.classify(Provider.RECURLY, 400, Map.of(), "{\"type\":\"validation\"}").code());
	}

	@Test
	public void recurlyInvalidApiVersionAndContentTypeAreRejectedNotValidation()
	{
		for (final String type : List.of("invalid_api_version", "invalid_content_type", "invalid_parameter"))
		{
			assertEquals(type, ErrorCode.PROVIDER_REJECTED, classifier.classify(Provider.RECURLY, 400, Map.of(),
					"{\"error\":{\"type\":\"" + type + "\"}}").code());
		}
	}

	@Test
	public void recurlyStatusWinsOverBodyType()
	{
		assertEquals(ErrorCode.PROVIDER_NOT_FOUND, classifier.classify(Provider.RECURLY, 404, Map.of(),
				"{\"error\":{\"type\":\"validation\"}}").code());
		assertEquals(ErrorCode.PROVIDER_REJECTED, classifier.classify(Provider.RECURLY, 400, Map.of(),
				"{\"error\":{\"type\":\"transaction\"}}").code());
	}

	@Test
	public void adyenBadRequestBodyDoesNotMakeItValidation()
	{
		assertEquals(ErrorCode.PROVIDER_REJECTED, classifier.classify(Provider.ADYEN, 400, Map.of(),
				"{\"type\":\"validation\",\"api_error_code\":\"invalid_request\"}").code());
	}

	@Test
	public void unparsableBodyFallsBackToStatusAndNeverThrows()
	{
		for (final Provider provider : ALL)
		{
			for (final String body : new String[] { null, "", "  ", "<html>", "{\"type\":", "[1,2]", "\"text\"",
					"{\"type\":{\"a\":1},\"api_error_code\":5}" })
			{
				assertEquals(ErrorCode.PROVIDER_REJECTED, classifier.classify(provider, 400, Map.of(), body).code());
				assertEquals(ErrorCode.PROVIDER_NOT_FOUND, classifier.classify(provider, 404, Map.of(), body).code());
			}
		}
	}

	@Test
	public void timeoutInTheCauseChainIsTimeout()
	{
		for (final Provider provider : ALL)
		{
			assertEquals(ErrorCode.PROVIDER_TIMEOUT,
					classifier.classifyTransport(provider, new SocketTimeoutException("read")).code());
			assertEquals(ErrorCode.PROVIDER_TIMEOUT, classifier.classifyTransport(provider,
					new ResourceAccessException("I/O error", new SocketTimeoutException("connect"))).code());
			assertEquals(ErrorCode.PROVIDER_TIMEOUT,
					classifier.classifyTransport(provider, new HttpTimeoutException("request timed out")).code());
			assertEquals(ErrorCode.PROVIDER_TIMEOUT, classifier.classifyTransport(provider,
					new RuntimeException(new IOException(new SocketTimeoutException()))).code());
		}
	}

	@Test
	public void noConnectionIsUnreachable()
	{
		for (final Provider provider : ALL)
		{
			for (final Throwable failure : List.of(new UnknownHostException("h"), new ConnectException("refused"),
					new SSLHandshakeException("tls"), new IOException("reset"),
					new ResourceAccessException("I/O error", new UnknownHostException("h"))))
			{
				final ProviderError error = classifier.classifyTransport(provider, failure);
				assertEquals(failure.toString(), ErrorCode.PROVIDER_UNREACHABLE, error.code());
				assertNull(error.status());
				assertEquals(provider, error.provider());
			}
		}
	}

	@Test
	public void badEndpointIsMisconfigured()
	{
		for (final Provider provider : ALL)
		{
			assertEquals(ErrorCode.PROVIDER_MISCONFIGURED,
					classifier.classifyTransport(provider, new IllegalArgumentException("URI is not absolute")).code());
			assertEquals(ErrorCode.PROVIDER_MISCONFIGURED,
					classifier.classifyTransport(provider, new RuntimeException(new URISyntaxException("x", "bad"))).code());
		}
	}

	@Test
	public void toExceptionUsesTheAdrStatusAndDetails()
	{
		final CockpitApiException rate = classifier
				.classify(Provider.RECURLY, 429, Map.of("Retry-After", List.of("30")), SECRET_BODY).toException("Slow down.");
		assertEquals(503, rate.code().status());
		assertEquals(Map.of("provider", "recurly", "status", 429, "retryAfter", "30"), rate.details());

		final CockpitApiException timeout = classifier
				.classifyTransport(Provider.CHARGEBEE, new SocketTimeoutException()).toException("No answer.");
		assertEquals(504, timeout.code().status());
		assertEquals(Map.of("provider", "chargebee"), timeout.details());

		assertEquals(502, classify(Provider.ADYEN, 404).toException("x").code().status());
		assertEquals(500, classifier.classifyTransport(Provider.ADYEN, new IllegalArgumentException())
				.toException("x").code().status());
	}

	@Test
	public void neitherErrorNorExceptionCarriesTheProviderBody()
	{
		final ProviderError error = classifier.classify(Provider.ADYEN, 500, Map.of(), SECRET_BODY);
		final CockpitApiException exception = error.toException(null);
		assertFalse(error.toString().contains("AQE-secret"));
		assertFalse(String.valueOf(exception.getMessage()).contains("AQE-secret"));
		assertFalse(exception.details().toString().contains("AQE-secret"));
	}
}
