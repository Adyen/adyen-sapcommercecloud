package com.adyen.cockpit.servlet;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.adyen.cockpit.api.CockpitJson;
import com.adyen.cockpit.api.CockpitRoute;
import com.adyen.cockpit.api.CockpitRouteRegistry;
import com.adyen.cockpit.api.DefaultCockpitRouteRegistry;
import com.fasterxml.jackson.databind.JsonNode;

/** Drives the servlet without a web application context: the registry comes from the test. */
final class ServletTestSupport
{
	private ServletTestSupport()
	{
	}

	static AdyenCockpitApiServlet servlet(final CockpitRouteRegistry registry)
	{
		return new AdyenCockpitApiServlet()
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected com.adyen.cockpit.api.CockpitAccess access()
			{
				return new com.adyen.cockpit.api.CockpitAccess();
			}

			@Override
			protected CockpitRouteRegistry routeRegistry()
			{
				return registry;
			}
		};
	}

	static AdyenCockpitApiServlet servlet(final Collection<? extends CockpitRoute> routes)
	{
		return servlet(new DefaultCockpitRouteRegistry(routes));
	}

	static void signIn(final String... authorities)
	{
		final TestingAuthenticationToken token = new TestingAuthenticationToken("someone", "n/a", authorities);
		token.setAuthenticated(true);
		SecurityContextHolder.getContext().setAuthentication(token);
	}

	static MockHttpServletRequest get(final String path)
	{
		final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/adyencockpit/api" + path);
		request.setPathInfo(path);
		return request;
	}

	/** A same-site JSON write, as the cockpit SPA sends it. */
	static MockHttpServletRequest post(final String path, final String json)
	{
		final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/adyencockpit/api" + path);
		request.setPathInfo(path);
		request.setContentType("application/json");
		request.setContent(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return request;
	}

	static MockHttpServletResponse call(final AdyenCockpitApiServlet servlet, final MockHttpServletRequest request)
			throws IOException
	{
		final MockHttpServletResponse response = new MockHttpServletResponse();
		servlet.service(request, response);
		return response;
	}

	static JsonNode json(final MockHttpServletResponse response) throws IOException
	{
		return CockpitJson.mapper().readTree(response.getContentAsByteArray());
	}

	static JsonNode json(final String text) throws IOException
	{
		return CockpitJson.mapper().readTree(text);
	}

	static List<String> names(final JsonNode node)
	{
		final List<String> names = new java.util.ArrayList<>();
		node.fieldNames().forEachRemaining(names::add);
		return names;
	}
}
