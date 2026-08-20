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
 * Creates the extension's essential data on every system update, which today means the activation retry
 * job and its trigger.
 *
 * <p>Deliberately automatic rather than a documented manual import. The retry policy is inert without the
 * job, and an inert retry policy is indistinguishable from the situation it was written to fix: paid
 * orders with no subscription and nobody looking. A step an operator has to remember on every environment
 * is a step that gets missed on one of them.</p>
 */
@SystemSetup(extension = AdyensubscriptionconnectorConstants.EXTENSIONNAME)
public class AdyensubscriptionconnectorSystemSetup extends AbstractSystemSetup
{
	protected static final String RETRY_JOB_IMPEX = "/impex/essentialdata-subscription-activation-retry.impex";

	@SystemSetup(type = Type.ESSENTIAL, process = Process.ALL)
	public void createEssentialData(final SystemSetupContext context)
	{
		importImpexFile(context, RETRY_JOB_IMPEX);
	}

	@Override
	public List<SystemSetupParameter> getInitializationOptions()
	{
		return List.of();
	}
}
