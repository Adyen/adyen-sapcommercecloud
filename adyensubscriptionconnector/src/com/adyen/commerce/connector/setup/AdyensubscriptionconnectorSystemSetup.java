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

import com.adyen.commerce.connector.constants.AdyensubscriptionconnectorConstants;

import de.hybris.platform.commerceservices.setup.AbstractSystemSetup;
import de.hybris.platform.core.initialization.SystemSetup;
import de.hybris.platform.core.initialization.SystemSetup.Process;
import de.hybris.platform.core.initialization.SystemSetup.Type;
import de.hybris.platform.core.initialization.SystemSetupContext;
import de.hybris.platform.core.initialization.SystemSetupParameter;

/**
 * Creates the extension's essential data on every system update: the two background jobs that make the
 * connector self-correcting, each with its trigger.
 *
 * <p>Deliberately automatic rather than a documented manual import. Both jobs are the recovery half of a
 * policy that is inert without them, and an inert recovery is indistinguishable from the situation it was
 * written to fix. The retry job is what comes back for an activation that failed transiently, so without
 * it a paid order simply never gets its subscription. The reconciliation sweep is the only path from a
 * webhook that was lost, refused while the node was down, or never sent to the platform's actual answer,
 * so without it a reference keeps whatever status it was last told about and nothing corrects it.</p>
 *
 * <p>Both therefore belong in essential data rather than project data. A step an operator has to remember
 * on every environment is a step that gets missed on one of them, and the environment that missed it
 * looks healthy right up until the first delivery goes astray.</p>
 */
@SystemSetup(extension = AdyensubscriptionconnectorConstants.EXTENSIONNAME)
public class AdyensubscriptionconnectorSystemSetup extends AbstractSystemSetup
{
	protected static final String RETRY_JOB_IMPEX = "/impex/essentialdata-subscription-activation-retry.impex";
	protected static final String RECONCILIATION_JOB_IMPEX = "/impex/essentialdata-subscription-reconciliation-cronjob.impex";

	@SystemSetup(type = Type.ESSENTIAL, process = Process.ALL)
	public void createEssentialData(final SystemSetupContext context)
	{
		importImpexFile(context, RETRY_JOB_IMPEX);
		importImpexFile(context, RECONCILIATION_JOB_IMPEX);
	}

	@Override
	public List<SystemSetupParameter> getInitializationOptions()
	{
		return List.of();
	}
}
