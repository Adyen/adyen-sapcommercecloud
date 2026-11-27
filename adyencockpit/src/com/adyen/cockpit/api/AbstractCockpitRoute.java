package com.adyen.cockpit.api;

/**
 * Takes the declaration of a route in its constructor, so that the metadata of a route is available from a
 * no-arg subclass constructor without any injected dependency. The architecture test relies on that: it
 * checks every route of the Spring configuration without building the platform beans they depend on.
 */
public abstract class AbstractCockpitRoute implements CockpitRoute
{
	private final Method method;
	private final String path;
	private final String permission;
	private final boolean write;
	private final boolean dangerous;
	private final String auditAction;

	protected AbstractCockpitRoute(final Method method, final String path, final String permission, final boolean write,
			final boolean dangerous, final String auditAction)
	{
		this.method = method;
		this.path = path;
		this.permission = permission;
		this.write = write;
		this.dangerous = dangerous;
		this.auditAction = auditAction;
	}

	/** A route that is not dangerous. */
	protected AbstractCockpitRoute(final Method method, final String path, final String permission, final boolean write,
			final String auditAction)
	{
		this(method, path, permission, write, false, auditAction);
	}

	@Override
	public Method method()
	{
		return method;
	}

	@Override
	public String path()
	{
		return path;
	}

	@Override
	public String permission()
	{
		return permission;
	}

	@Override
	public boolean write()
	{
		return write;
	}

	@Override
	public boolean dangerous()
	{
		return dangerous;
	}

	@Override
	public String auditAction()
	{
		return auditAction;
	}

	@Override
	public String toString()
	{
		return method + " " + path;
	}
}
