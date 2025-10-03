package com.adyen.v6.resolver;

import com.adyen.v6.util.WebServicesBaseUrlResolver;
import de.hybris.platform.commerceservices.i18n.CommerceCommonI18NService;
import de.hybris.platform.site.BaseSiteService;
import org.apache.commons.lang3.StringUtils;

public class OccPaymentRedirectReturnUrlResolver {
    private WebServicesBaseUrlResolver webServicesBaseUrlResolver;
    private CommerceCommonI18NService commerceCommonI18NService;
    private BaseSiteService baseSiteService;


    public String resolvePaymentRedirectReturnUrl() {
        return resolvePaymentRedirectReturnUrl(null, false);
    }

    public String resolvePaymentRedirectReturnUrlExpressPDPCheckout(String productCode) {
        return resolvePaymentRedirectReturnUrl(productCode, false);
    }

    public String resolvePaymentRedirectReturnUrlExpressCartCheckout() {
        return resolvePaymentRedirectReturnUrl(null, true);
    }

    protected String resolvePaymentRedirectReturnUrl(String productCode, boolean isExpressCart) {
        String occBaseUrl = webServicesBaseUrlResolver.getOCCBaseUrl(true);
        String currency = commerceCommonI18NService.getCurrentCurrency().getIsocode();
        String language = commerceCommonI18NService.getCurrentLanguage().getIsocode();
        String baseSiteUid = baseSiteService.getCurrentBaseSite().getUid();

        String baseUrl = occBaseUrl + "/v2/" + baseSiteUid + "/adyen/redirect?lang=" + language + "&curr=" + currency;

        if (StringUtils.isNotEmpty(productCode)) {
            return baseUrl + "&productCode=" + productCode + "&express=true";
        }

        if (isExpressCart) {
            return baseUrl + "&express=true";
        }

        return baseUrl;
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
