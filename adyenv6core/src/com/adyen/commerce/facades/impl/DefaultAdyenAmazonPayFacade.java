package com.adyen.commerce.facades.impl;

import com.adyen.commerce.facades.AdyenAmazonPayFacade;
import com.adyen.v6.service.AdyenAmazonPayIntegratorService;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.site.BaseSiteService;

/**
 * Default implementation of {@link AdyenAmazonPayFacade}.
 */
public class DefaultAdyenAmazonPayFacade implements AdyenAmazonPayFacade {

    protected final AdyenAmazonPayIntegratorService adyenAmazonPayIntegratorService;
    protected final BaseSiteService baseSiteService;
    protected final SiteBaseUrlResolutionService siteBaseUrlResolutionService;

    public DefaultAdyenAmazonPayFacade(final AdyenAmazonPayIntegratorService adyenAmazonPayIntegratorService,
                                       final BaseSiteService baseSiteService,
                                       final SiteBaseUrlResolutionService siteBaseUrlResolutionService) {
        this.adyenAmazonPayIntegratorService = adyenAmazonPayIntegratorService;
        this.baseSiteService = baseSiteService;
        this.siteBaseUrlResolutionService = siteBaseUrlResolutionService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAmazonPayToken(final String amazonPayCheckoutSessionId) {
        return adyenAmazonPayIntegratorService.getAmazonPayTokenByCheckoutSessionId(amazonPayCheckoutSessionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getReturnUrl(final String url) {
        return siteBaseUrlResolutionService.getWebsiteUrlForSite(baseSiteService.getCurrentBaseSite(), true, url);
    }
}
