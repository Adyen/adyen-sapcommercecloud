package com.adyen.cockpit.jalo;

import java.util.Map;

import de.hybris.platform.core.Registry;
import de.hybris.platform.util.JspContext;

import com.adyen.cockpit.constants.AdyencockpitConstants;

/**
 * Extension manager for adyencockpit. A coremodule without one builds green and then fails to start,
 * so this class exists even though the extension keeps no state of its own.
 */
public class AdyencockpitManager extends GeneratedAdyencockpitManager
{
	public static AdyencockpitManager getInstance()
	{
		return (AdyencockpitManager) Registry.getCurrentTenant().getJaloConnection().getExtensionManager()
				.getExtension(AdyencockpitConstants.EXTENSIONNAME);
	}

	@Override
	public void createEssentialData(final Map<String, String> params, final JspContext jspc)
	{
		// none
	}

	@Override
	public void createProjectData(final Map<String, String> params, final JspContext jspc)
	{
		// none
	}
}
