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

import com.adyen.v6.model.AdyenNotificationModel;
import com.adyen.v6.model.NotificationItemModel;
import com.adyen.v6.repository.OrderRepository;
import com.adyen.v6.repository.PaymentTransactionRepository;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.processengine.BusinessProcessService;
import de.hybris.platform.returns.model.ReturnProcessModel;
import de.hybris.platform.returns.model.ReturnRequestModel;
import de.hybris.platform.servicelayer.model.ModelService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.adyen.model.notification.NotificationRequestItem.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenNotificationServiceTest {
    @Mock
    private ModelService modelServiceMock;

    @Mock
    private AdyenTransactionService adyenTransactionServiceMock;

    @Mock
    private OrderRepository orderRepositoryMock;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepositoryMock;

    @Mock
    private BusinessProcessService businessProcessServiceMock;

    @Mock
    private PaymentTransactionEntryModel paymentTransactionEntryModelMock;

    @InjectMocks
    private DefaultAdyenNotificationService adyenNotificationService;

    @Before
    public void setUp() {
        when(modelServiceMock.create(PaymentTransactionEntryModel.class)).thenReturn(new PaymentTransactionEntryModel());

        DefaultAdyenBusinessProcessService adyenBusinessProcessService = new DefaultAdyenBusinessProcessService();
        adyenBusinessProcessService.setBusinessProcessService(businessProcessServiceMock);

        adyenNotificationService.setAdyenBusinessProcessService(adyenBusinessProcessService);
    }

    @After
    public void tearDown() {
        // implement here code executed after each test
    }

    /**
     * Test CAPTURE notification handling
     * It should save the capture notification and emmit the AdyenCaptured event
     */
    @Test
    public void testCaptureNotification() throws Exception {
        PaymentTransactionModel paymentTransactionModel = new PaymentTransactionModel();
        paymentTransactionModel.setEntries(new ArrayList<PaymentTransactionEntryModel>());

        OrderModel orderModel = createDummyOrderModel();

        paymentTransactionModel.setOrder(orderModel);

        when(paymentTransactionRepositoryMock.getTransactionModel(Mockito.any(String.class))).thenReturn(paymentTransactionModel);

        AdyenNotificationModel adyenNotificationInfo = new AdyenNotificationModel();
        adyenNotificationInfo.setOriginalReference("123");
        adyenNotificationInfo.setPspReference("456");
        adyenNotificationInfo.setEventCode(EVENT_CODE_CAPTURE);
        adyenNotificationInfo.setSuccess(true);

        NotificationItemModel notificationItemModel = new NotificationItemModel();
        notificationItemModel.setOriginalReference("123");
        notificationItemModel.setPspReference("456");
        notificationItemModel.setEventCode(EVENT_CODE_CAPTURE);
        notificationItemModel.setSuccess(true);

        when(adyenTransactionServiceMock.createCapturedTransactionFromNotification(paymentTransactionModel, adyenNotificationInfo)).thenReturn(paymentTransactionEntryModelMock);

        adyenNotificationService.processNotification(notificationItemModel);

        //Verify that we emmit the event of Capture to the order processes
        verify(businessProcessServiceMock).triggerEvent("order_process_code_AdyenCaptured");

        //Verify that the capture transaction is saved
        verify(modelServiceMock).save(paymentTransactionEntryModelMock);
    }

    /**
     * Test AUTHORISATION notification handling
     * It should save the capture notification and emmit the AdyenCaptured event
     */
    @Test
    public void testAuthorisationNotification() throws Exception {
        String pspReference = "123";
        String merchantReference = "001";
        final BigDecimal amount = new BigDecimal(2);

        OrderModel orderModel = createDummyOrderModel();

        NotificationItemModel notificationItemModel = new NotificationItemModel();
        notificationItemModel.setPspReference(pspReference);
        notificationItemModel.setEventCode(EVENT_CODE_AUTHORISATION);
        notificationItemModel.setMerchantReference(merchantReference);
        notificationItemModel.setSuccess(true);
        notificationItemModel.setAmountValue(amount);

        when(paymentTransactionRepositoryMock.getTransactionModel(Mockito.any(String.class))).thenReturn(null);

        when(orderRepositoryMock.getOrderModel(Mockito.any(String.class))).thenReturn(orderModel);

        adyenNotificationService.processNotification(notificationItemModel);

        //Verify that we emmit the event of Capture to the order processes
        verify(businessProcessServiceMock).triggerEvent("order_process_code_AdyenPaymentResult");

        //Verify that the authorizeOrderModel is called
        verify(adyenTransactionServiceMock).authorizeOrderModel(orderModel, merchantReference, pspReference, amount);
    }

    /**
     * Test failed authorisation
     */
    @Test
    public void testFailedAuthorisationNotification() {
        String pspReference = "123";
        String merchantReference = "001";

        OrderModel orderModel = createDummyOrderModel();

        NotificationItemModel notificationItemModel = new NotificationItemModel();
        notificationItemModel.setPspReference(pspReference);
        notificationItemModel.setEventCode(EVENT_CODE_AUTHORISATION);
        notificationItemModel.setMerchantReference(merchantReference);
        notificationItemModel.setSuccess(false);

        AdyenNotificationModel adyenNotificationModel = new AdyenNotificationModel();
        adyenNotificationModel.setPspReference(pspReference);
        adyenNotificationModel.setEventCode(EVENT_CODE_AUTHORISATION);
        adyenNotificationModel.setMerchantReference(merchantReference);
        adyenNotificationModel.setSuccess(false);

        when(paymentTransactionRepositoryMock.getTransactionModel(Mockito.any(String.class))).thenReturn(null);

        when(orderRepositoryMock.getOrderModel(Mockito.any(String.class))).thenReturn(orderModel);

        adyenNotificationService.processNotification(notificationItemModel);

        //Verify that we emmit the event of Capture to the order processes
        verify(businessProcessServiceMock).triggerEvent("order_process_code_AdyenPaymentResult");

        //Verify that the authorizeOrderModel is called
        verify(adyenTransactionServiceMock).storeFailedAuthorizationFromNotification(adyenNotificationModel, orderModel);
    }

    /**
     * Test Duplicate AUTHORISATION event handling
     */
    @Test
    public void testDuplicateAuthorisationNotification() {
        NotificationItemModel notificationItemModel = new NotificationItemModel();
        notificationItemModel.setPspReference("123");
        notificationItemModel.setEventCode(EVENT_CODE_AUTHORISATION);
        notificationItemModel.setSuccess(true);

        when(paymentTransactionRepositoryMock.getTransactionModel(Mockito.any(String.class))).thenReturn(new PaymentTransactionModel());

        adyenNotificationService.processNotification(notificationItemModel);

        //Verify that we emmit the event of Capture to the order processes
        verify(orderRepositoryMock, Mockito.never()).getOrderModel(Mockito.any(String.class));
    }

    /**
     * Test successful Refund notification
     */
    @Test
    public void testRefundNotification() throws Exception {
        PaymentTransactionModel paymentTransactionModel = new PaymentTransactionModel();
        paymentTransactionModel.setEntries(new ArrayList<PaymentTransactionEntryModel>());

        OrderModel orderModel = createDummyOrderModel();

        paymentTransactionModel.setOrder(orderModel);

        when(paymentTransactionRepositoryMock.getTransactionModel(Mockito.any(String.class))).thenReturn(paymentTransactionModel);


        AdyenNotificationModel adyenNotificationModel = new AdyenNotificationModel();
        adyenNotificationModel.setOriginalReference("123");
        adyenNotificationModel.setPspReference("456");
        adyenNotificationModel.setEventCode(EVENT_CODE_REFUND);
        adyenNotificationModel.setSuccess(true);

        NotificationItemModel notificationItemModel = new NotificationItemModel();
        notificationItemModel.setOriginalReference("123");
        notificationItemModel.setPspReference("456");
        notificationItemModel.setEventCode(EVENT_CODE_REFUND);
        notificationItemModel.setSuccess(true);

        when(adyenTransactionServiceMock.createRefundedTransactionFromNotification(paymentTransactionModel, adyenNotificationModel)).thenReturn(paymentTransactionEntryModelMock);

        adyenNotificationService.processNotification(notificationItemModel);

        //Verify that we emmit the event of Capture to the order processes
        verify(businessProcessServiceMock).triggerEvent("return_process_code_AdyenRefunded");

        //Verify that the capture transaction is saved
        verify(modelServiceMock).save(paymentTransactionEntryModelMock);
    }


    private OrderModel createDummyOrderModel() {
        Collection<OrderProcessModel> orderProcessModels = new ArrayList<OrderProcessModel>();
        OrderProcessModel orderProcessModel = new OrderProcessModel();
        orderProcessModel.setCode("order_process_code");
        orderProcessModels.add(orderProcessModel);

        //Create necessary instances for refunds
        ReturnProcessModel returnProcess = new ReturnProcessModel();
        returnProcess.setCode("return_process_code");

        Collection<ReturnProcessModel> returnProcesses = new ArrayList<>();
        returnProcesses.add(returnProcess);

        ReturnRequestModel returnRequest = new ReturnRequestModel();
        returnRequest.setReturnProcess(returnProcesses);

        List<ReturnRequestModel> returnRequests = new ArrayList<>();
        returnRequests.add(returnRequest);

        OrderModel orderModel = new OrderModel();
        orderModel.setOrderProcess(orderProcessModels);
        orderModel.setReturnRequests(returnRequests);

        return orderModel;
    }
}
