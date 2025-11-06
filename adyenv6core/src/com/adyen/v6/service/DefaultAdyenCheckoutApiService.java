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
package com.adyen.v6.service;

import com.adyen.commerce.services.AdyenRequestService;
import com.adyen.model.checkout.*;
import com.adyen.model.recurring.*;
import com.adyen.service.RecurringApi;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.util.AmountUtil;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.store.BaseStoreModel;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultAdyenCheckoutApiService extends AbstractAdyenApiService implements AdyenCheckoutApiService {

    private static final Logger LOG = Logger.getLogger(DefaultAdyenCheckoutApiService.class);

    public DefaultAdyenCheckoutApiService(BaseStoreModel baseStore, String merchantAccount, AdyenRequestService adyenRequestService) {
        super(baseStore, merchantAccount, adyenRequestService);
    }

    @Override
    public PaymentResponse processPaymentRequest(final CartData cartData, PaymentRequest originPaymentsRequest, final RequestInfo requestInfo, final CustomerModel customerModel) throws Exception {
        LOG.debug("Component payment");

        PaymentsApi checkoutApi = new PaymentsApi(client);

        PaymentRequest paymentsRequest = adyenRequestService.createPaymentsRequest(merchantAccount,
                cartData,
                originPaymentsRequest,
                requestInfo,
                customerModel, baseStore.getAdyenRecurringContractMode(), baseStore.getAdyenGuestUserTokenization(), null);

        adyenRequestService.applyAdditionalData(cartData, paymentsRequest);

        LOG.debug(paymentsRequest);
        PaymentResponse paymentsResponse = checkoutApi.payments(paymentsRequest);
        LOG.debug(paymentsResponse);

        return paymentsResponse;
    }

    /**
     * Process partial payment request with custom amount
     */
    @Override
    public PaymentResponse processPartialPaymentRequest(final CartData cartData,
                                                       PaymentRequest originPaymentsRequest,
                                                       final RequestInfo requestInfo,
                                                       final CustomerModel customerModel,
                                                       final BigDecimal customAmount,
                                                       final String currency) throws Exception {
        LOG.debug("Processing partial payment with custom amount: " + customAmount + " " + currency);

        PaymentsApi checkoutApi = new PaymentsApi(client);


        PaymentRequest paymentsRequest = adyenRequestService.createPartialPaymentRequest(merchantAccount,
                cartData,
                originPaymentsRequest,
                requestInfo,
                customerModel,
                baseStore.getAdyenRecurringContractMode(),
                baseStore.getAdyenGuestUserTokenization(),
                customAmount,
                currency);

        adyenRequestService.applyAdditionalData(cartData, paymentsRequest);

        LOG.debug(paymentsRequest);
        PaymentResponse paymentsResponse = checkoutApi.payments(paymentsRequest);
        LOG.debug(paymentsResponse);

        return paymentsResponse;
    }

    public PaymentResponse sendPaymentRequest(final PaymentRequest paymentRequest, final RequestInfo requestInfo) throws IOException, ApiException {
        PaymentsApi checkoutApi = new PaymentsApi(client);

        adyenRequestService.decoratePayPalSubmitPaymentRequest(merchantAccount, paymentRequest, requestInfo);

        LOG.debug(paymentRequest);
        PaymentResponse paymentsResponse = checkoutApi.payments(paymentRequest);
        LOG.debug(paymentsResponse);

        return paymentsResponse;
    }

    @Override
    public PaymentDetailsResponse authorise3DSPayment(PaymentDetailsRequest paymentsDetailsRequest) throws Exception {
        LOG.debug("Authorize 3DS payment");

        PaymentsApi checkout = new PaymentsApi(client);

        LOG.debug(paymentsDetailsRequest);
        PaymentDetailsResponse paymentsDetailsResponse = checkout.paymentsDetails(paymentsDetailsRequest);
        LOG.debug(paymentsDetailsResponse);

        return paymentsDetailsResponse;
    }


    @Override
    public List<PaymentMethod> getPaymentMethods(final BigDecimal amount,
                                                 final String currency,
                                                 final String countryCode,
                                                 final String shopperLocale,
                                                 final String shopperReference,
                                                 final String shopperConversionId) throws IOException, ApiException {

        final PaymentMethodsResponse response = getPaymentMethodsResponse(amount, currency, countryCode, shopperLocale, shopperReference, shopperConversionId);
        return response.getPaymentMethods();
    }

    @Override
    public PaymentMethodsResponse getPaymentMethodsResponse(final BigDecimal amount,
                                                            final String currency,
                                                            final String countryCode,
                                                            final String shopperLocale,
                                                            final String shopperReference,
                                                            final String shopperConversionId) throws IOException, ApiException {
        return getPaymentMethodsResponse(amount, currency, countryCode, shopperLocale, shopperReference, null, null, shopperConversionId);
    }

    @Override
    public PaymentMethodsResponse getPaymentMethodsResponse(final BigDecimal amount,
                                                            final String currency,
                                                            final String countryCode,
                                                            final String shopperLocale,
                                                            final String shopperReference,
                                                            final List<String> excludedPaymentMethods,
                                                            final List<String> allowedPaymentMethods,
                                                            final String shopperConversionId) throws IOException, ApiException {
        LOG.debug("Get payment methods response");

        PaymentsApi checkout = new PaymentsApi(client);
        PaymentMethodsRequest request = new PaymentMethodsRequest();
        request.merchantAccount(merchantAccount)
                .amount(AmountUtil.createAmount(amount, currency))
                .countryCode(countryCode);

        if (StringUtils.isNotEmpty(shopperLocale)) {
            request.setShopperLocale(shopperLocale);
        } else {
            LOG.warn("Empty shopper locale");
        }

        if (StringUtils.isNotEmpty(shopperReference)) {
            request.setShopperReference(shopperReference);
        } else {
            LOG.warn("Empty shopper reference");
        }

        if (CollectionUtils.isNotEmpty(excludedPaymentMethods)) {
            request.setBlockedPaymentMethods(excludedPaymentMethods);
        }

        if (CollectionUtils.isNotEmpty(allowedPaymentMethods)) {
            request.setAllowedPaymentMethods(allowedPaymentMethods);
        }

        if (StringUtils.isNotEmpty(shopperConversionId)) {
            request.setShopperConversionId(shopperConversionId);
        } else {
            LOG.warn("Empty shopper conversion id");
        }

        LOG.debug(request);
        final PaymentMethodsResponse response = checkout.paymentMethods(request);
        LOG.debug(response);

        return response;
    }

    @Override
    @Deprecated
    public List<PaymentMethod> getPaymentMethods(final BigDecimal amount,
                                                 final String currency,
                                                 final String countryCode,
                                                 final String shopperLocale) throws IOException {
        try {
            return getPaymentMethods(amount, currency, countryCode, shopperLocale, null, null);
        } catch (ApiException e) {
            LOG.error(e);
        }
        return null;
    }

    @Override
    @Deprecated
    public List<RecurringDetail> getStoredCards(final String customerId) throws IOException, ApiException {
        LOG.debug("Get stored cards");

        if (customerId == null) {
            LOG.info("Customer id is null");
            return new ArrayList<>();
        }

        RecurringApi recurring = new RecurringApi(client);

        RecurringDetailsRequest request = adyenRequestService.createListRecurringDetailsRequest(merchantAccount, customerId);

        LOG.debug(request);
        RecurringDetailsResult result = recurring.listRecurringDetails(request);
        LOG.debug(result);

        if (result.getDetails() == null || result.getDetails().isEmpty()) {
            return new ArrayList<>();
        }

        //Return only cards
        return result.getDetails()
                .stream()
                .map(RecurringDetailWrapper::getRecurringDetail)
                .filter(detail -> (detail.getCard() != null && detail.getRecurringDetailReference() != null))
                .collect(Collectors.toList());
    }

    @Override
    public boolean disableStoredCard(final String customerId, final String recurringReference) throws IOException, ApiException {
        LOG.debug("Disable stored card");

        RecurringApi recurring = new RecurringApi(client);

        DisableRequest request = adyenRequestService.createDisableRequest(merchantAccount, customerId, recurringReference);

        LOG.debug(request);
        DisableResult result = recurring.disable(request);
        LOG.debug(result);

        return ("[detail-successfully-disabled]".equals(result.getResponse()) || "[all-details-successfully-disabled]".equals(result.getResponse()));
    }

    @Override
    public PaymentDetailsResponse getPaymentDetailsFromPayload(PaymentCompletionDetails details) throws Exception {
        LOG.debug("Get payment details from payload");

        PaymentsApi checkout = new PaymentsApi(client);

        PaymentDetailsRequest paymentsDetailsRequest = new PaymentDetailsRequest();
        paymentsDetailsRequest.setDetails(details);

        LOG.debug(paymentsDetailsRequest);
        PaymentDetailsResponse paymentsResponse = checkout.paymentsDetails(paymentsDetailsRequest);
        LOG.debug(paymentsResponse);

        return paymentsResponse;
    }

    @Override
    public PaymentDetailsResponse getPaymentDetailsFromPayload(PaymentDetailsRequest detailsRequest) throws Exception {
        LOG.debug("Get payment details from payload");

        PaymentsApi checkout = new PaymentsApi(client);

        LOG.debug(detailsRequest);
        PaymentDetailsResponse paymentsResponse = checkout.paymentsDetails(detailsRequest);
        LOG.debug(paymentsResponse);

        return paymentsResponse;
    }

    @Override
    public String getDeviceFingerprintUrl() {
        DateFormat df = new SimpleDateFormat("yyyyMMdd");
        Date today = Calendar.getInstance().getTime();
        return "https://live.adyen.com/hpp/js/df.js?v=" + df.format(today);
    }
}
