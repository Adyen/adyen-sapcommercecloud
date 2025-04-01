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
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
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
import static org.mockito.Mockito.when;

/**
 * Tests AdyenCheckAuthorizationAction
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenCheckAuthorizationActionTest extends AbstractActionTest {

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
    private AdyenCheckAuthorizationAction adyenCheckAuthorizationAction;

    @Before
    public void setUp() {
        when(paymentInfoModelMock.getAdyenPaymentMethod()).thenReturn("visa");
        when(orderModelMock.getPaymentInfo()).thenReturn(paymentInfoModelMock);

        // implement here code executed before each test
        when(orderProcessModelMock.getCode()).thenReturn("1234");
        when(orderProcessModelMock.getOrder()).thenReturn(orderModelMock);

        adyenCheckAuthorizationAction = new AdyenCheckAuthorizationAction(adyenPaymentServiceFactoryMock, baseStoreServiceMock);
        adyenCheckAuthorizationAction.setModelService(modelServiceMock);

        when(baseStoreServiceMock.getCurrentBaseStore()).thenReturn(baseStoreModelMock);
        when(adyenPaymentServiceFactoryMock.createAdyenCheckoutApiService(baseStoreModelMock)).thenReturn(adyenCheckoutApiServiceMock);
    }

    @After
    public void tearDown() {
        // implement here code executed after each test
    }

    /**
     * when not adyen payment
     * then consider authorized
     */
    @Test
    public void testNonAdyenPayment() {
        when(paymentInfoModelMock.getAdyenPaymentMethod()).thenReturn("");

        String result = adyenCheckAuthorizationAction.execute(orderProcessModelMock);

        assertEquals(AdyenCheckAuthorizationAction.Transition.OK.toString(), result);
    }

    /**
     * when already authorized adyen payment
     * then consider authorized
     */
    @Test
    public void testAlreadyAuthorized() {
        List<PaymentTransactionModel> transactions = new ArrayList<>();

        PaymentTransactionModel authorizedTransaction = createAdyenTransaction();
        authorizedTransaction.setPlannedAmount(new BigDecimal(10));
        authorizedTransaction.getEntries().add(createAuthorizedEntry());
        transactions.add(authorizedTransaction);

        when(orderModelMock.getPaymentTransactions()).thenReturn(transactions);

        String result = adyenCheckAuthorizationAction.execute(orderProcessModelMock);

        assertEquals(AdyenCheckAuthorizationAction.Transition.OK.toString(), result);
    }

    /**
     * when no transactions
     * then consider waiting for authorisation
     */
    @Test
    public void testNoTransactionsAuthorization() {
        when(orderModelMock.getPaymentTransactions()).thenReturn(new ArrayList<PaymentTransactionModel>());

        String result = adyenCheckAuthorizationAction.execute(orderProcessModelMock);

        assertEquals(AdyenCheckAuthorizationAction.Transition.WAIT.toString(), result);
    }

    /**
     * when authorization is failed
     * then consider not authorized
     */
    @Test
    public void testFailedAuthorization() {
        List<PaymentTransactionModel> transactions = new ArrayList<>();

        PaymentTransactionModel authorizedTransaction = createAdyenTransaction();
        transactions.add(authorizedTransaction);

        authorizedTransaction.getEntries().add(createAuthorizedRejectedEntry());

        when(orderModelMock.getPaymentTransactions()).thenReturn(transactions);

        String result = adyenCheckAuthorizationAction.execute(orderProcessModelMock);

        assertEquals(AdyenCheckAuthorizationAction.Transition.NOK.toString(), result);
    }
}
