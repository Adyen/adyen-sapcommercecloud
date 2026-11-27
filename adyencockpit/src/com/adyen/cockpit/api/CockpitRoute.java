package com.adyen.cockpit.api;

/**
 * One endpoint of the cockpit API, defined as a Spring bean. The servlet knows nothing about individual
 * endpoints: it looks a request up in the {@link CockpitRouteRegistry}, enforces what the route declares
 * here and only then calls {@link #handle}. Any extension can add an endpoint by defining a bean of this type
 * in its own Spring file, so the declaration is data the servlet and the architecture test can read without
 * running the handler.
 */
public interface CockpitRoute
{
	/** The permission of a route any signed-in Backoffice user may call. */
	String AUTHENTICATED = "authenticated";

	/** HTTP methods a route can answer. */
	enum Method
	{
		GET, POST, PUT, PATCH, DELETE;

		/** The method of that name, or null when the cockpit API has no such method. */
		public static Method fromName(final String name)
		{
			if (name == null)
			{
				return null;
			}
			for (final Method method : values())
			{
				if (method.name().equalsIgnoreCase(name))
				{
					return method;
				}
			}
			return null;
		}
	}

	Method method();

	/** Relative to the servlet mapping, with {@code {name}} segments for path variables. */
	String path();

	/** The Spring Security authority required, or {@link #AUTHENTICATED}. Never blank. */
	String permission();

	/** True for every route that changes state; such a route is only served to a trusted write. */
	boolean write();

	/** The route needs a reason and an explicit confirmation in the request body. */
	boolean dangerous();

	/** Stable name of what the audit trail records for this route; required on a write route. */
	String auditAction();

	CockpitResponse handle(CockpitRequest request);
}
