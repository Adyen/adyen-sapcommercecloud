/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.connector.setup;

import java.util.List;
import java.util.UUID;

import com.adyen.commerce.connector.constants.AdyensubscriptionconnectorConstants;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;

import de.hybris.platform.commerceservices.setup.AbstractSystemSetup;
import de.hybris.platform.core.initialization.SystemSetup;
import de.hybris.platform.core.initialization.SystemSetup.Process;
import de.hybris.platform.core.initialization.SystemSetup.Type;
import de.hybris.platform.core.initialization.SystemSetupContext;
import de.hybris.platform.core.initialization.SystemSetupParameter;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

/**
 * Creates the extension's essential data on every system update: the background jobs that make the connector
 * self-correcting, each with its trigger.
 *
 * <p>Essential data rather than project data, because both jobs are the recovery half of a policy that is
 * inert without them. The retry job is what comes back for an activation that failed transiently, and the
 * reconciliation sweep is the only path from a webhook that was lost, refused or never sent to the
 * platform's actual answer.</p>
 */
@SystemSetup(extension = AdyensubscriptionconnectorConstants.EXTENSIONNAME)
public class AdyensubscriptionconnectorSystemSetup extends AbstractSystemSetup
{
	protected static final String RETRY_JOB_IMPEX = "/impex/essentialdata-subscription-activation-retry.impex";
	protected static final String RECONCILIATION_JOB_IMPEX = "/impex/essentialdata-subscription-reconciliation-cronjob.impex";
	protected static final String RETENTION_JOB_IMPEX = "/impex/essentialdata-subscription-retention.impex";

	private FlexibleSearchService flexibleSearchService;
	private ModelService modelService;

	@SystemSetup(type = Type.ESSENTIAL, process = Process.ALL)
	public void createEssentialData(final SystemSetupContext context)
	{
		importImpexFile(context, RETRY_JOB_IMPEX);
		importImpexFile(context, RECONCILIATION_JOB_IMPEX);
		importImpexFile(context, RETENTION_JOB_IMPEX);
		mintMissingSubscriptionCodes(context);
	}

	/**
	 * Gives a public identifier to any reference that has none, without which the reference cannot be named
	 * by a shopper-facing request while still billing.
	 *
	 * <p>Here rather than in an impex because the value is a UUID, and rather than in the reading code
	 * because minting on a read races itself: two tabs on the shopper's own list would mint two values and
	 * the older link would stop resolving. A no-op once there is nothing left to fill.</p>
	 */
	protected void mintMissingSubscriptionCodes(final SystemSetupContext context)
	{
		// Blank as well as null, and TRIM, because the reading side uses isBlank: a code of one space is
		// otherwise a row the page can never offer and the minting can never reach.
		final FlexibleSearchQuery query = new FlexibleSearchQuery(
				"SELECT {pk} FROM {BillingSubscriptionRef} WHERE {code} IS NULL OR TRIM({code}) = ''");
		final List<BillingSubscriptionRefModel> missing = flexibleSearchService
				.<BillingSubscriptionRefModel> search(query).getResult();
		if (missing.isEmpty())
		{
			return;
		}

		for (final BillingSubscriptionRefModel subscription : missing)
		{
			subscription.setCode(UUID.randomUUID().toString());
		}
		modelService.saveAll(missing);
		logInfo(context, "Assigned a public code to " + missing.size() + " subscription reference(s) that "
				+ "predate the column.");
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	@Override
	public List<SystemSetupParameter> getInitializationOptions()
	{
		return List.of();
	}
}
