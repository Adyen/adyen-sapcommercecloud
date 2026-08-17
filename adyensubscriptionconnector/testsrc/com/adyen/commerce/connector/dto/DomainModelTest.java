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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.adyen.commerce.connector.enums.BillingPlatform;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * Unit test for the vendor-neutral domain model invariants and immutability: the types are
 * unit-constructible, enforce their required fields, and defensively copy mutable inputs.
 */
@UnitTest
public class DomainModelTest
{
	@Test
	public void adyenTokenHandleAcceptsValidContract()
	{
		final AdyenTokenHandle handle = new AdyenTokenHandle("MERCH", "shopper", "TOKEN", null, null);
		assertFalse(handle.hasNetworkTransactionId());
	}

	@Test
	public void adyenTokenHandleRejectsBlankToken()
	{
		assertThrows(IllegalArgumentException.class, () -> new AdyenTokenHandle("MERCH", "shopper", "  ", null, null));
	}

	@Test
	public void referenceRejectsNullPlatform()
	{
		assertThrows(IllegalArgumentException.class, () -> new BillingCustomerRef(null, "ext"));
	}

	@Test
	public void referenceRejectsBlankExternalId()
	{
		assertThrows(IllegalArgumentException.class, () -> new BillingSubscriptionRef(BillingPlatform.CHARGEBEE, ""));
	}

	@Test
	public void billingCycleRejectsNonPositiveCount()
	{
		assertThrows(IllegalArgumentException.class, () -> new BillingCycle(BillingInterval.MONTH, 0));
	}

	@Test
	public void mapsAreDefensivelyCopiedAndImmutable()
	{
		final Map<String, String> source = new HashMap<>();
		source.put("a", "1");
		final CustomerSyncRequest request = new CustomerSyncRequest("cust", "e@x.com", "First", "Last", source);

		source.put("b", "2"); // mutating the source after construction must not leak in
		assertEquals(1, request.metadata().size());
		assertThrows(UnsupportedOperationException.class, () -> request.metadata().put("c", "3"));
	}

	@Test
	public void nullMapBecomesEmpty()
	{
		final PlanResolutionRequest request = new PlanResolutionRequest("PROD-1", null);
		assertTrue(request.context().isEmpty());
	}
}
