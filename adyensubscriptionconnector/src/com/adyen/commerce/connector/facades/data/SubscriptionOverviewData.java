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
package com.adyen.commerce.connector.facades.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the subscriptions page shows: one section per billing platform whose connector is loaded and
 * on which the shopper has something to see, in the order the connectors are registered.
 */
public class SubscriptionOverviewData implements Serializable
{
	private static final long serialVersionUID = 1L;

	private List<SubscriptionPlatformSectionData> sections = new ArrayList<>();

	public List<SubscriptionPlatformSectionData> getSections()
	{
		return sections;
	}

	public void setSections(final List<SubscriptionPlatformSectionData> sections)
	{
		this.sections = sections == null ? new ArrayList<>() : sections;
	}

	/** True when there is genuinely nothing to say: no subscriptions and no unfinished orders anywhere. */
	public boolean isEmpty()
	{
		return sections.isEmpty();
	}
}
