package com.adyen.cockpit.servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.adyen.cockpit.api.ApiError;
import com.adyen.cockpit.api.CockpitAccess;
import com.adyen.cockpit.api.CockpitApiException;
import com.adyen.cockpit.api.CockpitJson;
import com.adyen.cockpit.api.CockpitRequest;
import com.adyen.cockpit.api.CockpitResponse;
import com.adyen.cockpit.api.CockpitRoute;
import com.adyen.cockpit.api.CockpitRouteRegistry;
import com.adyen.cockpit.api.CockpitRouteRegistry.RouteMatch;
import com.adyen.cockpit.api.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Backs the cockpit application. It runs inside the Backoffice webapp, behind Backoffice's own security
 * chain, so the caller is whoever is signed in to Backoffice and there is no second login to get wrong.
 *
 * <p>The servlet knows no endpoint. It finds the {@link CockpitRoute} for the request and enforces what the
 * route declares, in the order ADR 0002 fixes: route, permission, trusted write (see
 * {@link #isTrustedWrite}), reason and confirmation of a dangerous route, then the handler. Every error
 * leaves as the same JSON envelope and carries the correlation id of the request.</p>
 */
public class AdyenCockpitApiServlet extends HttpServlet
{
	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(AdyenCockpitApiServlet.class);

	static final String CORRELATION_HEADER = "X-Correlation-Id";
	private static final String ADMIN_DENIED = "Connecting a store to Adyen requires a Backoffice administrator.";

	@Override
	protected void service(final HttpServletRequest request, final HttpServletResponse response) throws IOException
	{
		final String correlationId = UUID.randomUUID().toString();
		response.setHeader(CORRELATION_HEADER, correlationId);
		final String path = StringUtils.defaultString(request.getPathInfo());
		try
		{
			final CockpitRoute.Method method = CockpitRoute.Method.fromName(request.getMethod());
			final Optional<RouteMatch> match = method == null ? Optional.empty() : routeRegistry().find(method, path);
			if (match.isEmpty())
			{
				fail(request, response, path, correlationId, ErrorCode.UNKNOWN_ENDPOINT, "Unknown endpoint.", null, null);
				return;
			}
			final CockpitRoute route = match.get().route();
			if (!access().hasPermission(route.permission()))
			{
				final String message = CockpitAccess.ADMIN_AUTHORITY.equals(route.permission()) ? ADMIN_DENIED
						: "You do not have permission to do this.";
				fail(request, response, path, correlationId, ErrorCode.PERMISSION_DENIED, message, null, null);
				return;
			}
			if (route.write() && !isTrustedWrite(request))
			{
				fail(request, response, path, correlationId, ErrorCode.UNTRUSTED_WRITE,
						"Send the request as JSON from the cockpit.", null, null);
				return;
			}
			final CockpitRequest cockpitRequest = new CockpitRequest(request, match.get().pathVariables(), correlationId);
			if (route.dangerous())
			{
				final JsonNode json = cockpitRequest.json();
				final JsonNode confirmation = json == null ? null : json.get("confirmation");
				if (confirmation == null || !confirmation.isObject())
				{
					fail(request, response, path, correlationId, ErrorCode.CONFIRMATION_REQUIRED,
							"Confirm this action to continue.", "confirmation", null);
					return;
				}
				final JsonNode reason = confirmation.get("reason");
				if (reason == null || !reason.isTextual() || StringUtils.isBlank(reason.asText()))
				{
					fail(request, response, path, correlationId, ErrorCode.REASON_REQUIRED,
							"Give a reason for this action.", "confirmation.reason", null);
					return;
				}
				// confirmation.storeName is reserved: a follow-up checks it on live stores; not validated here.
			}
			final CockpitResponse result = route.handle(cockpitRequest);
			final byte[] bytes;
			try
			{
				bytes = CockpitJson.mapper().writeValueAsBytes(result.body());
			}
			catch (final JsonProcessingException e)
			{
				// Serialised before anything is written, so a body Jackson cannot handle still gets the envelope.
				internalError(request, response, path, correlationId, e);
				return;
			}
			write(response, result.status(), bytes);
		}
		catch (final CockpitApiException e)
		{
			fail(request, response, path, correlationId, e.code(), e.getMessage(), e.field(), e.details());
		}
		catch (final RuntimeException e)
		{
			internalError(request, response, path, correlationId, e);
		}
	}

	private void internalError(final HttpServletRequest request, final HttpServletResponse response, final String path,
			final String correlationId, final Exception e) throws IOException
	{
		// The exception type only: a message here could carry a submitted key or Adyen's reply.
		LOG.error("Cockpit request {} {} failed with {} ({}), correlationId {}.", request.getMethod(), path,
				ErrorCode.INTERNAL_ERROR, e.getClass().getSimpleName(), correlationId);
		send(response, ErrorCode.INTERNAL_ERROR.status(), new ApiError(ErrorCode.INTERNAL_ERROR.name(),
				"The request could not be completed.", null, null, correlationId));
	}

	private void fail(final HttpServletRequest request, final HttpServletResponse response, final String path,
			final String correlationId, final ErrorCode code, final String message, final String field,
			final Map<String, Object> details) throws IOException
	{
		if (code.status() >= 500)
		{
			LOG.warn("Cockpit request {} {} refused with {}, correlationId {}.", request.getMethod(), path, code,
					correlationId);
		}
		else
		{
			LOG.info("Cockpit request {} {} refused with {}, correlationId {}.", request.getMethod(), path, code,
					correlationId);
		}
		send(response, code.status(), new ApiError(code.name(), message, field, details, correlationId));
	}

	/**
	 * Looked up per request on purpose. The Backoffice root context refreshes lazily on the first HTTP
	 * session, so a reference taken in {@code init()} can be taken too early. Protected so a test can supply
	 * a registry without a web application context.
	 */
	protected CockpitRouteRegistry routeRegistry()
	{
		final ApplicationContext context = WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
		return context.getBean("adyencockpitRouteRegistry", CockpitRouteRegistry.class);
	}

	/** Looked up per request for the same reason as the registry; protected so a test can override it. */
	protected CockpitAccess access()
	{
		final ApplicationContext context = WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
		return context.getBean("adyencockpitAccess", CockpitAccess.class);
	}

	/**
	 * Backoffice runs with CSRF protection off and its session cookie carries no SameSite attribute, so a
	 * write is only trusted when it could not have come from another site: it must be JSON, which an HTML
	 * form cannot send and a cross-site fetch cannot send without a preflight this servlet never answers,
	 * and, where the browser says where the request came from, it must say the same origin. The Origin
	 * header is not compared with the Host header because a reverse proxy may rewrite the latter.
	 */
	private static boolean isTrustedWrite(final HttpServletRequest request)
	{
		final String contentType = StringUtils.defaultString(request.getContentType()).toLowerCase();
		if (!contentType.startsWith("application/json"))
		{
			return false;
		}
		final String fetchSite = request.getHeader("Sec-Fetch-Site");
		return fetchSite == null || "same-origin".equals(fetchSite);
	}

	private static void send(final HttpServletResponse response, final int status, final Object body) throws IOException
	{
		write(response, status, CockpitJson.mapper().writeValueAsBytes(body));
	}

	private static void write(final HttpServletResponse response, final int status, final byte[] bytes) throws IOException
	{
		response.setStatus(status);
		response.setContentType("application/json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setHeader("Cache-Control", "no-store");
		response.getOutputStream().write(bytes);
	}
}
