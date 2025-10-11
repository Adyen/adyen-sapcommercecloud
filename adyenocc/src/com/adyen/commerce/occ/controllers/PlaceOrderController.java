package com.adyen.commerce.occ.controllers;

import com.adyen.commerce.constants.AdyenoccConstants;
import com.adyen.commerce.controllerbase.PlaceOrderControllerBase;
import com.adyen.commerce.facades.AdyenCheckoutApiFacade;
import com.adyen.commerce.occ.api.AdyenPlaceOrderApi;
import com.adyen.commerce.occ.resolver.PaymentRedirectReturnUrlResolver;
import com.adyen.commerce.request.PlaceOrderRequest;
import com.adyen.commerce.response.OCCPlaceOrderResponse;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.adyen.v6.service.AdyenPartialPaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.hybris.platform.acceleratorfacades.flow.CheckoutFlowFacade;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.site.BaseSiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@ApiVersion("v2")
public class PlaceOrderController extends PlaceOrderControllerBase implements AdyenPlaceOrderApi {

    @Autowired
    private AdyenCheckoutApiFacade adyenCheckoutApiFacade;

    @Autowired
    private CheckoutFlowFacade checkoutFlowFacade;

    @Autowired
    private CartFacade cartFacade;

    @Resource(name = "siteBaseUrlResolutionService")
    private SiteBaseUrlResolutionService siteBaseUrlResolutionService;

    @Resource(name = "baseSiteService")
    private BaseSiteService baseSiteService;

    @Autowired
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Resource(name = "checkoutCustomerStrategy")
    private CheckoutCustomerStrategy checkoutCustomerStrategy;

    @Autowired
    private PaymentRedirectReturnUrlResolver paymentRedirectReturnUrlResolver;

    @Resource(name = "modelService")
    private ModelService modelService;

    @Resource(name = "adyenPartialPaymentService")
    private AdyenPartialPaymentService adyenPartialPaymentService;

    @Override
    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = AdyenoccConstants.ADYEN_USER_CART_PREFIX + "/place-order", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> onPlaceOrder(@RequestBody String placeOrderStringRequest, HttpServletRequest request) throws Exception {
        PlaceOrderRequest placeOrderRequest = objectMapper.readValue(placeOrderStringRequest, PlaceOrderRequest.class);
        OCCPlaceOrderResponse placeOrderResponse = super.placeOrderOCC(placeOrderRequest, request);
        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }

    @Override
    @Secured({"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT"})
    @PostMapping(value = AdyenoccConstants.ADYEN_USER_PREFIX + "/additional-details", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> onAdditionalDetails(@RequestBody PaymentDetailsRequest detailsRequest) throws JsonProcessingException {
        OCCPlaceOrderResponse placeOrderResponse = handleAdditionalDetailsOCC(detailsRequest);

        String response = objectMapper.writeValueAsString(placeOrderResponse);
        return ResponseEntity.ok(response);
    }


    @Override
    public String getPaymentRedirectReturnUrl() {
        return paymentRedirectReturnUrlResolver.resolvePaymentRedirectReturnUrl();
    }

    @Override
    public AdyenCheckoutApiFacade getAdyenCheckoutApiFacade() {
        return adyenCheckoutApiFacade;
    }

    @Override
    public CheckoutFlowFacade getCheckoutFlowFacade() {
        return checkoutFlowFacade;
    }

    @Override
    public CartFacade getCartFacade() {
        return cartFacade;
    }

    @Override
    public BaseSiteService getBaseSiteService() {
        return baseSiteService;
    }

    @Override
    public SiteBaseUrlResolutionService getSiteBaseUrlResolutionService() {
        return siteBaseUrlResolutionService;
    }

    @Override
    public AdyenCheckoutFacade getAdyenCheckoutFacade() {
        return adyenCheckoutFacade;
    }

    @Override
    public CheckoutCustomerStrategy getCheckoutCustomerStrategy() {
        return checkoutCustomerStrategy;
    }

    @Override
    public ModelService getModelService() {
        return modelService;
    }

    @Override
    public AdyenPartialPaymentService getAdyenPartialPaymentService() {
        return adyenPartialPaymentService;
    }
}
