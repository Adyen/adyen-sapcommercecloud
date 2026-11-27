package com.adyen.cockpit.setup.routes;

import org.apache.commons.lang3.StringUtils;

import com.adyen.cockpit.api.AbstractCockpitRoute;
import com.adyen.cockpit.api.CockpitAccess;
import com.adyen.cockpit.setup.CockpitSetupService;

import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * Shared by the store setup routes. They write live credentials into store configuration, so each requires
 * an administrator. The dependencies are set by Spring and the declaration is fixed in the constructor, so
 * a route can be inspected without them.
 */
public abstract class AbstractSetupRoute extends AbstractCockpitRoute
{
	private CockpitSetupService setupService;
	private BaseStoreService baseStoreService;

	protected AbstractSetupRoute(final Method method, final String path, final boolean write, final String auditAction)
	{
		super(method, path, CockpitAccess.ADMIN_AUTHORITY, write, auditAction);
	}

	protected CockpitSetupService setup()
	{
		return setupService;
	}

	/** The store of that uid, or null when the uid is blank or names no store. */
	protected BaseStoreModel store(final String uid)
	{
		if (StringUtils.isBlank(uid))
		{
			return null;
		}
		try
		{
			return baseStoreService.getBaseStoreForUid(uid);
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	public void setSetupService(final CockpitSetupService setupService)
	{
		this.setupService = setupService;
	}

	public void setBaseStoreService(final BaseStoreService baseStoreService)
	{
		this.baseStoreService = baseStoreService;
	}
}
