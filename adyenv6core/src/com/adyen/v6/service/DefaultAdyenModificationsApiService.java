package com.adyen.v6.service;

import com.adyen.commerce.services.AdyenRequestService;
import com.adyen.model.RequestOptions;
import com.adyen.model.checkout.*;
import com.adyen.service.checkout.ModificationsApi;
import com.adyen.v6.util.AmountUtil;
import de.hybris.platform.store.BaseStoreModel;
import org.apache.log4j.Logger;
import org.springframework.retry.support.RetryTemplate;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

public class DefaultAdyenModificationsApiService extends AbstractAdyenApiService implements AdyenModificationsApiService {

    private static final Logger LOG = Logger.getLogger(DefaultAdyenModificationsApiService.class);

    public DefaultAdyenModificationsApiService(BaseStoreModel baseStore, String merchantAccount, final AdyenRequestService adyenRequestService, final RetryTemplate adyenCustomerInteractionRetryTemplate, final RetryTemplate adyenBackgroundProcessRetryTemplate) {
        super(baseStore, merchantAccount, adyenRequestService, adyenCustomerInteractionRetryTemplate, adyenBackgroundProcessRetryTemplate);
    }

    @Override
    public PaymentCaptureResponse capture(final BigDecimal amount, final Currency currency, final String pspReference, final String merchantReference) throws Exception {
        LOG.debug("Captures");

        final ModificationsApi modificationsApi = new ModificationsApi(getClient());

        final PaymentCaptureRequest captureRequest = new PaymentCaptureRequest();
        captureRequest.setAmount(AmountUtil.createAmount(amount, currency.getCurrencyCode()));
        captureRequest.setReference(merchantReference);
        captureRequest.setMerchantAccount(merchantAccount);

        RequestOptions requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        return adyenBackgroundProcessRetryTemplate.execute(context -> {
            LOG.debug(captureRequest);
            PaymentCaptureResponse paymentCaptureResponse = modificationsApi.captureAuthorisedPayment(pspReference, captureRequest, requestOptions);
            LOG.debug(paymentCaptureResponse);

            return paymentCaptureResponse;
        });
    }


    @Override
    public PaymentReversalResponse cancelOrRefund(final String paymentPspReference, final String merchantReference) throws Exception {
        LOG.debug("Cancel or refunds");

        final ModificationsApi modificationsApi = new ModificationsApi(getClient());

        final PaymentReversalRequest reversalRequest = new PaymentReversalRequest();
        reversalRequest.setReference(merchantReference);
        reversalRequest.setMerchantAccount(merchantAccount);

        RequestOptions requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        return adyenBackgroundProcessRetryTemplate.execute(context -> {
            LOG.debug(reversalRequest);
            PaymentReversalResponse paymentReversalResponse = modificationsApi.refundOrCancelPayment(paymentPspReference, reversalRequest, requestOptions);
            LOG.debug(paymentReversalResponse);

            return paymentReversalResponse;
        });
    }

    public PaymentRefundResponse refund(final BigDecimal amount, final Currency currency, final String paymentPspReference, final String reference) throws Exception {
        LOG.debug("Refunds");

        final ModificationsApi modificationsApi = new ModificationsApi(getClient());

        final PaymentRefundRequest paymentRefundRequest = new PaymentRefundRequest();
        paymentRefundRequest.setAmount(AmountUtil.createAmount(amount, currency.getCurrencyCode()));
        paymentRefundRequest.setMerchantAccount(merchantAccount);
        paymentRefundRequest.setReference(reference);

        RequestOptions requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        return adyenBackgroundProcessRetryTemplate.execute(context -> {
            LOG.debug(paymentRefundRequest);
            PaymentRefundResponse paymentRefundResponse = modificationsApi.refundCapturedPayment(paymentPspReference, paymentRefundRequest, requestOptions);
            LOG.debug(paymentRefundResponse);

            return paymentRefundResponse;
        });
    }

}
