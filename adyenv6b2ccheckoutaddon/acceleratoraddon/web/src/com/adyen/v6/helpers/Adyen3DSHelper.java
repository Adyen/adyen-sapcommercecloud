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
 *  Copyright (c) 2025 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.helpers;

import com.adyen.model.checkout.CheckoutRedirectAction;
import com.adyen.model.checkout.PaymentCompletionDetails;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.checkout.PaymentResponseAction;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.hybris.platform.commercefacades.order.data.CCPaymentInfoData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ui.Model;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

import static com.adyen.commerce.constants.AdyenwebcommonsConstants.PAYLOAD_PARAM;
import static com.adyen.commerce.constants.AdyenwebcommonsConstants.REDIRECT_RESULT_PARAM;
import static com.adyen.v6.constants.AdyenControllerConstants.Views.Pages.MultiStepCheckout.Validate3DSPaymentPage;
import static com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade.MODEL_CHECKOUT_SHOPPER_HOST;
import static com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade.MODEL_CLIENT_KEY;
import static com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade.MODEL_ENVIRONMENT_MODE;
import static com.adyen.v6.constants.Adyenv6coreConstants.SHOPPER_LOCALE;

/**
 * Service for handling 3DS authentication flows
 */
public class Adyen3DSHelper {

    private final AdyenCheckoutFacade adyenCheckoutFacade;

    public Adyen3DSHelper(AdyenCheckoutFacade adyenCheckoutFacade) {
        this.adyenCheckoutFacade = adyenCheckoutFacade;
    }

    /**
     * Creates PaymentDetailsRequest from HTTP request parameters
     * 
     * @param request the HTTP servlet request
     * @return PaymentDetailsRequest with populated details
     */
    public PaymentDetailsRequest createPaymentDetailsRequest(HttpServletRequest request) {
        PaymentDetailsRequest paymentDetailsRequest = new PaymentDetailsRequest();
        PaymentCompletionDetails details = new PaymentCompletionDetails();

        String redirectResult = request.getParameter(REDIRECT_RESULT_PARAM);
        String payload = request.getParameter(PAYLOAD_PARAM);

        if (StringUtils.isNotEmpty(redirectResult)) {
            details.setRedirectResult(redirectResult);
        } else if (StringUtils.isNotEmpty(payload)) {
            details.setPayload(payload);
        }

        paymentDetailsRequest.details(details);
        return paymentDetailsRequest;
    }

    /**
     * Prepares model for 3DS validation page
     * 
     * @param model the Spring model
     * @param paymentsResponse the payment response containing action
     * @param cartData optional cart data for country code
     * @return the view name for 3DS validation
     * @throws JsonProcessingException if JSON processing fails
     */
    public String prepareValidation3DSModel(Model model, PaymentResponse paymentsResponse, CartData cartData) 
            throws JsonProcessingException {
        PaymentResponseAction action = paymentsResponse.getAction();
        
        model.addAttribute(MODEL_CLIENT_KEY, adyenCheckoutFacade.getClientKey());
        model.addAttribute(MODEL_CHECKOUT_SHOPPER_HOST, adyenCheckoutFacade.getCheckoutShopperHost());
        model.addAttribute(MODEL_ENVIRONMENT_MODE, adyenCheckoutFacade.getEnvironmentMode());
        model.addAttribute(SHOPPER_LOCALE, adyenCheckoutFacade.getShopperLocale());
        
        if (cartData != null) {
            model.addAttribute("countryCode", getCountryCode(cartData));
        }
        
        model.addAttribute("action", ((CheckoutRedirectAction) action.getActualInstance()).toJson());
        
        return Validate3DSPaymentPage;
    }

    /**
     * Prepares model for 3DS validation page without cart data
     * 
     * @param model the Spring model
     * @param paymentsResponse the payment response containing action
     * @return the view name for 3DS validation
     * @throws JsonProcessingException if JSON processing fails
     */
    public String prepareValidation3DSModel(Model model, PaymentResponse paymentsResponse) 
            throws JsonProcessingException {
        return prepareValidation3DSModel(model, paymentsResponse, null);
    }

    /**
     * Extracts country code from cart data
     * 
     * @param cartData the cart data
     * @return the country ISO code or empty string if not found
     */
    protected String getCountryCode(CartData cartData) {
        return Optional.ofNullable(cartData.getPaymentInfo())
                .map(CCPaymentInfoData::getBillingAddress)
                .or(() -> Optional.ofNullable(cartData.getDeliveryAddress()))
                .map(AddressData::getCountry)
                .map(CountryData::getIsocode)
                .orElse("");
    }
}