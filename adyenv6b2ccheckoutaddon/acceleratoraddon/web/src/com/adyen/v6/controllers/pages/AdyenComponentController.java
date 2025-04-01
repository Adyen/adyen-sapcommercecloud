/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2020 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.controllers.pages;


import com.adyen.model.checkout.GooglePayDetails;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.controllers.dtos.PaymentResultDTO;
import com.adyen.v6.exceptions.AdyenComponentException;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import de.hybris.platform.acceleratorfacades.flow.CheckoutFlowFacade;
import de.hybris.platform.acceleratorfacades.order.AcceleratorCheckoutFacade;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractCheckoutController;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.site.BaseSiteService;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.adyen.v6.constants.AdyenControllerConstants.CHECKOUT_RESULT_URL;
import static com.adyen.v6.constants.AdyenControllerConstants.COMPONENT_PREFIX;
import static com.adyen.v6.constants.AdyenControllerConstants.SUMMARY_CHECKOUT_PREFIX;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_AMAZONPAY;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_BCMC_MOBILE;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_PIX;


@RestController
@RequestMapping(COMPONENT_PREFIX)
public class AdyenComponentController extends AbstractCheckoutController {
    private static final Logger LOGGER = Logger.getLogger(AdyenComponentController.class);

    @Resource(name = "adyenCheckoutFacade")
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Resource(name = "checkoutFlowFacade")
    private CheckoutFlowFacade checkoutFlowFacade;

    @Resource(name = "acceleratorCheckoutFacade")
    private AcceleratorCheckoutFacade checkoutFacade;

    @Resource(name = "siteBaseUrlResolutionService")
    private SiteBaseUrlResolutionService siteBaseUrlResolutionService;

    @Resource(name = "baseSiteService")
    private BaseSiteService baseSiteService;

    private final List<String> PAYMENT_METHODS_WITH_VALIDATED_TERMS = Arrays.asList(PAYMENT_METHOD_AMAZONPAY,
            PAYMENT_METHOD_BCMC_MOBILE,
            PAYMENT_METHOD_PIX);

    @RequestMapping(value = "/resultHandler", method = RequestMethod.POST)
    @ResponseBody
    public String componentPaymentResultHandler(@RequestBody final PaymentResultDTO paymentResultDTO) throws Exception {
        final OrderData orderData = getAdyenCheckoutFacade().handleResultcomponentPayment(paymentResultDTO);
        return redirectToOrderConfirmationPage(orderData);
    }

    @PostMapping(value = "/payment", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<PaymentResponse> componentPayment(@RequestHeader String host, @RequestBody PaymentRequest body, final HttpServletRequest request) throws AdyenComponentException {
        try {
            validateOrderForm();

            final CartData cartData = getCheckoutFlowFacade().getCheckoutCart();
            String paymentMethod = cartData.getAdyenPaymentMethod();

            cartData.setAdyenReturnUrl(getReturnUrl(paymentMethod));

            PaymentResponse paymentsResponse = getAdyenCheckoutFacade().componentPayment(request, cartData, body);
            return ResponseEntity.ok().body(paymentsResponse);
        } catch (InvalidCartException e) {
            LOGGER.error("InvalidCartException: " + e.getMessage());
            throw new AdyenComponentException(e.getMessage());
        } catch (ApiException e) {
            LOGGER.error("ApiException: " + e);
            throw new AdyenComponentException("checkout.error.authorization.payment.refused");
        } catch (AdyenNonAuthorizedPaymentException e) {
            if (Objects.nonNull(e.getPaymentsResponse()) && Objects.nonNull(e.getPaymentsResponse().getAction())) {
                return ResponseEntity.ok().body(e.getPaymentsResponse());
            }
            LOGGER.warn("AdyenNonAuthorizedPaymentException occurred. Payment " + e.getPaymentResult().getPspReference() + "is refused.");
            throw new AdyenComponentException("checkout.error.authorization.payment.refused");
        } catch (Exception e) {
            LOGGER.error("Exception", e);
            throw new AdyenComponentException("checkout.error.authorization.payment.error");
        }
    }

    @PostMapping(value = "/submit-details", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentDetailsResponse> submitDetails(@RequestBody PaymentDetailsRequest detailsRequest, final HttpServletRequest request) throws AdyenComponentException {
        try {
            PaymentDetailsResponse paymentsResponse = getAdyenCheckoutFacade().componentDetails(detailsRequest);
            return ResponseEntity.ok().body(paymentsResponse);
        } catch (ApiException e) {
            LOGGER.error("ApiException: " + e);
            throw new AdyenComponentException("checkout.error.authorization.payment.refused");
        } catch (Exception e) {
            LOGGER.error("Exception", e);
            throw new AdyenComponentException("checkout.error.authorization.payment.error");
        }
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = AdyenComponentException.class)
    public String adyenComponentExceptionHandler(AdyenComponentException e) {
        return e.getMessage();
    }

    /**
     * Validates the order form before to filter out invalid order states
     *
     * @param requestJson
     * @return True if the order form is invalid and false if everything is valid.
     */
    protected void validateOrderForm() throws InvalidCartException {

        if (getCheckoutFlowFacade().hasNoDeliveryAddress()) {
            throw new InvalidCartException("checkout.deliveryAddress.notSelected");
        }

        if (getCheckoutFlowFacade().hasNoDeliveryMode()) {
            throw new InvalidCartException("checkout.deliveryMethod.notSelected");
        }

        if (getCheckoutFlowFacade().hasNoPaymentInfo()) {
            throw new InvalidCartException("checkout.paymentMethod.notSelected");
        }

        final CartData cartData = getCheckoutFacade().getCheckoutCart();

        if (!getCheckoutFacade().containsTaxValues()) {
            LOGGER.error(String.format("Cart %s does not have any tax values, which means the tax cacluation was not properly done, placement of order can't continue", cartData.getCode()));
            throw new InvalidCartException("checkout.error.tax.missing");
        }

        if (!cartData.isCalculated()) {
            LOGGER.error(String.format("Cart %s has a calculated flag of FALSE, placement of order can't continue", cartData.getCode()));
            throw new InvalidCartException("checkout.error.cart.notcalculated");
        }
    }

    private String getReturnUrl(String paymentMethod) {
        String url;
        if (GooglePayDetails.TypeEnum.GOOGLEPAY.getValue().equals(paymentMethod)) {
            //Google Pay will only use returnUrl if redirected to 3DS authentication
            url = SUMMARY_CHECKOUT_PREFIX + "/authorise-3d-adyen-response";
        } else {
            url = SUMMARY_CHECKOUT_PREFIX + CHECKOUT_RESULT_URL;
        }
        BaseSiteModel currentBaseSite = baseSiteService.getCurrentBaseSite();
        return siteBaseUrlResolutionService.getWebsiteUrlForSite(currentBaseSite, true, url);
    }

    public AdyenCheckoutFacade getAdyenCheckoutFacade() {
        return adyenCheckoutFacade;
    }

    public void setAdyenCheckoutFacade(AdyenCheckoutFacade adyenCheckoutFacade) {
        this.adyenCheckoutFacade = adyenCheckoutFacade;
    }

    public CheckoutFlowFacade getCheckoutFlowFacade() {
        return checkoutFlowFacade;
    }

    public void setCheckoutFlowFacade(CheckoutFlowFacade checkoutFlowFacade) {
        this.checkoutFlowFacade = checkoutFlowFacade;
    }

    public AcceleratorCheckoutFacade getCheckoutFacade() {
        return checkoutFacade;
    }

    public void setCheckoutFacade(AcceleratorCheckoutFacade checkoutFacade) {
        this.checkoutFacade = checkoutFacade;
    }

}
