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
package com.adyen.commerce.connector.dto;

/**
 * A payment method the billing platform already holds, as the platform itself describes it.
 *
 * <p>{@code displayLabel} is composed by the adapter and not by the core, because only the adapter knows
 * whether it is looking at a card, a mandate or an agreement; {@code card} is optional detail rather than
 * the identity of the thing.</p>
 *
 * <p>{@code id} is the platform's own identifier and is never a vendor-encoded composite - it travels back
 * verbatim in a {@link PaymentMethodChoice.AlreadyOnPlatform}.</p>
 */
public record PlatformPaymentMethod(String id, String displayLabel, CardMetadata card, boolean defaultForCustomer)
{
	public PlatformPaymentMethod
	{
		Dtos.requireText(id, "id");
		Dtos.requireText(displayLabel, "displayLabel");
	}

	/**
	 * JavaBeans accessors, because this record is read from a JSP: EL resolves {@code ${option.id}} by
	 * introspection and a record's {@code id()} is not a property. Without them the page throws
	 * {@code PropertyNotFoundException}, which the CMS component renderer catches and renders as an empty
	 * slot rather than an error. {@code SubscriptionViewContractTest} pins them.
	 */
	public String getId()
	{
		return id;
	}

	public String getDisplayLabel()
	{
		return displayLabel;
	}

	public CardMetadata getCard()
	{
		return card;
	}

	public boolean isDefaultForCustomer()
	{
		return defaultForCustomer;
	}
}
