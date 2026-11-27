package com.adyen.cockpit.setup.routes;

import com.adyen.cockpit.api.CockpitApiException;
import com.adyen.cockpit.api.CockpitRequest;
import com.adyen.cockpit.api.CockpitResponse;
import com.adyen.cockpit.api.ErrorCode;

import de.hybris.platform.store.BaseStoreModel;

/** The merchant accounts a store's Management API key can see. */
public class SetupMerchantAccountsRoute extends AbstractSetupRoute
{
	public SetupMerchantAccountsRoute()
	{
		super(Method.GET, "/setup/merchant-accounts", false, null);
	}

	@Override
	public CockpitResponse handle(final CockpitRequest request)
	{
		final BaseStoreModel store = store(request.param("store"));
		if (store == null)
		{
			throw new CockpitApiException(ErrorCode.UNKNOWN_STORE, "Unknown base store.", "store", null);
		}
		return CockpitResponse.ok(setup().merchantAccounts(store));
	}
}
