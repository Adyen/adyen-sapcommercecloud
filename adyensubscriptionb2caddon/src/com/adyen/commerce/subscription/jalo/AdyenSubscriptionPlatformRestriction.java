/*
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.subscription.jalo;

import com.adyen.commerce.subscription.restrictions.SubscriptionPlatformRestrictionDescriptionHandler;

import de.hybris.platform.jalo.SessionContext;

/**
 * Jalo class of the restriction, required by the type system. The service layer reads the description through
 * {@link SubscriptionPlatformRestrictionDescriptionHandler}; this returns the same sentence for jalo callers.
 */
public class AdyenSubscriptionPlatformRestriction extends GeneratedAdyenSubscriptionPlatformRestriction
{
	@Override
	public String getDescription(final SessionContext ctx)
	{
		return SubscriptionPlatformRestrictionDescriptionHandler.DESCRIPTION;
	}
}
