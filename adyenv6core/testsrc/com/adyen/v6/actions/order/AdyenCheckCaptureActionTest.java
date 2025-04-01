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
package com.adyen.v6.actions.order;

import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.service.AdyenCheckoutApiService;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests AdyenCheckCaptureAction
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenCheckCaptureActionTest extends AbstractActionTest {
    @Mock
    private OrderProcessModel orderProcessModelMock;
    @Mock
    private OrderModel orderModelMock;
    @Mock
    private PaymentInfoModel paymentInfoModelMock;
    @Mock
    private ModelService modelServiceMock;
    @Mock
    private AdyenPaymentServiceFactory adyenPaymentServiceFactoryMock;
    @Mock
    private BaseStoreService baseStoreServiceMock;
    @Mock
    private BaseStoreModel baseStoreModelMock;
    @Mock
    private AdyenCheckoutApiService adyenCheckoutApiServiceMock;

    @InjectMocks
    private AdyenCheckCaptureAction adyenCheckCaptureAction;


    @Before
    public void setUp() {
        when(paymentInfoModelMock.getAdyenPaymentMethod()).thenReturn("visa");

        when(orderModelMock.getTotalPrice()).thenReturn(12.34);
        when(orderModelMock.getPaymentInfo()).thenReturn(paymentInfoModelMock);

        when(orderProcessModelMock.getCode()).thenReturn("1234");
        when(orderProcessModelMock.getOrder()).thenReturn(orderModelMock);

        adyenCheckCaptureAction = new AdyenCheckCaptureAction(adyenPaymentServiceFactoryMock, baseStoreServiceMock);
        adyenCheckCaptureAction.setModelService(modelServiceMock);
        when(adyenCheckCaptureAction.getAdyenPaymentService(orderModelMock)).thenReturn(adyenCheckoutApiServiceMock);

        when(baseStoreServiceMock.getCurrentBaseStore()).thenReturn(baseStoreModelMock);
        when(adyenPaymentServiceFactoryMock.createAdyenCheckoutApiService(baseStoreModelMock)).thenReturn(adyenCheckoutApiServiceMock);

    }

    @After
    public void tearDown() {
        // implement here code executed after each test
    }

    /**
     * No authorizations found -> wait
     *
     * @throws Exception
     */
    @Test
    public void testNoAuthorizations() throws Exception {
        List<PaymentTransactionModel> transactions = new ArrayList<>();
        when(orderModelMock.getPaymentTransactions()).thenReturn(transactions);

        assertEquals(
                AdyenCheckCaptureAction.Transition.WAIT.toString(),
                adyenCheckCaptureAction.execute(orderProcessModelMock)
        );

        PaymentTransactionModel adyenTransaction = createAdyenTransaction();

        adyenTransaction.getEntries().add(createAuthorizedEntry());
        adyenTransaction.getEntries().add(createCaptureReceivedEntry());

        transactions.add(adyenTransaction);

        assertEquals(
                AdyenCheckCaptureAction.Transition.WAIT.toString(),
                adyenCheckCaptureAction.execute(orderProcessModelMock)
        );

        adyenTransaction.getEntries().add(createCaptureSuccessEntry());

        assertEquals(
                AdyenCheckCaptureAction.Transition.OK.toString(),
                adyenCheckCaptureAction.execute(orderProcessModelMock)
        );
    }

    /**
     * Failed capture scenario
     *
     * @throws Exception
     */
    @Test
    public void testCaptureRejected() throws Exception {
        PaymentTransactionModel adyenTransaction = createAdyenTransaction();
        List<PaymentTransactionEntryModel> transactionEntries = new ArrayList<>();

        transactionEntries.add(createAuthorizedEntry());
        transactionEntries.add(createCaptureReceivedEntry());
        transactionEntries.add(createCaptureRejectedEntry());

        adyenTransaction.setEntries(transactionEntries);

        List<PaymentTransactionModel> transactions = new ArrayList<>();
        transactions.add(adyenTransaction);
        when(orderModelMock.getPaymentTransactions()).thenReturn(transactions);

        String result = adyenCheckCaptureAction.execute(orderProcessModelMock);

        assertEquals(AdyenCheckCaptureAction.Transition.NOK.toString(), result);
        verify(orderModelMock).setStatus(OrderStatus.PAYMENT_NOT_CAPTURED);
        verify(modelServiceMock).save(orderProcessModelMock.getOrder());
    }
}
