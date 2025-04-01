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
package com.adyen.v6.facades;

import com.adyen.model.checkout.PaymentDetailsRequest;
import com.adyen.model.checkout.PaymentDetailsResponse;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.checkout.PaymentResponseAction;
import com.adyen.v6.exceptions.AdyenNonAuthorizedPaymentException;
import com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.repository.OrderRepository;
import com.adyen.v6.service.AdyenBusinessProcessService;
import com.adyen.v6.service.AdyenOrderService;
import com.adyen.v6.service.AdyenTransactionService;
import com.adyen.v6.service.DefaultAdyenCheckoutApiService;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.core.model.c2l.LanguageModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CalculationService;
import de.hybris.platform.order.CartFactory;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.keygenerator.KeyGenerator;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.SignatureException;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.facades.impl.DefaultAdyenCheckoutFacade.SESSION_LOCKED_CART;
import static com.adyen.v6.model.RequestInfo.ACCEPT_HEADER;
import static com.adyen.v6.model.RequestInfo.USER_AGENT_HEADER;
import static de.hybris.platform.order.impl.DefaultCartService.SESSION_CART_PARAMETER_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenCheckoutFacadeTest {
    public static final String CODE = "code";

    @Spy
    @InjectMocks
    private DefaultAdyenCheckoutFacade adyenCheckoutFacade;

    @Mock
    private BaseStoreService baseStoreServiceMock;
    @Mock
    private SessionService sessionServiceMock;
    @Mock
    private CartService cartServiceMock;
    @Mock
    private OrderFacade orderFacadeMock;
    @Mock
    private CheckoutFacade checkoutFacadeMock;
    @Mock
    private DefaultAdyenCheckoutApiService adyenPaymentServiceMock;
    @Mock
    private AdyenTransactionService adyenTransactionServiceMock;
    @Mock
    private OrderRepository orderRepositoryMock;
    @Mock
    private AdyenOrderService adyenOrderServiceMock;
    @Mock
    private CheckoutCustomerStrategy checkoutCustomerStrategyMock;
    @Mock
    private AdyenPaymentServiceFactory adyenPaymentServiceFactoryMock;
    @Mock
    private CommonI18NService commonI18NServiceMock;
    @Mock
    private KeyGenerator keyGeneratorMock;
    @Mock
    private ModelService modelServiceMock;
    @Mock
    private AdyenBusinessProcessService adyenBusinessProcessServiceMock;
    @Mock
    private Converter<OrderModel, OrderData> orderConverterMock;
    @Mock
    private CartFactory cartFactoryMock;
    @Mock
    private CalculationService calculationServiceMock;

    @Mock
    private AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;

    @Mock
    private OrderModel orderModelMock;
    private CartModel cartModelMock;

    private PaymentResponse paymentResponseMock;
    private PaymentResponse paymentsResponseMock;
    private PaymentDetailsResponse paymentsDetailsResponseMock;

    private PaymentResponse paymentsResponse;

    final String PAYMENT_RESPONSE_ACTION_JSON = "{"
            + "\"type\": \"typeValue\","
            + "\"paymentMethodType\": \"paymentMethodTypeValue\","
            + "\"url\": \"urlValue\","
            + "\"method\": \"methodValue\","
            + "\"token\": \"tokenValue\","
            + "\"paymentData\": \"paymentDataValue\","
            + "\"action\": \"actionValue\","
            + "\"sdkData\": {"
            + "    \"ephemeralPublicKey\": \"ephemeralPublicKeyValue\","
            + "    \"tag\": \"tagValue\","
            + "    \"publicKeyHash\": \"publicKeyHashValue\","
            + "    \"transactionId\": \"transactionIdValue\""
            + "},"
            + "\"paymentMethod\": {"
            + "    \"type\": \"typeValue\","
            + "    \"number\": \"numberValue\","
            + "    \"expiryMonth\": \"expiryMonthValue\","
            + "    \"expiryYear\": \"expiryYearValue\","
            + "    \"holderName\": \"holderNameValue\","
            + "    \"cvc\": \"cvcValue\""
            + "},"
            + "\"issuer\": \"issuerValue\","
            + "\"reference\": \"referenceValue\""
            + "}";

    @Before
    public void setUp() throws SignatureException, InvalidCartException, CalculationException, IOException {
        BaseStoreModel baseStoreModelMock = mock(BaseStoreModel.class);
        cartModelMock = mock(CartModel.class);
        OrderData orderDataMock = mock(OrderData.class);
        paymentsResponseMock = mock(PaymentResponse.class);
        paymentsDetailsResponseMock = mock(PaymentDetailsResponse.class);
        CartData cartDataMock = mock(CartData.class);

        doNothing().when(calculationServiceMock).calculate(cartModelMock);

        doReturn(orderDataMock).when(orderConverterMock).convert(orderModelMock);
        when(adyenMerchantAccountStrategy.getWebMerchantAccount()).thenReturn("merchantAccount");
        when(baseStoreServiceMock.getCurrentBaseStore()).thenReturn(baseStoreModelMock);

        when(cartModelMock.getCode()).thenReturn(CODE);
        when(cartServiceMock.getSessionCart()).thenReturn(cartModelMock);
        when(cartFactoryMock.createCart()).thenReturn(cartModelMock);

        when(orderDataMock.getCode()).thenReturn(CODE);
        when(checkoutFacadeMock.placeOrder()).thenReturn(orderDataMock);

        when(cartDataMock.getCode()).thenReturn(CODE);
        when(checkoutFacadeMock.getCheckoutCart()).thenReturn(cartDataMock);

//        when(paymentResponseMock.getPspReference()).thenReturn("pspRef");

        when(paymentsResponseMock.getPspReference()).thenReturn("pspRef");
        paymentsResponse = new PaymentResponse();
        paymentsResponse.setPspReference("pspRef");

        paymentsResponse.setAction(PaymentResponseAction.fromJson(PAYMENT_RESPONSE_ACTION_JSON));

        when(adyenPaymentServiceFactoryMock.createAdyenCheckoutApiService(baseStoreModelMock)).thenReturn(adyenPaymentServiceMock);

        LanguageModel languageModel = new LanguageModel();
        languageModel.setIsocode("en");

        when(commonI18NServiceMock.getCurrentLanguage()).thenReturn(languageModel);
        when(keyGeneratorMock.generate()).thenReturn(new Object());
        adyenCheckoutFacade.setOrderConverter(orderConverterMock);
    }

    @Test
    public void testRestoreSessionCart() throws InvalidCartException {
        when(sessionServiceMock.getAttribute(SESSION_LOCKED_CART)).thenReturn(null);
        try {
            adyenCheckoutFacade.restoreSessionCart();
            fail("Expecting exception");
        } catch (InvalidCartException ignored) {
        }

        when(sessionServiceMock.getAttribute(SESSION_LOCKED_CART)).thenReturn(cartModelMock);
        adyenCheckoutFacade.restoreSessionCart();

        verify(cartServiceMock).setSessionCart(cartModelMock);
        verify(sessionServiceMock).removeAttribute(SESSION_LOCKED_CART);
    }

    @Test
    public void testLockSessionCart() {
        when(sessionServiceMock.getAttribute(SESSION_LOCKED_CART)).thenReturn(null);

        adyenCheckoutFacade.lockSessionCart();

        verify(sessionServiceMock).setAttribute(SESSION_LOCKED_CART, cartModelMock);
        verify(sessionServiceMock).removeAttribute(SESSION_CART_PARAMETER_NAME);
    }

    @Test
    public void testAuthorizeCardPayment() throws Exception {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        CartData cartDataMock = mock(CartData.class);
        OrderModel orderModelMock = mock(OrderModel.class);

        //When payment is authorized
        paymentsResponse.setResultCode(PaymentResponse.ResultCodeEnum.AUTHORISED);
        when(checkoutCustomerStrategyMock.isAnonymousCheckout()).thenReturn(true);
        when(checkoutCustomerStrategyMock.getCurrentUserForCheckout()).thenReturn(null);
        when(adyenPaymentServiceMock.processPaymentRequest(any(CartData.class),null, any(RequestInfo.class), any(CustomerModel.class))).thenReturn(paymentsResponse);
        when(orderRepositoryMock.getOrderModel(CODE)).thenReturn(orderModelMock);
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_CC);
        when(requestMock.getHeader(USER_AGENT_HEADER)).thenReturn("userAgent");
        when(requestMock.getHeader(ACCEPT_HEADER)).thenReturn("acceptHeader");
        when(requestMock.getRemoteAddr()).thenReturn("addr");
        when(requestMock.getRequestURI()).thenReturn("uri");
        when(requestMock.getRequestURL()).thenReturn(new StringBuffer("url"));

        adyenCheckoutFacade.authorisePayment(requestMock, cartDataMock);

        verifyAuthorized(orderModelMock);

        //When payment is 3D secure
        paymentsResponse.setResultCode(PaymentResponse.ResultCodeEnum.REDIRECTSHOPPER);

        try {
            adyenCheckoutFacade.authorisePayment(requestMock, cartDataMock);
            fail("Expecting exception");
        } catch (AdyenNonAuthorizedPaymentException e) {
            //throw exception with paymentResult details
            assertEquals(paymentsResponse, e.getPaymentsResponse());
        }

        //When payment is refused
        paymentsResponse.setResultCode(PaymentResponse.ResultCodeEnum.REFUSED);

        try {
            adyenCheckoutFacade.authorisePayment(requestMock, cartDataMock);
            fail("Expecting exception");
        } catch (AdyenNonAuthorizedPaymentException e) {
            //throw exception with paymentResult details
            assertEquals(paymentsResponse, e.getPaymentsResponse());
        }
    }

    @Test
    public void testHandle3DResponse() throws Exception {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        PaymentDetailsRequest paymentDetailsRequestMock = mock(PaymentDetailsRequest.class);
        CartData cartDataMock = mock(CartData.class);
        OrderModel orderModelMock = mock(OrderModel.class);

        //When payment is authorized
        paymentsDetailsResponseMock.setResultCode(PaymentDetailsResponse.ResultCodeEnum.AUTHORISED);
        when(checkoutCustomerStrategyMock.isAnonymousCheckout()).thenReturn(true);
        when(checkoutCustomerStrategyMock.getCurrentUserForCheckout()).thenReturn(null);
        when(adyenPaymentServiceMock.authorise3DSPayment(new PaymentDetailsRequest())).thenReturn(paymentsDetailsResponseMock);
        when(orderRepositoryMock.getOrderModel(CODE)).thenReturn(orderModelMock);
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_CC);
        when(requestMock.getHeader(USER_AGENT_HEADER)).thenReturn("userAgent");
        when(requestMock.getHeader(ACCEPT_HEADER)).thenReturn("acceptHeader");
        when(requestMock.getRemoteAddr()).thenReturn("addr");
        when(requestMock.getRequestURI()).thenReturn("uri");
        when(requestMock.getRequestURL()).thenReturn(new StringBuffer("url"));

        verifyAuthorized(orderModelMock);

        //When payment is refused
        paymentsDetailsResponseMock.setResultCode(PaymentDetailsResponse.ResultCodeEnum.REFUSED);

        try {
            adyenCheckoutFacade.handle3DSResponse(paymentDetailsRequestMock);
            fail("Expecting exception");
        } catch (AdyenNonAuthorizedPaymentException e) {
            //throw exception with paymentResult details
            assertEquals(paymentsDetailsResponseMock, e.getPaymentsDetailsResponse());
        }
    }

    private CartData createCartData() {
        CartData cartData = new CartData();

        PriceData priceData = new PriceData();
        priceData.setValue(new BigDecimal("12.34"));
        priceData.setCurrencyIso("EUR");

        AddressData deliveryAddress = new AddressData();
        deliveryAddress.setCountry(new CountryData());

        cartData.setTotalPrice(priceData);
        cartData.setCode(CODE);
        cartData.setDeliveryAddress(deliveryAddress);

        return cartData;
    }

    private void verifyAuthorized(OrderModel orderModelMock) throws InvalidCartException {
        //authorized transactions should be stored
        verify(adyenTransactionServiceMock).authorizeOrderModel(cartModelMock, CODE, "pspRef");
        //order should be created
        verify(checkoutFacadeMock).placeOrder();
        //update of order metadata should happen
        verify(adyenOrderServiceMock).updatePaymentInfo(eq(orderModelMock), any(), any());
    }
}
