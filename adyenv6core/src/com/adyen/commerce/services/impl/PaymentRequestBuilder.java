package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.BrowserInfo;
import com.adyen.model.checkout.Company;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.util.AmountUtil;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.user.CustomerModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.HashMap;

/**
 * Builder class for creating PaymentRequest objects with fluent API
 */
public class PaymentRequestBuilder {
    private final PaymentRequest paymentRequest;

    public PaymentRequestBuilder() {
        this.paymentRequest = new PaymentRequest();
    }

    public PaymentRequestBuilder merchantAccount(String merchantAccount) {
        paymentRequest.setMerchantAccount(merchantAccount);
        return this;
    }

    public PaymentRequestBuilder amount(CartData cartData) {
        if (cartData.getTotalPriceWithTax() != null) {
            paymentRequest.setAmount(AmountUtil.createAmount(
                cartData.getTotalPriceWithTax().getValue(),
                cartData.getTotalPriceWithTax().getCurrencyIso()
            ));
        }
        return this;
    }

    public PaymentRequestBuilder reference(String reference) {
        paymentRequest.setReference(reference);
        return this;
    }

    public PaymentRequestBuilder browserInfo(String userAgent, String acceptHeader) {
        paymentRequest.setBrowserInfo(new BrowserInfo()
            .userAgent(userAgent)
            .acceptHeader(acceptHeader));
        return this;
    }

    public PaymentRequestBuilder shopperDetails(CustomerModel customerModel) {
        if (customerModel != null) {
            paymentRequest.setShopperReference(customerModel.getCustomerID());
            paymentRequest.setShopperEmail(customerModel.getContactEmail());
        }
        return this;
    }

    public PaymentRequestBuilder requestInfo(RequestInfo requestInfo) {
        if (requestInfo != null) {
            paymentRequest.setShopperIP(requestInfo.getShopperIp());
            paymentRequest.setOrigin(requestInfo.getOrigin());
            paymentRequest.setShopperLocale(requestInfo.getShopperLocale());
        }
        return this;
    }

    public PaymentRequestBuilder returnUrl(String returnUrl) {
        if (StringUtils.isNotEmpty(returnUrl)) {
            paymentRequest.setReturnUrl(returnUrl);
        }
        return this;
    }

    public PaymentRequestBuilder redirectMethods() {
        paymentRequest.setRedirectFromIssuerMethod(RequestMethod.POST.toString());
        paymentRequest.setRedirectToIssuerMethod(RequestMethod.POST.toString());
        return this;
    }

    public PaymentRequestBuilder telephoneNumber(String phoneNumber) {
        if (StringUtils.isNotEmpty(phoneNumber)) {
            paymentRequest.setTelephoneNumber(phoneNumber);
        }
        return this;
    }

    public PaymentRequestBuilder countryCode(String countryCode) {
        if (StringUtils.isNotEmpty(countryCode)) {
            paymentRequest.setCountryCode(countryCode);
        }
        return this;
    }

    public PaymentRequestBuilder company(Company company) {
        if (company != null) {
            paymentRequest.setCompany(company);
        }
        return this;
    }

    public PaymentRequestBuilder additionalData(String key, String value) {
        if (paymentRequest.getAdditionalData() == null) {
            paymentRequest.setAdditionalData(new HashMap<>());
        }
        paymentRequest.getAdditionalData().put(key, value);
        return this;
    }

    public PaymentRequestBuilder channel(PaymentRequest.ChannelEnum channel) {
        paymentRequest.setChannel(channel);
        return this;
    }

    public PaymentRequestBuilder shopperConversionId(String shopperConversionId) {
        paymentRequest.setShopperConversionId(shopperConversionId);
        return this;
    }

    public PaymentRequest build() {
        return paymentRequest;
    }
}