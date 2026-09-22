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
package com.adyen.commerce.connector.chargebee.plan.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.chargebee.model.ChargebeePlanMappingModel;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.PlanResolutionRequest;
import com.adyen.commerce.connector.exception.PlanNotMappedException;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;

/**
 * Unit test for {@link DefaultChargebeePlanResolver} against a mocked FlexibleSearch.
 */
@UnitTest
public class DefaultChargebeePlanResolverTest
{
	@Mock
	private FlexibleSearchService flexibleSearchService;
	@Mock
	private SearchResult<ChargebeePlanMappingModel> searchResult;
	@Mock
	private ChargebeePlanMappingModel mapping;

	private DefaultChargebeePlanResolver resolver;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		resolver = new DefaultChargebeePlanResolver();
		resolver.setFlexibleSearchService(flexibleSearchService);
		when(flexibleSearchService.<ChargebeePlanMappingModel> search(any(FlexibleSearchQuery.class)))
				.thenReturn(searchResult);
	}

	@Test
	public void resolvesMappedProductCode() throws Exception
	{
		when(searchResult.getResult()).thenReturn(List.of(mapping));
		when(mapping.getItemPriceId()).thenReturn("price-1");
		when(mapping.getPriceId()).thenReturn(null);

		final PlanRef ref = resolver.resolve(new PlanResolutionRequest("PROD-1", Map.of()));

		assertEquals("price-1", ref.planId());
		assertNull(ref.priceId());
	}

	@Test
	public void throwsWhenProductCodeNotMapped()
	{
		when(searchResult.getResult()).thenReturn(List.of());

		assertThrows(PlanNotMappedException.class, () -> resolver.resolve(new PlanResolutionRequest("UNKNOWN", Map.of())));
	}
}
