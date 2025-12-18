package com.adyen.commerce.service.impl;

import com.adyen.commerce.handler.SubscriptionPaymentMethodHandler;
import com.adyen.commerce.handler.factory.SubscriptionPaymentMethodsHandlerFactory;
import com.adyen.commerce.service.SubscriptionAdyenCheckoutApiService;
import com.adyen.commerce.services.impl.AddressConverter;
import com.adyen.commerce.services.impl.ApplicationInfoService;
import com.adyen.commerce.services.impl.PaymentRequestBuilder;
import com.adyen.commerce.util.AddressUtil;
import com.adyen.model.RequestOptions;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.StorefrontType;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.service.AbstractAdyenApiService;
import com.adyen.v6.util.AmountUtil;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.store.BaseStoreModel;
import org.apache.log4j.Logger;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

public class DefaultSubscriptionAdyenCheckoutApiService extends AbstractAdyenApiService implements SubscriptionAdyenCheckoutApiService {
    private static final Logger LOG = Logger.getLogger(DefaultSubscriptionAdyenCheckoutApiService.class);

    private final SubscriptionPaymentMethodsHandlerFactory subscriptionPaymentMethodsHandlerFactory;
    private final ApplicationInfoService applicationInfoService;
    private final Converter<AddressModel, AddressData> addressConverter;


    public DefaultSubscriptionAdyenCheckoutApiService(BaseStoreModel baseStore, String merchantAccount, ApplicationInfoService applicationInfoService, Converter<AddressModel, AddressData> addressConverter, RetryTemplate adyenCustomerInteractionRetryTemplate, RetryTemplate adyenBackgroundProcessRetryTemplate) {
        super(baseStore, merchantAccount, null, adyenCustomerInteractionRetryTemplate, adyenBackgroundProcessRetryTemplate);
        this.applicationInfoService = applicationInfoService;
        this.subscriptionPaymentMethodsHandlerFactory = new SubscriptionPaymentMethodsHandlerFactory();
        this.addressConverter = addressConverter;
    }


    public PaymentResponse processPaymentRequest(final AbstractOrderModel subscriptionOrder) throws Exception {
        LOG.debug("Subscription payment");

        PaymentsApi checkoutApi = new PaymentsApi(client);

        final RequestInfo requestInfo = new RequestInfo();
        requestInfo.setStorefrontType(StorefrontType.SUBSCRIPTION);

        PaymentRequest paymentRequest = buildBasePaymentRequest(subscriptionOrder, requestInfo);

        RequestOptions requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        return adyenBackgroundProcessRetryTemplate.execute(context -> {
            LOG.debug(paymentRequest);
            PaymentResponse paymentsResponse = checkoutApi.payments(paymentRequest, requestOptions);
            LOG.debug(paymentsResponse);

            return paymentsResponse;
        });
    }

    public PaymentResponse processPaymentRequest(final AbstractOrderModel subscriptionOrder, final AbstractOrderModel onFirstBillOrder) throws Exception {
        LOG.debug("Subscription + onFirstBill payment");

        PaymentsApi checkoutApi = new PaymentsApi(client);

        final RequestInfo requestInfo = new RequestInfo();
        requestInfo.setStorefrontType(StorefrontType.SUBSCRIPTION);

        PaymentRequest paymentRequest = buildBasePaymentRequest(subscriptionOrder, onFirstBillOrder, requestInfo);

        RequestOptions requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        return adyenBackgroundProcessRetryTemplate.execute(context -> {
            LOG.debug(paymentRequest);
            PaymentResponse paymentsResponse = checkoutApi.payments(paymentRequest, requestOptions);
            LOG.debug(paymentsResponse);

            return paymentsResponse;
        });
    }


    protected PaymentRequest buildBasePaymentRequest(AbstractOrderModel subscriptionOrderModel, RequestInfo requestInfo) {

        BigDecimal totalPrice = BigDecimal.valueOf(subscriptionOrderModel.getTotalPrice());

        return buildBasePaymentRequestInternal(subscriptionOrderModel, requestInfo, totalPrice);
    }

    protected PaymentRequest buildBasePaymentRequest(AbstractOrderModel subscriptionOrderModel, AbstractOrderModel firstBillOrderModel, RequestInfo requestInfo) {

        BigDecimal subscriptionAmount = AmountUtil.calculateAmountWithTaxes(subscriptionOrderModel);
        BigDecimal firstBillAmount = AmountUtil.calculateAmountWithTaxes(firstBillOrderModel);

        if (!subscriptionOrderModel.getCurrency().getIsocode().equals(firstBillOrderModel.getCurrency().getIsocode())) {
            throw new IllegalArgumentException("Currency mismatch for subscription and first bill order");
        }

        BigDecimal totalPrice = subscriptionAmount.setScale(2, RoundingMode.HALF_EVEN).add(firstBillAmount);

        return buildBasePaymentRequestInternal(subscriptionOrderModel, requestInfo, totalPrice);
    }

    protected PaymentRequest buildBasePaymentRequestInternal(AbstractOrderModel subscriptionOrderModel, RequestInfo requestInfo, BigDecimal totalPrice) {

        AddressData deliveryAddressData = addressConverter.convert(subscriptionOrderModel.getDeliveryAddress());
        AddressData billingAddressData = addressConverter.convert(subscriptionOrderModel.getPaymentInfo().getBillingAddress());

        PaymentRequestBuilder builder = new PaymentRequestBuilder()
                .merchantAccount(merchantAccount)
                .amount(totalPrice, subscriptionOrderModel.getCurrency().getIsocode())
                .reference(subscriptionOrderModel.getCode())
                .shopperDetails((CustomerModel) subscriptionOrderModel.getUser())
                .countryCode(AddressUtil.getCountryCode(billingAddressData, deliveryAddressData))
                .requestInfo(requestInfo);


        PaymentRequest paymentRequest = builder.build();

        paymentRequest.setApplicationInfo(applicationInfoService.createApplicationInfo(requestInfo));

        paymentRequest.setBillingAddress(AddressConverter.convertToBillingAddress(billingAddressData));
        paymentRequest.setDeliveryAddress(AddressConverter.convertToDeliveryAddress(deliveryAddressData));

        Optional<SubscriptionPaymentMethodHandler> paymentMethodHandler = subscriptionPaymentMethodsHandlerFactory.getHandler(subscriptionOrderModel.getPaymentInfo().getAdyenPaymentMethod());

        if (paymentMethodHandler.isPresent()) {
            paymentMethodHandler.get().updatePaymentRequest(paymentRequest, subscriptionOrderModel);
        } else {
            LOG.error("No handler for given payment method: " + subscriptionOrderModel.getPaymentInfo().getAdyenPaymentMethod());
            throw new IllegalArgumentException("No handler for given payment method");
        }

        return paymentRequest;
    }

}
