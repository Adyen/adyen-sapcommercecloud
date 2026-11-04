/*
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.connector.jalo;

import com.adyen.commerce.connector.restrictions.SubscriptionPlatformRestrictionDescriptionHandler;

import de.hybris.platform.cms2.jalo.restrictions.AbstractRestriction;
import de.hybris.platform.jalo.SessionContext;

/**
 * Jalo class of the restriction, required by the type system. The service layer reads the description through
 * {@link SubscriptionPlatformRestrictionDescriptionHandler}; this returns the same sentence for jalo callers.
 *
 * <p>Hand-written and not generated (the type is {@code generate="false"}): in a jaloLogicFree extension the
 * generator writes the jalo class itself into gensrc, where it could not implement the abstract
 * {@link AbstractRestriction#getDescription(SessionContext)}.</p>
 */
public class AdyenSubscriptionPlatformRestriction extends AbstractRestriction
{
	@Override
	public String getDescription(final SessionContext ctx)
	{
		return SubscriptionPlatformRestrictionDescriptionHandler.DESCRIPTION;
	}
}
