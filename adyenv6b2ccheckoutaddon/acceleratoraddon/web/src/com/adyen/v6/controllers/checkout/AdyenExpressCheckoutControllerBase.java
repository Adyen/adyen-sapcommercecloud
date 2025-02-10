package com.adyen.v6.controllers.checkout;

import com.adyen.model.checkout.GooglePayDetails;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.site.BaseSiteService;

import static com.adyen.v6.constants.AdyenControllerConstants.CHECKOUT_RESULT_URL;
import static com.adyen.v6.constants.AdyenControllerConstants.SUMMARY_CHECKOUT_PREFIX;

public abstract class AdyenExpressCheckoutControllerBase {

    protected String getReturnUrl(String paymentMethod) {
        String url;
        if (GooglePayDetails.TypeEnum.GOOGLEPAY.getValue().equals(paymentMethod)) {
            //Google Pay will only use returnUrl if redirected to 3DS authentication
            url = SUMMARY_CHECKOUT_PREFIX + "/authorise-3d-adyen-response";
        } else {
            url = SUMMARY_CHECKOUT_PREFIX + CHECKOUT_RESULT_URL;
        }
        BaseSiteModel currentBaseSite = getBaseSiteService().getCurrentBaseSite();
        return getSiteBaseUrlResolutionService().getWebsiteUrlForSite(currentBaseSite, true, url);
    }

    abstract public BaseSiteService getBaseSiteService();

    abstract public SiteBaseUrlResolutionService getSiteBaseUrlResolutionService();
}
