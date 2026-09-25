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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.PlanNotMappedException;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Unit test for {@link DefaultChargebeePlanResolver} against a mocked FlexibleSearch.
 */
@UnitTest
public class DefaultChargebeePlanResolverTest
{
	private static final String STORE = "electronics";

	@Mock
	private FlexibleSearchService flexibleSearchService;
	@Mock
	private SearchResult<ChargebeePlanMappingModel> searchResult;

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
		givenMappings(mapping(null, "price-1"));

		final PlanRef ref = resolver.resolve(request("PROD-1"));

		assertEquals("price-1", ref.planId());
		assertNull(ref.priceId());
	}

	@Test
	public void theStoresOwnMappingWinsOverTheDefault() throws Exception
	{
		givenMappings(mapping(null, "default-price"), mapping(STORE, "store-price"));

		assertEquals("store-price", resolver.resolve(request("PROD-1")).planId());
	}

	@Test
	public void fallsBackToTheMappingWithoutAStore() throws Exception
	{
		givenMappings(mapping("apparel", "other-price"), mapping(null, "default-price"));

		assertEquals("default-price", resolver.resolve(request("PROD-1")).planId());
	}

	@Test
	public void anotherStoresMappingDoesNotApply()
	{
		givenMappings(mapping("apparel", "other-price"));

		final PlanNotMappedException error = assertThrows(PlanNotMappedException.class,
				() -> resolver.resolve(request("PROD-1")));
		assertTrue(error.getMessage().contains(STORE));
	}

	@Test
	public void throwsWhenProductCodeNotMapped()
	{
		when(searchResult.getResult()).thenReturn(List.of());

		assertThrows(PlanNotMappedException.class, () -> resolver.resolve(request("UNKNOWN")));
	}

	@Test
	public void refusesTwoDefaultsForOneProduct()
	{
		givenMappings(mapping(null, "price-a"), mapping(null, "price-b"));

		assertThrows(ConnectorNotConfiguredException.class, () -> resolver.resolve(request("PROD-1")));
	}

	/** Built before stubbing: creating the mocks inside {@code thenReturn} would nest stubbings. */
	private void givenMappings(final ChargebeePlanMappingModel... mappings)
	{
		when(searchResult.getResult()).thenReturn(List.of(mappings));
	}

	private static PlanResolutionRequest request(final String productCode)
	{
		return new PlanResolutionRequest(productCode, STORE, Map.of());
	}

	private static ChargebeePlanMappingModel mapping(final String storeUid, final String itemPriceId)
	{
		final ChargebeePlanMappingModel mapping = mock(ChargebeePlanMappingModel.class);
		if (storeUid != null)
		{
			final BaseStoreModel store = mock(BaseStoreModel.class);
			when(store.getUid()).thenReturn(storeUid);
			when(mapping.getBaseStore()).thenReturn(store);
		}
		when(mapping.getItemPriceId()).thenReturn(itemPriceId);
		return mapping;
	}
}
