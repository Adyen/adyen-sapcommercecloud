package com.adyen.cockpit.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * The rules every route must meet (ADR 0002). The registry applies them at registration and the
 * architecture test applies them to the routes of the Spring configuration, so one definition serves both.
 */
public class CockpitRouteRules
{
	/** One message per broken rule, empty when the routes are fine. */
	public List<String> violations(final Collection<? extends CockpitRoute> routes)
	{
		final List<String> all = new ArrayList<>();
		for (final List<String> routeViolations : check(routes).values())
		{
			all.addAll(routeViolations);
		}
		return all;
	}

	/** The routes that break a rule, each with its messages. Every route of a duplicate pair is included. */
	public Map<CockpitRoute, List<String>> check(final Collection<? extends CockpitRoute> routes)
	{
		final Map<CockpitRoute, List<String>> broken = new IdentityHashMap<>();
		final Map<CockpitRoute, List<String>> ordered = new LinkedHashMap<>();
		for (final CockpitRoute route : routes)
		{
			final List<String> own = new ArrayList<>();
			if (route.method() == null || StringUtils.isBlank(route.path()))
			{
				own.add(describe(route) + " declares no method or path");
			}
			if (StringUtils.isBlank(route.permission()))
			{
				own.add(describe(route) + " declares no permission");
			}
			if (route.write() && StringUtils.isBlank(route.auditAction()))
			{
				own.add(describe(route) + " is a write route without an audit action");
			}
			if (route.method() != null && route.method() != CockpitRoute.Method.GET && !route.write())
			{
				own.add(describe(route) + " is not a GET but is not marked write");
			}
			if (route.dangerous() && !route.write())
			{
				own.add(describe(route) + " is dangerous but not a write route");
			}
			if (!own.isEmpty())
			{
				broken.put(route, own);
			}
		}

		final Map<String, List<CockpitRoute>> byKey = new LinkedHashMap<>();
		for (final CockpitRoute route : routes)
		{
			if (route.method() != null && route.path() != null)
			{
				byKey.computeIfAbsent(key(route), k -> new ArrayList<>()).add(route);
			}
		}
		for (final List<CockpitRoute> sameKey : byKey.values())
		{
			if (sameKey.size() > 1)
			{
				for (final CockpitRoute route : sameKey)
				{
					broken.computeIfAbsent(route, r -> new ArrayList<>())
							.add(describe(route) + " duplicates the method and path of another route");
				}
			}
		}

		// Report in the order the routes were given.
		final Set<CockpitRoute> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		for (final CockpitRoute route : routes)
		{
			if (broken.containsKey(route) && seen.add(route))
			{
				ordered.put(route, broken.get(route));
			}
		}
		return ordered;
	}

	/** Method plus path with every variable name erased: {@code /a/{x}} and {@code /a/{y}} are one route. */
	protected String key(final CockpitRoute route)
	{
		final StringBuilder key = new StringBuilder(route.method().name());
		for (final String segment : CockpitRouteRegistry.segments(route.path()))
		{
			key.append('/').append(CockpitRouteRegistry.isVariable(segment) ? "{}" : segment);
		}
		return key.toString();
	}

	private String describe(final CockpitRoute route)
	{
		return "Route " + route.method() + " " + route.path() + " (" + route.getClass().getSimpleName() + ")";
	}
}
