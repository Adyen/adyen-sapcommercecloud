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

import com.adyen.commerce.connector.model.AdyenSubscriptionPlatformRestrictionModel;

import de.hybris.platform.servicelayer.model.attribute.DynamicAttributeHandler;

/**
 * The read-only {@code description} every CMS restriction carries, which Backoffice and SmartEdit show in
 * place of the restriction's settings. This restriction has none, so the sentence is fixed.
 */
public class SubscriptionPlatformRestrictionDescriptionHandler
		implements DynamicAttributeHandler<String, AdyenSubscriptionPlatformRestrictionModel>
{
	public static final String DESCRIPTION = "Visible while a subscription billing platform connector is loaded";

	@Override
	public String get(final AdyenSubscriptionPlatformRestrictionModel restriction)
	{
		return DESCRIPTION;
	}

	@Override
	public void set(final AdyenSubscriptionPlatformRestrictionModel restriction, final String value)
	{
		throw new UnsupportedOperationException("The description of this restriction is derived, not stored");
	}
}
