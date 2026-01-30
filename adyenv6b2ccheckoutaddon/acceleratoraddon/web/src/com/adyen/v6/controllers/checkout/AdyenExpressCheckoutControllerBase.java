package com.adyen.v6.controllers.checkout;

import com.adyen.v6.helpers.AdyenUrlHelper;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.site.BaseSiteService;

import javax.annotation.Resource;

/**
 * Base class for express checkout controllers providing common URL generation functionality
 */
public abstract class AdyenExpressCheckoutControllerBase {

    @Resource(name = "adyenUrlHelper")
    private AdyenUrlHelper adyenUrlHelper;

    /**
     * Generates the appropriate return URL based on payment method
     *
     * @param paymentMethod the Adyen payment method
     * @return the complete return URL
     */
    protected String getReturnUrl(String paymentMethod) {
        return adyenUrlHelper.getReturnUrl(paymentMethod);
    }

    /**
     * Gets the base site service
     *
     * @return BaseSiteService instance
     */
    public abstract BaseSiteService getBaseSiteService();

    /**
     * Gets the site base URL resolution service
     *
     * @return SiteBaseUrlResolutionService instance
     */
    public abstract SiteBaseUrlResolutionService getSiteBaseUrlResolutionService();
}
