package com.adyen.cockpit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.adyen.cockpit.api.CockpitRoute.Method;

import de.hybris.bootstrap.annotations.UnitTest;

@UnitTest
public class CockpitRouteRegistryTest
{
	static class Stub extends AbstractCockpitRoute
	{
		Stub(final Method method, final String path, final String permission, final boolean write, final String audit)
		{
			super(method, path, permission, write, audit);
		}

		@Override
		public CockpitResponse handle(final CockpitRequest request)
		{
			return CockpitResponse.ok(path());
		}
	}

	private static Stub get(final String path)
	{
		return new Stub(Method.GET, path, CockpitRoute.AUTHENTICATED, false, null);
	}

	@Test
	public void matchesMethodAndPathAndExtractsPathVariables()
	{
		final Stub route = get("/stores/{store}/config/{key}");
		final DefaultCockpitRouteRegistry registry = new DefaultCockpitRouteRegistry(List.of(route));

		final CockpitRouteRegistry.RouteMatch match = registry.find(Method.GET, "/stores/us/config/hmac").orElseThrow();

		assertSame(route, match.route());
		assertEquals(Map.of("store", "us", "key", "hmac"), match.pathVariables());
	}

	@Test
	public void doesNotMatchAnotherMethodAnotherLengthOrAnotherLiteral()
	{
		final DefaultCockpitRouteRegistry registry = new DefaultCockpitRouteRegistry(List.of(get("/stores/{store}")));
		assertTrue(registry.find(Method.POST, "/stores/us").isEmpty());
		assertTrue(registry.find(Method.GET, "/stores").isEmpty());
		assertTrue(registry.find(Method.GET, "/stores/us/extra").isEmpty());
		assertTrue(registry.find(Method.GET, "/shops/us").isEmpty());
	}

	@Test
	public void literalSegmentWinsOverVariableWhateverTheOrder()
	{
		final Stub variable = get("/stores/{store}");
		final Stub literal = get("/stores/summary");
		for (final List<Stub> order : List.of(List.of(variable, literal), List.of(literal, variable)))
		{
			final DefaultCockpitRouteRegistry registry = new DefaultCockpitRouteRegistry(order);
			assertSame(literal, registry.find(Method.GET, "/stores/summary").orElseThrow().route());
			assertSame(variable, registry.find(Method.GET, "/stores/us").orElseThrow().route());
		}
	}

	@Test
	public void earlierLiteralBeatsALaterOne()
	{
		final Stub early = get("/a/b/{c}");
		final Stub late = get("/a/{b}/c");
		final DefaultCockpitRouteRegistry registry = new DefaultCockpitRouteRegistry(List.of(late, early));
		assertSame(early, registry.find(Method.GET, "/a/b/c").orElseThrow().route());
	}

	@Test
	public void invalidRoutesAreNotServedButValidOnesAre()
	{
		final Stub fine = get("/fine");
		final Stub noPermission = new Stub(Method.GET, "/open", " ", false, null);
		final Stub writeWithoutAudit = new Stub(Method.POST, "/w", "ROLE_X", true, null);
		final Stub notMarkedWrite = new Stub(Method.DELETE, "/d", "ROLE_X", false, "d.delete");
		final DefaultCockpitRouteRegistry registry = new DefaultCockpitRouteRegistry(
				List.of(fine, noPermission, writeWithoutAudit, notMarkedWrite));

		assertSame(fine, registry.find(Method.GET, "/fine").orElseThrow().route());
		assertTrue(registry.find(Method.GET, "/open").isEmpty());
		assertTrue(registry.find(Method.POST, "/w").isEmpty());
		assertTrue(registry.find(Method.DELETE, "/d").isEmpty());
	}

	@Test
	public void duplicateRoutesAreBothLeftOutBecauseNeitherIsMoreRightfulThanTheOther()
	{
		final DefaultCockpitRouteRegistry registry = new DefaultCockpitRouteRegistry(
				List.of(get("/dup/{a}"), get("/dup/{b}"), get("/other")));
		assertTrue(registry.find(Method.GET, "/dup/x").isEmpty());
		assertTrue(registry.find(Method.GET, "/other").isPresent());
	}

	@Test
	public void trailingSlashesAndEmptySegmentsAreIgnored()
	{
		final DefaultCockpitRouteRegistry registry = new DefaultCockpitRouteRegistry(List.of(get("/a/b")));
		assertTrue(registry.find(Method.GET, "/a/b/").isPresent());
		assertTrue(registry.find(Method.GET, "a//b").isPresent());
	}
}
