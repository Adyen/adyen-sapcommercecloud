package com.adyen.cockpit.servlet;

import static com.adyen.cockpit.servlet.ServletTestSupport.call;
import static com.adyen.cockpit.servlet.ServletTestSupport.get;
import static com.adyen.cockpit.servlet.ServletTestSupport.json;
import static com.adyen.cockpit.servlet.ServletTestSupport.post;
import static com.adyen.cockpit.servlet.ServletTestSupport.servlet;
import static com.adyen.cockpit.servlet.ServletTestSupport.signIn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.adyen.cockpit.api.AbstractCockpitRoute;
import com.adyen.cockpit.api.CockpitApiException;
import com.adyen.cockpit.api.CockpitRequest;
import com.adyen.cockpit.api.CockpitResponse;
import com.adyen.cockpit.api.CockpitRoute;
import com.adyen.cockpit.api.DefaultCockpitRouteRegistry;
import com.adyen.cockpit.api.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;

import de.hybris.bootstrap.annotations.UnitTest;

@UnitTest
public class AdyenCockpitApiServletTest
{
	private static final String ADMIN = "ROLE_ADMINGROUP";

	/** A route with a configurable declaration; the handler runs a supplied action. */
	private static class TestRoute extends AbstractCockpitRoute
	{
		private final AtomicInteger calls = new AtomicInteger();
		private final java.util.function.Function<CockpitRequest, CockpitResponse> action;

		TestRoute(final Method method, final String path, final String permission, final boolean write,
				final boolean dangerous, final java.util.function.Function<CockpitRequest, CockpitResponse> action)
		{
			super(method, path, permission, write, dangerous, write ? "test.action" : null);
			this.action = action;
		}

		@Override
		public CockpitResponse handle(final CockpitRequest request)
		{
			calls.incrementAndGet();
			return action.apply(request);
		}
	}

	private static TestRoute readRoute(final String path, final String permission)
	{
		return new TestRoute(CockpitRoute.Method.GET, path, permission, false, false,
				r -> CockpitResponse.ok(Map.of("ok", true)));
	}

	private static TestRoute dangerousRoute()
	{
		return new TestRoute(CockpitRoute.Method.POST, "/danger", ADMIN, true, true,
				r -> CockpitResponse.ok(Map.of("done", true)));
	}

	@After
	public void clearSecurity()
	{
		SecurityContextHolder.clearContext();
	}

	private static JsonNode error(final MockHttpServletResponse response, final int status, final String code)
			throws IOException
	{
		assertEquals(status, response.getStatus());
		assertEquals("application/json", response.getContentType().split(";")[0]);
		assertEquals("no-store", response.getHeader("Cache-Control"));
		final JsonNode body = json(response);
		assertEquals(code, body.get("code").asText());
		assertFalse("no legacy error field", body.has("error"));
		assertNotNull(body.get("message"));
		assertEquals("header and body carry the same correlation id", response.getHeader("X-Correlation-Id"),
				body.get("correlationId").asText());
		java.util.UUID.fromString(body.get("correlationId").asText());
		return body;
	}

	@Test
	public void unknownPathAnswers404UnknownEndpoint() throws IOException
	{
		signIn(ADMIN);
		final MockHttpServletResponse response = call(servlet(List.of(readRoute("/known", ADMIN))), get("/nothing"));
		final JsonNode body = error(response, 404, "UNKNOWN_ENDPOINT");
		assertEquals("Unknown endpoint.", body.get("message").asText());
		assertFalse(body.has("field"));
		assertFalse(body.has("details"));
	}

	@Test
	public void knownPathWithAnotherMethodAnswers404() throws IOException
	{
		signIn(ADMIN);
		error(call(servlet(List.of(readRoute("/known", ADMIN))), post("/known", "{}")), 404, "UNKNOWN_ENDPOINT");
	}

	@Test
	public void unsupportedHttpMethodAnswers404InsteadOfCrashing() throws IOException
	{
		signIn(ADMIN);
		final MockHttpServletRequest request = new MockHttpServletRequest("TRACE", "/x");
		request.setPathInfo("/known");
		error(call(servlet(List.of(readRoute("/known", ADMIN))), request), 404, "UNKNOWN_ENDPOINT");
	}

	@Test
	public void missingPermissionAnswers403WithEnvelopeAndNeverRunsTheHandler() throws IOException
	{
		signIn("ROLE_BACKOFFICEADMINGROUP");
		final TestRoute route = readRoute("/secret", ADMIN);
		final JsonNode body = error(call(servlet(List.of(route)), get("/secret")), 403, "PERMISSION_DENIED");
		assertEquals("Connecting a store to Adyen requires a Backoffice administrator.", body.get("message").asText());
		assertEquals(0, route.calls.get());
	}

	@Test
	public void missingOtherPermissionUsesTheGenericMessage() throws IOException
	{
		signIn(ADMIN);
		final JsonNode body = error(call(servlet(List.of(readRoute("/other", "ROLE_OTHER"))), get("/other")), 403,
				"PERMISSION_DENIED");
		assertEquals("You do not have permission to do this.", body.get("message").asText());
	}

	@Test
	public void noAuthenticationAnswers403EvenForAuthenticatedOnlyRoutes() throws IOException
	{
		error(call(servlet(List.of(readRoute("/open", CockpitRoute.AUTHENTICATED))), get("/open")), 403,
				"PERMISSION_DENIED");
	}

	@Test
	public void authenticatedPermissionAcceptsAnyAuthority() throws IOException
	{
		signIn("ROLE_NOTHING_SPECIAL");
		final MockHttpServletResponse response = call(servlet(List.of(readRoute("/open", CockpitRoute.AUTHENTICATED))),
				get("/open"));
		assertEquals(200, response.getStatus());
		assertEquals(json("{\"ok\":true}"), json(response));
	}

	@Test
	public void writeWithWrongContentTypeAnswers415() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = new TestRoute(CockpitRoute.Method.POST, "/w", ADMIN, true, false,
				r -> CockpitResponse.ok(Map.of()));
		final MockHttpServletRequest request = post("/w", "{}");
		request.setContentType("application/x-www-form-urlencoded");
		error(call(servlet(List.of(route)), request), 415, "UNTRUSTED_WRITE");
		assertEquals(0, route.calls.get());
	}

	@Test
	public void writeFromAnotherSiteAnswers415() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = new TestRoute(CockpitRoute.Method.POST, "/w", ADMIN, true, false,
				r -> CockpitResponse.ok(Map.of()));
		final MockHttpServletRequest request = post("/w", "{}");
		request.addHeader("Sec-Fetch-Site", "cross-site");
		error(call(servlet(List.of(route)), request), 415, "UNTRUSTED_WRITE");
		assertEquals(0, route.calls.get());
	}

	@Test
	public void writeFromSameOriginIsTrusted() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = new TestRoute(CockpitRoute.Method.POST, "/w", ADMIN, true, false,
				r -> CockpitResponse.ok(Map.of("w", 1)));
		final MockHttpServletRequest request = post("/w", "{}");
		request.addHeader("Sec-Fetch-Site", "same-origin");
		assertEquals(200, call(servlet(List.of(route)), request).getStatus());
	}

	@Test
	public void permissionIsCheckedBeforeTheTrustedWriteCheck() throws IOException
	{
		signIn("ROLE_OTHER");
		final TestRoute route = new TestRoute(CockpitRoute.Method.POST, "/w", ADMIN, true, false,
				r -> CockpitResponse.ok(Map.of()));
		final MockHttpServletRequest request = post("/w", "{}");
		request.setContentType("text/plain");
		error(call(servlet(List.of(route)), request), 403, "PERMISSION_DENIED");
	}

	private void assertConfirmationRequired(final String requestBody) throws IOException
	{
		final TestRoute route = dangerousRoute();
		final JsonNode body = error(call(servlet(List.of(route)), post("/danger", requestBody)), 400,
				"CONFIRMATION_REQUIRED");
		assertEquals("confirmation", body.get("field").asText());
		assertEquals("Confirm this action to continue.", body.get("message").asText());
		assertEquals(0, route.calls.get());
	}

	private void assertReasonRequired(final String requestBody) throws IOException
	{
		final TestRoute route = dangerousRoute();
		final JsonNode body = error(call(servlet(List.of(route)), post("/danger", requestBody)), 400, "REASON_REQUIRED");
		assertEquals("confirmation.reason", body.get("field").asText());
		assertEquals("Give a reason for this action.", body.get("message").asText());
		assertEquals(0, route.calls.get());
	}

	@Test
	public void dangerousRouteWithoutBodyAnswersConfirmationRequired() throws IOException
	{
		signIn(ADMIN);
		assertConfirmationRequired("");
	}

	@Test
	public void dangerousRouteWithoutConfirmationObjectAnswersConfirmationRequired() throws IOException
	{
		signIn(ADMIN);
		assertConfirmationRequired("{\"value\":1}");
	}

	@Test
	public void dangerousRouteWithNonObjectBodyAnswersConfirmationRequired() throws IOException
	{
		signIn(ADMIN);
		assertConfirmationRequired("[1]");
	}

	@Test
	public void confirmationThatIsNotAnObjectAnswersConfirmationRequired() throws IOException
	{
		signIn(ADMIN);
		assertConfirmationRequired("{\"confirmation\":\"yes\"}");
		assertConfirmationRequired("{\"confirmation\":[\"rotate\"]}");
		assertConfirmationRequired("{\"confirmation\":null}");
	}

	@Test
	public void oldTopLevelReasonAndConfirmAreNoLongerAccepted() throws IOException
	{
		signIn(ADMIN);
		assertConfirmationRequired("{\"reason\":\"rotate key\",\"confirm\":true}");
	}

	@Test
	public void emptyConfirmationAnswersReasonRequired() throws IOException
	{
		signIn(ADMIN);
		assertReasonRequired("{\"confirmation\":{}}");
	}

	@Test
	public void blankOrNonTextualReasonAnswersReasonRequired() throws IOException
	{
		signIn(ADMIN);
		assertReasonRequired("{\"confirmation\":{\"reason\":\"   \"}}");
		assertReasonRequired("{\"confirmation\":{\"reason\":5}}");
	}

	@Test
	public void dangerousRouteWithConfirmationRunsTheHandlerWhichCanStillReadTheBody() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = new TestRoute(CockpitRoute.Method.POST, "/danger", ADMIN, true, true,
				r -> CockpitResponse.ok(Map.of("value", r.json().get("value").asInt(), "reason",
						r.json().get("confirmation").get("reason").asText())));
		final MockHttpServletResponse response = call(servlet(List.of(route)),
				post("/danger", "{\"confirmation\":{\"reason\":\"rotate key\"},\"value\":1}"));
		assertEquals(200, response.getStatus());
		assertEquals(json("{\"value\":1,\"reason\":\"rotate key\"}"), json(response));
		assertEquals(1, route.calls.get());
	}

	@Test
	public void storeNameInTheConfirmationIsNotCheckedYet() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = dangerousRoute();
		final MockHttpServletResponse response = call(servlet(List.of(route)),
				post("/danger", "{\"confirmation\":{\"reason\":\"rotate\",\"storeName\":\"anything\"}}"));
		assertEquals(200, response.getStatus());
		assertEquals(1, route.calls.get());
	}

	@Test
	public void unexpectedExceptionAnswers500WithAGenericMessageNeverTheExceptionText() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = new TestRoute(CockpitRoute.Method.GET, "/boom", ADMIN, false, false, r ->
		{
			throw new RuntimeException("secret");
		});
		final MockHttpServletResponse response = call(servlet(List.of(route)), get("/boom"));
		final JsonNode body = error(response, 500, "INTERNAL_ERROR");
		assertEquals("The request could not be completed.", body.get("message").asText());
		assertFalse(response.getContentAsString().contains("secret"));
	}

	@Test
	public void failureOfTheRegistryLookupAnswers500() throws IOException
	{
		signIn(ADMIN);
		final AdyenCockpitApiServlet servlet = servlet((method, path) ->
		{
			throw new IllegalStateException("no context");
		});
		final MockHttpServletResponse response = call(servlet, get("/x"));
		error(response, 500, "INTERNAL_ERROR");
		assertFalse(response.getContentAsString().contains("no context"));
	}

	@Test
	public void apiExceptionCarriesCodeMessageFieldAndDetails() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = new TestRoute(CockpitRoute.Method.GET, "/limited", ADMIN, false, false, r ->
		{
			throw new CockpitApiException(ErrorCode.PROVIDER_RATE_LIMITED, "Adyen is busy.", "store",
					Map.of("provider", "adyen", "retryAfter", "30"));
		});
		final JsonNode body = error(call(servlet(List.of(route)), get("/limited")), 503, "PROVIDER_RATE_LIMITED");
		assertEquals("Adyen is busy.", body.get("message").asText());
		assertEquals("store", body.get("field").asText());
		assertEquals("30", body.get("details").get("retryAfter").asText());
	}

	@Test
	public void everyErrorCodeAnswersItsStatusAndEnvelope() throws IOException
	{
		signIn(ADMIN);
		final Map<ErrorCode, Integer> expected = new java.util.EnumMap<>(ErrorCode.class);
		expected.put(ErrorCode.INVALID_REQUEST, 400);
		expected.put(ErrorCode.REASON_REQUIRED, 400);
		expected.put(ErrorCode.CONFIRMATION_REQUIRED, 400);
		expected.put(ErrorCode.STORE_NAME_MISMATCH, 400);
		expected.put(ErrorCode.PERMISSION_DENIED, 403);
		expected.put(ErrorCode.UNKNOWN_ENDPOINT, 404);
		expected.put(ErrorCode.UNKNOWN_STORE, 404);
		expected.put(ErrorCode.OPERATION_IN_PROGRESS, 409);
		expected.put(ErrorCode.CONCURRENT_MODIFICATION, 409);
		expected.put(ErrorCode.PAYLOAD_TOO_LARGE, 413);
		expected.put(ErrorCode.UNTRUSTED_WRITE, 415);
		expected.put(ErrorCode.REQUEST_REJECTED, 422);
		expected.put(ErrorCode.INTERNAL_ERROR, 500);
		expected.put(ErrorCode.PROVIDER_UNAUTHORISED, 502);
		expected.put(ErrorCode.PROVIDER_FORBIDDEN, 502);
		expected.put(ErrorCode.PROVIDER_NOT_FOUND, 502);
		expected.put(ErrorCode.PROVIDER_VALIDATION, 502);
		expected.put(ErrorCode.PROVIDER_REJECTED, 502);
		expected.put(ErrorCode.PROVIDER_UNREACHABLE, 502);
		expected.put(ErrorCode.PROVIDER_MISCONFIGURED, 500);
		expected.put(ErrorCode.PROVIDER_RATE_LIMITED, 503);
		expected.put(ErrorCode.PROVIDER_TIMEOUT, 504);
		assertEquals("the table lists every code", ErrorCode.values().length, expected.size());
		for (final Map.Entry<ErrorCode, Integer> entry : expected.entrySet())
		{
			final ErrorCode code = entry.getKey();
			final TestRoute route = new TestRoute(CockpitRoute.Method.GET, "/e", ADMIN, false, false, r ->
			{
				throw new CockpitApiException(code, "Message for " + code);
			});
			final JsonNode body = error(call(servlet(List.of(route)), get("/e")), entry.getValue(), code.name());
			assertEquals("Message for " + code, body.get("message").asText());
			assertEquals(entry.getValue().intValue(), code.status());
		}
	}

	@Test
	public void providerCodesNeverAnswer401Or403()
	{
		for (final ErrorCode code : ErrorCode.values())
		{
			if (code.name().startsWith("PROVIDER_"))
			{
				assertTrue(code + " must not look like a lost session or a missing permission",
						code.status() != 401 && code.status() != 403);
			}
		}
	}

	@Test
	public void routeSuccessKeepsItsStatusAndBodyAndNeverCachesIt() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = new TestRoute(CockpitRoute.Method.GET, "/created", ADMIN, false, false,
				r -> CockpitResponse.of(422, Map.of("usable", false)));
		final MockHttpServletResponse response = call(servlet(List.of(route)), get("/created"));
		assertEquals(422, response.getStatus());
		assertEquals("no-store", response.getHeader("Cache-Control"));
		assertEquals("UTF-8", response.getCharacterEncoding());
		assertEquals(json("{\"usable\":false}"), json(response));
		assertNotNull(response.getHeader("X-Correlation-Id"));
	}

	@Test
	public void pathVariablesReachTheHandler() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = new TestRoute(CockpitRoute.Method.GET, "/stores/{store}/config", ADMIN, false, false,
				r -> CockpitResponse.ok(Map.of("store", r.pathVariable("store"))));
		final MockHttpServletResponse response = call(servlet(List.of(route)), get("/stores/us/config"));
		assertEquals(json("{\"store\":\"us\"}"), json(response));
	}

	@Test
	public void eachRequestGetsItsOwnCorrelationId() throws IOException
	{
		signIn(ADMIN);
		final AdyenCockpitApiServlet servlet = servlet(List.of(readRoute("/known", ADMIN)));
		final String first = call(servlet, get("/nope")).getHeader("X-Correlation-Id");
		final String second = call(servlet, get("/nope")).getHeader("X-Correlation-Id");
		assertFalse(first.equals(second));
	}

	/**
	 * The extension point, in the production layout: the registry and every extension's route beans live in
	 * the core context ({@code <ext>-spring.xml} files merge into it), so a route another extension defines
	 * there under its own bean name is served without a change to adyencockpit, and so is a route in an
	 * ancestor context.
	 */
	@Test
	public void routeFromAnotherExtensionAndFromAnAncestorContextAreFoundAndCalledThroughTheServlet() throws IOException
	{
		signIn(ADMIN);
		final GenericApplicationContext ancestor = new GenericApplicationContext();
		ancestor.registerBeanDefinition("globalRoute", new RootBeanDefinition(SecondForeignRoute.class));
		ancestor.refresh();
		final GenericApplicationContext core = new GenericApplicationContext(ancestor);
		core.registerBeanDefinition("adyencockpitRouteRegistry", new RootBeanDefinition(DefaultCockpitRouteRegistry.class));
		core.registerBeanDefinition("subscriptionsPingRoute", new RootBeanDefinition(ForeignRoute.class));
		core.refresh();
		try
		{
			final AdyenCockpitApiServlet servlet = servlet(
					core.getBean("adyencockpitRouteRegistry", com.adyen.cockpit.api.CockpitRouteRegistry.class));
			final MockHttpServletResponse sameContext = call(servlet, get("/foreign/ping"));
			assertEquals(200, sameContext.getStatus());
			assertEquals(json("{\"from\":\"another extension\"}"), json(sameContext));
			assertEquals(json("{\"from\":\"ancestor\"}"), json(call(servlet, get("/foreign/ancestor"))));
		}
		finally
		{
			core.close();
			ancestor.close();
		}
	}

	/** Documents the limit: a route only in a CHILD context (the Backoffice web context layout) is not seen. */
	@Test
	public void routeDefinedOnlyInAChildContextIsNotFound() throws IOException
	{
		signIn(ADMIN);
		final GenericApplicationContext core = new GenericApplicationContext();
		core.registerBeanDefinition("adyencockpitRouteRegistry", new RootBeanDefinition(DefaultCockpitRouteRegistry.class));
		core.refresh();
		final GenericApplicationContext child = new GenericApplicationContext(core);
		child.registerBeanDefinition("backofficeOnlyRoute", new RootBeanDefinition(ForeignRoute.class));
		child.refresh();
		try
		{
			final AdyenCockpitApiServlet servlet = servlet(
					core.getBean("adyencockpitRouteRegistry", com.adyen.cockpit.api.CockpitRouteRegistry.class));
			error(call(servlet, get("/foreign/ping")), 404, "UNKNOWN_ENDPOINT");
		}
		finally
		{
			child.close();
			core.close();
		}
	}

	@Test
	public void anonymousTokenIsNotAuthenticatedAndGets403OnAnyRoute() throws IOException
	{
		SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.AnonymousAuthenticationToken(
				"key", "anonymousUser", org.springframework.security.core.authority.AuthorityUtils.createAuthorityList(ADMIN)));
		final AdyenCockpitApiServlet servlet = servlet(
				List.of(readRoute("/open", CockpitRoute.AUTHENTICATED), readRoute("/admin", ADMIN)));
		error(call(servlet, get("/open")), 403, "PERMISSION_DENIED");
		error(call(servlet, get("/admin")), 403, "PERMISSION_DENIED");
	}

	@Test
	public void successBodyJacksonCannotSerialiseAnswersInternalErrorEnvelope() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = new TestRoute(CockpitRoute.Method.GET, "/bad", ADMIN, false, false,
				r -> CockpitResponse.ok(new Object()));
		final MockHttpServletResponse response = call(servlet(List.of(route)), get("/bad"));
		final JsonNode body = error(response, 500, "INTERNAL_ERROR");
		assertEquals("The request could not be completed.", body.get("message").asText());
	}

	@Test
	public void bodyOverTheLimitByContentLengthAnswers413WithoutReadingIt() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = new TestRoute(CockpitRoute.Method.POST, "/big", ADMIN, true, false,
				r -> CockpitResponse.ok(Map.of("read", r.body(Map.class) != null)));
		final MockHttpServletRequest request = post("/big", "{}");
		request.setContent(new byte[CockpitRequest.MAX_BODY_BYTES + 1]);
		final JsonNode body = error(call(servlet(List.of(route)), request), 413, "PAYLOAD_TOO_LARGE");
		assertEquals("body", body.get("field").asText());
	}

	@Test
	public void bodyOverTheLimitWithoutAContentLengthIsStillRefused() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = new TestRoute(CockpitRoute.Method.POST, "/big", ADMIN, true, false,
				r -> CockpitResponse.ok(Map.of("read", r.body(Map.class) != null)));
		final byte[] huge = new byte[CockpitRequest.MAX_BODY_BYTES + 10];
		java.util.Arrays.fill(huge, (byte) ' ');
		final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x")
		{
			@Override
			public long getContentLengthLong()
			{
				return -1;
			}
		};
		request.setPathInfo("/big");
		request.setContentType("application/json");
		request.setContent(huge);
		error(call(servlet(List.of(route)), request), 413, "PAYLOAD_TOO_LARGE");
	}

	@Test
	public void bodyAtTheLimitIsAccepted() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = new TestRoute(CockpitRoute.Method.POST, "/ok", ADMIN, true, false,
				r -> CockpitResponse.ok(Map.of("json", r.json() != null)));
		final byte[] exact = new byte[CockpitRequest.MAX_BODY_BYTES];
		java.util.Arrays.fill(exact, (byte) ' ');
		exact[0] = '{';
		exact[exact.length - 1] = '}';
		final MockHttpServletRequest request = post("/ok", "{}");
		request.setContent(exact);
		assertEquals(200, call(servlet(List.of(route)), request).getStatus());
	}

	@Test
	public void emptyBodyGivesNullJsonAndNullObject() throws IOException
	{
		signIn(ADMIN);
		final TestRoute route = new TestRoute(CockpitRoute.Method.POST, "/empty", ADMIN, true, false,
				r -> CockpitResponse.ok(Map.of("json", r.json() == null, "obj", r.body(Map.class) == null)));
		assertEquals(json("{\"json\":true,\"obj\":true}"), json(call(servlet(List.of(route)), post("/empty", ""))));
	}

	@Test(expected = NullPointerException.class)
	public void apiExceptionRequiresACode()
	{
		new CockpitApiException(null, "x");
	}

	public static class ForeignRoute extends AbstractCockpitRoute
	{
		public ForeignRoute()
		{
			super(Method.GET, "/foreign/ping", AUTHENTICATED, false, null);
		}

		@Override
		public CockpitResponse handle(final CockpitRequest request)
		{
			return CockpitResponse.ok(Map.of("from", "another extension"));
		}
	}

	public static class SecondForeignRoute extends AbstractCockpitRoute
	{
		public SecondForeignRoute()
		{
			super(Method.GET, "/foreign/ancestor", AUTHENTICATED, false, null);
		}

		@Override
		public CockpitResponse handle(final CockpitRequest request)
		{
			return CockpitResponse.ok(Map.of("from", "ancestor"));
		}
	}
}
