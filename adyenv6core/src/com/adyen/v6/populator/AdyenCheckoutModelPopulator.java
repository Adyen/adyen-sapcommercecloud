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
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.populator;

import com.adyen.v6.dto.CheckoutConfigDTO;
import com.adyen.v6.dto.ExpressCheckoutConfigDTO;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ui.Model;

import java.util.Objects;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_SEPA_DIRECTDEBIT;
import static com.adyen.v6.constants.Adyenv6coreConstants.SHOPPER_LOCALE;

/**
 * Populates a Spring MVC {@link Model} with Adyen checkout configuration attributes
 * for the Accelerator storefront.
 * <p>
 * Separates UI model population from the facade's business logic.
 */
public class AdyenCheckoutModelPopulator {

    // Model attribute keys — kept here to co-locate them with the code that uses them
    public static final String MODEL_SELECTED_PAYMENT_METHOD = "selectedPaymentMethod";
    public static final String MODEL_PAYMENT_METHODS = "paymentMethods";
    public static final String MODEL_CREDIT_CARD_LABEL = "creditCardLabel";
    public static final String MODEL_ALLOWED_CARDS = "allowedCards";
    public static final String MODEL_REMEMBER_DETAILS = "showRememberTheseDetails";
    public static final String MODEL_STORED_CARDS = "storedCards";
    public static final String MODEL_DF_URL = "dfUrl";
    public static final String MODEL_CLIENT_KEY = "clientKey";
    public static final String MODEL_MERCHANT_ACCOUNT = "merchantAccount";
    public static final String MODEL_CHECKOUT_SHOPPER_HOST = "checkoutShopperHost";
    public static final String MODEL_OPEN_INVOICE_METHODS = "openInvoiceMethods";
    public static final String MODEL_SHOW_SOCIAL_SECURITY_NUMBER = "showSocialSecurityNumber";
    public static final String MODEL_SHOW_BOLETO = "showBoleto";
    public static final String MODEL_SHOW_COMBO_CARD = "showComboCard";
    public static final String MODEL_ISSUER_LISTS = "issuerLists";
    public static final String MODEL_ENVIRONMENT_MODE = "environmentMode";
    public static final String MODEL_AMOUNT = "amount";
    public static final String MODEL_AMOUNT_DECIMAL = "amountDecimal";
    public static final String MODEL_IMMEDIATE_CAPTURE = "immediateCapture";
    public static final String MODEL_PAYPAL_MERCHANT_ID = "paypalMerchantId";
    public static final String MODEL_PAYPAL_INTENT = "paypalIntent";
    public static final String MODEL_COUNTRY_CODE = "countryCode";
    public static final String MODEL_APPLEPAY_MERCHANT_IDENTIFIER = "applePayMerchantIdentifier";
    public static final String MODEL_APPLEPAY_MERCHANT_NAME = "applePayMerchantName";
    public static final String MODEL_GOOGLEPAY_MERCHANT_ID = "googlePayMerchantId";
    public static final String MODEL_GOOGLEPAY_GATEWAY_MERCHANT_ID = "googlePayGatewayMerchantId";
    public static final String MODEL_AMAZONPAY_CONFIGURATION = "amazonPayConfiguration";
    public static final String MODEL_DELIVERY_ADDRESS = "deliveryAddress";
    public static final String MODEL_GIFT_CARD_BRAND = "giftCardBrand";
    public static final String MODEL_CARD_HOLDER_NAME_REQUIRED = "cardHolderNameRequired";
    public static final String EXPRESS_PAYMENT_CONFIG = "expressPaymentConfig";
    public static final String LOCALE = "locale";

    /**
     * Populates the model for the main checkout page (Accelerator legacy flow).
     *
     * @param model             the Spring MVC model
     * @param checkoutConfigDTO the checkout configuration DTO
     */
    public void populateCheckoutData(final Model model, final CheckoutConfigDTO checkoutConfigDTO) {
        model.addAttribute(MODEL_SELECTED_PAYMENT_METHOD, checkoutConfigDTO.getSelectedPaymentMethod());
        model.addAttribute(MODEL_PAYMENT_METHODS, checkoutConfigDTO.getAlternativePaymentMethods());
        model.addAttribute(MODEL_CREDIT_CARD_LABEL, checkoutConfigDTO.getCreditCardLabel());
        model.addAttribute(MODEL_ALLOWED_CARDS, checkoutConfigDTO.getAllowedCards());
        model.addAttribute(MODEL_REMEMBER_DETAILS, checkoutConfigDTO.isShowRememberTheseDetails());
        model.addAttribute(MODEL_STORED_CARDS, checkoutConfigDTO.getStoredPaymentMethodList());
        model.addAttribute(MODEL_DF_URL, checkoutConfigDTO.getDeviceFingerPrintUrl());
        model.addAttribute(MODEL_CHECKOUT_SHOPPER_HOST, checkoutConfigDTO.getCheckoutShopperHost());
        model.addAttribute(MODEL_ENVIRONMENT_MODE, checkoutConfigDTO.getEnvironmentMode());
        model.addAttribute(SHOPPER_LOCALE, checkoutConfigDTO.getShopperLocale());
        model.addAttribute("merchantDisplayName", checkoutConfigDTO.getMerchantDisplayName());
        model.addAttribute("shopperEmail", checkoutConfigDTO.getShopperEmail());
        model.addAttribute(MODEL_OPEN_INVOICE_METHODS, checkoutConfigDTO.getOpenInvoiceMethods());
        model.addAttribute(MODEL_SHOW_SOCIAL_SECURITY_NUMBER, checkoutConfigDTO.isShowSocialSecurityNumber());
        model.addAttribute(MODEL_SHOW_BOLETO, checkoutConfigDTO.isShowBoleto());
        model.addAttribute(MODEL_SHOW_COMBO_CARD, checkoutConfigDTO.isShowComboCard());
        model.addAttribute(MODEL_ISSUER_LISTS, checkoutConfigDTO.getIssuerLists());
        model.addAttribute(MODEL_CLIENT_KEY, checkoutConfigDTO.getAdyenClientKey());
        model.addAttribute(MODEL_AMOUNT, checkoutConfigDTO.getAmount());
        model.addAttribute(MODEL_IMMEDIATE_CAPTURE, checkoutConfigDTO.isImmediateCapture());
        model.addAttribute(MODEL_PAYPAL_MERCHANT_ID, checkoutConfigDTO.getAdyenPaypalMerchantId());
        model.addAttribute(MODEL_COUNTRY_CODE, checkoutConfigDTO.getCountryCode());
        model.addAttribute(MODEL_CARD_HOLDER_NAME_REQUIRED, checkoutConfigDTO.isCardHolderNameRequired());
        model.addAttribute(PAYMENT_METHOD_SEPA_DIRECTDEBIT, checkoutConfigDTO.isSepaDirectDebit());
    }

    /**
     * Populates the model for the express checkout cart page and PDP.
     *
     * @param model                    the Spring MVC model
     * @param expressCheckoutConfigDTO the express checkout configuration DTO
     */
    public void populateExpressCheckoutData(final Model model, final ExpressCheckoutConfigDTO expressCheckoutConfigDTO) {
        if (StringUtils.isNotEmpty(expressCheckoutConfigDTO.getApplePayMerchantId())) {
            model.addAttribute(MODEL_APPLEPAY_MERCHANT_IDENTIFIER, expressCheckoutConfigDTO.getApplePayMerchantId());
        }
        if (StringUtils.isNotEmpty(expressCheckoutConfigDTO.getApplePayMerchantName())) {
            model.addAttribute(MODEL_APPLEPAY_MERCHANT_NAME, expressCheckoutConfigDTO.getApplePayMerchantName());
        }
        if (expressCheckoutConfigDTO.getExpressPaymentConfig() != null) {
            model.addAttribute(EXPRESS_PAYMENT_CONFIG, expressCheckoutConfigDTO.getExpressPaymentConfig());
        }
        if (expressCheckoutConfigDTO.getPayPalIntent() != null) {
            model.addAttribute(MODEL_PAYPAL_INTENT, expressCheckoutConfigDTO.getPayPalIntent());
        }
        if (StringUtils.isNotEmpty(expressCheckoutConfigDTO.getGooglePayMerchantId())) {
            model.addAttribute(MODEL_GOOGLEPAY_MERCHANT_ID, expressCheckoutConfigDTO.getGooglePayMerchantId());
        }
        if (StringUtils.isNotEmpty(expressCheckoutConfigDTO.getGooglePayGatewayMerchantId())) {
            model.addAttribute(MODEL_GOOGLEPAY_GATEWAY_MERCHANT_ID, expressCheckoutConfigDTO.getGooglePayGatewayMerchantId());
        }
        model.addAttribute(SHOPPER_LOCALE, expressCheckoutConfigDTO.getShopperLocale());
        model.addAttribute(MODEL_ENVIRONMENT_MODE, expressCheckoutConfigDTO.getEnvironmentMode());
        model.addAttribute(MODEL_CLIENT_KEY, expressCheckoutConfigDTO.getClientKey());
        model.addAttribute(MODEL_MERCHANT_ACCOUNT, expressCheckoutConfigDTO.getMerchantAccount());
        model.addAttribute(MODEL_AMOUNT, expressCheckoutConfigDTO.getAmount());
        model.addAttribute(MODEL_AMOUNT_DECIMAL, expressCheckoutConfigDTO.getAmountDecimal());
        model.addAttribute(MODEL_DF_URL, expressCheckoutConfigDTO.getDfUrl());
        model.addAttribute(MODEL_CHECKOUT_SHOPPER_HOST, expressCheckoutConfigDTO.getCheckoutShopperHost());
    }
}
