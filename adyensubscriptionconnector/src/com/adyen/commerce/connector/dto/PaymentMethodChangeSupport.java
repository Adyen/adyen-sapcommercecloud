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

import java.util.Set;

/**
 * What a connector can do about a shopper changing the card an existing subscription is billed to.
 *
 * <p>Scope and sources travel together: a supported scope naming no source advertises a feature while
 * accepting nothing the core can offer, which renders a control whose every submission is refused, so the
 * constructor rejects that combination.</p>
 */
public record PaymentMethodChangeSupport(PaymentMethodChangeScope scope, Set<PaymentMethodSource> sources)
{
	/** Declared by a platform that cannot do it at all, and by adapters that have not implemented it yet. */
	public static final PaymentMethodChangeSupport NONE =
			new PaymentMethodChangeSupport(PaymentMethodChangeScope.NOT_SUPPORTED, Set.of());

	public PaymentMethodChangeSupport
	{
		Dtos.requireValue(scope, "scope");
		sources = sources == null ? Set.of() : Set.copyOf(sources);
		if (scope.isSupported() == sources.isEmpty())
		{
			throw new IllegalArgumentException("A connector that supports a payment-method change must name "
					+ "at least one source it accepts, and one that does not must name none");
		}
	}

	public boolean isSupported()
	{
		return scope.isSupported();
	}

	public boolean accepts(final PaymentMethodSource source)
	{
		return sources.contains(source);
	}
}
