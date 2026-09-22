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
package com.adyen.v6.helpers;

import com.adyen.model.checkout.GooglePayDetails;
import com.adyen.v6.util.AdyenUtil;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.site.BaseSiteService;

import static com.adyen.v6.constants.AdyenControllerConstants.AUTHORISE_3D_SECURE_PAYMENT_URL;
import static com.adyen.v6.constants.AdyenControllerConstants.CHECKOUT_RESULT_URL;
import static com.adyen.v6.constants.AdyenControllerConstants.SUMMARY_CHECKOUT_PREFIX;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_BCMC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;

/**
 * Service for handling Adyen URL generation and payment method validation
 */
public class AdyenUrlHelper {

    private final SiteBaseUrlResolutionService siteBaseUrlResolutionService;
    private final BaseSiteService baseSiteService;

    public AdyenUrlHelper(SiteBaseUrlResolutionService siteBaseUrlResolutionService,
                          BaseSiteService baseSiteService) {
        this.siteBaseUrlResolutionService = siteBaseUrlResolutionService;
        this.baseSiteService = baseSiteService;
    }

    /**
     * Generates the appropriate return URL based on payment method
     * 
     * @param paymentMethod the Adyen payment method
     * @return the complete return URL
     */
    public String getReturnUrl(String paymentMethod) {
        String urlPath = determineUrlPath(paymentMethod);
        BaseSiteModel currentBaseSite = baseSiteService.getCurrentBaseSite();
        return siteBaseUrlResolutionService.getWebsiteUrlForSite(currentBaseSite, true, urlPath);
    }

    /**
     * Determines if a payment method requires 3DS authentication
     * 
     * @param paymentMethod the Adyen payment method
     * @return true if 3DS is required, false otherwise
     */
    public boolean is3DSPaymentMethod(String paymentMethod) {
        return PAYMENT_METHOD_CC.equals(paymentMethod) 
            || PAYMENT_METHOD_BCMC.equals(paymentMethod) 
            || AdyenUtil.isOneClick(paymentMethod);
    }

    /**
     * Determines if a payment method is Google Pay
     * 
     * @param paymentMethod the Adyen payment method
     * @return true if Google Pay, false otherwise
     */
    public boolean isGooglePay(String paymentMethod) {
        return GooglePayDetails.TypeEnum.GOOGLEPAY.getValue().equals(paymentMethod);
    }

    /**
     * Determines the URL path based on payment method type
     * 
     * @param paymentMethod the Adyen payment method
     * @return the URL path
     */
    protected String determineUrlPath(String paymentMethod) {
        if (is3DSPaymentMethod(paymentMethod) || isGooglePay(paymentMethod)) {
            return SUMMARY_CHECKOUT_PREFIX + AUTHORISE_3D_SECURE_PAYMENT_URL;
        }
        return SUMMARY_CHECKOUT_PREFIX + CHECKOUT_RESULT_URL;
    }
}