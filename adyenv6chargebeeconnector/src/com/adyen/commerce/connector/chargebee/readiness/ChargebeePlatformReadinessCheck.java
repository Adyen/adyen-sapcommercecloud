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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.chargebee.model.ChargebeeConfigModel;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.model.AdyenSubscriptionConfigModel;
import com.adyen.commerce.connector.readiness.BillingPlatformReadinessCheck;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;

import de.hybris.platform.store.BaseStoreModel;

/**
 * Tells whether a base store holds everything the Chargebee connector reads from its {@code chargebeeConfig}.
 * Judged from the stored values alone: whether Chargebee accepts the credentials only shows on the first call.
 */
public class ChargebeePlatformReadinessCheck implements BillingPlatformReadinessCheck
{
	private static final String CHARGEBEE_CONFIGURATION = "Chargebee configuration";
	private static final String MERCHANT_ACCOUNT_MISMATCH = AdyenSubscriptionConfigModel.ADYENGATEWAYMERCHANTACCOUNT
			+ " does not match the store's Adyen merchant account";

	private AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;

	@Override
	public BillingPlatform platform()
	{
		return BillingPlatform.CHARGEBEE;
	}

	@Override
	public List<String> missingSettings(final BaseStoreModel store)
	{
		final List<String> missing = new ArrayList<>();
		final ChargebeeConfigModel config = store.getChargebeeConfig();
		if (config == null)
		{
			missing.add(CHARGEBEE_CONFIGURATION);
			return missing;
		}

		requiredSettings(config).forEach((name, value) -> {
			if (StringUtils.isBlank(value))
			{
				missing.add(name);
			}
		});

		// A blank gateway account is already reported above; comparing it would only repeat that.
		if (StringUtils.isNotBlank(config.getAdyenGatewayMerchantAccount())
				&& !isBoundToStoreMerchantAccount(config, store))
		{
			missing.add(MERCHANT_ACCOUNT_MISMATCH);
		}
		return missing;
	}

	/**
	 * Settings the connector cannot work without, keyed by attribute qualifier, in create-wizard order.
	 */
	protected Map<String, String> requiredSettings(final ChargebeeConfigModel config)
	{
		final Map<String, String> settings = new LinkedHashMap<>();
		settings.put(AdyenSubscriptionConfigModel.SUBSCRIPTIONSITEID, config.getSubscriptionSiteId());
		settings.put(AdyenSubscriptionConfigModel.SUBSCRIPTIONAPIKEY, config.getSubscriptionApiKey());
		settings.put(AdyenSubscriptionConfigModel.SUBSCRIPTIONGATEWAYACCOUNTID, config.getSubscriptionGatewayAccountId());
		settings.put(AdyenSubscriptionConfigModel.ADYENGATEWAYMERCHANTACCOUNT, config.getAdyenGatewayMerchantAccount());
		settings.put(ChargebeeConfigModel.CHARGEBEEWEBHOOKUSERNAME, config.getChargebeeWebhookUsername());
		settings.put(ChargebeeConfigModel.CHARGEBEEWEBHOOKPASSWORD, config.getChargebeeWebhookPassword());
		return settings;
	}

	/**
	 * Compares the way the connector's merchant-account guard does: the configured account trimmed, the store's
	 * as the merchant account strategy returns it.
	 */
	protected boolean isBoundToStoreMerchantAccount(final ChargebeeConfigModel config, final BaseStoreModel store)
	{
		return StringUtils.equals(StringUtils.trimToNull(config.getAdyenGatewayMerchantAccount()),
				adyenMerchantAccountStrategy.getWebMerchantAccount(store));
	}

	public void setAdyenMerchantAccountStrategy(final AdyenMerchantAccountStrategy adyenMerchantAccountStrategy)
	{
		this.adyenMerchantAccountStrategy = adyenMerchantAccountStrategy;
	}
}
