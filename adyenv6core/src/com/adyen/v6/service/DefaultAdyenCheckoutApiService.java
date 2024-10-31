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
import com.adyen.commerce.services.impl.DefaultAdyenRequestService;
import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.CreateCheckoutSessionRequest;
import com.adyen.model.checkout.CreateCheckoutSessionResponse;
import com.adyen.model.checkout.PaymentCompletionDetails;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.PaymentMethod;
import com.adyen.model.checkout.PaymentMethodsRequest;
import com.adyen.model.checkout.PaymentMethodsResponse;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.recurring.DisableRequest;
import com.adyen.model.recurring.DisableResult;
import com.adyen.model.recurring.RecurringDetail;
import com.adyen.model.recurring.RecurringDetailWrapper;
import com.adyen.model.recurring.RecurringDetailsRequest;
import com.adyen.model.recurring.RecurringDetailsResult;
import com.adyen.model.terminal.ConnectedTerminalsRequest;
import com.adyen.model.terminal.ConnectedTerminalsResponse;
import com.adyen.model.terminal.TerminalAPIRequest;
import com.adyen.model.terminal.TerminalAPIResponse;
import com.adyen.service.PosPayment;
import com.adyen.service.RecurringApi;
import com.adyen.service.TerminalCloudAPI;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import com.adyen.terminal.serialization.TerminalAPIGsonBuilder;
import com.adyen.v6.enums.RecurringContractMode;
import com.adyen.v6.factory.AdyenRequestFactory;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;
import com.adyen.v6.util.AmountUtil;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefaultAdyenCheckoutApiService extends AbstractAdyenApiService implements AdyenCheckoutApiService {

    private static final Logger LOG = Logger.getLogger(DefaultAdyenCheckoutApiService.class);

    public DefaultAdyenCheckoutApiService(BaseStoreModel baseStore, String merchantAccount, AdyenRequestService adyenRequestService) {
        super(baseStore, merchantAccount, adyenRequestService);
    }

    @Override
    public ConnectedTerminalsResponse getConnectedTerminals() throws IOException, ApiException {
        LOG.debug("Get connected terminals");
        PosPayment posPayment = new PosPayment(posClient);
        ConnectedTerminalsRequest connectedTerminalsRequest = new ConnectedTerminalsRequest();

        connectedTerminalsRequest.setMerchantAccount(merchantAccount);
        if (baseStore.getAdyenPosStoreId() != null && StringUtils.isNotEmpty(baseStore.getAdyenPosStoreId())) {
            connectedTerminalsRequest.setStore(baseStore.getAdyenPosStoreId());
        }
        LOG.debug(connectedTerminalsRequest);
        ConnectedTerminalsResponse connectedTerminalsResponse = posPayment.connectedTerminals(connectedTerminalsRequest);
        LOG.debug(connectedTerminalsResponse);
        return connectedTerminalsResponse;

    }

    @Override
    public PaymentResponse processPaymentRequest(final CartData cartData, PaymentRequest originPaymentsRequest, final RequestInfo requestInfo, final CustomerModel customerModel) throws Exception {
        LOG.debug("Component payment");

        PaymentsApi checkoutApi = new PaymentsApi(client);

        PaymentRequest paymentsRequest = getAdyenRequestFactory().createPaymentsRequest(merchantAccount,
                cartData,
                originPaymentsRequest,
                requestInfo,
                customerModel, baseStore.getAdyenRecurringContractMode(), baseStore.getAdyenGuestUserTokenization());

        adyenRequestService.applyAdditionalData(cartData, paymentsRequest);

        LOG.debug(paymentsRequest);
        PaymentResponse paymentsResponse = checkoutApi.payments(paymentsRequest);
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
                                                 final String shopperReference) throws IOException, ApiException {

        final PaymentMethodsResponse response = getPaymentMethodsResponse(amount, currency, countryCode, shopperLocale, shopperReference);
        return response.getPaymentMethods();
    }

    @Override
    public PaymentMethodsResponse getPaymentMethodsResponse(final BigDecimal amount,
                                                            final String currency,
                                                            final String countryCode,
                                                            final String shopperLocale,
                                                            final String shopperReference) throws IOException, ApiException {
        return getPaymentMethodsResponse(amount, currency, countryCode, shopperLocale, shopperReference, null);
    }

    @Override
    public PaymentMethodsResponse getPaymentMethodsResponse(final BigDecimal amount,
                                                            final String currency,
                                                            final String countryCode,
                                                            final String shopperLocale,
                                                            final String shopperReference,
                                                            final List<String> excludedPaymentMethods) throws IOException, ApiException {
        LOG.debug("Get payment methods response");

        PaymentsApi checkout = new PaymentsApi(client);
        PaymentMethodsRequest request = new PaymentMethodsRequest();
        request.merchantAccount(merchantAccount)
                .amount(AmountUtil.createAmount(amount, currency))
                .countryCode(countryCode);

        if (!StringUtils.isEmpty(shopperLocale)) {
            request.setShopperLocale(shopperLocale);
        }

        if (!StringUtils.isEmpty(shopperReference)) {
            request.setShopperReference(shopperReference);
        }

        if (CollectionUtils.isNotEmpty(excludedPaymentMethods)) {
            request.setBlockedPaymentMethods(excludedPaymentMethods);
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
            return getPaymentMethods(amount, currency, countryCode, shopperLocale, null);
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

        RecurringDetailsRequest request = getAdyenRequestFactory().createListRecurringDetailsRequest(merchantAccount, customerId);

        LOG.debug(request);
        RecurringDetailsResult result = recurring.listRecurringDetails(request);
        LOG.debug(result);

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

        DisableRequest request = getAdyenRequestFactory().createDisableRequest(merchantAccount, customerId, recurringReference);

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
    public CreateCheckoutSessionResponse getPaymentSessionData(final CartData cartData) throws IOException, ApiException {
        final PaymentsApi checkout = new PaymentsApi(client);
        final PriceData totalPriceWithTax = cartData.getTotalPriceWithTax();

        final CreateCheckoutSessionRequest createCheckoutSessionRequest = new CreateCheckoutSessionRequest();
        createCheckoutSessionRequest.amount(AmountUtil.createAmount(totalPriceWithTax.getValue(), totalPriceWithTax.getCurrencyIso()));
        createCheckoutSessionRequest.merchantAccount(merchantAccount);
        if (cartData.getDeliveryAddress() != null) {
            createCheckoutSessionRequest.countryCode(cartData.getDeliveryAddress().getCountry().getIsocode());
        }
        createCheckoutSessionRequest.returnUrl(Optional.ofNullable(cartData.getAdyenReturnUrl()).orElse("returnUrl"));
        createCheckoutSessionRequest.reference(cartData.getCode());

        return checkout.sessions(createCheckoutSessionRequest);
    }

    @Override
    public CreateCheckoutSessionResponse getPaymentSessionData(final Amount amount) throws IOException, ApiException {
        final PaymentsApi checkout = new PaymentsApi(client);

        final CreateCheckoutSessionRequest createCheckoutSessionRequest = new CreateCheckoutSessionRequest();
        createCheckoutSessionRequest.amount(amount);
        createCheckoutSessionRequest.merchantAccount(merchantAccount);
        createCheckoutSessionRequest.returnUrl("returnUrl"); //dummy url because it's required by api
        createCheckoutSessionRequest.reference("reference"); //dummy reference because it's required by api

        return checkout.sessions(createCheckoutSessionRequest);
    }

    @Override
    public String getDeviceFingerprintUrl() {
        DateFormat df = new SimpleDateFormat("yyyyMMdd");
        Date today = Calendar.getInstance().getTime();
        return "https://live.adyen.com/hpp/js/df.js?v=" + df.format(today);
    }

    /**
     * Send POS Payment Request using Adyen Terminal API
     */
    @Override
    public TerminalAPIResponse sendSyncPosPaymentRequest(CartData cartData, CustomerModel customer, String serviceId) throws Exception {
        LOG.debug("Send sync pos payment request");

        TerminalCloudAPI terminalCloudAPI = new TerminalCloudAPI(posClient);

        RecurringContractMode recurringContractMode = getBaseStore().getAdyenPosRecurringContractMode();
        TerminalAPIRequest terminalApiRequest = adyenRequestFactory.createTerminalAPIRequest(cartData, customer, recurringContractMode, serviceId);

        LOG.debug(TerminalAPIGsonBuilder.create().toJson(terminalApiRequest));
        TerminalAPIResponse terminalApiResponse = terminalCloudAPI.sync(terminalApiRequest);

        LOG.debug(TerminalAPIGsonBuilder.create().toJson(terminalApiResponse));
        return terminalApiResponse;
    }

    /**
     * Send POS Status Request using Adyen Terminal API
     */
    @Override
    public TerminalAPIResponse sendSyncPosStatusRequest(CartData cartData, String originalServiceId) throws Exception {
        LOG.debug("Send sync pos status request");

        TerminalCloudAPI terminalCloudAPI = new TerminalCloudAPI(posClient);

        TerminalAPIRequest terminalApiRequest = adyenRequestFactory.createTerminalAPIRequestForStatus(cartData, originalServiceId);

        LOG.debug(TerminalAPIGsonBuilder.create().toJson(terminalApiRequest));
        TerminalAPIResponse terminalApiResponse = terminalCloudAPI.sync(terminalApiRequest);

        LOG.debug(TerminalAPIGsonBuilder.create().toJson(terminalApiResponse));
        return terminalApiResponse;
    }
}
