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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.Test;

import com.adyen.commerce.connector.facades.data.PaymentMethodChangeOfferData;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeReport;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeResult;
import com.adyen.commerce.connector.facades.data.PaymentMethodEnrollmentNotice;
import com.adyen.commerce.connector.facades.data.PaymentMethodEnrollmentOfferData;
import com.adyen.commerce.connector.facades.data.PaymentMethodOptionData;
import com.adyen.commerce.connector.facades.data.SubscriptionDisplayState;
import com.adyen.commerce.connector.facades.data.SubscriptionEntryData;
import com.adyen.commerce.connector.facades.data.SubscriptionOverviewData;
import com.adyen.commerce.connector.facades.data.SubscriptionPlatformSectionData;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionEntryWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionOverviewWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionPaymentMethodChangeResultWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionSectionWsDTO;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * Unit test for {@link AdyenMySubscriptionsWsMapper}. The derived flags are getters with no field behind
 * them ({@code cancellable}, {@code tone}, {@code paymentMethodChangeOffered}), the case a bean mapper
 * drops without a word, so each is pinned here against the rule it comes from.
 */
@UnitTest
public class AdyenMySubscriptionsWsMapperTest
{
	private final AdyenMySubscriptionsWsMapper mapper = new AdyenMySubscriptionsWsMapper();

	@Test
	public void mapsEveryFieldOfAnEntryAndDerivesTheFlags()
	{
		final Instant effective = Instant.parse("2026-11-01T10:15:30Z");
		final Instant ordered = Instant.parse("2026-10-01T08:00:00Z");
		final SubscriptionEntryData entry = entry("sub-1", SubscriptionDisplayState.PAST_DUE);
		entry.setEffectiveDate(Date.from(effective));
		entry.setOrderDate(Date.from(ordered));
		entry.setQuantity(3);
		entry.setOrderCode("00001");
		entry.setPaymentMethodSummary("Visa ending 1111");
		entry.setManageable(true);
		entry.setPaymentMethodChangeable(true);
		entry.setPaymentMethodControl(true);
		entry.setPaymentMethodShareable(true);
		entry.setPaymentMethodChangeCovered(true);
		entry.setPaymentMethodOptions(List.of(new PaymentMethodOptionData("pm-1", "Visa 1111", true)));
		entry.setAdyenVaultOptions(List.of(new PaymentMethodOptionData("v-1", "Mastercard 4444", false)));

		final AdyenSubscriptionEntryWsDTO dto = mapper.toEntry(entry);

		assertEquals("sub-1", dto.getCode());
		assertEquals("Product sub-1", dto.getProductName());
		assertEquals("PAST_DUE", dto.getState());
		assertEquals("attention", dto.getTone());
		assertEquals("2026-11-01T10:15:30Z", dto.getEffectiveDate());
		assertEquals("2026-10-01T08:00:00Z", dto.getOrderDate());
		assertEquals(Integer.valueOf(3), dto.getQuantity());
		assertEquals("00001", dto.getOrderCode());
		assertEquals("Visa ending 1111", dto.getPaymentMethodSummary());
		assertTrue(dto.isManageable());
		assertTrue(dto.isCancellable());
		assertTrue(dto.isPaymentMethodChangeable());
		assertTrue(dto.isPaymentMethodControl());
		assertTrue(dto.isPaymentMethodShareable());
		assertTrue(dto.isPaymentMethodChangeCovered());
		assertEquals("pm-1", dto.getPaymentMethodOptions().get(0).getId());
		assertEquals("Visa 1111", dto.getPaymentMethodOptions().get(0).getDisplayLabel());
		assertTrue(dto.getPaymentMethodOptions().get(0).isCurrent());
		assertEquals("v-1", dto.getAdyenVaultOptions().get(0).getId());
		assertFalse(dto.getAdyenVaultOptions().get(0).isCurrent());
	}

	@Test
	public void cancellableFollowsTheEntryRuleNotTheState()
	{
		final SubscriptionEntryData notManageable = entry("a", SubscriptionDisplayState.ACTIVE);
		notManageable.setManageable(false);
		final SubscriptionEntryData ended = entry("b", SubscriptionDisplayState.ENDED);
		ended.setManageable(true);
		final SubscriptionEntryData active = entry("c", SubscriptionDisplayState.ACTIVE);
		active.setManageable(true);

		assertFalse(mapper.toEntry(notManageable).isCancellable());
		assertFalse(mapper.toEntry(ended).isCancellable());
		assertTrue(mapper.toEntry(active).isCancellable());
	}

	@Test
	public void toneIsTakenFromTheStateForEveryState()
	{
		for (final SubscriptionDisplayState state : SubscriptionDisplayState.values())
		{
			final AdyenSubscriptionEntryWsDTO dto = mapper.toEntry(entry("x", state));
			assertEquals(state.name(), dto.getState());
			assertEquals(state.getTone(), dto.getTone());
		}
	}

	@Test
	public void absentDatesAndStateStayNull()
	{
		final SubscriptionEntryData entry = new SubscriptionEntryData();
		entry.setCode("sub-1");

		final AdyenSubscriptionEntryWsDTO dto = mapper.toEntry(entry);

		assertNull(dto.getEffectiveDate());
		assertNull(dto.getOrderDate());
		assertNull(dto.getState());
		assertNull(dto.getTone());
		assertNull(dto.getQuantity());
		assertFalse(dto.isCancellable());
	}

	@Test
	public void sectionDerivesPaymentMethodChangeOfferedFromOffersAndRowControl()
	{
		final SubscriptionPlatformSectionData none = section();
		final SubscriptionPlatformSectionData rowControl = section();
		rowControl.setRowPaymentMethodControlOffered(true);
		final SubscriptionPlatformSectionData accountWide = section();
		accountWide.setPaymentMethodChangeOffers(List.of(new PaymentMethodChangeOfferData()));

		assertFalse(mapper.toSection(none).isPaymentMethodChangeOffered());
		assertTrue(mapper.toSection(rowControl).isPaymentMethodChangeOffered());
		assertTrue(mapper.toSection(rowControl).isRowPaymentMethodControlOffered());
		assertTrue(mapper.toSection(accountWide).isPaymentMethodChangeOffered());
		assertFalse(mapper.toSection(accountWide).isRowPaymentMethodControlOffered());
	}

	@Test
	public void sectionCarriesOffersOrdersAndSupportFlag()
	{
		final SubscriptionPlatformSectionData section = section();
		section.setPaymentMethodChangeSupported(true);
		section.setOrdersAwaitingSetup(List.of("00007"));
		final PaymentMethodChangeOfferData change = new PaymentMethodChangeOfferData();
		change.setSubscriptionCode("sub-1");
		change.setAccountName("Store A");
		change.setPaymentMethodOptions(List.of(new PaymentMethodOptionData("pm-1", "Visa", true)));
		change.setAdyenVaultOptions(List.of(new PaymentMethodOptionData("v-1", "Amex", false)));
		section.setPaymentMethodChangeOffers(List.of(change));
		final PaymentMethodEnrollmentOfferData enrollment = new PaymentMethodEnrollmentOfferData();
		enrollment.setSubscriptionCode("sub-1");
		enrollment.setAccountName("Store A");
		enrollment.setNotice(PaymentMethodEnrollmentNotice.REPLACES_METHOD_ON_FILE);
		section.setPaymentMethodEnrollmentOffers(List.of(enrollment));
		section.setSubscriptions(List.of(entry("sub-1", SubscriptionDisplayState.ACTIVE)));

		final SubscriptionOverviewData overview = new SubscriptionOverviewData();
		overview.setSections(List.of(section));
		final AdyenSubscriptionOverviewWsDTO dto = mapper.toOverview(overview);
		final AdyenSubscriptionSectionWsDTO mapped = dto.getSections().get(0);

		assertEquals("RECURLY", mapped.getPlatformCode());
		assertEquals("Recurly", mapped.getPlatformName());
		assertTrue(mapped.isPaymentMethodChangeSupported());
		assertEquals(List.of("00007"), mapped.getOrdersAwaitingSetup());
		assertEquals(1, mapped.getSubscriptions().size());
		assertEquals("sub-1", mapped.getPaymentMethodChangeOffers().get(0).getSubscriptionCode());
		assertEquals("Store A", mapped.getPaymentMethodChangeOffers().get(0).getAccountName());
		assertEquals("pm-1", mapped.getPaymentMethodChangeOffers().get(0).getPaymentMethodOptions().get(0).getId());
		assertEquals("v-1", mapped.getPaymentMethodChangeOffers().get(0).getAdyenVaultOptions().get(0).getId());
		assertEquals("REPLACES_METHOD_ON_FILE", mapped.getPaymentMethodEnrollmentOffers().get(0).getNotice());
		assertEquals("Store A", mapped.getPaymentMethodEnrollmentOffers().get(0).getAccountName());
	}

	@Test
	public void nullOverviewMapsToAnEmptyOne()
	{
		assertTrue(mapper.toOverview(null).getSections().isEmpty());
		assertTrue(mapper.toOverview(new SubscriptionOverviewData()).getSections().isEmpty());
	}

	@Test
	public void changeReportMapsResultCountsAndPartial()
	{
		final AdyenSubscriptionPaymentMethodChangeResultWsDTO partial = mapper
				.toChangeResult(new PaymentMethodChangeReport(PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS, 2, 1));
		final AdyenSubscriptionPaymentMethodChangeResultWsDTO plain = mapper
				.toChangeResult(PaymentMethodChangeReport.of(PaymentMethodChangeResult.NOT_SUPPORTED_HERE));

		assertEquals("CHANGED_ALL_SUBSCRIPTIONS", partial.getResult());
		assertEquals(2, partial.getMoved());
		assertEquals(1, partial.getFailed());
		assertTrue(partial.isPartial());
		assertEquals("NOT_SUPPORTED_HERE", plain.getResult());
		assertEquals(0, plain.getMoved());
		assertFalse(plain.isPartial());
	}

	@Test
	public void noDtoExposesAMapOrAPlanCode()
	{
		// MOXy silently nulls Map fields, and the plan code is the platform's price identifier.
		for (final Class<?> type : List.of(AdyenSubscriptionOverviewWsDTO.class, AdyenSubscriptionSectionWsDTO.class,
				AdyenSubscriptionEntryWsDTO.class))
		{
			for (final java.lang.reflect.Field field : type.getDeclaredFields())
			{
				assertFalse(type.getSimpleName() + "." + field.getName(),
						java.util.Map.class.isAssignableFrom(field.getType()));
				assertFalse(field.getName(), field.getName().toLowerCase().contains("plan"));
			}
		}
	}

	private static SubscriptionEntryData entry(final String code, final SubscriptionDisplayState state)
	{
		final SubscriptionEntryData entry = new SubscriptionEntryData();
		entry.setCode(code);
		entry.setProductName("Product " + code);
		entry.setState(state);
		return entry;
	}

	private static SubscriptionPlatformSectionData section()
	{
		final SubscriptionPlatformSectionData section = new SubscriptionPlatformSectionData();
		section.setPlatformCode("RECURLY");
		section.setPlatformName("Recurly");
		return section;
	}
}
