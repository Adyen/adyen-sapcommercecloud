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
package com.adyen.commerce.connector.chargebee.readiness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.chargebee.model.ChargebeeConfigModel;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Unit test for {@link ChargebeePlatformReadinessCheck}: which settings a store lacks before it may switch to
 * Chargebee, judged from the stored configuration only.
 */
@UnitTest
public class ChargebeePlatformReadinessCheckTest
{
	private static final String STORE_MERCHANT_ACCOUNT = "AdyenStoreECOM";
	private static final String MISMATCH = "adyenGatewayMerchantAccount does not match the store's Adyen merchant account";

	@Mock
	private BaseStoreModel store;
	@Mock
	private ChargebeeConfigModel config;
	@Mock
	private AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;

	private ChargebeePlatformReadinessCheck check;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		when(store.getChargebeeConfig()).thenReturn(config);
		when(adyenMerchantAccountStrategy.getWebMerchantAccount(store)).thenReturn(STORE_MERCHANT_ACCOUNT);
		when(config.getSubscriptionSiteId()).thenReturn("acme");
		when(config.getSubscriptionApiKey()).thenReturn("cb-key");
		when(config.getSubscriptionGatewayAccountId()).thenReturn("gw_adyen");
		when(config.getAdyenGatewayMerchantAccount()).thenReturn(STORE_MERCHANT_ACCOUNT);
		when(config.getChargebeeWebhookUsername()).thenReturn("cb-user");
		when(config.getChargebeeWebhookPassword()).thenReturn("cb-pass");

		check = new ChargebeePlatformReadinessCheck();
		check.setAdyenMerchantAccountStrategy(adyenMerchantAccountStrategy);
	}

	@Test
	public void platformIsChargebee()
	{
		assertEquals(BillingPlatform.CHARGEBEE, check.platform());
	}

	@Test
	public void completeConfigurationBoundToTheStoreAccountIsReady()
	{
		assertTrue(check.missingSettings(store).isEmpty());
	}

	@Test
	public void missingConfigurationIsReportedAlone()
	{
		when(store.getChargebeeConfig()).thenReturn(null);

		assertEquals(List.of("Chargebee configuration"), check.missingSettings(store));
		verify(adyenMerchantAccountStrategy, never()).getWebMerchantAccount(any());
	}

	@Test
	public void everyBlankRequiredSettingIsReportedByName()
	{
		when(config.getSubscriptionSiteId()).thenReturn(null);
		when(config.getSubscriptionApiKey()).thenReturn("");
		when(config.getSubscriptionGatewayAccountId()).thenReturn("  ");
		when(config.getAdyenGatewayMerchantAccount()).thenReturn(null);
		when(config.getChargebeeWebhookUsername()).thenReturn("\t");
		when(config.getChargebeeWebhookPassword()).thenReturn(null);

		assertEquals(List.of("subscriptionSiteId", "subscriptionApiKey", "subscriptionGatewayAccountId",
				"adyenGatewayMerchantAccount", "chargebeeWebhookUsername", "chargebeeWebhookPassword"),
				check.missingSettings(store));
	}

	/**
	 * The webhook credentials are required too: without them every Chargebee delivery is refused.
	 */
	@Test
	public void missingWebhookCredentialsAloneMakeTheStoreUnready()
	{
		when(config.getChargebeeWebhookPassword()).thenReturn(null);

		assertEquals(List.of("chargebeeWebhookPassword"), check.missingSettings(store));
	}

	@Test
	public void gatewayAccountOfAnotherMerchantAccountIsReported()
	{
		when(config.getAdyenGatewayMerchantAccount()).thenReturn("AdyenOtherECOM");

		assertEquals(List.of(MISMATCH), check.missingSettings(store));
	}

	@Test
	public void storeWithoutAdyenMerchantAccountIsReportedAsMismatch()
	{
		when(adyenMerchantAccountStrategy.getWebMerchantAccount(store)).thenReturn(null);

		assertEquals(List.of(MISMATCH), check.missingSettings(store));
	}

	/**
	 * A blank gateway account is reported once, as missing, and not a second time as a mismatch.
	 */
	@Test
	public void blankGatewayAccountIsReportedOnlyAsMissing()
	{
		when(config.getAdyenGatewayMerchantAccount()).thenReturn("  ");

		assertEquals(List.of("adyenGatewayMerchantAccount"), check.missingSettings(store));
	}

	/**
	 * The connector trims the configured account before its own guard compares it, so surrounding whitespace
	 * must not block a switch the connector would accept.
	 */
	@Test
	public void gatewayAccountIsComparedTrimmed()
	{
		when(config.getAdyenGatewayMerchantAccount()).thenReturn("  " + STORE_MERCHANT_ACCOUNT + " ");

		assertTrue(check.missingSettings(store).isEmpty());
	}
}
