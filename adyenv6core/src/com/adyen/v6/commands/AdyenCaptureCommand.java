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
package com.adyen.v6.commands;


import com.adyen.model.checkout.*;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.repository.OrderRepository;
import com.adyen.v6.service.AdyenModificationsApiService;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.payment.commands.CaptureCommand;
import de.hybris.platform.payment.commands.request.CaptureRequest;
import de.hybris.platform.payment.commands.result.CaptureResult;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.dto.TransactionStatusDetails;
import de.hybris.platform.store.BaseStoreModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.*;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_ONECLICK;

/**
 * Issues a Capture request
 */
public class AdyenCaptureCommand implements CaptureCommand {
    private static final Logger LOG = Logger.getLogger(AdyenCaptureCommand.class);

    private final String CAPTURE_RECEIVED_RESPONSE = "[capture-received]";

    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    private OrderRepository orderRepository;

    /**
     * {@inheritDoc}
     *
     * @see de.hybris.platform.payment.commands.Command#perform(java.lang.Object)
     */
    @Override
    public CaptureResult perform(final CaptureRequest request) {
        CaptureResult result = createCaptureResultFromRequest(request);

        LOG.info("Capture request received with requestId: " + request.getRequestId() + ", requestToken: " + request.getRequestToken());

        String originalPSPReference = request.getRequestId();
        String reference = request.getRequestToken();
        final BigDecimal amount = request.getTotalAmount();
        final Currency currency = request.getCurrency();

        OrderModel order = orderRepository.getOrderModel(reference);
        if (order == null) {
            LOG.error("Order model with code: " + reference + " cannot be found");
            result.setTransactionStatus(TransactionStatus.ERROR);
            return result;
        }

        BaseStoreModel baseStore = order.getStore();
        Assert.notNull(baseStore, "BaseStore model is null");
        AdyenModificationsApiService adyenPaymentService = adyenPaymentServiceFactory.createAdyenModificationsApiService(baseStore);

        final PaymentInfoModel paymentInfo = order.getPaymentInfo();
        Assert.notNull(paymentInfo, "PaymentInfoModel is null");

        boolean isImmediateCapture = baseStore.getAdyenImmediateCapture();

        boolean autoCapture = isImmediateCapture || !supportsManualCapture(paymentInfo.getAdyenPaymentMethod());

        if (autoCapture) {
            result.setTransactionStatus(TransactionStatus.ACCEPTED);
            result.setTransactionStatusDetails(TransactionStatusDetails.SUCCESFULL);
        } else {
            try {
                PaymentCaptureResponse capture = adyenPaymentService.capture(amount, currency, originalPSPReference, reference);

                if (PaymentCaptureResponse.StatusEnum.RECEIVED.equals(capture.getStatus())) {
                    result.setTransactionStatus(TransactionStatus.ACCEPTED);  //Accepted so that TakePaymentAction doesn't fail
                    result.setTransactionStatusDetails(TransactionStatusDetails.REVIEW_NEEDED);
                } else {
                    result.setTransactionStatus(TransactionStatus.REJECTED);
                    result.setTransactionStatusDetails(TransactionStatusDetails.UNKNOWN_CODE);
                }
            } catch (Exception e) {
                LOG.error("Capture Exception: " + e, e);
            }
        }

        LOG.info("Capture status: " + result.getTransactionStatus().name() + ":" + result.getTransactionStatusDetails().name());

        return result;
    }

    protected CaptureResult createCaptureResultFromRequest(CaptureRequest request) {
        CaptureResult result = new CaptureResult();

        result.setCurrency(request.getCurrency());
        result.setTotalAmount(request.getTotalAmount());
        result.setRequestTime(new Date());
        result.setMerchantTransactionCode(request.getMerchantTransactionCode());
        result.setRequestId(request.getRequestId());
        result.setRequestToken(request.getRequestToken());

        //Default status = ERROR
        result.setTransactionStatus(TransactionStatus.ERROR);
        result.setTransactionStatusDetails(TransactionStatusDetails.UNKNOWN_CODE);

        return result;
    }

    protected boolean supportsManualCapture(String paymentMethod) {
        if (StringUtils.isEmpty(paymentMethod)) {
            return false;
        }

        if (paymentMethod.startsWith(PAYMENT_METHOD_ONECLICK)) {
            return true;
        }

        List<String> supportedPaymentMethods = new ArrayList<String>();
        supportedPaymentMethods.add(PAYMENT_METHOD_CC);

        supportedPaymentMethods.addAll(Arrays.stream(CardDetails.TypeEnum.values()).map(CardDetails.TypeEnum::getValue).toList());
        supportedPaymentMethods.addAll(Arrays.stream(PayPalDetails.TypeEnum.values()).map(PayPalDetails.TypeEnum::getValue).toList());
        supportedPaymentMethods.addAll(Arrays.stream(KlarnaDetails.TypeEnum.values()).map(KlarnaDetails.TypeEnum::getValue).toList());
        supportedPaymentMethods.addAll(Arrays.stream(AfterpayDetails.TypeEnum.values()).map(AfterpayDetails.TypeEnum::getValue).toList());
        supportedPaymentMethods.addAll(Arrays.stream(RatepayDetails.TypeEnum.values()).map(RatepayDetails.TypeEnum::getValue).toList());
        supportedPaymentMethods.addAll(Arrays.stream(SepaDirectDebitDetails.TypeEnum.values()).map(SepaDirectDebitDetails.TypeEnum::getValue).toList());
        supportedPaymentMethods.addAll(Arrays.stream(ApplePayDetails.TypeEnum.values()).map(ApplePayDetails.TypeEnum::getValue).toList());
        supportedPaymentMethods.addAll(Arrays.stream(GooglePayDetails.TypeEnum.values()).map(GooglePayDetails.TypeEnum::getValue).toList());
        supportedPaymentMethods.addAll(Arrays.stream(PayWithGoogleDetails.TypeEnum.values()).map(PayWithGoogleDetails.TypeEnum::getValue).toList());
        supportedPaymentMethods.addAll(Arrays.stream(AmazonPayDetails.TypeEnum.values()).map(AmazonPayDetails.TypeEnum::getValue).toList());
        supportedPaymentMethods.addAll(Arrays.stream(TwintDetails.TypeEnum.values()).map(TwintDetails.TypeEnum::getValue).toList());

        return supportedPaymentMethods.contains(paymentMethod);
    }

    public AdyenPaymentServiceFactory getAdyenPaymentServiceFactory() {
        return adyenPaymentServiceFactory;
    }

    public void setAdyenPaymentServiceFactory(AdyenPaymentServiceFactory adyenPaymentServiceFactory) {
        this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
    }

    public OrderRepository getOrderRepository() {
        return orderRepository;
    }

    public void setOrderRepository(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
}
