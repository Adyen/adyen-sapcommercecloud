package com.adyen.cockpit.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.adyen.cockpit.api.ErrorCode;
import com.adyen.cockpit.api.provider.Provider;
import com.adyen.cockpit.setup.ManagementApiClient.Failure;
import com.adyen.cockpit.setup.ManagementApiClient.Response;

import de.hybris.bootstrap.annotations.UnitTest;

@UnitTest
public class ManagementApiClientTest
{
	private static final String URL = "https://management-test.adyen.com/v3/me";
	private static final String SECRET = "{\"message\":\"AQE-secret\"}";

	private RestTemplate template;
	private MockRestServiceServer server;
	private ManagementApiClient client;

	@Before
	public void setUp()
	{
		template = new RestTemplate();
		server = MockRestServiceServer.bindTo(template).build();
		client = new ManagementApiClient(template);
	}

	private Response call()
	{
		return client.exchange(URL, HttpMethod.GET, null, "key");
	}

	private Response answer(final HttpStatus status)
	{
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
				.andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(SECRET));
		return call();
	}

	@Test
	public void successHasNoError()
	{
		server.expect(requestTo(URL)).andRespond(withSuccess("{\"id\":\"x\"}", MediaType.APPLICATION_JSON));
		final Response response = call();
		assertTrue(response.ok());
		assertNull(response.error());
		assertEquals("x", response.body().get("id"));
	}

	@Test
	public void unauthorisedStaysUnrecognised()
	{
		final Response response = answer(HttpStatus.UNAUTHORIZED);
		assertEquals(Failure.UNRECOGNISED, response.failure());
		assertEquals(ErrorCode.PROVIDER_UNAUTHORISED, response.error().code());
		assertEquals(Provider.ADYEN, response.error().provider());
	}

	@Test
	public void forbiddenStaysForbidden()
	{
		final Response response = answer(HttpStatus.FORBIDDEN);
		assertEquals(Failure.FORBIDDEN, response.failure());
		assertEquals(ErrorCode.PROVIDER_FORBIDDEN, response.error().code());
	}

	@Test
	public void notFoundIsRejectedWithItsOwnCode()
	{
		final Response response = answer(HttpStatus.NOT_FOUND);
		assertEquals(Failure.REJECTED, response.failure());
		assertEquals(404, response.status());
		assertEquals(ErrorCode.PROVIDER_NOT_FOUND, response.error().code());
		assertEquals(Integer.valueOf(404), response.error().status());
	}

	@Test
	public void unprocessableIsValidation()
	{
		final Response response = answer(HttpStatus.UNPROCESSABLE_ENTITY);
		assertEquals(Failure.REJECTED, response.failure());
		assertEquals(ErrorCode.PROVIDER_VALIDATION, response.error().code());
		assertFalse(response.error().toString().contains("AQE-secret"));
	}

	@Test
	public void serverErrorIsRejected()
	{
		final Response response = answer(HttpStatus.INTERNAL_SERVER_ERROR);
		assertEquals(Failure.REJECTED, response.failure());
		assertEquals(ErrorCode.PROVIDER_REJECTED, response.error().code());
	}

	@Test
	public void tooManyRequestsCarriesRetryAfter()
	{
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("retry-after", "42"));
		final Response response = call();
		assertEquals(Failure.REJECTED, response.failure());
		assertEquals(ErrorCode.PROVIDER_RATE_LIMITED, response.error().code());
		assertEquals("42", response.error().retryAfter());
	}

	@Test
	public void timeoutIsUnreachableForTheSetupPageButTimeoutForTheApi()
	{
		server.expect(requestTo(URL)).andRespond(request ->
		{
			throw new SocketTimeoutException("Read timed out");
		});
		final Response response = call();
		assertEquals(Failure.UNREACHABLE, response.failure());
		assertEquals(ErrorCode.PROVIDER_TIMEOUT, response.error().code());
		assertNull(response.error().status());
	}

	@Test
	public void transportFailureCausedByABadUrlIsMisconfiguredInBothVocabularies()
	{
		server.expect(requestTo(URL)).andRespond(request ->
		{
			throw new IOException("bad", new IllegalArgumentException("not a URL"));
		});
		final Response response = call();
		assertEquals(Failure.MISCONFIGURED, response.failure());
		assertEquals(ErrorCode.PROVIDER_MISCONFIGURED, response.error().code());
	}

	@Test
	public void unknownHostIsUnreachable()
	{
		server.expect(requestTo(URL)).andRespond(request ->
		{
			throw new UnknownHostException("management-test.adyen.com");
		});
		final Response response = call();
		assertEquals(Failure.UNREACHABLE, response.failure());
		assertEquals(ErrorCode.PROVIDER_UNREACHABLE, response.error().code());
	}

	@Test
	public void unusableEndpointIsMisconfigured()
	{
		final Response response = new ManagementApiClient(new RestTemplate()).exchange("not-a-url", HttpMethod.GET, null, "key");
		assertEquals(Failure.MISCONFIGURED, response.failure());
		assertEquals(ErrorCode.PROVIDER_MISCONFIGURED, response.error().code());
	}

	@Test
	public void failedFactoryDerivesAnError()
	{
		assertEquals(ErrorCode.PROVIDER_UNAUTHORISED, Response.failed(Failure.UNRECOGNISED, 401).error().code());
		assertEquals(ErrorCode.PROVIDER_RATE_LIMITED, Response.failed(Failure.REJECTED, 429).error().code());
		assertEquals(ErrorCode.PROVIDER_REJECTED, Response.failed(Failure.REJECTED, 0).error().code());
		assertEquals(ErrorCode.PROVIDER_UNREACHABLE, Response.failed(Failure.UNREACHABLE, 0).error().code());
		assertEquals(ErrorCode.PROVIDER_MISCONFIGURED, Response.failed(Failure.MISCONFIGURED, 0).error().code());
		assertNull(Response.of(null).error());
	}
}
