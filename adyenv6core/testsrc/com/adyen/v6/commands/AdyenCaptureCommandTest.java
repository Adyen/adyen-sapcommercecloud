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

import com.adyen.model.checkout.PaymentCaptureResponse;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.repository.OrderRepository;
import com.adyen.v6.service.DefaultAdyenModificationsApiService;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.payment.commands.request.CaptureRequest;
import de.hybris.platform.payment.commands.result.CaptureResult;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.dto.TransactionStatusDetails;
import de.hybris.platform.store.BaseStoreModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenCaptureCommandTest {
    private CaptureRequest captureRequest;

    @Mock
    private AdyenPaymentServiceFactory adyenPaymentServiceFactoryMock;

    @Mock
    private DefaultAdyenModificationsApiService adyenModificationsApiService;

    @Mock
    private OrderRepository orderRepositoryMock;

    private BaseStoreModel baseStore;

    AdyenCaptureCommand adyenCaptureCommand;

    @Before
    public void setUp() {
        adyenCaptureCommand = new AdyenCaptureCommand();
        captureRequest = new CaptureRequest("merchantTransactionCode", "requestId", "requestToken", Currency.getInstance("EUR"), new BigDecimal(100), "Adyen");

        PaymentInfoModel paymentInfoModel = new PaymentInfoModel();
        paymentInfoModel.setAdyenPaymentMethod("visa");

        OrderModel orderModel = new OrderModel();
        orderModel.setPaymentInfo(paymentInfoModel);

        when(orderRepositoryMock.getOrderModel(Mockito.any(String.class))).thenReturn(orderModel);

        baseStore = new BaseStoreModel();
        baseStore.setAdyenImmediateCapture(false);
        orderModel.setStore(baseStore);

        when(adyenPaymentServiceFactoryMock.createAdyenModificationsApiService(baseStore)).thenReturn(adyenModificationsApiService);

        adyenCaptureCommand.setOrderRepository(orderRepositoryMock);
        adyenCaptureCommand.setAdyenPaymentServiceFactory(adyenPaymentServiceFactoryMock);
    }

    @After
    public void tearDown() {
        // implement here code executed after each test
    }

    /**
     * Test successful capture
     *
     * @throws Exception
     */
    @Test
    public void testManualCaptureSuccess() throws Exception {
        // Arrange
        BigDecimal amount = new BigDecimal(100);
        Currency currency = Currency.getInstance("EUR");
        String authReference = "authReference";
        String merchantReference = "merchantReference";

        PaymentCaptureResponse mockResponse = new PaymentCaptureResponse();
        mockResponse.setPspReference("12345");
        mockResponse.setStatus(PaymentCaptureResponse.StatusEnum.RECEIVED);

        when(adyenModificationsApiService.capture(amount, currency, authReference, merchantReference)).thenReturn(mockResponse);

        // Act
        PaymentCaptureResponse response = adyenModificationsApiService.capture(amount, currency, authReference, merchantReference);

        // Assert
        assertEquals("12345", response.getPspReference());
        assertEquals(PaymentCaptureResponse.StatusEnum.RECEIVED, response.getStatus());
    }

    /**
     * Test immediate capture
     */
    @Test
    public void testImmediateCaptureSuccess() {
        baseStore.setAdyenImmediateCapture(true);

        CaptureResult result = adyenCaptureCommand.perform(captureRequest);
        assertEquals(TransactionStatus.ACCEPTED, result.getTransactionStatus());
        assertEquals(TransactionStatusDetails.SUCCESFULL, result.getTransactionStatusDetails());
    }

    /**
     * Test manual capture for a payment method that doesn't support manual capture
     */
    @Test
    public void testManualNotSupportedCaptureSuccess() {
        OrderModel orderModel = new OrderModel();

        PaymentInfoModel paymentInfoModelMock = mock(PaymentInfoModel.class);
        when(paymentInfoModelMock.getAdyenPaymentMethod()).thenReturn("paysafe");
        orderModel.setPaymentInfo(paymentInfoModelMock);

        orderModel.setStore(baseStore);
        when(orderRepositoryMock.getOrderModel(Mockito.any(String.class))).thenReturn(orderModel);

        CaptureResult result = adyenCaptureCommand.perform(captureRequest);
        assertEquals(TransactionStatus.ACCEPTED, result.getTransactionStatus());
        assertEquals(TransactionStatusDetails.SUCCESFULL, result.getTransactionStatusDetails());
    }

    @Test
    public void shouldReturnTrueForSupportedMethods() {
        // All supported payment methods
        String[] supportedMethods = {
                "card", "adyen_cc", "scheme", "paypal", "klarna", "klarna_account", "klarna_paynow", "afterpay_default",
                "afterpaytouch", "clearpay", "ratepay", "afterpay_default", "sepadirectdebit", "applepay",
                "paywithgoogle", "googlepay", "amazonpay", "twint"
        };

        List<String> notSupportedMethods = new ArrayList<String>();

        for (String method : supportedMethods) {
            if (!adyenCaptureCommand.supportsManualCapture(method)) {
                notSupportedMethods.add(method);
            }
        }

        assertTrue("The method should support manual capture for: " + notSupportedMethods, notSupportedMethods.isEmpty());
    }

    @Test
    public void shouldReturnTrueForStoredCard() {
        String unsupportedMethod = "adyen_oneclick_A1234567";
        assertTrue("The method should  support manual capture for stored card",
                adyenCaptureCommand.supportsManualCapture(unsupportedMethod));
    }

    @Test
    public void shouldReturnFalseForUnsupportedMethod() {
        String unsupportedMethod = "banktransfer";
        assertFalse("The method should not support manual capture for an unsupported method",
                adyenCaptureCommand.supportsManualCapture(unsupportedMethod));
    }

    @Test
    public void shouldReturnFalseForNullInput() {
        assertFalse("The method should not support manual capture for null input",
                adyenCaptureCommand.supportsManualCapture(null));
    }

    @Test
    public void shouldReturnFalseForEmptyStringInput() {
        assertFalse("The method should not support manual capture for an empty string input",
                adyenCaptureCommand.supportsManualCapture(""));
    }
}
