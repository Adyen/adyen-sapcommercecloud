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
package com.adyen.commerce.connector.restrictions;

import com.adyen.commerce.connector.facades.MySubscriptionsFacade;
import com.adyen.commerce.connector.model.AdyenSubscriptionPlatformRestrictionModel;

import de.hybris.platform.cms2.servicelayer.data.RestrictionData;
import de.hybris.platform.cms2.servicelayer.services.evaluator.CMSRestrictionEvaluator;

/**
 * Lets a CMS item through only while at least one subscription billing platform connector is loaded, so the
 * My Account entry for subscriptions follows the extensions actually deployed rather than imported data.
 */
public class SubscriptionPlatformRestrictionEvaluator
		implements CMSRestrictionEvaluator<AdyenSubscriptionPlatformRestrictionModel>
{
	private MySubscriptionsFacade mySubscriptionsFacade;

	@Override
	public boolean evaluate(final AdyenSubscriptionPlatformRestrictionModel restriction, final RestrictionData context)
	{
		return getMySubscriptionsFacade().hasAvailablePlatforms();
	}

	protected MySubscriptionsFacade getMySubscriptionsFacade()
	{
		return mySubscriptionsFacade;
	}

	public void setMySubscriptionsFacade(final MySubscriptionsFacade mySubscriptionsFacade)
	{
		this.mySubscriptionsFacade = mySubscriptionsFacade;
	}
}
