package com.adyen.cockpit.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Collects every {@link CockpitRoute} bean of its application context and the contexts above it, on first
 * use. It is lazy because the route beans of other extensions may not exist yet when this bean is created,
 * and an extension adds an endpoint by defining a route bean, not by editing this extension.
 *
 * <p>Route beans belong in the extension's core Spring file ({@code <ext>-spring.xml}, loaded into the
 * {@code <ext>.application-context}), which merges into the same core context as this registry. A route in a
 * child context, such as {@code *-backoffice-spring.xml} (the Backoffice web context), is not found:
 * {@code beansOfTypeIncludingAncestors} looks at this context and its ancestors, never at descendants.</p>
 *
 * <p>A route that
 * breaks a rule of {@link CockpitRouteRules} is left out and logged at ERROR, so one broken extension does
 * not take the cockpit down.
 */
public class DefaultCockpitRouteRegistry implements CockpitRouteRegistry, ApplicationContextAware
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultCockpitRouteRegistry.class);

	private ApplicationContext applicationContext;
	private Collection<? extends CockpitRoute> fixedRoutes;
	private final CockpitRouteRules rules = new CockpitRouteRules();
	private volatile List<CockpitRoute> routes;

	public DefaultCockpitRouteRegistry()
	{
		// routes come from the application context
	}

	/** A registry over a given set of routes, for tests. */
	public DefaultCockpitRouteRegistry(final Collection<? extends CockpitRoute> routes)
	{
		this.fixedRoutes = List.copyOf(routes);
	}

	@Override
	public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
	{
		this.applicationContext = applicationContext;
	}

	@Override
	public Optional<RouteMatch> find(final CockpitRoute.Method method, final String path)
	{
		final List<String> segments = CockpitRouteRegistry.segments(path);
		RouteMatch best = null;
		int[] bestScore = null;
		for (final CockpitRoute route : routes())
		{
			if (route.method() != method)
			{
				continue;
			}
			final List<String> pattern = CockpitRouteRegistry.segments(route.path());
			if (pattern.size() != segments.size())
			{
				continue;
			}
			final Map<String, String> variables = new LinkedHashMap<>();
			final int[] score = new int[pattern.size()];
			boolean matches = true;
			for (int i = 0; i < pattern.size() && matches; i++)
			{
				final String part = pattern.get(i);
				if (CockpitRouteRegistry.isVariable(part))
				{
					variables.put(part.substring(1, part.length() - 1), segments.get(i));
				}
				else if (part.equals(segments.get(i)))
				{
					score[i] = 1;
				}
				else
				{
					matches = false;
				}
			}
			if (matches && (best == null || beats(score, bestScore)))
			{
				best = new RouteMatch(route, variables);
				bestScore = score;
			}
		}
		return Optional.ofNullable(best);
	}

	/** A literal segment earlier in the path wins over a variable there. */
	private static boolean beats(final int[] score, final int[] other)
	{
		for (int i = 0; i < score.length; i++)
		{
			if (score[i] != other[i])
			{
				return score[i] > other[i];
			}
		}
		return false;
	}

	protected List<CockpitRoute> routes()
	{
		List<CockpitRoute> result = routes;
		if (result == null)
		{
			synchronized (this)
			{
				result = routes;
				if (result == null)
				{
					result = collect();
					routes = result;
				}
			}
		}
		return result;
	}

	private List<CockpitRoute> collect()
	{
		final Collection<? extends CockpitRoute> candidates = fixedRoutes != null ? fixedRoutes
				: BeanFactoryUtils.beansOfTypeIncludingAncestors(applicationContext, CockpitRoute.class).values();
		final List<CockpitRoute> all = new ArrayList<>(candidates);
		final Map<CockpitRoute, List<String>> broken = rules.check(all);
		for (final List<String> problems : broken.values())
		{
			for (final String problem : problems)
			{
				LOG.error("Cockpit route not served: {}.", problem);
			}
		}
		final List<CockpitRoute> served = new ArrayList<>();
		for (final CockpitRoute route : all)
		{
			if (!broken.containsKey(route))
			{
				served.add(route);
			}
		}
		return List.copyOf(served);
	}
}
