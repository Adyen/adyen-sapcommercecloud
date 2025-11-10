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
import com.adyen.v6.model.AdyenJapanInstallmentConfigModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.List;
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
        
        // Check specific country configurations
        switch (countryIsoCode) {
            case "BR":
                return getInstallmentOptionsForBrazil(baseStore);
            case "MX":
                return getInstallmentOptionsForMexico(baseStore);
            case "JP":
                return getInstallmentOptionsForJapan(baseStore);
            default:
                LOGGER.debug("Installments not supported for country: " + countryIsoCode);
                return null;
        }
    }
    
    /**
     * Get installment options for Brazil
     */
    private InstallmentOptionsDTO getInstallmentOptionsForBrazil(BaseStoreModel baseStore) {
        Object brazilConfig = baseStore.getAdyenBrazilInstallmentConfig();
        if (brazilConfig == null) {
            LOGGER.debug("No Brazil installment configuration found");
            return null;
        }
        
        try {
            Boolean enabled = (Boolean) modelService.getAttributeValue(brazilConfig, "enabled");
            if (!Boolean.TRUE.equals(enabled)) {
                LOGGER.debug("Brazil installments are disabled");
                return null;
            }
            
            LOGGER.debug("Building installment options for Brazil");
            return buildInstallmentOptionsFromConfig(brazilConfig, "BR");
        } catch (Exception e) {
            LOGGER.error("Error accessing Brazil installment config: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get installment options for Mexico
     */
    private InstallmentOptionsDTO getInstallmentOptionsForMexico(BaseStoreModel baseStore) {
        Object mexicoConfig = baseStore.getAdyenMexicoInstallmentConfig();
        if (mexicoConfig == null) {
            LOGGER.debug("No Mexico installment configuration found");
            return null;
        }
        
        try {
            Boolean enabled = (Boolean) modelService.getAttributeValue(mexicoConfig, "enabled");
            if (!Boolean.TRUE.equals(enabled)) {
                LOGGER.debug("Mexico installments are disabled");
                return null;
            }
            
            LOGGER.debug("Building installment options for Mexico");
            return buildInstallmentOptionsFromConfig(mexicoConfig, "MX");
        } catch (Exception e) {
            LOGGER.error("Error accessing Mexico installment config: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get installment options for Japan
     */
    private InstallmentOptionsDTO getInstallmentOptionsForJapan(BaseStoreModel baseStore) {
        AdyenJapanInstallmentConfigModel adyenJapanInstallmentConfig = baseStore.getAdyenJapanInstallmentConfig();
        if (adyenJapanInstallmentConfig == null) {
            LOGGER.debug("No Japan installment configuration found");
            return null;
        }
        
        try {
            if (!Boolean.TRUE.equals(adyenJapanInstallmentConfig.getEnabled())) {
                LOGGER.debug("Japan installments are disabled");
                return null;
            }
            
            LOGGER.debug("Building installment options for Japan");
            return buildInstallmentOptionsFromConfig(adyenJapanInstallmentConfig, "JP");
        } catch (Exception e) {
            LOGGER.error("Error accessing Japan installment config: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Build InstallmentOptionsDTO from country-specific configuration
     */
    private InstallmentOptionsDTO buildInstallmentOptionsFromConfig(Object config, String countryIsoCode) {
        try {
            String installmentOptionsConfig = modelService.getAttributeValue(config, "installmentOptions");
            String installmentPlansConfig = modelService.getAttributeValue(config, "installmentPlans");
            String showInstallmentAmountsConfig = modelService.getAttributeValue(config, "showInstallmentAmounts");
            String showInstallmentPlansConfig = modelService.getAttributeValue(config, "showInstallmentPlans");
            
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