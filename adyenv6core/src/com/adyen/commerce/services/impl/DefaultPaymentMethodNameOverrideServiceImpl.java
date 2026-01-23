package com.adyen.commerce.services.impl;

import com.adyen.commerce.services.PaymentMethodNameOverrideService;
import com.adyen.model.checkout.PaymentMethod;
import com.adyen.model.checkout.PaymentMethodsResponse;
import de.hybris.platform.core.model.c2l.LanguageModel;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultPaymentMethodNameOverrideServiceImpl implements PaymentMethodNameOverrideService {
    private BaseStoreService baseStoreService;
    private CommonI18NService commonI18NService;


    public PaymentMethodsResponse overridePaymentMethodNamesFromConfig(final PaymentMethodsResponse paymentMethodsResponse) {
        Map<String, Map<LanguageModel, String>> adyenPaymentMethodNameConfig = baseStoreService.getCurrentBaseStore().getAdyenPaymentMethodNameConfig();
        LanguageModel currentLanguage = commonI18NService.getCurrentLanguage();

        List<PaymentMethod> paymentMethods = paymentMethodsResponse.getPaymentMethods();

        if (Objects.nonNull(adyenPaymentMethodNameConfig) && CollectionUtils.isNotEmpty(adyenPaymentMethodNameConfig.keySet())) {
            for (PaymentMethod paymentMethod : paymentMethods) {
                if (StringUtils.isNotEmpty(paymentMethod.getType())) {
                    Map<LanguageModel, String> languageOverridesMap = adyenPaymentMethodNameConfig.get(paymentMethod.getType());
                    if (Objects.nonNull(languageOverridesMap)) {
                        String currentLanguageNameOverride = languageOverridesMap.get(currentLanguage);
                        if (StringUtils.isNotEmpty(currentLanguageNameOverride)) {
                            paymentMethod.setName(currentLanguageNameOverride);
                        }
                    }
                }
            }
        }

        return paymentMethodsResponse;
    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }

    public void setCommonI18NService(CommonI18NService commonI18NService) {
        this.commonI18NService = commonI18NService;
    }
}
