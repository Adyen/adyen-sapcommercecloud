package com.adyen.cockpit.setup.routes;

import com.adyen.cockpit.api.CockpitApiException;
import com.adyen.cockpit.api.CockpitRequest;
import com.adyen.cockpit.api.CockpitResponse;
import com.adyen.cockpit.api.ErrorCode;
import com.adyen.cockpit.setup.dto.SetupRequests;

import de.hybris.platform.store.BaseStoreModel;

/** Saves the merchant account of a store, if the store's key can see it. */
public class SetupMerchantAccountRoute extends AbstractSetupRoute
{
	public SetupMerchantAccountRoute()
	{
		super(Method.POST, "/setup/merchant-account", true, "setup.merchant-account.save");
	}

	@Override
	public CockpitResponse handle(final CockpitRequest request)
	{
		final SetupRequests.MerchantAccount body = request.body(SetupRequests.MerchantAccount.class);
		final BaseStoreModel store = body == null ? null : store(body.store());
		if (store == null)
		{
			throw new CockpitApiException(ErrorCode.INVALID_REQUEST, "Name a base store and a merchant account.");
		}
		if (!setup().saveMerchantAccount(store, body.merchantAccount()))
		{
			throw new CockpitApiException(ErrorCode.REQUEST_REJECTED,
					"That merchant account is not one this store's key can see.");
		}
		return CockpitResponse.ok(setup().describe(store));
	}
}
