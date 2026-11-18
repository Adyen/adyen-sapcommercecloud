package com.adyen.cockpit.setup;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * The Adyen Management API as the cockpit uses it: one store's key, against the environment that store
 * is in.
 *
 * <p>Refusals come back as a {@link Failure} rather than an exception. A 401 and a 403 mean different
 * things to a merchant - the key is not recognised, versus the key lacks a permission - and collapsing
 * them sends people looking for the wrong fix.</p>
 */
public class ManagementApiClient
{
	private static final Logger LOG = LoggerFactory.getLogger(ManagementApiClient.class);

	public static final String TEST_ENDPOINT = "https://management-test.adyen.com/v3";
	public static final String LIVE_ENDPOINT = "https://management-live.adyen.com/v3";

	/** Overrides the environment a store's test mode selects. Unset in normal use. */
	static final String ENDPOINT_OVERRIDE_PROPERTY = "adyencockpit.management.api.endpoint";

	private static final String X_API_KEY = "X-API-Key";
	private static final int CONNECT_TIMEOUT_MS = 10_000;
	private static final int READ_TIMEOUT_MS = 30_000;

	public enum Failure
	{
		/** 401: Adyen does not know this key in this environment. */
		UNRECOGNISED,
		/** 403: the key is known but lacks the role this call needs. */
		FORBIDDEN,
		/** Any other status Adyen answered with. */
		REJECTED,
		/** No answer at all: DNS, TLS, timeout. */
		UNREACHABLE,
		/** The configured endpoint is not a usable URL. */
		MISCONFIGURED
	}

	/** What Adyen answered. Exactly one of body and failure is set. */
	public record Response(Map<String, Object> body, Failure failure, int status)
	{
		public boolean ok()
		{
			return failure == null;
		}

		static Response of(final Map<String, Object> body)
		{
			return new Response(body == null ? Map.of() : body, null, 200);
		}

		static Response failed(final Failure failure, final int status)
		{
			return new Response(Map.of(), failure, status);
		}
	}

	private ConfigurationService configurationService;
	private final RestTemplate restTemplate;

	public ManagementApiClient()
	{
		// Without timeouts a stalled Adyen call holds a Backoffice request thread indefinitely.
		final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
		factory.setReadTimeout(READ_TIMEOUT_MS);
		this.restTemplate = new RestTemplate(factory);
	}

	public Response get(final BaseStoreModel store, final String path, final String apiKey)
	{
		return exchange(endpointFor(store) + path, HttpMethod.GET, null, apiKey);
	}

	public Response post(final BaseStoreModel store, final String path, final Object body, final String apiKey)
	{
		return exchange(endpointFor(store) + path, HttpMethod.POST, body, apiKey);
	}

	/**
	 * A store in test mode talks to the test environment and a live store to the live one; keys do not
	 * cross between them. A store whose mode is unset is treated as test, the side where a mistake costs
	 * nothing.
	 */
	public String endpointFor(final BaseStoreModel store)
	{
		final String override = configurationService.getConfiguration().getString(ENDPOINT_OVERRIDE_PROPERTY);
		if (StringUtils.isNotBlank(override))
		{
			return StringUtils.removeEnd(override.trim(), "/");
		}
		return Boolean.FALSE.equals(store.getAdyenTestMode()) ? LIVE_ENDPOINT : TEST_ENDPOINT;
	}

	/**
	 * The one place a request leaves for Adyen. Separated so tests can answer for Adyen. Logs the path
	 * and the outcome only: the request carries a key and Adyen's error body can echo it.
	 */
	@SuppressWarnings("unchecked")
	protected Response exchange(final String url, final HttpMethod method, final Object body, final String apiKey)
	{
		final HttpHeaders headers = new HttpHeaders();
		headers.set(X_API_KEY, apiKey);
		headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
		if (body != null)
		{
			headers.setContentType(MediaType.APPLICATION_JSON);
		}
		try
		{
			return Response.of(restTemplate.exchange(url, method, new HttpEntity<>(body, headers), Map.class).getBody());
		}
		catch (final HttpStatusCodeException e)
		{
			final HttpStatusCode status = e.getStatusCode();
			LOG.info("Adyen answered {} to {} {}.", status.value(), method, pathOf(url));
			if (status.value() == 401)
			{
				return Response.failed(Failure.UNRECOGNISED, 401);
			}
			if (status.value() == 403)
			{
				return Response.failed(Failure.FORBIDDEN, 403);
			}
			return Response.failed(Failure.REJECTED, status.value());
		}
		catch (final ResourceAccessException e)
		{
			LOG.warn("Adyen could not be reached for {} {} ({}).", method, pathOf(url), e.getClass().getSimpleName());
			return Response.failed(Failure.UNREACHABLE, 0);
		}
		catch (final IllegalArgumentException e)
		{
			LOG.error("The Management API endpoint is not a usable absolute URL.");
			return Response.failed(Failure.MISCONFIGURED, 0);
		}
		catch (final RestClientException e)
		{
			LOG.warn("Adyen call {} {} failed ({}).", method, pathOf(url), e.getClass().getSimpleName());
			return Response.failed(Failure.REJECTED, 0);
		}
	}

	private static String pathOf(final String url)
	{
		final int versionAt = url.indexOf("/v3");
		return versionAt < 0 ? url : url.substring(versionAt);
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
