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
 *  Copyright (c) 2025 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.service;

import com.adyen.v6.dto.InstallmentOptionsDTO;
import com.adyen.v6.enums.AdyenInstallmentCountry;
import com.adyen.v6.model.AdyenInstallmentConfigModel;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.spockframework.util.CollectionUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Default implementation of AdyenInstallmentsConfigurationService
 */
public class DefaultAdyenInstallmentsConfigurationService implements AdyenInstallmentsConfigurationService {

    private static final Logger LOGGER = Logger.getLogger(DefaultAdyenInstallmentsConfigurationService.class);

    private BaseStoreService baseStoreService;
    private CartService cartService;
    private ModelService modelService;

    @Override
    public InstallmentOptionsDTO getInstallmentOptionsForCountry() {
        BaseStoreModel baseStore = baseStoreService.getCurrentBaseStore();
        
        // Get cart and delivery address
        CartModel cartModel = cartService.getSessionCart();
        if (cartModel == null || cartModel.getDeliveryAddress() == null || cartModel.getDeliveryAddress().getCountry() == null) {
            LOGGER.warn("Cart or delivery address is null, cannot determine country for installments");
            return null;
        }
        
        String countryIsoCode = cartModel.getDeliveryAddress().getCountry().getIsocode();
        LOGGER.debug("Checking installment support for country: " + countryIsoCode);
        
        // Convert country ISO code to enum
        AdyenInstallmentCountry installmentCountry;
        try {
            installmentCountry = AdyenInstallmentCountry.valueOf(countryIsoCode);
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Installments not supported for country: " + countryIsoCode);
            return null;
        }
        
        return getInstallmentOptionsForCountry(baseStore, installmentCountry);
    }
    
    /**
     * Get installment options for a specific country
     */
    private InstallmentOptionsDTO getInstallmentOptionsForCountry(BaseStoreModel baseStore, AdyenInstallmentCountry country) {
        List<AdyenInstallmentConfigModel> installmentConfigs = baseStore.getAdyenInstallmentConfigs();
        if (installmentConfigs == null || installmentConfigs.isEmpty()) {
            LOGGER.info("No installment configurations found for base store");
            return null;
        }
        
        // Find configuration for the specific country
        Optional<AdyenInstallmentConfigModel> configOptional = installmentConfigs.stream()
                .filter(config -> country.equals(config.getCountry()))
                .findFirst();
        
        if (!configOptional.isPresent()) {
            LOGGER.info("No installment configuration found for country: " + country.getCode());
            return null;
        }
        
        AdyenInstallmentConfigModel config = configOptional.get();
        
        try {
            if (!Boolean.TRUE.equals(config.getEnabled())) {
                LOGGER.info("Installments are disabled for country: " + country.getCode());
                return null;
            }
            
            // Check if currency is supported
            if (!isCurrencySupported(config)) {
                LOGGER.info("Currency not supported for installments in country: " + country.getCode());
                return null;
            }
            
            LOGGER.debug("Building installment options for country: " + country.getCode());
            return buildInstallmentOptionsFromConfig(config, country.getCode());
        } catch (Exception e) {
            LOGGER.error("Error accessing installment config for country " + country.getCode() + ": " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Build InstallmentOptionsDTO from installment configuration
     */
    private InstallmentOptionsDTO buildInstallmentOptionsFromConfig(AdyenInstallmentConfigModel config, String countryIsoCode) {
        try {
            String installmentOptionsConfig = config.getInstallmentOptions();
            String installmentPlansConfig = config.getInstallmentPlans();
            String showInstallmentAmountsConfig = config.getShowInstallmentAmounts();
            String showInstallmentPlansConfig = config.getShowInstallmentPlans();
            
            List<Integer> installmentValues;
            if (StringUtils.isEmpty(installmentOptionsConfig)) {
                LOGGER.error("Installment options configuration is missing for country: " + countryIsoCode);
                return null;
            } else {
                String[] values = StringUtils.split(installmentOptionsConfig, ',');
                installmentValues = Arrays.stream(values)
                        .map(String::trim)
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
            }
            
            List<String> installmentPlans;
            if (StringUtils.isEmpty(installmentPlansConfig)) {
                installmentPlans = Arrays.asList("regular");
            } else {
                String[] plans = StringUtils.split(installmentPlansConfig, ',');
                installmentPlans = Arrays.stream(plans)
                        .map(String::trim)
                        .collect(Collectors.toList());
            }
            
            List<Integer> showAmountValues;
            if (StringUtils.isEmpty(showInstallmentAmountsConfig)) {
                showAmountValues = Arrays.asList(1, 2, 3);
            } else {
                String[] values = StringUtils.split(showInstallmentAmountsConfig, ',');
                showAmountValues = Arrays.stream(values)
                        .map(String::trim)
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
            }
            
            List<String> showAmountPlans;
            if (StringUtils.isEmpty(showInstallmentPlansConfig)) {
                showAmountPlans = Arrays.asList("regular");
            } else {
                String[] plans = StringUtils.split(showInstallmentPlansConfig, ',');
                showAmountPlans = Arrays.stream(plans)
                        .map(String::trim)
                        .collect(Collectors.toList());
            }
            
            InstallmentOptionsDTO installmentOptionsDTO = new InstallmentOptionsDTO();
            
            InstallmentOptionsDTO.CardInstallmentOptions cardOptions = new InstallmentOptionsDTO.CardInstallmentOptions();
            cardOptions.setValues(installmentValues);
            cardOptions.setPlans(installmentPlans);
            installmentOptionsDTO.setCard(cardOptions);
            
            InstallmentOptionsDTO.ShowInstallmentAmounts showAmounts = new InstallmentOptionsDTO.ShowInstallmentAmounts();
            showAmounts.setValues(showAmountValues);
            showAmounts.setPlans(showAmountPlans);
            installmentOptionsDTO.setShowInstallmentAmounts(showAmounts);
            
            LOGGER.debug("Built installment options for " + countryIsoCode + ": values=" + installmentValues + ", plans=" + installmentPlans);
            return installmentOptionsDTO;
        } catch (Exception e) {
            LOGGER.error("Error building installment options from config for country " + countryIsoCode + ": " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Check if the current cart's currency is supported for installments
     */
    private boolean isCurrencySupported(AdyenInstallmentConfigModel config) {
        try {
            CartModel cartModel = cartService.getSessionCart();
            if (cartModel == null || cartModel.getCurrency() == null) {
                LOGGER.warn("Cart or currency is null, cannot validate currency support");
                return false;
            }
            
            CurrencyModel cartCurrency = cartModel.getCurrency();
            List<CurrencyModel> supportedCurrencies = config.getSupportedCurrencies();

            if (CollectionUtils.isEmpty(supportedCurrencies)) {
                LOGGER.warn("No supported currencies configured, allowing all currencies");
                return false;
            }
            
            // Check if cart currency is in the supported currencies list
            boolean isSupported = supportedCurrencies.stream()
                    .anyMatch(currency -> currency.getIsocode().equals(cartCurrency.getIsocode()));
            
            if (!isSupported) {
                LOGGER.info("Currency " + cartCurrency.getIsocode() + " is not supported for installments." );
            }
            
            return isSupported;
        } catch (Exception e) {
            LOGGER.error("Error checking currency support: " + e.getMessage(), e);
            return false;
        }
    }

    // Getters and Setters
    public BaseStoreService getBaseStoreService() {
        return baseStoreService;
    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }

    public CartService getCartService() {
        return cartService;
    }

    public void setCartService(CartService cartService) {
        this.cartService = cartService;
    }

    public ModelService getModelService() {
        return modelService;
    }

    public void setModelService(ModelService modelService) {
        this.modelService = modelService;
    }
}