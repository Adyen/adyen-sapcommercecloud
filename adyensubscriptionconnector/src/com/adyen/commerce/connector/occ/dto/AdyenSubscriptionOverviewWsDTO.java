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
package com.adyen.commerce.connector.occ.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The shopper's subscriptions, one section per billing platform.
 */
public class AdyenSubscriptionOverviewWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private List<AdyenSubscriptionSectionWsDTO> sections = new ArrayList<>();

	public List<AdyenSubscriptionSectionWsDTO> getSections()
	{
		return sections;
	}

	public void setSections(final List<AdyenSubscriptionSectionWsDTO> sections)
	{
		this.sections = sections == null ? new ArrayList<>() : sections;
	}
}
