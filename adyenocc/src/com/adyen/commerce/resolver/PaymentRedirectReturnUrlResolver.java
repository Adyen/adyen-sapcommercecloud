package com.adyen.commerce.resolver;

import com.adyen.commerce.utils.WebServicesBaseUrlResolver;
import de.hybris.platform.commerceservices.i18n.CommerceCommonI18NService;
import de.hybris.platform.site.BaseSiteService;

public class PaymentRedirectReturnUrlResolver {
    private WebServicesBaseUrlResolver webServicesBaseUrlResolver;
    private CommerceCommonI18NService commerceCommonI18NService;
    private BaseSiteService baseSiteService;


    public String resolvePaymentRedirectReturnUrl() {
        String occBaseUrl = webServicesBaseUrlResolver.getOCCBaseUrl(true);
        String currency = commerceCommonI18NService.getCurrentCurrency().getIsocode();
        String language = commerceCommonI18NService.getCurrentLanguage().getIsocode();
        String baseSiteUid = baseSiteService.getCurrentBaseSite().getUid();

        return occBaseUrl + "/v2/" + baseSiteUid + "/adyen/redirect?lang=" + language + "&curr=" + currency;
    }

    public void setWebServicesBaseUrlResolver(WebServicesBaseUrlResolver webServicesBaseUrlResolver) {
        this.webServicesBaseUrlResolver = webServicesBaseUrlResolver;
    }

    public void setCommerceCommonI18NService(CommerceCommonI18NService commerceCommonI18NService) {
        this.commerceCommonI18NService = commerceCommonI18NService;
    }

    public void setBaseSiteService(BaseSiteService baseSiteService) {
        this.baseSiteService = baseSiteService;
    }
}
