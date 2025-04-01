package com.adyen.commerce.controllers.api;

import com.adyen.commerce.controllerbase.PlaceOrderControllerBase;
import com.adyen.commerce.facades.AdyenCheckoutApiFacade;
import com.adyen.commerce.request.PlaceOrderRequest;
import com.adyen.commerce.response.PlaceOrderResponse;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import de.hybris.platform.acceleratorfacades.flow.CheckoutFlowFacade;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.site.BaseSiteService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import static com.adyen.commerce.constants.AdyenwebcommonsConstants.ADYEN_CHECKOUT_API_PREFIX;
import static com.adyen.commerce.constants.AdyenwebcommonsConstants.AUTHORISE_3D_SECURE_PAYMENT_URL;


@RequestMapping("/api/checkout")
@Controller
public class AdyenPlaceOrderController extends PlaceOrderControllerBase {

    @Autowired
    private CheckoutFlowFacade checkoutFlowFacade;

    @Autowired
    private CartFacade cartFacade;

    @Autowired
    private AdyenCheckoutApiFacade adyenCheckoutApiFacade;

    @Resource(name = "siteBaseUrlResolutionService")
    private SiteBaseUrlResolutionService siteBaseUrlResolutionService;

    @Resource(name = "baseSiteService")
    private BaseSiteService baseSiteService;

    @Autowired
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Resource(name = "checkoutCustomerStrategy")
    private CheckoutCustomerStrategy checkoutCustomerStrategy;

    @RequireHardLogIn
    @PostMapping("/place-order")
    public ResponseEntity<PlaceOrderResponse> onPlaceOrder(@RequestBody PlaceOrderRequest placeOrderRequest, HttpServletRequest request) throws Exception {
        PlaceOrderResponse placeOrderResponse = super.placeOrder(placeOrderRequest, request);

        return ResponseEntity.ok(placeOrderResponse);
    }

    @RequireHardLogIn
    @PostMapping("/additional-details")
    public ResponseEntity<PlaceOrderResponse> onAdditionalDetails(@RequestBody PaymentDetailsRequest detailsRequest, HttpServletRequest request) throws Exception {
        PlaceOrderResponse placeOrderResponse = super.handleAdditionalDetails(detailsRequest);

        return ResponseEntity.ok(placeOrderResponse);
    }

    @RequireHardLogIn
    @PostMapping("/payment-canceled")
    public ResponseEntity<Void> onCancel() throws InvalidCartException, CalculationException {
        super.handleCancel();
        return ResponseEntity.ok().build();
    }


    @Override
    public String getPaymentRedirectReturnUrl() {
        String url = ADYEN_CHECKOUT_API_PREFIX + AUTHORISE_3D_SECURE_PAYMENT_URL;

        BaseSiteModel currentBaseSite = getBaseSiteService().getCurrentBaseSite();

        return getSiteBaseUrlResolutionService().getWebsiteUrlForSite(currentBaseSite, true, url);
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
}
