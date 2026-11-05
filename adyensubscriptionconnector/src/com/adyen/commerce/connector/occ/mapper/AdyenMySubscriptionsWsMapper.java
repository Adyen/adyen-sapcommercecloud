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
package com.adyen.commerce.connector.occ.mapper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.adyen.commerce.connector.facades.data.PaymentMethodChangeOfferData;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeReport;
import com.adyen.commerce.connector.facades.data.PaymentMethodEnrollmentOfferData;
import com.adyen.commerce.connector.facades.data.PaymentMethodOptionData;
import com.adyen.commerce.connector.facades.data.SubscriptionEntryData;
import com.adyen.commerce.connector.facades.data.SubscriptionOverviewData;
import com.adyen.commerce.connector.facades.data.SubscriptionPlatformSectionData;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionChangeOfferWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionEnrollmentOfferWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionEntryWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionOverviewWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionPaymentMethodChangeResultWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionPaymentMethodOptionWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionSectionWsDTO;

/**
 * Maps what the {@code MySubscriptionsFacade} returns onto the OCC DTOs, by hand rather than through an
 * Orika {@code DataMapper}: the derived flags ({@code cancellable}, {@code tone},
 * {@code paymentMethodChangeOffered}) are getters with no backing field, which Orika would silently skip
 * unless every WsDTO declared a matching setter. Writing them out keeps the rule in one visible place.
 *
 * <p>Every step is a {@code protected} instance method so a merchant can override one of them.</p>
 */
public class AdyenMySubscriptionsWsMapper
{
	public AdyenSubscriptionOverviewWsDTO toOverview(final SubscriptionOverviewData source)
	{
		final AdyenSubscriptionOverviewWsDTO target = new AdyenSubscriptionOverviewWsDTO();
		if (source != null)
		{
			target.setSections(mapAll(source.getSections(), this::toSection));
		}
		return target;
	}

	protected AdyenSubscriptionSectionWsDTO toSection(final SubscriptionPlatformSectionData source)
	{
		final AdyenSubscriptionSectionWsDTO target = new AdyenSubscriptionSectionWsDTO();
		target.setPlatformCode(source.getPlatformCode());
		target.setPlatformName(source.getPlatformName());
		target.setSubscriptions(mapAll(source.getSubscriptions(), this::toEntry));
		target.setOrdersAwaitingSetup(new ArrayList<>(source.getOrdersAwaitingSetup()));
		target.setPaymentMethodChangeOffers(mapAll(source.getPaymentMethodChangeOffers(), this::toChangeOffer));
		target.setPaymentMethodEnrollmentOffers(
				mapAll(source.getPaymentMethodEnrollmentOffers(), this::toEnrollmentOffer));
		target.setPaymentMethodChangeSupported(source.isPaymentMethodChangeSupported());
		target.setPaymentMethodChangeOffered(source.isPaymentMethodChangeOffered());
		target.setRowPaymentMethodControlOffered(source.isRowPaymentMethodControlOffered());
		return target;
	}

	protected AdyenSubscriptionEntryWsDTO toEntry(final SubscriptionEntryData source)
	{
		final AdyenSubscriptionEntryWsDTO target = new AdyenSubscriptionEntryWsDTO();
		target.setCode(source.getCode());
		target.setProductName(source.getProductName());
		if (source.getState() != null)
		{
			target.setState(source.getState().name());
			target.setTone(source.getState().getTone());
		}
		target.setEffectiveDate(toIso(source.getEffectiveDate()));
		target.setQuantity(source.getQuantity());
		target.setOrderCode(source.getOrderCode());
		target.setOrderDate(toIso(source.getOrderDate()));
		target.setPaymentMethodSummary(source.getPaymentMethodSummary());
		target.setManageable(source.isManageable());
		target.setCancellable(source.isCancellable());
		target.setPaymentMethodChangeable(source.isPaymentMethodChangeable());
		target.setPaymentMethodControl(source.isPaymentMethodControl());
		target.setPaymentMethodShareable(source.isPaymentMethodShareable());
		target.setPaymentMethodChangeCovered(source.isPaymentMethodChangeCovered());
		target.setPaymentMethodOptions(mapAll(source.getPaymentMethodOptions(), this::toOption));
		target.setAdyenVaultOptions(mapAll(source.getAdyenVaultOptions(), this::toOption));
		return target;
	}

	protected AdyenSubscriptionChangeOfferWsDTO toChangeOffer(final PaymentMethodChangeOfferData source)
	{
		final AdyenSubscriptionChangeOfferWsDTO target = new AdyenSubscriptionChangeOfferWsDTO();
		target.setSubscriptionCode(source.getSubscriptionCode());
		target.setAccountName(source.getAccountName());
		target.setPaymentMethodOptions(mapAll(source.getPaymentMethodOptions(), this::toOption));
		target.setAdyenVaultOptions(mapAll(source.getAdyenVaultOptions(), this::toOption));
		return target;
	}

	protected AdyenSubscriptionEnrollmentOfferWsDTO toEnrollmentOffer(final PaymentMethodEnrollmentOfferData source)
	{
		final AdyenSubscriptionEnrollmentOfferWsDTO target = new AdyenSubscriptionEnrollmentOfferWsDTO();
		target.setSubscriptionCode(source.getSubscriptionCode());
		target.setAccountName(source.getAccountName());
		target.setNotice(source.getNotice() == null ? null : source.getNotice().name());
		return target;
	}

	protected AdyenSubscriptionPaymentMethodOptionWsDTO toOption(final PaymentMethodOptionData source)
	{
		final AdyenSubscriptionPaymentMethodOptionWsDTO target = new AdyenSubscriptionPaymentMethodOptionWsDTO();
		target.setId(source.getId());
		target.setDisplayLabel(source.getDisplayLabel());
		target.setCurrent(source.isCurrent());
		return target;
	}

	public AdyenSubscriptionPaymentMethodChangeResultWsDTO toChangeResult(final PaymentMethodChangeReport source)
	{
		final AdyenSubscriptionPaymentMethodChangeResultWsDTO target = new AdyenSubscriptionPaymentMethodChangeResultWsDTO();
		target.setResult(source.result() == null ? null : source.result().name());
		target.setMoved(source.moved());
		target.setFailed(source.failed());
		target.setPartial(source.isPartial());
		return target;
	}

	/** ISO-8601 in UTC with a trailing {@code Z}, or {@code null} for no date. */
	protected String toIso(final Date date)
	{
		return date == null ? null : date.toInstant().toString();
	}

	protected <S, T> List<T> mapAll(final List<S> sources, final java.util.function.Function<S, T> mapping)
	{
		final List<T> targets = new ArrayList<>();
		if (sources != null)
		{
			for (final S source : sources)
			{
				if (source != null)
				{
					targets.add(mapping.apply(source));
				}
			}
		}
		return targets;
	}
}
