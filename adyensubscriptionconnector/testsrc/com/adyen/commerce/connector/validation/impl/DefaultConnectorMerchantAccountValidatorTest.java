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
package com.adyen.commerce.connector.validation.impl;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Unit test for {@link DefaultConnectorMerchantAccountValidator} — enforces connector ≡ store Adyen
 * merchant account.
 */
@UnitTest
public class DefaultConnectorMerchantAccountValidatorTest
{
	@Mock
	private AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;
	@Mock
	private SubscriptionBillingConnector connector;
	@Mock
	private BaseStoreModel store;

	private DefaultConnectorMerchantAccountValidator validator;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		validator = new DefaultConnectorMerchantAccountValidator();
		validator.setAdyenMerchantAccountStrategy(adyenMerchantAccountStrategy);
	}

	@Test
	public void shouldPassWhenAccountsMatch() throws Exception
	{
		when(connector.configuredAdyenMerchantAccount()).thenReturn("MERCH");
		when(adyenMerchantAccountStrategy.getWebMerchantAccount(store)).thenReturn("MERCH");

		validator.validate(connector, store);
	}

	@Test
	public void shouldFailWhenAccountsMismatch()
	{
		when(connector.configuredAdyenMerchantAccount()).thenReturn("MERCH-A");
		when(connector.platform()).thenReturn(BillingPlatform.ZUORA);
		when(adyenMerchantAccountStrategy.getWebMerchantAccount(store)).thenReturn("MERCH-B");
		when(store.getUid()).thenReturn("electronics");

		assertThrows(PreconditionFailedException.class, () -> validator.validate(connector, store));
	}

	/**
	 * ADYEN_NATIVE has no external gateway, so there is nothing to bind and nothing to compare.
	 */
	@Test
	public void shouldSkipForTheAdyenNativeConnector() throws Exception
	{
		when(connector.configuredAdyenMerchantAccount()).thenReturn(null);
		when(connector.platform()).thenReturn(BillingPlatform.ADYEN_NATIVE);

		validator.validate(connector, store);

		verify(adyenMerchantAccountStrategy, never()).getWebMerchantAccount(store);
	}

	/**
	 * The regression this guards: an external connector answering "not configured" used to be treated the
	 * same as ADYEN_NATIVE's "not applicable", which silently disabled the check — and did so before
	 * activateSubscription had created the customer on the remote platform.
	 */
	@Test
	public void shouldFailWhenAnExternalConnectorHasNoConfiguredAccount()
	{
		when(connector.configuredAdyenMerchantAccount()).thenReturn(null);
		when(connector.platform()).thenReturn(BillingPlatform.CHARGEBEE);
		when(store.getUid()).thenReturn("electronics");

		assertThrows(PreconditionFailedException.class, () -> validator.validate(connector, store));
		verify(adyenMerchantAccountStrategy, never()).getWebMerchantAccount(store);
	}

	@Test
	public void shouldTreatABlankConfiguredAccountAsUnset()
	{
		when(connector.configuredAdyenMerchantAccount()).thenReturn("   ");
		when(connector.platform()).thenReturn(BillingPlatform.RECURLY);
		when(store.getUid()).thenReturn("electronics");

		assertThrows(PreconditionFailedException.class, () -> validator.validate(connector, store));
	}
}
