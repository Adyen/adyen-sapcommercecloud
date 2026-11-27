package com.adyen.cockpit.setup.routes;

import java.util.LinkedHashMap;
import java.util.Map;

import com.adyen.cockpit.api.CockpitRequest;
import com.adyen.cockpit.api.CockpitResponse;

/** The stores and the path Adyen posts notifications to. */
public class SetupStoresRoute extends AbstractSetupRoute
{
	public SetupStoresRoute()
	{
		super(Method.GET, "/setup/stores", false, null);
	}

	@Override
	public CockpitResponse handle(final CockpitRequest request)
	{
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("notificationPath", setup().notificationPath("{site}"));
		body.put("stores", setup().stores());
		return CockpitResponse.ok(body);
	}
}
