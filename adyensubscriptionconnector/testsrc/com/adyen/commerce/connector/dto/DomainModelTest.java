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
 * Unit test for the domain model's invariants: the types enforce their required fields and defensively
 * copy mutable inputs.
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
		final PlanResolutionRequest request = new PlanResolutionRequest("PROD-1", "electronics", null);
		assertTrue(request.context().isEmpty());
	}

	@Test
	public void planResolutionNeedsABaseStore()
	{
		assertThrows(IllegalArgumentException.class, () -> new PlanResolutionRequest("PROD-1", " ", null));
	}

	/**
	 * The shopper is redirected to this unmodified, and on some platforms it carries the credential that
	 * opens their account, so anything that is not an absolute https address is refused here rather than
	 * where it would become a redirect.
	 */
	@Test
	public void enrollmentPageRejectsAnythingButAnAbsoluteHttpsAddress()
	{
		assertEquals("https://mystore.recurly.com/account/abc",
				new PaymentMethodEnrollmentPage("https://mystore.recurly.com/account/abc").url());
		for (final String url : new String[] { "http://mystore.recurly.com/account/abc",
				"//mystore.recurly.com/account/abc", "/my-account/cards", "javascript:alert(1)", " " })
		{
			assertThrows("accepted '" + url + "'", IllegalArgumentException.class,
					() -> new PaymentMethodEnrollmentPage(url));
		}
	}

	/** Offered and "what arriving does" are the same fact, so the page cannot invite without warning. */
	@Test
	public void enrollmentSupportIsOfferedOnlyWhenItSaysWhatArrivingDoes()
	{
		assertFalse(PaymentMethodEnrollmentSupport.NONE.isOffered());
		assertTrue(new PaymentMethodEnrollmentSupport(PaymentMethodEnrollmentEffect.ADDS_METHOD).isOffered());
	}
}
