package com.adyen.v6.controllers.checkout;

import com.adyen.model.checkout.PaymentCompletionDetails;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commercefacades.product.ProductOption;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.commerceservices.url.UrlResolver;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

import static com.adyen.v6.constants.AdyenControllerConstants.*;
import static de.hybris.platform.addonsupport.controllers.AbstractAddOnController.REDIRECT_PREFIX;

@Controller
@RequestMapping(value = "/checkout/express")
public class AdyenExpressRedirectController {
    private static final Logger LOGGER = Logger.getLogger(AdyenExpressRedirectController.class);

    protected static final String REDIRECT_URL_ORDER_CONFIRMATION = REDIRECT_PREFIX + "/checkout/orderConfirmation/";

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Autowired
    private CheckoutCustomerStrategy checkoutCustomerStrategy;

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private UrlResolver<ProductData> productDataUrlResolver;

    @GetMapping(value = CHECKOUT_RESULT_URL)
    @RequireHardLogIn
    public String handleAdyenResponse(final HttpServletRequest request, final RedirectAttributes redirectModel, final @RequestParam(required = false) String productCode) {
        String redirectResult = request.getParameter(REDIRECT_RESULT);
        PaymentCompletionDetails details = new PaymentCompletionDetails();

        if (redirectResult != null && !redirectResult.isEmpty()) {
            details.setRedirectResult(redirectResult);
        } else if (StringUtils.isNotEmpty(request.getParameter(PAYLOAD))) {
            details.setPayload(request.getParameter(PAYLOAD));
        }

        try {
            PaymentDetailsResponse response = adyenCheckoutFacade.handleRedirectPayload(details);

            switch (response.getResultCode()) {
                case AUTHORISED, RECEIVED:
                    LOGGER.debug("Redirecting to order confirmation");
                    OrderData orderData = orderFacade.getOrderDetailsForCodeWithoutUser(response.getMerchantReference());
                    if (orderData == null) {
                        LOGGER.error("Order " + response.getMerchantReference() + " not found");
                        throw new Exception("Order not found");
                    }
                    return redirectToOrderConfirmationPage(orderData);
                case REFUSED:
                    LOGGER.info("PaymentResponse " + response.getPspReference() + " is REFUSED");
                    return redirectToSelectPaymentMethodWithError(redirectModel, CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_REFUSED, productCode);
                case CANCELLED:
                    LOGGER.info("PaymentResponse " + response.getPspReference() + " is CANCELLED");
                    return redirectToSelectPaymentMethodWithError(redirectModel, CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_CANCELLED, productCode);
                default:
                    LOGGER.error("PaymentResponse " + response.getPspReference() + " - error occurred");
                    return redirectToSelectPaymentMethodWithError(redirectModel, CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_ERROR, productCode);
            }
        } catch (CalculationException | InvalidCartException e) {
            LOGGER.warn(e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }

        LOGGER.warn("Redirecting to home page");
        return REDIRECT_PREFIX + "/";
    }

    private String redirectToSelectPaymentMethodWithError(final RedirectAttributes redirectModel, final String messageKey, final String productCode) {
        GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, messageKey);

        if (StringUtils.isNotBlank(productCode)) {
            final List<ProductOption> extraOptions = Arrays.asList(ProductOption.VARIANT_MATRIX_BASE, ProductOption.VARIANT_MATRIX_URL,
                    ProductOption.VARIANT_MATRIX_MEDIA, ProductOption.PRICE);

            final ProductData productData = productFacade.getProductForCodeAndOptions(productCode, extraOptions);


            return REDIRECT_PREFIX + productDataUrlResolver.resolve(productData);
        }

        LOGGER.debug("Redirecting to cart with error: " + messageKey);
        return REDIRECT_PREFIX + CART_PREFIX;
    }

    protected String redirectToOrderConfirmationPage(final OrderData orderData)
    {
        return REDIRECT_URL_ORDER_CONFIRMATION
                + (checkoutCustomerStrategy.isAnonymousCheckout() ? orderData.getGuid() : orderData.getCode());
    }
}
