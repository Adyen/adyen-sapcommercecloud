package com.adyen.cockpit.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the route that serves a request. The servlet looks it up by bean name per request, so a merchant can
 * replace the registry.
 */
public interface CockpitRouteRegistry
{
	/** A route and the values of its path variables for one request. */
	record RouteMatch(CockpitRoute route, Map<String, String> pathVariables)
	{
	}

	/** The route for this method and path, literal segments winning over variables. */
	Optional<RouteMatch> find(CockpitRoute.Method method, String path);

	/** The non-empty segments of a path. */
	static List<String> segments(final String path)
	{
		final List<String> segments = new ArrayList<>();
		if (path != null)
		{
			for (final String segment : path.split("/"))
			{
				if (!segment.isEmpty())
				{
					segments.add(segment);
				}
			}
		}
		return segments;
	}

	static boolean isVariable(final String segment)
	{
		return segment.length() > 2 && segment.startsWith("{") && segment.endsWith("}");
	}
}
