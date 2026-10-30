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
package com.adyen.commerce.connector.occ.exceptions;

import de.hybris.platform.webservicescommons.errors.exceptions.WebserviceException;

/**
 * The one answer to a subscription action that did not happen, whatever the reason: a code that is not the
 * shopper's, a state that does not allow it, a missing platform, a platform that said no. Telling them apart
 * on the wire would tell a caller probing for codes which ones exist, so the message is fixed, carries no
 * reason or subject, and never quotes anything a billing platform said. Translated by the OCC error
 * resolver into a 400 {@code ErrorListWsDTO} of type {@code SubscriptionActionRefusedError}.
 */
public class SubscriptionActionRefusedException extends WebserviceException
{
	public static final String TYPE = "SubscriptionActionRefusedError";
	public static final String MESSAGE = "The subscription action was refused";

	public SubscriptionActionRefusedException()
	{
		super(MESSAGE);
	}

	@Override
	public String getType()
	{
		return TYPE;
	}

	@Override
	public String getSubjectType()
	{
		return null;
	}
}
