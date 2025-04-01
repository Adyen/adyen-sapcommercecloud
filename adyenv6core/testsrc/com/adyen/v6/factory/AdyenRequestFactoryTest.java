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
package com.adyen.v6.factory;

import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.GenericIssuerPaymentMethodDetails;
import com.adyen.model.checkout.PayPalDetails;
import com.adyen.model.checkout.PaymentDetails;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.nexo.AmountsReq;
import com.adyen.model.nexo.MessageCategoryType;
import com.adyen.model.nexo.MessageHeader;
import com.adyen.model.nexo.SaleData;
import com.adyen.model.nexo.TransactionStatusRequest;
import com.adyen.model.recurring.Recurring;
import com.adyen.model.terminal.SaleToAcquirerData;
import com.adyen.model.terminal.TerminalAPIRequest;
import com.adyen.v6.enums.RecurringContractMode;
import com.adyen.v6.model.RequestInfo;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CCPaymentInfoData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.data.PriceData;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.math.BigDecimal;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_EPS;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_ONECLICK;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_PAYPAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdyenRequestFactoryTest {

    private static final String MERCHANT_ACCOUNT = "merchantAccount";
    private static final String BILLING_TOWN = "billingTown";
    private static final String BILLING_COUNTRY = "GR";
    private static final String DELIVERY_TOWN = "deliveryTown";
    private static final String DELIVERY_COUNTRY = "NL";
    private static final String CUSTOMER_ID = "uuid";
    private static final String CUSTOMER_EMAIL = "email";
    private static final String CART_CODE = "code";
    private static final String STORE_NAME = "StoreName";
    private static final String CURRENCY = "EUR";
    private static final String AMOUNT = "12.34";
    private static final String RECURRING_REFERENCE = "recurring_reference";
    private static final String ISSUER_ID = "issuerId";
    private static final String FIRST_NAME = "FirstName";
    private static final String LAST_NAME = "LastName";
    private static final String TITLE_CODE = "mr";
    //Request
    private static final String ACCEPT_HEADER = "Accept";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String REMOTE_ADDRESS = "1.2.3.4";
    private static final String REQUEST_URL = "https://localhost:9002/electronics/en/checkout/multi/adyen/summary/placeOrder";
    private static final String REQUEST_URI = "/electronics/en/checkout/multi/adyen/summary/placeOrder";
    private static final String RETURN_URL = "https://localhost:9002/electronics/en/checkout/multi/adyen/summary/checkout-adyen-response";
    //POS
    private static final String SERVICE_ID = "serviceId";
    private static final String TERMINAL_ID = "V400m-123456789";


    @InjectMocks
    private AdyenRequestFactory adyenRequestFactory;

    @Mock
    private ConfigurationService configurationServiceMock;


    @Mock
    private CartData cartDataMock;

    @Mock
    private javax.servlet.http.HttpServletRequest requestMock;

    @Mock
    private CustomerModel customerModelMock;

    @Mock
    private AddressData deliveryAddressMock;

    @Mock
    private AddressData billingAddressMock;

    @Mock
    private CCPaymentInfoData paymentInfoMock;

    @Mock
    private CountryData deliveryCountryDataMock;

    @Mock
    private CountryData billingCountryDataMock;
    @Mock
    private PaymentDetails paymentDetailsMock;

    private OrderEntryData orderEntryData;

    private ProductData productData;

    @Before
    public void setUp() {
        adyenRequestFactory = new AdyenRequestFactory(configurationServiceMock);
        PriceData priceData = new PriceData();
        priceData.setValue(new BigDecimal(AMOUNT));
        priceData.setCurrencyIso(CURRENCY);

        when(cartDataMock.getTotalPriceWithTax()).thenReturn(priceData);
        when(cartDataMock.getCode()).thenReturn(CART_CODE);
        when(cartDataMock.getDeliveryAddress()).thenReturn(deliveryAddressMock);
        when(cartDataMock.getPaymentInfo()).thenReturn(paymentInfoMock);
        when(cartDataMock.getStore()).thenReturn(STORE_NAME);
        when(cartDataMock.getAdyenTerminalId()).thenReturn(TERMINAL_ID);

        when(paymentInfoMock.getBillingAddress()).thenReturn(billingAddressMock);
        when(deliveryAddressMock.getTown()).thenReturn(DELIVERY_TOWN);
        when(billingAddressMock.getTown()).thenReturn(BILLING_TOWN);
        when(deliveryAddressMock.getCountry()).thenReturn(deliveryCountryDataMock);
        when(billingAddressMock.getCountry()).thenReturn(billingCountryDataMock);
        when(deliveryCountryDataMock.getIsocode()).thenReturn(DELIVERY_COUNTRY);
        when(billingCountryDataMock.getIsocode()).thenReturn(BILLING_COUNTRY);

        when(customerModelMock.getCustomerID()).thenReturn(CUSTOMER_ID);

        when(customerModelMock.getContactEmail()).thenReturn(CUSTOMER_EMAIL);

        when(requestMock.getHeader(USER_AGENT_HEADER)).thenReturn(USER_AGENT_HEADER);
        when(requestMock.getHeader(ACCEPT_HEADER)).thenReturn(ACCEPT_HEADER);
        when(requestMock.getRemoteAddr()).thenReturn(REMOTE_ADDRESS);
        when(requestMock.getRequestURL()).thenReturn(new StringBuffer(REQUEST_URL));
        when(requestMock.getRequestURI()).thenReturn(REQUEST_URI);

        Configuration configurationMock = mock(BaseConfiguration.class);
        when(configurationMock.getString(any(String.class))).thenReturn("dummy");
        when(configurationServiceMock.getConfiguration()).thenReturn(configurationMock);
    }

    @Test
    public void testAuthorise() {
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_CC);

        PaymentRequest paymentsRequest;

        paymentsRequest = adyenRequestFactory.createPaymentsRequest(MERCHANT_ACCOUNT, cartDataMock, new PaymentRequest(), new RequestInfo(requestMock), customerModelMock, RecurringContractMode.RECURRING, false);

        //use delivery/billing address from cart
        assertEquals(DELIVERY_TOWN, paymentsRequest.getDeliveryAddress().getCity());
        assertEquals(DELIVERY_COUNTRY, paymentsRequest.getDeliveryAddress().getCountry());
        assertEquals(BILLING_TOWN, paymentsRequest.getBillingAddress().getCity());
        assertEquals(BILLING_COUNTRY, paymentsRequest.getBillingAddress().getCountry());

        assertNotNull(paymentsRequest.getShopperReference());

        assertEquals(USER_AGENT_HEADER, paymentsRequest.getBrowserInfo().getUserAgent());
        assertEquals(ACCEPT_HEADER, paymentsRequest.getBrowserInfo().getAcceptHeader());
        assertEquals(REMOTE_ADDRESS, paymentsRequest.getShopperIP());

        //Test recurring contract when remember-me is NOT set
        when(cartDataMock.getAdyenRememberTheseDetails()).thenReturn(false);
        testRecurringOption(null, null);
        testRecurringOption(RecurringContractMode.NONE, null);
        testRecurringOption(RecurringContractMode.ONECLICK, null);
        testRecurringOption(RecurringContractMode.RECURRING, Recurring.ContractEnum.RECURRING);
        testRecurringOption(RecurringContractMode.RECURRING, Recurring.ContractEnum.RECURRING);

        //Test recurring contract when remember-me is set
        when(cartDataMock.getAdyenRememberTheseDetails()).thenReturn(true);
        testRecurringOption(null, null);
        testRecurringOption(RecurringContractMode.NONE, null);
        testRecurringOption(RecurringContractMode.ONECLICK, Recurring.ContractEnum.ONECLICK);
        testRecurringOption(RecurringContractMode.RECURRING, Recurring.ContractEnum.RECURRING);
        testRecurringOption(RecurringContractMode.ONECLICK_RECURRING, Recurring.ContractEnum.ONECLICK);

        //When a store card is selected, send the reference and include the recurring contract
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_ONECLICK);
        when(cartDataMock.getAdyenSelectedReference()).thenReturn(RECURRING_REFERENCE);
        when(cartDataMock.getAdyenRememberTheseDetails()).thenReturn(false);
        paymentsRequest = adyenRequestFactory.createPaymentsRequest(MERCHANT_ACCOUNT, cartDataMock, new PaymentRequest(), new RequestInfo(requestMock), customerModelMock, null, false);

        final CardDetails paymentMethodDetails = paymentsRequest.getPaymentMethod().getCardDetails();
        assertEquals(RECURRING_REFERENCE, paymentMethodDetails.getRecurringDetailReference());
    }

    private void testRecurringOption(final RecurringContractMode recurringContractModeSetting, final Recurring.ContractEnum expectedRecurringContractMode) {
        PaymentRequest paymentRequest = adyenRequestFactory.createPaymentsRequest(MERCHANT_ACCOUNT, cartDataMock, new PaymentRequest(),new RequestInfo(requestMock), customerModelMock, recurringContractModeSetting, false);

        if (expectedRecurringContractMode == null) {
            assertFalse(paymentRequest.getEnableRecurring());
        } else {
            assertEquals(expectedRecurringContractMode, paymentRequest.getRecurringProcessingModel());
        }

        //when customer is set, shopperReference and Email should be set as well
        assertEquals(CUSTOMER_ID, paymentRequest.getShopperReference());
        assertEquals(CUSTOMER_EMAIL, paymentRequest.getShopperEmail());
    }

    @Test
    public void testEpsPaymentRequest() throws Exception {
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_EPS);
        when(cartDataMock.getAdyenReturnUrl()).thenReturn(RETURN_URL);
        final GenericIssuerPaymentMethodDetails type = new GenericIssuerPaymentMethodDetails().type(GenericIssuerPaymentMethodDetails.TypeEnum.EPS).issuer(ISSUER_ID);
        when(cartDataMock.getAdyenIssuerId()).thenReturn(ISSUER_ID);

        final PaymentRequest paymentsRequest = adyenRequestFactory.createPaymentsRequest(MERCHANT_ACCOUNT, cartDataMock,  new PaymentRequest(),new RequestInfo(requestMock), customerModelMock, null, false);

        assertNotNull(paymentsRequest);
        assertEquals(RETURN_URL, paymentsRequest.getReturnUrl());
        assertNotNull(paymentsRequest.getPaymentMethod());
        //assertEquals(PAYMENT_METHOD_EPS, paymentsRequest.getPaymentMethod().getType());
        assertEquals(ISSUER_ID, type.getIssuer());
    }

    @Test
    public void testPaypalPaymentRequest() throws Exception {
        when(cartDataMock.getAdyenPaymentMethod()).thenReturn(PAYMENT_METHOD_PAYPAL);
        when(cartDataMock.getAdyenReturnUrl()).thenReturn(RETURN_URL);
        when(deliveryAddressMock.getFirstName()).thenReturn(FIRST_NAME);
        when(deliveryAddressMock.getLastName()).thenReturn(LAST_NAME);
        when(deliveryAddressMock.getTitleCode()).thenReturn(TITLE_CODE);


        final PaymentRequest paymentsRequest = adyenRequestFactory.createPaymentsRequest(MERCHANT_ACCOUNT, cartDataMock,new PaymentRequest(), new RequestInfo(requestMock), customerModelMock, null, false);

        assertNotNull(paymentsRequest);
        assertEquals(RETURN_URL, paymentsRequest.getReturnUrl());
        assertNotNull(paymentsRequest.getPaymentMethod());
        assertTrue(paymentsRequest.getPaymentMethod().getActualInstance() instanceof PayPalDetails);
        assertNotNull(paymentsRequest.getShopperName());
        assertEquals(FIRST_NAME, paymentsRequest.getShopperName().getFirstName());
        assertEquals(LAST_NAME, paymentsRequest.getShopperName().getLastName());

    }

    @Test
    public void testTerminalApiPaymentRequestAnonymous() throws Exception {
        TerminalAPIRequest terminalApiRequest = adyenRequestFactory.createTerminalAPIRequest(cartDataMock, customerModelMock, null, SERVICE_ID);

        validateTerminalApiPaymentRequest(terminalApiRequest);
    }

    @Test
    public void testTerminalApiPaymentRequestWithRecurring() throws Exception {
        TerminalAPIRequest terminalApiRequest = adyenRequestFactory.createTerminalAPIRequest(cartDataMock, customerModelMock, RecurringContractMode.ONECLICK_RECURRING, SERVICE_ID);

        validateTerminalApiPaymentRequest(terminalApiRequest);

        assertNotNull(terminalApiRequest.getSaleToPOIRequest().getPaymentRequest().getSaleData().getSaleToAcquirerData());

        SaleToAcquirerData saleToAcquirerData = terminalApiRequest.getSaleToPOIRequest().getPaymentRequest().getSaleData().getSaleToAcquirerData();
        assertTrue(saleToAcquirerData.getRecurringContract().equals(Recurring.ContractEnum.RECURRING.toString()));
        assertTrue(saleToAcquirerData.getShopperEmail().equals(CUSTOMER_EMAIL));
        assertTrue(saleToAcquirerData.getShopperReference().equals(CUSTOMER_ID));
    }

    private void validateTerminalApiPaymentRequest(TerminalAPIRequest terminalApiRequest) {
        assertNotNull(terminalApiRequest);
        assertNotNull(terminalApiRequest.getSaleToPOIRequest());
        assertNotNull(terminalApiRequest.getSaleToPOIRequest().getMessageHeader());

        MessageHeader messageHeader = terminalApiRequest.getSaleToPOIRequest().getMessageHeader();
        assertNotNull(messageHeader.getServiceID());
        assertEquals(STORE_NAME, messageHeader.getSaleID());
        assertEquals(TERMINAL_ID, messageHeader.getPOIID());

        assertNotNull(terminalApiRequest.getSaleToPOIRequest().getPaymentRequest());
        assertNotNull(terminalApiRequest.getSaleToPOIRequest().getPaymentRequest().getSaleData());

        SaleData saleData = terminalApiRequest.getSaleToPOIRequest().getPaymentRequest().getSaleData();
        assertNotNull(saleData.getSaleTransactionID());
        assertEquals(CART_CODE, saleData.getSaleTransactionID().getTransactionID());

        assertNotNull(terminalApiRequest.getSaleToPOIRequest().getPaymentRequest().getPaymentTransaction());
        assertNotNull(terminalApiRequest.getSaleToPOIRequest().getPaymentRequest().getPaymentTransaction().getAmountsReq());

        AmountsReq amountsReq = terminalApiRequest.getSaleToPOIRequest().getPaymentRequest().getPaymentTransaction().getAmountsReq();
        assertEquals(CURRENCY, amountsReq.getCurrency());
        assertEquals(AMOUNT, amountsReq.getRequestedAmount().toString());

        SaleToAcquirerData saleToAcquirerData = terminalApiRequest.getSaleToPOIRequest().getPaymentRequest().getSaleData().getSaleToAcquirerData();
        assertNotNull(saleToAcquirerData.getApplicationInfo());
        assertTrue("adyen-java-api-library".equals(saleToAcquirerData.getApplicationInfo().getAdyenLibrary().getName()));
        assertTrue("adyen-hybris".equals(saleToAcquirerData.getApplicationInfo().getMerchantApplication().getName()));
        assertTrue("Hybris".equals(saleToAcquirerData.getApplicationInfo().getExternalPlatform().getName()));

    }

    @Test
    public void testTerminalApiStatusRequest() throws Exception {
        TerminalAPIRequest terminalApiRequest = adyenRequestFactory.createTerminalAPIRequestForStatus(cartDataMock, SERVICE_ID);

        assertNotNull(terminalApiRequest);
        assertNotNull(terminalApiRequest.getSaleToPOIRequest());
        assertNotNull(terminalApiRequest.getSaleToPOIRequest().getMessageHeader());

        MessageHeader messageHeader = terminalApiRequest.getSaleToPOIRequest().getMessageHeader();
        assertNotNull(messageHeader.getServiceID());
        assertEquals(STORE_NAME, messageHeader.getSaleID());
        assertEquals(TERMINAL_ID, messageHeader.getPOIID());

        assertNotNull(terminalApiRequest.getSaleToPOIRequest().getTransactionStatusRequest());
        TransactionStatusRequest transactionStatusRequest = terminalApiRequest.getSaleToPOIRequest().getTransactionStatusRequest();

        assertNotNull(transactionStatusRequest.getMessageReference());
        assertNotNull(transactionStatusRequest.getMessageReference().getMessageCategory());
        assertEquals(MessageCategoryType.PAYMENT, transactionStatusRequest.getMessageReference().getMessageCategory());
        assertNotNull(transactionStatusRequest.getMessageReference().getSaleID());
        assertEquals(STORE_NAME, transactionStatusRequest.getMessageReference().getSaleID());
        assertNotNull(transactionStatusRequest.getMessageReference().getServiceID());
        assertEquals(SERVICE_ID, transactionStatusRequest.getMessageReference().getServiceID());
    }

}
