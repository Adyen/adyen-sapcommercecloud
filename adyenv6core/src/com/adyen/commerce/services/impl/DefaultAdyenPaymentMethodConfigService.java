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
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.services.impl;

import com.adyen.commerce.services.AdyenPaymentMethodConfigService;
import com.adyen.model.checkout.PaymentMethod;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.adyen.v6.constants.Adyenv6coreConstants.*;

/**
 * Default implementation of {@link AdyenPaymentMethodConfigService}.
 */
public class DefaultAdyenPaymentMethodConfigService implements AdyenPaymentMethodConfigService {

    private static final String EXCLUDED_PAYMENT_METHODS_CONFIG = "adyen.payment-methods.excluded";
    private static final String ALLOWED_PAYMENT_METHODS_CONFIG = "adyen.payment-methods.allowed";

    private static final String REGION = "region";
    private static final String US = "US";
    private static final String US_LOCALE = "en_US";
    private static final String GB_LOCALE = "en_GB";
    private static final String DE_LOCALE = "de_DE";
    private static final String FR_LOCALE = "fr_FR";
    private static final String IT_LOCALE = "it_IT";
    private static final String ES_LOCALE = "es_ES";

    private static final Map<String, String> SHOPPER_LOCALE_MAP = Map.of(
            "de", DE_LOCALE,
            "fr", FR_LOCALE,
            "it", IT_LOCALE,
            "es", ES_LOCALE
    );

    private ConfigurationService configurationService;

    @Override
    public List<String> getExcludedPaymentMethods() {
        final String config = configurationService.getConfiguration().getString(EXCLUDED_PAYMENT_METHODS_CONFIG);
        if (StringUtils.isEmpty(config)) {
            return new ArrayList<>();
        }
        return Arrays.stream(StringUtils.split(config, ',')).map(String::trim).toList();
    }

    @Override
    public List<String> getAllowedPaymentMethods() {
        final String config = configurationService.getConfiguration().getString(ALLOWED_PAYMENT_METHODS_CONFIG);
        if (StringUtils.isEmpty(config)) {
            return new ArrayList<>();
        }
        return Arrays.stream(StringUtils.split(config, ',')).map(String::trim).toList();
    }

    @Override
    public Map<String, String> getApplePayConfig(final List<PaymentMethod> paymentMethods) {
        return getPaymentMethodConfig(paymentMethods, PAYMENT_METHOD_APPLEPAY);
    }

    @Override
    public Map<String, String> getGooglePayConfig(final List<PaymentMethod> paymentMethods) {
        return getPaymentMethodConfig(paymentMethods, PAYMENT_METHOD_GOOGLE_PAY);
    }

    @Override
    public Map<String, String> getPayPalConfig(final List<PaymentMethod> paymentMethods) {
        return getPaymentMethodConfig(paymentMethods, PAYMENT_METHOD_PAYPAL);
    }

    @Override
    public Optional<PaymentMethod> getAmazonPayMethod(final List<PaymentMethod> paymentMethods) {
        if (paymentMethods == null) {
            return Optional.empty();
        }
        return paymentMethods.stream()
                .filter(pm -> !pm.getType().isEmpty() && PAYMENT_METHOD_AMAZONPAY.contains(pm.getType()))
                .findFirst();
    }

    @Override
    public boolean isHiddenPaymentMethod(final PaymentMethod paymentMethod) {
        final String type = paymentMethod.getType();
        if (type == null || type.isEmpty()) {
            return true;
        }
        return type.equals("scheme")
                || (type.contains("wechatpay") && !type.equals("wechatpayWeb"))
                || type.startsWith(PAYMENT_METHOD_BOLETO)
                || type.contains(PAYMENT_METHOD_SEPA_DIRECTDEBIT)
                || (ISSUER_PAYMENT_METHODS.contains(type)
                        && !type.equals(PAYMENT_METHOD_ONLINEBANKING_IN)
                        && !type.equals(PAYMENT_METHOD_ONLINEBANKING_PL));
    }

    @Override
    public String resolveAmazonPayLocale(final Map<String, String> amazonPayConfig, final String shopperLocale) {
        if (Objects.nonNull(amazonPayConfig) && US.equals(amazonPayConfig.get(REGION))) {
            return US_LOCALE;
        }
        return SHOPPER_LOCALE_MAP.getOrDefault(shopperLocale != null ? shopperLocale : "", GB_LOCALE);
    }

    protected Map<String, String> getPaymentMethodConfig(final List<PaymentMethod> paymentMethods, final String paymentMethodName) {
        if (paymentMethods != null) {
            final Optional<PaymentMethod> match = paymentMethods.stream()
                    .filter(pm -> !pm.getType().isEmpty() && paymentMethodName.contains(pm.getType()))
                    .findFirst();
            if (match.isPresent()) {
                final Map<String, String> config = match.get().getConfiguration();
                if (!CollectionUtils.isEmpty(config)) {
                    return config;
                }
            }
        }
        return new HashMap<>();
    }

    // --- Getters / Setters ---

    public ConfigurationService getConfigurationService() {
        return configurationService;
    }

    public void setConfigurationService(final ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
}
