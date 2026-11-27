package com.adyen.cockpit.setup.routes;

import org.apache.commons.lang3.StringUtils;

import com.adyen.cockpit.api.CockpitApiException;
import com.adyen.cockpit.api.CockpitRequest;
import com.adyen.cockpit.api.CockpitResponse;
import com.adyen.cockpit.api.ErrorCode;
import com.adyen.cockpit.setup.dto.CredentialCheck;
import com.adyen.cockpit.setup.dto.SetupRequests;

import de.hybris.platform.store.BaseStoreModel;

/**
 * Saves a Management API key. A key Adyen refuses answers 422 with the {@link CredentialCheck} that explains
 * why: that is the expected result the page shows, not an error envelope.
 */
public class SetupManagementKeyRoute extends AbstractSetupRoute
{
	public SetupManagementKeyRoute()
	{
		super(Method.POST, "/setup/management-key", true, "setup.management-key.save");
	}

	@Override
	public CockpitResponse handle(final CockpitRequest request)
	{
		final SetupRequests.ManagementKey body = request.body(SetupRequests.ManagementKey.class);
		final BaseStoreModel store = body == null ? null : store(body.store());
		if (store == null || StringUtils.isBlank(body.apiKey()))
		{
			throw new CockpitApiException(ErrorCode.INVALID_REQUEST, "Name a base store and paste a key.");
		}
		final CredentialCheck check = setup().saveManagementKey(store, body.apiKey());
		return CockpitResponse.of(check.usable() ? 200 : 422, check);
	}
}
