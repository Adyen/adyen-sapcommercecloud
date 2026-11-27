package com.adyen.cockpit.setup.routes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.adyen.cockpit.api.CockpitApiException;
import com.adyen.cockpit.api.CockpitRequest;
import com.adyen.cockpit.api.CockpitResponse;
import com.adyen.cockpit.api.ErrorCode;
import com.adyen.cockpit.setup.ProvisioningInProgressException;
import com.adyen.cockpit.setup.dto.ProvisionReport;
import com.adyen.cockpit.setup.dto.SetupRequests;

import de.hybris.platform.store.BaseStoreModel;

/**
 * Creates the credentials the storefront runs on. A partial run answers 422 with the report of what did and
 * did not happen: that is the expected result the page shows, not an error envelope.
 */
public class SetupProvisionRoute extends AbstractSetupRoute
{
	public SetupProvisionRoute()
	{
		super(Method.POST, "/setup/provision", true, "setup.provision");
	}

	@Override
	public CockpitResponse handle(final CockpitRequest request)
	{
		final SetupRequests.Provision body = request.body(SetupRequests.Provision.class);
		final BaseStoreModel store = body == null ? null : store(body.store());
		if (store == null)
		{
			throw new CockpitApiException(ErrorCode.INVALID_REQUEST, "Name a base store.");
		}
		final Optional<String> problem = setup().provisionProblem(store, body);
		if (problem.isPresent())
		{
			throw new CockpitApiException(ErrorCode.REQUEST_REJECTED, problem.get());
		}
		final ProvisionReport report;
		try
		{
			report = setup().provision(store, body);
		}
		catch (final ProvisioningInProgressException e)
		{
			throw new CockpitApiException(ErrorCode.OPERATION_IN_PROGRESS,
					"Credentials are already being created for this store. Wait for that run to finish.");
		}
		final Map<String, Object> result = new LinkedHashMap<>();
		result.put("report", report);
		result.put("store", setup().describe(store));
		return CockpitResponse.of(report.complete() ? 200 : 422, result);
	}
}
