/*
 *                       ######
 *                       ######
 * ############    ####( ######  #####. ######  ############   ############
 * #############  #####( ######  #####. ######  #############  #############
 *        ######  #####( ######  #####. ######  #####  ######  #####  ######
 * ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 * ###### ######  #####( ######  #####. ######  #####          #####  ######
 * #############  #############  #############  #############  #####  ######
 *  ############   ############  #############   ############  #####  ######
 *                                      ######
 *                               #############
 *                               ############
 *
 * Adyen Hybris Extension
 *
 * Copyright (c) 2020 Adyen B.V.
 * This file is open source and available under the MIT license.
 * See the LICENSE file for more info.
 */

package com.adyen.v6.facades;

import com.adyen.model.checkout.InputDetail;
import com.adyen.model.checkout.Item;
import com.adyen.model.checkout.PaymentCompletionDetails;
import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.PaymentMethod;
import com.adyen.model.checkout.PaymentMethodsResponse;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.nexo.DocumentQualifierType;
import com.adyen.model.nexo.ErrorConditionType;
import com.adyen.model.nexo.OutputContent;
import com.adyen.model.nexo.OutputText;
import com.adyen.model.nexo.PaymentReceipt;
import com.adyen.model.nexo.RepeatedMessageResponse;
import com.adyen.model.nexo.RepeatedResponseMessageBody;
import com.adyen.model.nexo.ResultType;
import com.adyen.model.nexo.SaleToPOIResponse;
import com.adyen.model.nexo.TransactionStatusResponse;
import com.adyen.model.terminal.TerminalAPIResponse;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.converters.PosPaymentResponseConverter;
import com.adyen.v6.enums.RecurringContractMode;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.repository.OrderRepository;
import com.adyen.v6.service.AdyenBusinessProcessService;
import com.adyen.v6.service.AdyenCheckoutApiService;
import com.adyen.v6.service.AdyenOrderService;
import com.adyen.v6.service.AdyenTransactionService;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.user.converters.populator.AddressPopulator;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.c2l.CountryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.delivery.DeliveryModeModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.order.CalculationService;
import de.hybris.platform.order.CartFactory;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.configuration.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static com.adyen.constants.ApiConstants.ThreeDS2Property.CHALLENGE_RESULT;
import static com.adyen.constants.ApiConstants.ThreeDS2Property.FINGERPRINT_RESULT;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_EPS;
import static com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade.MODEL_ISSUER_LISTS;
import static com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade.MODEL_SELECTED_PAYMENT_METHOD;
import static com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade.SESSION_PENDING_ORDER_CODE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenCheckoutFacadeTest {

    private static final String DELIVERY_MODE = "deliveryMode";
    private static final String EXCEPTION = "exception";
    private static final String MERCHANT_REFERENCE = "merchantReference";
    private static final String PAYMENT_METHOD = "paymentMethod";
    private static final String PENDING_ORDER_CODE = "pendingOrder";
    private static final String RESULT = "result";
    private static final String SERVICE_ID = "serviceId";
    private static final String URL = "url";
    private static final String ORDER_CODE = "orderCode";

    @Spy
    @InjectMocks
    DefaultAdyenCheckoutFacade adyenCheckoutFacade = new DefaultAdyenCheckoutFacade();

    @Mock
    AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    @Mock
    AdyenCheckoutApiService adyenCheckoutApiService;
    @Mock
    BaseStoreService baseStoreService;
    @Mock
    CheckoutCustomerStrategy checkoutCustomerStrategy;
    @Mock
    PosPaymentResponseConverter posPaymentResponseConverter;
    @Mock
    CartService cartService;
    @Mock
    AdyenTransactionService adyenTransactionService;
    @Mock
    CheckoutFacade checkoutFacade;
    @Mock
    OrderRepository orderRepository;
    @Mock
    AdyenOrderService adyenOrderService;
    @Mock
    CommonI18NService commonI18NService;
    @Mock
    ModelService modelService;
    @Mock
    SessionService sessionService;
    @Mock
    Converter<CountryModel, CountryData> countryConverter;
    @Mock
    Converter<OrderModel, OrderData> orderConverter;
    @Mock
    CartFactory cartFactory;
    @Mock
    CalculationService calculationService;
    @Mock
    AddressPopulator addressPopulator;
    @Mock
    AdyenBusinessProcessService adyenBusinessProcessService;

    @Mock
    private ConfigurationService configurationServiceMock;
    @Mock
    private AdyenPaymentServiceFactory adyenPaymentServiceFactoryMock;
    @Mock
    private BaseStoreService baseStoreServiceMock;
    @Mock
    private BaseStoreModel baseStoreModelMock;
    @Mock
    private AdyenCheckoutApiService adyenCheckoutApiServiceMock;

    @Mock
    HttpServletRequest request;
    @Mock
    PaymentDetailsRequest details;
    @Mock
    CartData cartData;
    @Mock
    CartModel cartModel;
    @Mock
    PaymentResponse paymentsResponse;
    @Mock
    PaymentDetailsResponse paymentsDetailsResponse;
    @Mock
    OrderData orderData;
    @Mock
    OrderModel orderModel;
    @Mock
    BaseStoreModel baseStore;
    @Mock
    PriceData priceData;
    @Mock
    AddressData addressData;
    @Mock
    AddressModel addressModel;
    @Mock
    CountryData countryData;
    @Mock
    DeliveryModeModel deliveryModeModel;
    @Mock
    UserModel userModel;
    @Mock
    BaseStoreModel storeModel;
    @Mock
    TerminalAPIResponse terminalApiResponse;
    @Mock
    SaleToPOIResponse saleToPoiResponse;
    //Payment Response
    @Mock
    com.adyen.model.nexo.PaymentResponse nexpPaymentResponse;
    @Mock
    com.adyen.model.nexo.Response nexoResponse;
    //Status response
    @Mock
    com.adyen.model.nexo.Response nexoStatusResponse;
    @Mock
    TransactionStatusResponse transactionStatusResponse;
    @Mock
    RepeatedMessageResponse repeatedMessageResponse;
    @Mock
    RepeatedResponseMessageBody repeatedResponseMessageBody;
    //Receipts
    @Mock
    PaymentReceipt receipt;
    @Mock
    OutputContent outputContent;
    @Mock
    OutputText textWithNameValue;
    @Mock
    OutputText textJustName;
    @Mock
    OutputText textJustValue;
    @Mock
    Configuration configurationMock;

    @Before
    public void setUp() {
        when(checkoutCustomerStrategy.isAnonymousCheckout()).thenReturn(true);
        when(checkoutCustomerStrategy.getCurrentUserForCheckout()).thenReturn(new CustomerModel());
        when(request.getAttribute("originalServiceId")).thenReturn(SERVICE_ID);
        when(baseStoreService.getCurrentBaseStore()).thenReturn(baseStore);
        when(adyenPaymentServiceFactory.createAdyenCheckoutApiService(any())).thenReturn(adyenCheckoutApiService);
        when(configurationServiceMock.getConfiguration()).thenReturn(configurationMock);
        when(baseStoreServiceMock.getCurrentBaseStore()).thenReturn(baseStoreModelMock);
        when(adyenPaymentServiceFactoryMock.createAdyenCheckoutApiService(baseStoreModelMock)).thenReturn(adyenCheckoutApiServiceMock);
    }

    @Test
    public void testInitializeEpsCheckoutData() throws Exception {
        when(checkoutFacade.getCheckoutCart()).thenReturn(cartData);
        when(baseStore.getAdyenPosEnabled()).thenReturn(false);
        when(cartData.getTotalPriceWithTax()).thenReturn(priceData);
        when(priceData.getValue()).thenReturn(BigDecimal.TEN);
        when(priceData.getCurrencyIso()).thenReturn("EUR");
        when(cartData.getDeliveryAddress()).thenReturn(addressData);
        when(addressData.getCountry()).thenReturn(countryData);
        when(countryData.getIsocode()).thenReturn("NL");
        when(commonI18NService.getCurrentLanguage()).thenReturn(null);
        when(adyenCheckoutApiService.getPaymentMethodsResponse(any(), any(), any(), any(), any())).thenReturn(createEpsPaymentMethodsResponse());
        when(baseStore.getAdyenRecurringContractMode()).thenReturn(RecurringContractMode.NONE);
        when(cartService.getSessionCart()).thenReturn(new CartModel());
        when(cartData.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_EPS);

        Model model = new ExtendedModelMap();
        adyenCheckoutFacade.initializeCheckoutData(model);

        verify(modelService).save(any());
        assertTrue(model.containsAttribute(MODEL_SELECTED_PAYMENT_METHOD));
        assertEquals(PAYMENT_METHOD_EPS, model.asMap().get(MODEL_SELECTED_PAYMENT_METHOD));
        assertTrue(model.containsAttribute(MODEL_ISSUER_LISTS));
        assertTrue(((Map<String, String>) model.asMap().get(MODEL_ISSUER_LISTS)).containsKey(PAYMENT_METHOD_EPS));
    }

    private PaymentMethodsResponse createEpsPaymentMethodsResponse() {
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setType(PAYMENT_METHOD_EPS);
        InputDetail detail = new InputDetail();
        detail.setKey("issuer");
        detail.setType("select");
        Item item = new Item();
        item.setId(UUID.randomUUID().toString());
        item.setName("FakeIssuer");
        detail.addItemsItem(item);
        paymentMethod.addInputDetailsItem(detail);

        PaymentMethodsResponse response = new PaymentMethodsResponse();
        response.setPaymentMethods(Collections.singletonList(paymentMethod));

        return response;
    }

    @Test
    public void testInitiatePosPaymentSuccess() throws Exception {
        when(adyenCheckoutApiService.sendSyncPosPaymentRequest(eq(cartData), any(), eq(SERVICE_ID))).thenReturn(terminalApiResponse);
        when(posPaymentResponseConverter.convert(saleToPoiResponse)).thenReturn(paymentsResponse);
        //TerminalAPIUtil.getPaymentResult
        when(terminalApiResponse.getSaleToPOIResponse()).thenReturn(saleToPoiResponse);
        when(saleToPoiResponse.getPaymentResponse()).thenReturn(nexpPaymentResponse);

        setSuccessfulPaymentStubs();

        OrderData orderDataResult = adyenCheckoutFacade.initiatePosPayment(request, cartData);
        assertEquals(orderData, orderDataResult);
        verify(adyenCheckoutApiService).sendSyncPosPaymentRequest(eq(cartData), any(), eq(SERVICE_ID));
        verify(posPaymentResponseConverter).convert(saleToPoiResponse);
        verify(adyenTransactionService).authorizeOrderModel(any(), any(), any());
        verify(checkoutFacade).placeOrder();
    }

    @Test
    public void testInitiatePosPaymentFailure() throws Exception {
        when(adyenCheckoutApiService.sendSyncPosPaymentRequest(eq(cartData), any(), eq(SERVICE_ID))).thenReturn(terminalApiResponse);
        when(posPaymentResponseConverter.convert(saleToPoiResponse)).thenReturn(paymentsResponse);
        //TerminalAPIUtil.getPaymentResult
        when(terminalApiResponse.getSaleToPOIResponse()).thenReturn(saleToPoiResponse);
        when(saleToPoiResponse.getPaymentResponse()).thenReturn(nexpPaymentResponse);
        when(nexpPaymentResponse.getResponse()).thenReturn(nexoResponse);

        try {
            adyenCheckoutFacade.initiatePosPayment(request, cartData);
            fail("Expected AdyenNonAuthorizedPaymentException");
        } catch (AdyenNonAuthorizedPaymentException e) {
            assertEquals(terminalApiResponse, e.getTerminalApiResponse());
        }
    }

    @Test
    public void testInitiatePosPaymentBadRequest() throws Exception {
        when(adyenCheckoutApiService.sendSyncPosPaymentRequest(eq(cartData), any(), eq(SERVICE_ID))).thenReturn(terminalApiResponse);
        when(posPaymentResponseConverter.convert(saleToPoiResponse)).thenReturn(paymentsResponse);
        //TerminalAPIUtil.getPaymentResult
        when(terminalApiResponse.getSaleToPOIResponse()).thenReturn(null);

        try {
            adyenCheckoutFacade.initiatePosPayment(request, cartData);
            fail("Expected AdyenNonAuthorizedPaymentException");
        } catch (AdyenNonAuthorizedPaymentException e) {
            assertEquals(terminalApiResponse, e.getTerminalApiResponse());
        }
    }

    @Test
    public void testCheckPosPaymentStatusSuccessAndPaymentSuccess() throws Exception {
        when(adyenCheckoutApiService.sendSyncPosStatusRequest(cartData, SERVICE_ID)).thenReturn(terminalApiResponse);
        when(posPaymentResponseConverter.convert(saleToPoiResponse)).thenReturn(paymentsResponse);
        //TerminalAPIUtil.getStatusResult
        when(terminalApiResponse.getSaleToPOIResponse()).thenReturn(saleToPoiResponse);
        when(saleToPoiResponse.getTransactionStatusResponse()).thenReturn(transactionStatusResponse);
        when(transactionStatusResponse.getResponse()).thenReturn(nexoStatusResponse);
        when(nexoStatusResponse.getResult()).thenReturn(ResultType.SUCCESS);
        //TerminalAPIUtil.getPaymentResult
        when(saleToPoiResponse.getPaymentResponse()).thenReturn(null);
        when(transactionStatusResponse.getRepeatedMessageResponse()).thenReturn(repeatedMessageResponse);
        when(repeatedMessageResponse.getRepeatedResponseMessageBody()).thenReturn(repeatedResponseMessageBody);
        when(repeatedResponseMessageBody.getPaymentResponse()).thenReturn(nexpPaymentResponse);
        when(nexpPaymentResponse.getResponse()).thenReturn(nexoResponse);
        when(nexoResponse.getResult()).thenReturn(ResultType.SUCCESS);

        setSuccessfulPaymentStubs();

        OrderData orderDataResult = adyenCheckoutFacade.checkPosPaymentStatus(request, cartData);
        assertEquals(orderData, orderDataResult);
        verify(adyenCheckoutApiService).sendSyncPosStatusRequest(cartData, SERVICE_ID);
        verify(posPaymentResponseConverter).convert(saleToPoiResponse);
        verify(adyenTransactionService).authorizeOrderModel(any(), any(), any());
        verify(checkoutFacade).placeOrder();
    }

    @Test
    public void testCheckPosPaymentStatusSuccessButPaymentFailure() throws Exception {
        when(adyenCheckoutApiService.sendSyncPosStatusRequest(cartData, SERVICE_ID)).thenReturn(terminalApiResponse);
        when(posPaymentResponseConverter.convert(saleToPoiResponse)).thenReturn(paymentsResponse);
        //TerminalAPIUtil.getStatusResult
        when(terminalApiResponse.getSaleToPOIResponse()).thenReturn(saleToPoiResponse);
        when(saleToPoiResponse.getTransactionStatusResponse()).thenReturn(transactionStatusResponse);
        when(transactionStatusResponse.getResponse()).thenReturn(nexoStatusResponse);
        when(nexoStatusResponse.getResult()).thenReturn(ResultType.SUCCESS);
        //TerminalAPIUtil.getPaymentResult
        when(saleToPoiResponse.getPaymentResponse()).thenReturn(null);
        when(transactionStatusResponse.getRepeatedMessageResponse()).thenReturn(repeatedMessageResponse);
        when(repeatedMessageResponse.getRepeatedResponseMessageBody()).thenReturn(repeatedResponseMessageBody);
        when(repeatedResponseMessageBody.getPaymentResponse()).thenReturn(nexpPaymentResponse);
        when(nexpPaymentResponse.getResponse()).thenReturn(nexoResponse);
        when(nexoResponse.getResult()).thenReturn(ResultType.FAILURE);

        try {
            adyenCheckoutFacade.checkPosPaymentStatus(request, cartData);
            fail("Expected AdyenNonAuthorizedPaymentException");
        } catch (AdyenNonAuthorizedPaymentException e) {
            assertEquals(terminalApiResponse, e.getTerminalApiResponse());
        }
    }

    @Test
    public void testCheckPosPaymentStatusTimeout() throws Exception {
        when(adyenCheckoutApiService.sendSyncPosStatusRequest(cartData, SERVICE_ID)).thenReturn(terminalApiResponse);
        when(posPaymentResponseConverter.convert(saleToPoiResponse)).thenReturn(paymentsResponse);
        //TerminalAPIUtil.getStatusResult
        when(terminalApiResponse.getSaleToPOIResponse()).thenReturn(saleToPoiResponse);
        when(saleToPoiResponse.getTransactionStatusResponse()).thenReturn(transactionStatusResponse);
        when(transactionStatusResponse.getResponse()).thenReturn(nexoStatusResponse);
        when(nexoStatusResponse.getResult()).thenReturn(ResultType.FAILURE);
        //TerminalAPIUtil.getErrorConditionForStatus
        when(nexoStatusResponse.getErrorCondition()).thenReturn(ErrorConditionType.IN_PROGRESS);
        //isPosTimedOut (will timeout after 10 seconds)
        long processStartTime = System.currentTimeMillis();
        when(request.getAttribute("paymentStartTime")).thenReturn(processStartTime);
        when(request.getAttribute("totalTimeout")).thenReturn(10);

        try {
            adyenCheckoutFacade.checkPosPaymentStatus(request, cartData);
            fail("Expected AdyenNonAuthorizedPaymentException");
        } catch (AdyenNonAuthorizedPaymentException e) {
            assertEquals(terminalApiResponse, e.getTerminalApiResponse());
            verify(adyenCheckoutFacade, atLeast(2)).checkPosPaymentStatus(request, cartData);
        }
    }

    @Test
    public void testCheckPosPaymentStatusError() throws Exception {
        when(adyenCheckoutApiService.sendSyncPosStatusRequest(cartData, SERVICE_ID)).thenReturn(terminalApiResponse);
        when(posPaymentResponseConverter.convert(saleToPoiResponse)).thenReturn(paymentsResponse);
        //TerminalAPIUtil.getStatusResult
        when(terminalApiResponse.getSaleToPOIResponse()).thenReturn(saleToPoiResponse);
        when(saleToPoiResponse.getTransactionStatusResponse()).thenReturn(transactionStatusResponse);
        when(transactionStatusResponse.getResponse()).thenReturn(nexoStatusResponse);
        when(nexoStatusResponse.getResult()).thenReturn(ResultType.FAILURE);
        //TerminalAPIUtil.getErrorConditionForStatus
        //when(statusResponse.getErrorCondition()).thenReturn(ErrorConditionType.CANCEL);

        try {
            adyenCheckoutFacade.checkPosPaymentStatus(request, cartData);
            fail("Expected AdyenNonAuthorizedPaymentException");
        } catch (AdyenNonAuthorizedPaymentException e) {
            assertEquals(terminalApiResponse, e.getTerminalApiResponse());
        }
    }


    private void setSuccessfulPaymentStubs() throws InvalidCartException, InvalidCartException {
        //Receipt
        when(nexpPaymentResponse.getPaymentReceipt()).thenReturn(Collections.singletonList(receipt));
        when(receipt.getDocumentQualifier()).thenReturn(DocumentQualifierType.CUSTOMER_RECEIPT);
        when(receipt.getOutputContent()).thenReturn(outputContent);
        when(outputContent.getOutputText()).thenReturn(Arrays.asList(textWithNameValue, textJustName, textJustValue));
        when(textWithNameValue.getText()).thenReturn("name=some_name&value=some_value");
        when(textJustName.getText()).thenReturn("name=some_name");
        when(textJustValue.getText()).thenReturn("value=some_value");
        //createAuthorizedOrder
        when(cartService.getSessionCart()).thenReturn(new CartModel());
        when(adyenTransactionService.authorizeOrderModel(any(), any(), any())).thenReturn(new PaymentTransactionModel());
        //createOrderFromPaymentsResponse
        when(checkoutFacade.placeOrder()).thenReturn(orderData);
        when(orderRepository.getOrderModel(any())).thenReturn(new OrderModel());
        //updateOrder
        doNothing().when(adyenOrderService).updatePaymentInfo(any(), any(), any());
    }

    @Test
    public void testAuthorisePaymentRedirect() throws Exception {
        doNothing().when(sessionService).removeAttribute(any());
        when(cartData.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD);
        when(commonI18NService.getCurrentLanguage()).thenReturn(null);
        when(request.getRequestURL()).thenReturn(new StringBuffer(URL));
        when(request.getRequestURI()).thenReturn(URL);
        // when(adyenPaymentService.authorisePayment(any(), any(), any())).thenReturn(paymentsResponse);
        when(paymentsResponse.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER);
        when(checkoutFacade.placeOrder()).thenReturn(orderData);
        when(orderRepository.getOrderModel(any())).thenReturn(orderModel);
        doNothing().when(modelService).save(any());
        when(cartService.getSessionCart()).thenReturn(cartModel);

        try {
            adyenCheckoutFacade.authorisePayment(request, cartData);
            fail("Expected AdyenNonAuthorizedPaymentException");
        } catch (AdyenNonAuthorizedPaymentException e) {
            verify(adyenCheckoutApiService).processPaymentRequest(eq(cartData),null, any(), any());
            verify(cartModel).setStatus(OrderStatus.PAYMENT_PENDING);
            verify(checkoutFacade).placeOrder();
            assertNotNull(e.getPaymentsResponse());
            assertEquals(PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER, e.getPaymentsResponse().getResultCode());
        }
    }

    @Test
    public void testHandle3DResponseAuthorised() throws Exception {
        when(adyenCheckoutApiService.authorise3DSPayment(any())).thenReturn(paymentsDetailsResponse);
        when(paymentsResponse.getMerchantReference()).thenReturn(MERCHANT_REFERENCE);
        when(orderRepository.getOrderModel(any())).thenReturn(orderModel);
        doNothing().when(sessionService).removeAttribute(any());
        when(adyenTransactionService.createPaymentTransactionFromResultCode(any(), any(), any(), any())).thenReturn(new PaymentTransactionModel());
        when(paymentsResponse.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.AUTHORISED);
        when(paymentsDetailsResponse.getResultCode()).thenReturn(PaymentDetailsResponse.ResultCodeEnum.AUTHORISED);
        doNothing().when(modelService).save(any());
        when(orderConverter.convert(any())).thenReturn(orderData);
        doNothing().when(adyenBusinessProcessService).triggerOrderProcessEvent(any(), any());
        when(paymentsDetailsResponse.getMerchantReference()).thenReturn(ORDER_CODE);
        when(orderModel.getEntries()).thenReturn(Collections.emptyList());

        OrderData orderDataResult = adyenCheckoutFacade.handle3DSResponse(details);
        assertEquals(orderData, orderDataResult);
        verify(adyenCheckoutApiService).authorise3DSPayment(details);
        verify(orderRepository).getOrderModel(ORDER_CODE);
        verify(orderConverter).convert(orderModel);
    }

    @Test
    public void testHandle3DResponseError() throws Exception {
        when(adyenCheckoutApiService.authorise3DSPayment(any())).thenReturn(paymentsDetailsResponse);
        when(paymentsResponse.getMerchantReference()).thenReturn(MERCHANT_REFERENCE);
        when(orderRepository.getOrderModel(any())).thenReturn(orderModel);
        doNothing().when(sessionService).removeAttribute(any());
        when(adyenTransactionService.createPaymentTransactionFromResultCode(any(), any(), any(), any())).thenReturn(new PaymentTransactionModel());
        when(paymentsResponse.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.REFUSED);
        doNothing().when(modelService).save(any());
        when(cartFactory.createCart()).thenReturn(cartModel);
        doNothing().when(cartService).setSessionCart(any());
        doNothing().when(cartService).changeCurrentCartUser(any());
        when(orderModel.getEntries()).thenReturn(Collections.emptyList());
        when(orderModel.getDeliveryAddress()).thenReturn(addressModel);
        when(addressModel.getOriginal()).thenReturn(addressModel);
        when(orderModel.getDeliveryMode()).thenReturn(deliveryModeModel);
        when(deliveryModeModel.getCode()).thenReturn(DELIVERY_MODE);
        doNothing().when(addressPopulator).populate(any(), any());
        when(checkoutFacade.setDeliveryAddress(any())).thenReturn(true);
        when(checkoutFacade.setDeliveryMode(any())).thenReturn(true);
        doNothing().when(calculationService).calculate(any());
        doNothing().when(adyenBusinessProcessService).triggerOrderProcessEvent(any(), any());
        when(orderModel.getUser()).thenReturn(userModel);
        when(cartModel.getUser()).thenReturn(userModel);
        when(orderModel.getStore()).thenReturn(storeModel);
        when(cartModel.getStore()).thenReturn(storeModel);
        when(paymentsDetailsResponse.getMerchantReference()).thenReturn(ORDER_CODE);
        when(paymentsResponse.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.CHALLENGESHOPPER);
        when(orderRepository.getOrderModel(ORDER_CODE)).thenReturn(orderModel);

        try {
            adyenCheckoutFacade.handle3DSResponse(details);
            fail("Expected AdyenNonAuthorizedPaymentException");
        } catch (AdyenNonAuthorizedPaymentException e) {
            verify(adyenCheckoutApiService).authorise3DSPayment(details);
            verify(cartFactory).createCart();
            verify(cartService).setSessionCart(cartModel);
            verify(calculationService).calculate(cartModel);
        }
    }

    @Test
    public void testHandle3DResponseThrowsApiException() throws Exception {
        when(sessionService.getAttribute(SESSION_PENDING_ORDER_CODE)).thenReturn(PENDING_ORDER_CODE);
        when(adyenCheckoutApiService.authorise3DSPayment(any())).thenThrow(new ApiException(EXCEPTION, 999));
        when(orderRepository.getOrderModel(any())).thenReturn(orderModel);
        doNothing().when(sessionService).removeAttribute(any());
        doNothing().when(modelService).save(any());
        when(cartFactory.createCart()).thenReturn(cartModel);
        doNothing().when(cartService).setSessionCart(any());
        doNothing().when(cartService).changeCurrentCartUser(any());
        when(orderModel.getEntries()).thenReturn(Collections.emptyList());
        when(orderModel.getDeliveryAddress()).thenReturn(addressModel);
        when(addressModel.getOriginal()).thenReturn(addressModel);
        when(orderModel.getDeliveryMode()).thenReturn(deliveryModeModel);
        when(deliveryModeModel.getCode()).thenReturn(DELIVERY_MODE);
        doNothing().when(addressPopulator).populate(any(), any());
        when(checkoutFacade.setDeliveryAddress(any())).thenReturn(true);
        when(checkoutFacade.setDeliveryMode(any())).thenReturn(true);
        doNothing().when(calculationService).calculate(any());
        doNothing().when(adyenBusinessProcessService).triggerOrderProcessEvent(any(), any());
        when(orderModel.getUser()).thenReturn(userModel);
        when(cartModel.getUser()).thenReturn(userModel);
        when(orderModel.getStore()).thenReturn(storeModel);
        when(cartModel.getStore()).thenReturn(storeModel);

        try {
            adyenCheckoutFacade.handle3DSResponse(details);
            fail("Expected AdyenNonAuthorizedPaymentException");
        } catch (AdyenNonAuthorizedPaymentException e) {
            assertEquals(e.getMessage(), EXCEPTION);
            verify(adyenCheckoutApiService).authorise3DSPayment(details);
            verify(orderRepository, times(2)).getOrderModel(PENDING_ORDER_CODE);
            verify(cartFactory).createCart();
            verify(cartService).setSessionCart(cartModel);
            verify(calculationService).calculate(cartModel);
        }
    }

    @Test
    public void testHandle3DS2ResponseAuthorised() throws Exception {
        when(request.getParameter(FINGERPRINT_RESULT)).thenReturn(null);
        when(request.getParameter(CHALLENGE_RESULT)).thenReturn(RESULT);
        when(adyenCheckoutApiService.authorise3DSPayment(details)).thenReturn(paymentsDetailsResponse);
        when(paymentsResponse.getMerchantReference()).thenReturn(MERCHANT_REFERENCE);
        when(orderRepository.getOrderModel(any())).thenReturn(orderModel);
        doNothing().when(sessionService).removeAttribute(any());
        when(adyenTransactionService.createPaymentTransactionFromResultCode(any(), any(), any(), any())).thenReturn(new PaymentTransactionModel());
        doNothing().when(adyenOrderService).updatePaymentInfo(any(), any(), any());
        when(paymentsDetailsResponse.getResultCode()).thenReturn(PaymentDetailsResponse.ResultCodeEnum.AUTHORISED);
        when(paymentsResponse.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.AUTHORISED);
        doNothing().when(modelService).save(any());
        when(orderConverter.convert(any())).thenReturn(orderData);
        doNothing().when(adyenBusinessProcessService).triggerOrderProcessEvent(any(), any());
        when(paymentsDetailsResponse.getMerchantReference()).thenReturn(ORDER_CODE);
        when(orderModel.getEntries()).thenReturn(Collections.emptyList());

        OrderData orderDataResult = adyenCheckoutFacade.handle3DSResponse(details);
        assertEquals(orderData, orderDataResult);
        verify(adyenCheckoutApiService).authorise3DSPayment(details);
        verify(orderConverter).convert(orderModel);
    }

    @Test
    public void testHandle3DS2ResponseChallengeShopper() throws Exception {
        when(request.getParameter(FINGERPRINT_RESULT)).thenReturn(RESULT);
        when(request.getParameter(CHALLENGE_RESULT)).thenReturn(null);
        when(adyenCheckoutApiService.authorise3DSPayment(details)).thenReturn(paymentsDetailsResponse);
        when(paymentsDetailsResponse.getMerchantReference()).thenReturn(ORDER_CODE);
        when(paymentsResponse.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.CHALLENGESHOPPER);
        when(orderRepository.getOrderModel(ORDER_CODE)).thenReturn(orderModel);
        when(orderModel.getEntries()).thenReturn(Collections.emptyList());

        try {
            adyenCheckoutFacade.handle3DSResponse(details);
            fail("Expected AdyenNonAuthorizedPaymentException");
        } catch (AdyenNonAuthorizedPaymentException e) {
            verify(adyenCheckoutApiService).authorise3DSPayment(details);
            assertNotNull(e.getPaymentsDetailsResponse());
        }
    }

    @Test
    public void testHandle3DS2ResponseError() throws Exception {
        when(request.getParameter(FINGERPRINT_RESULT)).thenReturn(null);
        when(request.getParameter(CHALLENGE_RESULT)).thenReturn(RESULT);
        when(adyenCheckoutApiService.authorise3DSPayment(details)).thenReturn(paymentsDetailsResponse);
        when(paymentsResponse.getMerchantReference()).thenReturn(MERCHANT_REFERENCE);
        when(orderRepository.getOrderModel(any())).thenReturn(orderModel);
        doNothing().when(sessionService).removeAttribute(any());
        when(adyenTransactionService.createPaymentTransactionFromResultCode(any(), any(), any(), any())).thenReturn(new PaymentTransactionModel());
        doNothing().when(adyenOrderService).updatePaymentInfo(any(), any(), any());
        when(paymentsResponse.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.REFUSED);
        doNothing().when(modelService).save(any());
        when(cartFactory.createCart()).thenReturn(cartModel);
        doNothing().when(cartService).setSessionCart(any());
        doNothing().when(cartService).changeCurrentCartUser(any());
        when(orderModel.getEntries()).thenReturn(Collections.emptyList());
        when(orderModel.getDeliveryAddress()).thenReturn(addressModel);
        when(addressModel.getOriginal()).thenReturn(addressModel);
        when(orderModel.getDeliveryMode()).thenReturn(deliveryModeModel);
        when(deliveryModeModel.getCode()).thenReturn(DELIVERY_MODE);
        doNothing().when(addressPopulator).populate(any(), any());
        when(checkoutFacade.setDeliveryAddress(any())).thenReturn(true);
        when(checkoutFacade.setDeliveryMode(any())).thenReturn(true);
        doNothing().when(calculationService).calculate(any());
        doNothing().when(adyenBusinessProcessService).triggerOrderProcessEvent(any(), any());
        when(orderModel.getUser()).thenReturn(userModel);
        when(cartModel.getUser()).thenReturn(userModel);
        when(orderModel.getStore()).thenReturn(storeModel);
        when(cartModel.getStore()).thenReturn(storeModel);
        when(paymentsDetailsResponse.getMerchantReference()).thenReturn(ORDER_CODE);
        when(orderModel.getEntries()).thenReturn(Collections.emptyList());

        try {
            adyenCheckoutFacade.handle3DSResponse(details);
            fail("Expected AdyenNonAuthorizedPaymentException");
        } catch (AdyenNonAuthorizedPaymentException e) {
            verify(adyenCheckoutApiService).authorise3DSPayment(details);
            verify(cartFactory).createCart();
            verify(cartService).setSessionCart(cartModel);
            verify(calculationService).calculate(cartModel);
        }
    }

    @Test
    public void testHandle3DS2ResponseThrowsApiException() throws Exception {
        when(request.getParameter(FINGERPRINT_RESULT)).thenReturn(null);
        when(request.getParameter(CHALLENGE_RESULT)).thenReturn(RESULT);
        when(sessionService.getAttribute(SESSION_PENDING_ORDER_CODE)).thenReturn(PENDING_ORDER_CODE);
        when(adyenCheckoutApiService.authorise3DSPayment(details)).thenThrow(new ApiException(EXCEPTION, 999));
        when(orderRepository.getOrderModel(any())).thenReturn(orderModel);
        doNothing().when(sessionService).removeAttribute(any());
        doNothing().when(modelService).save(any());
        when(cartFactory.createCart()).thenReturn(cartModel);
        doNothing().when(cartService).setSessionCart(any());
        doNothing().when(cartService).changeCurrentCartUser(any());
        when(orderModel.getEntries()).thenReturn(Collections.emptyList());
        when(orderModel.getDeliveryAddress()).thenReturn(addressModel);
        when(addressModel.getOriginal()).thenReturn(addressModel);
        when(orderModel.getDeliveryMode()).thenReturn(deliveryModeModel);
        when(deliveryModeModel.getCode()).thenReturn(DELIVERY_MODE);
        doNothing().when(addressPopulator).populate(any(), any());
        when(checkoutFacade.setDeliveryAddress(any())).thenReturn(true);
        when(checkoutFacade.setDeliveryMode(any())).thenReturn(true);
        doNothing().when(calculationService).calculate(any());
        doNothing().when(adyenBusinessProcessService).triggerOrderProcessEvent(any(), any());
        when(orderModel.getUser()).thenReturn(userModel);
        when(cartModel.getUser()).thenReturn(userModel);
        when(orderModel.getStore()).thenReturn(storeModel);
        when(cartModel.getStore()).thenReturn(storeModel);

        try {
            adyenCheckoutFacade.handle3DSResponse(details);
            fail("Expected AdyenNonAuthorizedPaymentException");
        } catch (AdyenNonAuthorizedPaymentException e) {
            assertEquals(e.getMessage(), EXCEPTION);
            verify(adyenCheckoutApiService).authorise3DSPayment(details);
            verify(orderRepository, times(2)).getOrderModel(PENDING_ORDER_CODE);
            verify(cartFactory).createCart();
            verify(cartService).setSessionCart(cartModel);
            verify(calculationService).calculate(cartModel);
        }
    }

    @Test
    public void testHandleRedirectPayloadAuthorised() throws Exception {
        PaymentCompletionDetails paymentCompletionDetails = new PaymentCompletionDetails();
        when(sessionService.getAttribute(Adyenv6coreConstants.PAYMENT_METHOD)).thenReturn(PAYMENT_METHOD);
        when(adyenCheckoutApiService.getPaymentDetailsFromPayload(paymentCompletionDetails)).thenReturn(paymentsDetailsResponse);
        when(adyenCheckoutApiService.authorise3DSPayment(any())).thenReturn(paymentsDetailsResponse);
        when(paymentsResponse.getMerchantReference()).thenReturn(MERCHANT_REFERENCE);
        when(orderRepository.getOrderModel(any())).thenReturn(orderModel);
        doNothing().when(sessionService).removeAttribute(any());
        when(paymentsResponse.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.AUTHORISED);
        when(paymentsDetailsResponse.getResultCode()).thenReturn(PaymentDetailsResponse.ResultCodeEnum.AUTHORISED);
        when(adyenTransactionService.createPaymentTransactionFromResultCode(any(), any(), any(), any())).thenReturn(new PaymentTransactionModel());
        doNothing().when(adyenOrderService).updatePaymentInfo(any(), any(), any());
        doNothing().when(adyenBusinessProcessService).triggerOrderProcessEvent(any(), any());
        when(paymentsDetailsResponse.getMerchantReference()).thenReturn(ORDER_CODE);
        when(orderModel.getEntries()).thenReturn(Collections.emptyList());


        PaymentDetailsResponse paymentsDetailsResponseReturned = adyenCheckoutFacade.handleRedirectPayload(paymentCompletionDetails);
        assertEquals(paymentsDetailsResponseReturned, paymentsDetailsResponse);
        assertEquals(PaymentResponse.ResultCodeEnum.AUTHORISED, paymentsDetailsResponseReturned.getResultCode());
        verify(adyenCheckoutApiService).getPaymentDetailsFromPayload(details);
        verify(orderRepository).getOrderModel(ORDER_CODE);
    }

    @Test
    public void testHandleRedirectPayloadNotAuthorised() throws Exception {
        PaymentCompletionDetails paymentCompletionDetails = new PaymentCompletionDetails();
        when(sessionService.getAttribute(Adyenv6coreConstants.PAYMENT_METHOD)).thenReturn(PAYMENT_METHOD);
        when(adyenCheckoutApiService.getPaymentDetailsFromPayload(paymentCompletionDetails)).thenReturn(paymentsDetailsResponse);
        when(paymentsResponse.getMerchantReference()).thenReturn(MERCHANT_REFERENCE);
        when(orderRepository.getOrderModel(any())).thenReturn(orderModel);
        doNothing().when(sessionService).removeAttribute(any());
        when(paymentsResponse.getResultCode()).thenReturn(PaymentResponse.ResultCodeEnum.REFUSED);
        when(paymentsDetailsResponse.getResultCode()).thenReturn(PaymentDetailsResponse.ResultCodeEnum.REFUSED);
        doNothing().when(modelService).save(any());
        when(cartFactory.createCart()).thenReturn(cartModel);
        doNothing().when(cartService).setSessionCart(any());
        doNothing().when(cartService).changeCurrentCartUser(any());
        when(orderModel.getEntries()).thenReturn(Collections.emptyList());
        when(orderModel.getDeliveryAddress()).thenReturn(addressModel);
        when(addressModel.getOriginal()).thenReturn(addressModel);
        when(orderModel.getDeliveryMode()).thenReturn(deliveryModeModel);
        when(deliveryModeModel.getCode()).thenReturn(DELIVERY_MODE);
        doNothing().when(addressPopulator).populate(any(), any());
        when(checkoutFacade.setDeliveryAddress(any())).thenReturn(true);
        when(checkoutFacade.setDeliveryMode(any())).thenReturn(true);
        doNothing().when(calculationService).calculate(any());
        when(adyenTransactionService.createPaymentTransactionFromResultCode(any(), any(), any(), any())).thenReturn(new PaymentTransactionModel());
        doNothing().when(adyenOrderService).updatePaymentInfo(any(),any(), any());
        doNothing().when(adyenBusinessProcessService).triggerOrderProcessEvent(any(), any());
        when(orderModel.getUser()).thenReturn(userModel);
        when(cartModel.getUser()).thenReturn(userModel);
        when(orderModel.getStore()).thenReturn(storeModel);
        when(cartModel.getStore()).thenReturn(storeModel);
        when(orderRepository.getOrderModel(ORDER_CODE)).thenReturn(orderModel);
        when(orderModel.getEntries()).thenReturn(Collections.emptyList());
        when(paymentsDetailsResponse.getMerchantReference()).thenReturn(ORDER_CODE);

        PaymentDetailsResponse paymentsDetailsResponseReturned = adyenCheckoutFacade.handleRedirectPayload(paymentCompletionDetails);
        assertEquals(paymentsDetailsResponseReturned, paymentsDetailsResponse);
        assertEquals(PaymentResponse.ResultCodeEnum.REFUSED, paymentsDetailsResponseReturned.getResultCode());
        verify(adyenCheckoutApiService).getPaymentDetailsFromPayload(details);
        verify(cartFactory).createCart();
        verify(cartService).setSessionCart(cartModel);
        verify(calculationService).calculate(cartModel);
    }

    /**
     * Test dependency of addressPopulator
     * It should accept all populators which implements interface Populator<AddressModel, AddressData>
     */
    @Test
    public void testDependencyAddressPopulator() throws Exception {
        adyenCheckoutFacade.setAddressPopulator(new Populator<AddressModel, AddressData>() {
            @Override
            public void populate(AddressModel source, AddressData target) throws ConversionException {
                // not relevant
            }
        });
    }
}
