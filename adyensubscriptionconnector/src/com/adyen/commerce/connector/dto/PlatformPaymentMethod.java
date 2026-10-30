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
 *
 * <p>{@code importedTokenId} is the vaulted Adyen token the method was imported from, or {@code null} when
 * the platform does not say. It is what lets a card offered from the shopper's vault and the same card held
 * by the platform be recognised as one instrument.</p>
 */
public record PlatformPaymentMethod(String id, String displayLabel, CardMetadata card,
                                    boolean defaultForCustomer, String importedTokenId)
{
	public PlatformPaymentMethod
	{
		Dtos.requireText(id, "id");
		Dtos.requireText(displayLabel, "displayLabel");
	}

	/** For a platform that does not report where a method came from, or a method that was not imported. */
	public PlatformPaymentMethod(final String id, final String displayLabel, final CardMetadata card,
			final boolean defaultForCustomer)
	{
		this(id, displayLabel, card, defaultForCustomer, null);
	}
}
