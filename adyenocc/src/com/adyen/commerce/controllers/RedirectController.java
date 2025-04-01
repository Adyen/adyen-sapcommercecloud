/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.commerce.controllers;

import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.commerce.controllerbase.RedirectControllerBase;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commerceservices.i18n.CommerceCommonI18NService;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.site.BaseSiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.Base64;

import static com.adyen.commerce.constants.AdyenwebcommonsConstants.REDIRECT_PREFIX;

@Controller
@RequestMapping(value = AdyenoccConstants.ADYEN_PREFIX)
@ApiVersion("v2")
@Tag(name = "Adyen")
public class RedirectController extends RedirectControllerBase {
    private static final String REDIRECT_URL = "/redirect";
    private static final String ADYEN_REDIRECT_URL = "/adyen/redirect/";

    @Resource(name = "adyenCheckoutFacade")
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Resource(name = "configurationService")
    private ConfigurationService configurationService;

    @Resource(name = "commerceCommonI18NService")
    private CommerceCommonI18NService commerceCommonI18NService;

    @Resource(name = "baseSiteService")
    private BaseSiteService baseSiteService;

    @GetMapping(value = REDIRECT_URL)
    @Operation(operationId = "adyenRedirect", summary = "Handle redirect payment method", description =
            "Handles return after payment method redirect flow returns")
    public String authorizeRedirectPaymentGet(final HttpServletRequest request) {
        return super.authoriseRedirectGetPayment(request);
    }

    @PostMapping(value = REDIRECT_URL)
    @Operation(operationId = "adyenRedirect", summary = "Handle redirect payment method", description =
            "Handles return after payment method redirect flow returns")
    public String authorizeRedirectPaymentPost(@Parameter(description = "Payment details data", required = true) @RequestBody PaymentDetailsRequest detailsRequest) {
        return super.authoriseRedirectPostPayment(detailsRequest);
    }

    @Override
    public String getErrorRedirectUrl(String errorMessage) {
        String encodedMessage = Base64.getUrlEncoder().encodeToString(errorMessage.getBytes());
        return getSpartacusUrlPrefix() + ADYEN_REDIRECT_URL + "error/" + encodedMessage;
    }

    @Override
    public String getOrderConfirmationUrl(OrderData orderData) {
        return getSpartacusUrlPrefix() + ADYEN_REDIRECT_URL + orderData.getCode();

    }

    @Override
    public String getCartUrl() {
        return getSpartacusUrlPrefix() + "/cart";
    }

    private String getSpartacusUrlPrefix() {
        String currency = commerceCommonI18NService.getCurrentCurrency().getIsocode();
        String language = commerceCommonI18NService.getCurrentLanguage().getIsocode();
        String baseSiteUid = baseSiteService.getCurrentBaseSite().getUid();

        String baseUrl = configurationService.getConfiguration().getString("adyen.spartacus.baseurl");

        return REDIRECT_PREFIX + baseUrl + baseSiteUid + "/" + language + "/" + currency;
    }

    @Override
    public AdyenCheckoutFacade getAdyenCheckoutFacade() {
        return adyenCheckoutFacade;
    }
}
