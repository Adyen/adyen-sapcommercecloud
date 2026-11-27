package com.adyen.cockpit.api.routes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.adyen.cockpit.api.AbstractCockpitRoute;
import com.adyen.cockpit.api.CockpitAccess;
import com.adyen.cockpit.api.CockpitRequest;
import com.adyen.cockpit.api.CockpitResponse;

import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * What the SPA needs to start: who is signed in, whether that user is an administrator and which stores have
 * a Management key. Open to any signed-in user, so the SPA can tell a non-administrator why the setup is closed.
 */
public class BootstrapRoute extends AbstractCockpitRoute
{
	private UserService userService;
	private BaseStoreService baseStoreService;
	private CockpitAccess access = new CockpitAccess();

	public BootstrapRoute()
	{
		super(Method.GET, "/bootstrap", AUTHENTICATED, false, null);
	}

	@Override
	public CockpitResponse handle(final CockpitRequest request)
	{
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("user", userService.getCurrentUser().getUid());
		body.put("admin", access.isAdmin());
		final List<Map<String, Object>> stores = baseStoreService.getAllBaseStores().stream()
				.map(store -> Map.<String, Object> of("uid", store.getUid(),
						"configured", StringUtils.isNotBlank(store.getAdyenManagementApiKey())))
				.toList();
		body.put("stores", stores);
		return CockpitResponse.ok(body);
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setBaseStoreService(final BaseStoreService baseStoreService)
	{
		this.baseStoreService = baseStoreService;
	}

	public void setAccess(final CockpitAccess access)
	{
		this.access = access;
	}
}
