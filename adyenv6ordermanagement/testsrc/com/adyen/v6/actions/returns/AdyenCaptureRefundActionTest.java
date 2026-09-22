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
package com.adyen.v6.actions.returns;

import com.adyen.v6.actions.AbstractWaitableAction;
import com.adyen.v6.actions.order.AbstractActionTest;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.basecommerce.enums.ReturnStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.payment.PaymentService;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.dto.TransactionStatusDetails;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.returns.model.ReturnEntryModel;
import de.hybris.platform.returns.model.ReturnProcessModel;
import de.hybris.platform.returns.model.ReturnRequestModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.warehousing.returns.service.RefundAmountCalculationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Tests AdyenCheckCaptureAction
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenCaptureRefundActionTest extends AbstractActionTest {
    @Mock
    private ReturnProcessModel returnProcessModelMock;

    @Mock
    private OrderModel orderModelMock;

    @Mock
    private RefundAmountCalculationService refundAmountCalculationServiceMock;

    @Mock
    private PaymentService paymentServiceMock;

    @Mock
    private ReturnRequestModel returnRequestModelMock;

    @Mock
    private ModelService modelServiceMock;

    @Mock
    private PaymentInfoModel paymentInfoModelMock;

    private AdyenCaptureRefundAction adyenCaptureRefundAction;

    private BigDecimal originalRefundAmount;

    @Before
    public void setUp() {
        when(paymentInfoModelMock.getAdyenPaymentMethod()).thenReturn("visa");
        when(orderModelMock.getPaymentInfo()).thenReturn(paymentInfoModelMock);

        when(orderModelMock.getTotalPrice()).thenReturn(12.34);

        when(returnRequestModelMock.getOrder()).thenReturn(orderModelMock);
        when(returnRequestModelMock.getReturnEntries()).thenReturn(new ArrayList<ReturnEntryModel>());

        when(returnProcessModelMock.getReturnRequest()).thenReturn(returnRequestModelMock);
        when(returnProcessModelMock.getCode()).thenReturn("1234");

        when(refundAmountCalculationServiceMock.getCustomRefundAmount(returnRequestModelMock))
                .thenReturn(null);

        originalRefundAmount = new BigDecimal("12.34");
        when(refundAmountCalculationServiceMock.getOriginalRefundAmount(returnRequestModelMock))
                .thenReturn(originalRefundAmount);

        adyenCaptureRefundAction = new AdyenCaptureRefundAction();
        adyenCaptureRefundAction.setPaymentService(paymentServiceMock);
        adyenCaptureRefundAction.setRefundAmountCalculationService(refundAmountCalculationServiceMock);
        adyenCaptureRefundAction.setModelService(modelServiceMock);
    }

    /**
     * when no transactions at all
     * fail to refund
     */
    @Test
    public void testNoTransactions() {
        List<PaymentTransactionModel> transactions = new ArrayList<>();
        when(orderModelMock.getPaymentTransactions()).thenReturn(transactions);

        assertEquals(
                AbstractWaitableAction.Transition.NOK.toString(),
                adyenCaptureRefundAction.execute(returnProcessModelMock)
        );

        Mockito.verify(returnRequestModelMock).setStatus(ReturnStatus.PAYMENT_REVERSAL_FAILED);
        Mockito.verify(modelServiceMock).save(returnRequestModelMock);
    }

    /**
     * Test when there are failed REFUND transactions
     */
    @Test
    public void testFailedRefundTransactions() {
        PaymentTransactionModel adyenTransaction = createAdyenTransaction();
        List<PaymentTransactionModel> transactions = new ArrayList<>();
        transactions.add(adyenTransaction);

        when(orderModelMock.getPaymentTransactions()).thenReturn(transactions);

        adyenTransaction.getEntries().add(createAuthorizedEntry());
        adyenTransaction.getEntries().add(createCaptureSuccessEntry());
        adyenTransaction.getEntries().add(createRefundRejectedEntry());

        assertEquals(
                AbstractWaitableAction.Transition.NOK.toString(),
                adyenCaptureRefundAction.execute(returnProcessModelMock)
        );

        Mockito.verify(returnRequestModelMock).setStatus(ReturnStatus.PAYMENT_REVERSAL_FAILED);
        Mockito.verify(modelServiceMock).save(returnRequestModelMock);
    }

    /**
     * Test when the full amount is not captured yet
     */
    @Test
    public void testWaitForFullAmount() {
        PaymentTransactionModel adyenTransaction = createAdyenTransaction();
        List<PaymentTransactionModel> transactions = new ArrayList<>();
        transactions.add(adyenTransaction);

        when(orderModelMock.getPaymentTransactions()).thenReturn(transactions);

        adyenTransaction.getEntries().add(createAuthorizedEntry());
        adyenTransaction.getEntries().add(createCaptureSuccessEntry());

        PaymentTransactionEntryModel refundReceivedTransaction = new PaymentTransactionEntryModel();
        refundReceivedTransaction.setTransactionStatus(TransactionStatus.ACCEPTED.name());
        refundReceivedTransaction.setTransactionStatusDetails(TransactionStatusDetails.REVIEW_NEEDED.name());
        when(paymentServiceMock.refundFollowOn(adyenTransaction, originalRefundAmount))
                .thenReturn(refundReceivedTransaction);

        assertEquals(
                AbstractWaitableAction.Transition.WAIT.toString(),
                adyenCaptureRefundAction.execute(returnProcessModelMock)
        );

        PaymentTransactionEntryModel refundTransaction = createRefundSuccessEntry();
        refundTransaction.setAmount(new BigDecimal("12.33"));
        adyenTransaction.getEntries().add(refundTransaction);

        assertEquals(
                AbstractWaitableAction.Transition.WAIT.toString(),
                adyenCaptureRefundAction.execute(returnProcessModelMock)
        );
    }

    /**
     * Test when full amount is refunded
     */
    @Test
    public void testRefunded() {
        PaymentTransactionModel adyenTransaction = createAdyenTransaction();
        List<PaymentTransactionModel> transactions = new ArrayList<>();
        transactions.add(adyenTransaction);

        when(orderModelMock.getPaymentTransactions()).thenReturn(transactions);

        adyenTransaction.getEntries().add(createAuthorizedEntry());
        adyenTransaction.getEntries().add(createCaptureSuccessEntry());
        PaymentTransactionEntryModel refundTransaction = createRefundSuccessEntry();
        refundTransaction.setAmount(new BigDecimal("12.34"));
        adyenTransaction.getEntries().add(refundTransaction);

        assertEquals(
                AbstractWaitableAction.Transition.OK.toString(),
                adyenCaptureRefundAction.execute(returnProcessModelMock)
        );

        Mockito.verify(returnRequestModelMock).setStatus(ReturnStatus.PAYMENT_REVERSED);
        Mockito.verify(modelServiceMock).save(returnRequestModelMock);
    }
}
