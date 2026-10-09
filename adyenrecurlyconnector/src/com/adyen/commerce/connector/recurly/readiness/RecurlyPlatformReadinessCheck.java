package com.adyen.commerce.connector.recurly.readiness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.model.AdyenSubscriptionConfigModel;
import com.adyen.commerce.connector.readiness.BillingPlatformReadinessCheck;
import com.adyen.commerce.connector.recurly.model.RecurlyConfigModel;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;

import de.hybris.platform.store.BaseStoreModel;

/**
 * Checks a store's Recurly configuration before Recurly becomes its active platform: the settings every Recurly
 * call and webhook needs are present, and the Recurly gateway is bound to the store's own Adyen merchant account.
 * Reads the store only; nothing is sent to Recurly.
 */
public class RecurlyPlatformReadinessCheck implements BillingPlatformReadinessCheck {
    private static final String RECURLY_CONFIGURATION = "Recurly configuration";
    private static final String MERCHANT_ACCOUNT_MISMATCH =
            AdyenSubscriptionConfigModel.ADYENGATEWAYMERCHANTACCOUNT
                    + " does not match the store's Adyen merchant account";

    private AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;

    @Override
    public BillingPlatform platform() {
        return BillingPlatform.RECURLY;
    }

    @Override
    public List<String> missingSettings(final BaseStoreModel store) {
        final RecurlyConfigModel config = store.getRecurlyConfig();
        if (config == null) {
            return List.of(RECURLY_CONFIGURATION);
        }

        final List<String> missing = new ArrayList<>();
        requiredSettings(config).forEach((name, value) -> {
            if (StringUtils.isBlank(value)) {
                missing.add(name);
            }
        });
        if (!isGatewayBoundToStore(config, store)) {
            missing.add(MERCHANT_ACCOUNT_MISMATCH);
        }
        return missing;
    }

    /** Settings the connector cannot work without, keyed by attribute qualifier, in create-wizard order. */
    protected Map<String, String> requiredSettings(final RecurlyConfigModel config) {
        final Map<String, String> settings = new LinkedHashMap<>();
        settings.put(AdyenSubscriptionConfigModel.SUBSCRIPTIONAPIKEY, config.getSubscriptionApiKey());
        settings.put(AdyenSubscriptionConfigModel.SUBSCRIPTIONSITEID, config.getSubscriptionSiteId());
        settings.put(AdyenSubscriptionConfigModel.SUBSCRIPTIONGATEWAYACCOUNTID,
                config.getSubscriptionGatewayAccountId());
        settings.put(AdyenSubscriptionConfigModel.ADYENGATEWAYMERCHANTACCOUNT,
                config.getAdyenGatewayMerchantAccount());
        settings.put(RecurlyConfigModel.RECURLYWEBHOOKSIGNINGKEY, config.getRecurlyWebhookSigningKey());
        return settings;
    }

    /**
     * A blank gateway account is already reported as missing, so it is not reported twice. The comparison
     * matches the one the connector makes before importing a token.
     */
    protected boolean isGatewayBoundToStore(final RecurlyConfigModel config, final BaseStoreModel store) {
        final String gatewayAccount = StringUtils.trimToNull(config.getAdyenGatewayMerchantAccount());
        return gatewayAccount == null
                || gatewayAccount.equals(adyenMerchantAccountStrategy.getWebMerchantAccount(store));
    }

    public void setAdyenMerchantAccountStrategy(final AdyenMerchantAccountStrategy adyenMerchantAccountStrategy) {
        this.adyenMerchantAccountStrategy = adyenMerchantAccountStrategy;
    }
}
