package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.BrowserInfo;
import com.adyen.model.checkout.PaymentRequest;
import com.google.gson.Gson;
import de.hybris.platform.commercefacades.order.data.CartData;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Optional;

/**
 * Utility class for enhancing payment requests with 3DS2 data
 */
public class ThreeDSEnhancer {

    private static final Gson gson = new Gson();

    /**
     * Enhances the payment request with 3DS2 browser information
     */
    public static PaymentRequest enhance3DS2(PaymentRequest paymentRequest, CartData cartData) {
        if (paymentRequest == null || cartData == null) {
            return paymentRequest;
        }

        BrowserInfo browserInfo = createBrowserInfo(paymentRequest, cartData);
        
        paymentRequest.setAdditionalData(
            Optional.ofNullable(paymentRequest.getAdditionalData())
                .orElse(new HashMap<>())
        );
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentRequest.setBrowserInfo(browserInfo);

        return paymentRequest;
    }

    private static BrowserInfo createBrowserInfo(PaymentRequest paymentRequest, CartData cartData) {
        BrowserInfo existingBrowserInfo = paymentRequest.getBrowserInfo();
        BrowserInfo cartBrowserInfo = parseBrowserInfoFromCart(cartData);
        
        BrowserInfo browserInfo = Optional.ofNullable(cartBrowserInfo)
            .orElse(new BrowserInfo());

        // Preserve existing browser info if available
        if (existingBrowserInfo != null) {
            if (StringUtils.isNotEmpty(existingBrowserInfo.getAcceptHeader())) {
                browserInfo.acceptHeader(existingBrowserInfo.getAcceptHeader());
            }
            if (StringUtils.isNotEmpty(existingBrowserInfo.getUserAgent())) {
                browserInfo.userAgent(existingBrowserInfo.getUserAgent());
            }
        }

        return browserInfo;
    }

    private static BrowserInfo parseBrowserInfoFromCart(CartData cartData) {
        if (StringUtils.isEmpty(cartData.getAdyenBrowserInfo())) {
            return null;
        }

        try {
            return gson.fromJson(cartData.getAdyenBrowserInfo(), BrowserInfo.class);
        } catch (Exception e) {
            // Log error and return null if parsing fails
            return null;
        }
    }
}