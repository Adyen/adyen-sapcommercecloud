package com.adyen.cockpit.setup;

import java.util.List;
import java.util.Optional;

import com.adyen.cockpit.setup.dto.CredentialCheck;
import com.adyen.cockpit.setup.dto.MerchantAccounts;
import com.adyen.cockpit.setup.dto.ProvisionReport;
import com.adyen.cockpit.setup.dto.SetupRequests;
import com.adyen.cockpit.setup.dto.StoreSetupView;

import de.hybris.platform.store.BaseStoreModel;

/**
 * Connects a base store to Adyen, one step at a time: the Management API key, the merchant account, then
 * the credentials the storefront runs on. Each step saves what it produced, so a merchant can stop and
 * come back.
 */
public interface CockpitSetupService
{
	List<StoreSetupView> stores();

	StoreSetupView describe(BaseStoreModel store);

	/** The path, relative to a public base URL, that Adyen posts a site's notifications to. */
	String notificationPath(String siteUid);

	/** Stores the key against the store only if Adyen confirms it is active and holds every required role. */
	CredentialCheck saveManagementKey(BaseStoreModel store, String apiKey);

	/** The merchant accounts the store's Management API key can see. */
	MerchantAccounts merchantAccounts(BaseStoreModel store);

	/** Stores the merchant account only if it is one the store's key can see. */
	boolean saveMerchantAccount(BaseStoreModel store, String merchantAccount);

	/** Why provisioning cannot start for this request, or empty when it can. */
	Optional<String> provisionProblem(BaseStoreModel store, SetupRequests.Provision request);

	ProvisionReport provision(BaseStoreModel store, SetupRequests.Provision request);
}
