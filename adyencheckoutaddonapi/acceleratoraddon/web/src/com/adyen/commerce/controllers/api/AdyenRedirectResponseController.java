package com.adyen.commerce.controllers.api;

import com.adyen.commerce.controllerbase.RedirectControllerBase;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Base64;

import static com.adyen.commerce.constants.AdyencheckoutaddonapiWebConstants.*;
import static com.adyen.commerce.constants.AdyenwebcommonsConstants.ADYEN_CHECKOUT_API_PREFIX;
import static com.adyen.commerce.constants.AdyenwebcommonsConstants.AUTHORISE_3D_SECURE_PAYMENT_URL;
import static de.hybris.platform.acceleratorstorefrontcommons.controllers.AbstractController.REDIRECT_PREFIX;

@Controller
@RequestMapping(value = ADYEN_CHECKOUT_API_PREFIX)
public class AdyenRedirectResponseController extends RedirectControllerBase {
    private static final String SELECT_PAYMENT_METHOD_URL = ADYEN_CHECKOUT_PAGE_PREFIX + ADYEN_CHECKOUT_SELECT_PAYMENT;
    private static final String ORDER_CONFIRMATION_URL = ADYEN_CHECKOUT_PAGE_PREFIX + ADYEN_CHECKOUT_ORDER_CONFIRMATION;

    @Resource(name = "adyenCheckoutFacade")
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Resource(name = "checkoutCustomerStrategy")
    private CheckoutCustomerStrategy checkoutCustomerStrategy;

    @GetMapping(value = AUTHORISE_3D_SECURE_PAYMENT_URL)
    @RequireHardLogIn
    public String authoriseRedirectGetPayment(final HttpServletRequest request) {
        return super.authoriseRedirectGetPayment(request);
    }


    @PostMapping(value = AUTHORISE_3D_SECURE_PAYMENT_URL)
    @RequireHardLogIn
    public String authoriseRedirectPostPayment(@RequestBody PaymentDetailsRequest detailsRequest) {
        return super.authoriseRedirectPostPayment(detailsRequest);
    }

    @Override
    public String getErrorRedirectUrl(String errorMessage) {
        return REDIRECT_PREFIX + SELECT_PAYMENT_METHOD_URL + "/error/" + Base64.getUrlEncoder().encodeToString(errorMessage.getBytes());
    }

    @Override
    public String getOrderConfirmationUrl(OrderData orderData) {

        String orderCode = checkoutCustomerStrategy.isAnonymousCheckout() ? orderData.getGuid() : orderData.getCode();

        return REDIRECT_PREFIX + ORDER_CONFIRMATION_URL + '/' + orderCode;
    }

    @Override
    public String getCartUrl() {
        return REDIRECT_PREFIX + CART_PREFIX;
    }

    @Override
    public AdyenCheckoutFacade getAdyenCheckoutFacade() {
        return adyenCheckoutFacade;
    }

}