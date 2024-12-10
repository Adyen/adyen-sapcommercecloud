package com.adyen.v6.facades.impl;

import com.adyen.commerce.facades.AdyenCheckoutApiFacade;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.customer.CustomerFacade;
import de.hybris.platform.commercefacades.i18n.I18NFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.user.data.RegionData;
import de.hybris.platform.commerceservices.customer.CustomerAccountService;
import de.hybris.platform.commerceservices.customer.DuplicateUidException;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.c2l.LanguageModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderEntryModel;
import de.hybris.platform.core.model.order.delivery.DeliveryModeModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.product.UnitModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.deliveryzone.model.ZoneDeliveryModeModel;
import de.hybris.platform.deliveryzone.model.ZoneDeliveryModeValueModel;
import de.hybris.platform.order.CartFactory;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.DeliveryModeService;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.product.ProductService;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static com.adyen.v6.facades.impl.DefaultAdyenExpressCheckoutFacade.USER_NAME;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultAdyenExpressCheckoutFacadeTest {

    @Mock
    private ModelService modelService;

    @Mock
    private CartService cartService;

    @Mock
    private CommerceCartService commerceCartService;

    @Mock
    private Converter<AddressData, AddressModel> addressReverseConverter;

    @Mock
    private ProductService productService;

    @Mock
    private I18NFacade i18NFacade;

    @Mock
    private DeliveryModeService deliveryModeService;

    @Mock
    private CommonI18NService commonI18NService;

    @Mock
    private CustomerFacade customerFacade;

    @Mock
    private CustomerAccountService customerAccountService;

    @Mock
    private CartFactory cartFactory;

    @Mock
    private UserService userService;

    @Mock
    private Converter<CartModel, CartData> cartConverter;

    @Mock
    private AdyenCheckoutFacade adyenCheckoutFacade;

    @Mock
    private SessionService sessionService;

    @Mock
    private AdyenCheckoutApiFacade adyenCheckoutApiFacade;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private DefaultAdyenExpressCheckoutFacade defaultAdyenExpressCheckoutFacade;


    @Test
    public void expressCheckoutPDP() throws Exception {
        //given
        String productCode = "productCode";
        PaymentRequest paymentRequest = new PaymentRequest();
        String paymentMethod = "paymentMethod";
        PaymentInfoModel paymentInfoModel = new PaymentInfoModel();
        CustomerModel customerModel = new CustomerModel();
        CartModel sessionCartModel = new CartModel();
        DeliveryModeModel deliveryModeModel = new DeliveryModeModel();
        AddressModel addressModel = new AddressModel();
        ProductModel productModel = new ProductModel();
        UnitModel unitModel = new UnitModel();
        CartModel cartWithEntry = new CartModel();
        OrderEntryModel entry = new OrderEntryModel();
        cartWithEntry.setEntries(Arrays.asList(entry));

        productModel.setUnit(unitModel);

        AddressData addressData = setUpAddressData();

        when(modelService.create(PaymentInfoModel.class)).thenReturn(paymentInfoModel);
        when(userService.getCurrentUser()).thenReturn(customerModel);
        when(userService.isAnonymousUser(any())).thenReturn(false);
        when(cartFactory.createCart()).thenReturn(cartWithEntry);
        when(deliveryModeService.getDeliveryModeForCode(any())).thenReturn(deliveryModeModel);
        when(modelService.create(AddressModel.class)).thenReturn(addressModel);
        when(productService.getProductForCode(productCode)).thenReturn(productModel);

        when(cartService.hasSessionCart()).thenReturn(true);
        when(cartService.getSessionCart()).thenReturn(sessionCartModel);

        ArgumentCaptor<CartModel> cartModelArgumentCaptor = ArgumentCaptor.forClass(CartModel.class);

        //when
        defaultAdyenExpressCheckoutFacade.expressCheckoutPDP(productCode, paymentRequest, paymentMethod, addressData, request);

        //then
        verify(cartService).addNewEntry(cartWithEntry, productModel, 1L, unitModel);
        verify(commerceCartService, times(1)).calculateCart((CommerceCartParameter) any());
        verify(cartService, times(2)).setSessionCart(cartModelArgumentCaptor.capture());

        List<CartModel> cartsSetInSession = cartModelArgumentCaptor.getAllValues();
        assertEquals(cartWithEntry, cartsSetInSession.get(0));
        assertEquals(sessionCartModel, cartsSetInSession.get(1));

        verify(adyenCheckoutFacade, times(1)).componentPayment(any(), any(), any());
    }


    @Test
    public void expressCheckoutPDPOCC() throws Exception {
        //given
        String productCode = "productCode";
        PaymentRequest paymentRequest = new PaymentRequest();
        String paymentMethod = "paymentMethod";
        PaymentInfoModel paymentInfoModel = new PaymentInfoModel();
        CustomerModel customerModel = new CustomerModel();
        CartModel sessionCartModel = new CartModel();
        DeliveryModeModel deliveryModeModel = new DeliveryModeModel();
        AddressModel addressModel = new AddressModel();
        ProductModel productModel = new ProductModel();
        UnitModel unitModel = new UnitModel();
        CartModel cartWithEntry = new CartModel();
        OrderEntryModel entry = new OrderEntryModel();
        cartWithEntry.setEntries(Arrays.asList(entry));

        productModel.setUnit(unitModel);

        AddressData addressData = setUpAddressData();

        when(modelService.create(PaymentInfoModel.class)).thenReturn(paymentInfoModel);
        when(userService.getCurrentUser()).thenReturn(customerModel);
        when(userService.isAnonymousUser(any())).thenReturn(false);
        when(cartFactory.createCart()).thenReturn(cartWithEntry);
        when(deliveryModeService.getDeliveryModeForCode(any())).thenReturn(deliveryModeModel);
        when(modelService.create(AddressModel.class)).thenReturn(addressModel);
        when(productService.getProductForCode(productCode)).thenReturn(productModel);

        when(cartService.hasSessionCart()).thenReturn(true);
        when(cartService.getSessionCart()).thenReturn(sessionCartModel);

        ArgumentCaptor<CartModel> cartModelArgumentCaptor = ArgumentCaptor.forClass(CartModel.class);

        //when
        defaultAdyenExpressCheckoutFacade.expressCheckoutPDPOCC(productCode, paymentRequest, paymentMethod, addressData, request);

        //then
        verify(cartService).addNewEntry(cartWithEntry, productModel, 1L, unitModel);
        verify(commerceCartService, times(1)).calculateCart((CommerceCartParameter) any());
        verify(cartService, times(2)).setSessionCart(cartModelArgumentCaptor.capture());

        List<CartModel> cartsSetInSession = cartModelArgumentCaptor.getAllValues();
        assertEquals(cartWithEntry, cartsSetInSession.get(0));
        assertEquals(sessionCartModel, cartsSetInSession.get(1));

        verify(adyenCheckoutApiFacade, times(1)).placeOrderWithPayment(any(), any(), any());
    }

    @Test
    public void expressCheckoutCart() throws Exception {
        //given
        String productCode = "productCode";
        PaymentRequest paymentRequest = new PaymentRequest();
        String paymentMethod = "paymentMethod";
        PaymentInfoModel paymentInfoModel = new PaymentInfoModel();
        CustomerModel customerModel = new CustomerModel();
        DeliveryModeModel deliveryModeModel = new DeliveryModeModel();
        AddressModel addressModel = new AddressModel();
        ProductModel productModel = new ProductModel();
        UnitModel unitModel = new UnitModel();
        CartModel cartWithEntry = new CartModel();
        OrderEntryModel entry = new OrderEntryModel();
        cartWithEntry.setEntries(Arrays.asList(entry));

        productModel.setUnit(unitModel);

        AddressData addressData = setUpAddressData();

        when(modelService.create(PaymentInfoModel.class)).thenReturn(paymentInfoModel);
        when(userService.getCurrentUser()).thenReturn(customerModel);
        when(userService.isAnonymousUser(any())).thenReturn(false);
        when(cartService.getSessionCart()).thenReturn(cartWithEntry);
        when(deliveryModeService.getDeliveryModeForCode(any())).thenReturn(deliveryModeModel);
        when(modelService.create(AddressModel.class)).thenReturn(addressModel);

        //when
        defaultAdyenExpressCheckoutFacade.expressCheckoutCart(paymentRequest, paymentMethod, addressData, request);

        //then
        verify(commerceCartService, times(1)).recalculateCart((CommerceCartParameter) any());

        verify(adyenCheckoutFacade, times(1)).componentPayment(any(), any(), any());
    }

    @Test
    public void expressCheckoutCartOCC() throws Exception {
        //given
        String productCode = "productCode";
        PaymentRequest paymentRequest = new PaymentRequest();
        String paymentMethod = "paymentMethod";
        PaymentInfoModel paymentInfoModel = new PaymentInfoModel();
        CustomerModel customerModel = new CustomerModel();
        DeliveryModeModel deliveryModeModel = new DeliveryModeModel();
        AddressModel addressModel = new AddressModel();
        ProductModel productModel = new ProductModel();
        UnitModel unitModel = new UnitModel();
        CartModel cartWithEntry = new CartModel();
        OrderEntryModel entry = new OrderEntryModel();
        cartWithEntry.setEntries(Arrays.asList(entry));

        productModel.setUnit(unitModel);

        AddressData addressData = setUpAddressData();

        when(modelService.create(PaymentInfoModel.class)).thenReturn(paymentInfoModel);
        when(userService.getCurrentUser()).thenReturn(customerModel);
        when(userService.isAnonymousUser(any())).thenReturn(false);
        when(cartService.getSessionCart()).thenReturn(cartWithEntry);
        when(deliveryModeService.getDeliveryModeForCode(any())).thenReturn(deliveryModeModel);
        when(modelService.create(AddressModel.class)).thenReturn(addressModel);

        //when
        defaultAdyenExpressCheckoutFacade.expressCheckoutCartOCC(paymentRequest, paymentMethod, addressData, request);

        //then
        verify(commerceCartService, times(1)).recalculateCart((CommerceCartParameter) any());

        verify(adyenCheckoutApiFacade, times(1)).placeOrderWithPayment(any(), any(), any());
    }


    @Test(expected = IllegalArgumentException.class)
    public void expressCheckoutPDPNullPaymentMethod() throws Exception {
        //given
        String productCode = "productCode";
        PaymentRequest paymentRequest = new PaymentRequest();
        String paymentMethod = null;

        AddressData addressData = setUpAddressData();


        //when
        defaultAdyenExpressCheckoutFacade.expressCheckoutPDP(productCode, paymentRequest, paymentMethod, addressData, request);
    }

    @Test(expected = IllegalArgumentException.class)
    public void expressCheckoutPDPOCCNullPaymentMethod() throws Exception {
        //given
        String productCode = "productCode";
        PaymentRequest paymentRequest = new PaymentRequest();
        String paymentMethod = null;

        AddressData addressData = setUpAddressData();


        //when
        defaultAdyenExpressCheckoutFacade.expressCheckoutPDPOCC(productCode, paymentRequest, paymentMethod, addressData, request);
    }

    @Test(expected = IllegalArgumentException.class)
    public void expressCheckoutCartNullPaymentMethod() throws Exception {
        //given
        PaymentRequest paymentRequest = new PaymentRequest();
        String paymentMethod = null;

        AddressData addressData = setUpAddressData();


        //when
        defaultAdyenExpressCheckoutFacade.expressCheckoutCart(paymentRequest, paymentMethod, addressData, request);
    }

    @Test(expected = IllegalArgumentException.class)
    public void expressCheckoutCartOCCNullPaymentMethod() throws Exception {
        //given
        PaymentRequest paymentRequest = new PaymentRequest();
        String paymentMethod = null;

        AddressData addressData = setUpAddressData();

        //when
        defaultAdyenExpressCheckoutFacade.expressCheckoutCartOCC(paymentRequest, paymentMethod, addressData, request);
    }

    @Test
    public void removeDeliveryModeFromSessionCart() throws CalculationException {
        //given
        CartModel cartModel = new CartModel();
        DeliveryModeModel deliveryModeModel = new DeliveryModeModel();
        cartModel.setDeliveryMode(deliveryModeModel);

        when(cartService.getSessionCart()).thenReturn(cartModel);
        when(cartService.hasSessionCart()).thenReturn(true);

        ArgumentCaptor<CartModel> cartCaptor = ArgumentCaptor.forClass(CartModel.class);

        //when
        defaultAdyenExpressCheckoutFacade.removeDeliveryModeFromSessionCart();

        //then
        verify(modelService).save(cartCaptor.capture());
        CartModel capturedCart = cartCaptor.getValue();
        assertNull(capturedCart.getDeliveryMode());

        verify(commerceCartService).recalculateCart((CommerceCartParameter) any());
    }

    @Test
    public void prepareCart() {
        //given
        CartModel cartModel = new CartModel();
        DeliveryModeModel deliveryModeModel = new DeliveryModeModel();
        AddressModel addressModel = new AddressModel();
        PaymentInfoModel paymentInfo = new PaymentInfoModel();

        ArgumentCaptor<CartModel> cartCaptor = ArgumentCaptor.forClass(CartModel.class);

        //when
        defaultAdyenExpressCheckoutFacade.prepareCart(cartModel, deliveryModeModel, addressModel, paymentInfo);

        //then
        verify(modelService).save(cartCaptor.capture());
        CartModel capturedCart = cartCaptor.getValue();

        assertNotNull(capturedCart.getDeliveryMode());
        assertNotNull(capturedCart.getDeliveryAddress());
        assertNotNull(capturedCart.getPaymentAddress());
        assertNotNull(capturedCart.getPaymentInfo());

    }

    @Test
    public void prepareAddressModel() {
        //given
        AddressData addressData = new AddressData();
        AddressModel addressModel = new AddressModel();
        CustomerModel customerModel = new CustomerModel();

        when(modelService.create(AddressModel.class)).thenReturn(addressModel);

        ArgumentCaptor<AddressModel> addressCaptor = ArgumentCaptor.forClass(AddressModel.class);

        //when
        defaultAdyenExpressCheckoutFacade.prepareAddressModel(addressData, customerModel);

        //then
        verify(modelService).save(addressCaptor.capture());
        AddressModel capturedAddress = addressCaptor.getValue();

        assertEquals(customerModel, capturedAddress.getOwner());
        assertTrue(capturedAddress.getBillingAddress());
        assertTrue(capturedAddress.getShippingAddress());
    }

    @Test(expected = IllegalArgumentException.class)
    public void prepareAddressModelException() {
        //given
        AddressData addressData = new AddressData();
        CustomerModel customerModel = new CustomerModel();

        when(modelService.create(AddressModel.class)).thenReturn(null);


        //when
        defaultAdyenExpressCheckoutFacade.prepareAddressModel(addressData, customerModel);
    }

    @Test
    public void addProductToCart() {
        //given
        CartModel cartModel = new CartModel();
        ProductModel productModel = new ProductModel();
        UnitModel unitModel = new UnitModel();
        productModel.setUnit(unitModel);

        when(productService.getProductForCode(any())).thenReturn(productModel);

        //when
        defaultAdyenExpressCheckoutFacade.addProductToCart("code", cartModel);

        //then
        verify(cartService).addNewEntry(cartModel, productModel, 1L, unitModel);
    }

    @Test
    public void addProductToCartProductNotFound() {
        //given
        CartModel cartModel = new CartModel();


        when(productService.getProductForCode(any())).thenReturn(null);

        //when
        defaultAdyenExpressCheckoutFacade.addProductToCart("code", cartModel);

        //then
        verifyNoInteractions(cartService);
    }

    @Test
    public void updateRegionData() {
        //given
        AddressData addressData = setUpAddressData();

        //when
        defaultAdyenExpressCheckoutFacade.updateRegionData(addressData);

        //then
        assertNotNull(addressData.getRegion());
        assertEquals("US-CA", addressData.getRegion().getIsocode());
    }

    private AddressData setUpAddressData() {
        AddressData addressData = new AddressData();
        addressData.setEmail("test@address.com");

        String isocodeShort = "CA";

        RegionData addressRegion = new RegionData();
        addressRegion.setIsocodeShort(isocodeShort);
        CountryData countryData = new CountryData();
        countryData.setIsocode("US");
        addressData.setRegion(addressRegion);
        addressData.setCountry(countryData);

        RegionData region1 = new RegionData();
        RegionData region2 = new RegionData();

        region2.setIsocodeShort(isocodeShort);
        region2.setIsocode("US-CA");
        region1.setIsocodeShort("test");
        region1.setIsocode("test");

        when(i18NFacade.getRegionsForCountryIso(any())).thenReturn(Arrays.asList(region1, region2));
        return addressData;
    }

    @Test
    public void updateRegionDataNotInList() {
        //given
        AddressData addressData = new AddressData();
        String isocodeShort = "CA";

        RegionData addressRegion = new RegionData();
        addressRegion.setIsocodeShort(isocodeShort);
        CountryData countryData = new CountryData();
        countryData.setIsocode("US");
        addressData.setRegion(addressRegion);
        addressData.setCountry(countryData);

        RegionData region1 = new RegionData();
        RegionData region2 = new RegionData();

        region2.setIsocodeShort("NY");
        region2.setIsocode("US-CA");
        region1.setIsocodeShort("test");
        region1.setIsocode("test");

        when(i18NFacade.getRegionsForCountryIso(any())).thenReturn(Arrays.asList(region1, region2));

        //when
        defaultAdyenExpressCheckoutFacade.updateRegionData(addressData);

        //then
        assertNull(addressData.getRegion());
    }

    @Test
    public void updateRegionDataNull() {
        //given
        AddressData addressData = new AddressData();

        CountryData countryData = new CountryData();
        countryData.setIsocode("US");
        addressData.setRegion(null);
        addressData.setCountry(countryData);

        //when
        defaultAdyenExpressCheckoutFacade.updateRegionData(addressData);

        //then
        assertNull(addressData.getRegion());
    }

    @Test
    public void updateRegionDataEmpty() {
        //given
        AddressData addressData = new AddressData();

        CountryData countryData = new CountryData();
        countryData.setIsocode("US");
        RegionData regionData = new RegionData();
        regionData.setIsocodeShort("");
        addressData.setRegion(regionData);
        addressData.setCountry(countryData);

        //when
        defaultAdyenExpressCheckoutFacade.updateRegionData(addressData);

        //then
        assertNull(addressData.getRegion());
    }

    @Test
    public void getExpressDeliveryModePrice() {
        //given
        ZoneDeliveryModeModel zoneDeliveryMode = new ZoneDeliveryModeModel();
        ZoneDeliveryModeValueModel zoneDeliveryModeValue1 = new ZoneDeliveryModeValueModel();
        ZoneDeliveryModeValueModel zoneDeliveryModeValue2 = new ZoneDeliveryModeValueModel();
        CurrencyModel currencyModel1 = new CurrencyModel();
        CurrencyModel currencyModel2 = new CurrencyModel();
        currencyModel1.setIsocode("USD");
        currencyModel2.setIsocode("EUR");
        zoneDeliveryModeValue1.setCurrency(currencyModel1);
        zoneDeliveryModeValue2.setCurrency(currencyModel2);
        zoneDeliveryMode.setValues(new HashSet<>(Arrays.asList(zoneDeliveryModeValue1, zoneDeliveryModeValue2)));

        when(deliveryModeService.getDeliveryModeForCode(any())).thenReturn(zoneDeliveryMode);
        when(commonI18NService.getCurrentCurrency()).thenReturn(currencyModel2);

        //when
        Optional<ZoneDeliveryModeValueModel> expressDeliveryModePrice = defaultAdyenExpressCheckoutFacade.getExpressDeliveryModePrice();

        //then
        assertTrue(expressDeliveryModePrice.isPresent());
        assertEquals(zoneDeliveryModeValue2, expressDeliveryModePrice.get());
    }

    @Test
    public void createGuestCustomer() throws DuplicateUidException {
        //given
        String email = "test@address.com";
        String expectedUid = "GUID|test@address.com";

        CustomerModel customerModel = new CustomerModel();

        when(modelService.create(CustomerModel.class)).thenReturn(customerModel);

        when(customerFacade.generateGUID()).thenReturn("GUID");
        when(commonI18NService.getCurrentCurrency()).thenReturn(new CurrencyModel());
        when(commonI18NService.getCurrentLanguage()).thenReturn(new LanguageModel());

        ArgumentCaptor<CustomerModel> customerModelArgumentCaptor = ArgumentCaptor.forClass(CustomerModel.class);
        ArgumentCaptor<String> guidArgumentCaptor = ArgumentCaptor.forClass(String.class);

        //when
        defaultAdyenExpressCheckoutFacade.createGuestCustomer(email);

        //then
        verify(customerAccountService).registerGuestForAnonymousCheckout(customerModelArgumentCaptor.capture(), guidArgumentCaptor.capture());

        CustomerModel customerValue = customerModelArgumentCaptor.getValue();
        String guidValue = guidArgumentCaptor.getValue();

        assertEquals("GUID", guidValue);

        assertEquals(expectedUid, customerValue.getUid());
        assertEquals(USER_NAME, customerValue.getName());
        assertEquals(CustomerType.GUEST, customerValue.getType());
        assertNotNull(customerValue.getSessionCurrency());
        assertNotNull(customerValue.getSessionLanguage());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createGuestCustomerBadEmail() throws DuplicateUidException {
        //given
        String email = "test@address";
        String expectedUid = "GUID|test@address";

        CustomerModel customerModel = new CustomerModel();


        //when
        defaultAdyenExpressCheckoutFacade.createGuestCustomer(email);
    }


    @Test(expected = IllegalArgumentException.class)
    public void createGuestCustomerNullEmail() throws DuplicateUidException {
        //given
        String email = null;

        //when
        defaultAdyenExpressCheckoutFacade.createGuestCustomer(email);
    }

    @Test
    public void createCartForExpressCheckout() {
        //given
        CustomerModel customerModel = new CustomerModel();
        CartModel cartModel = new CartModel();

        when(cartFactory.createCart()).thenReturn(cartModel);

        //when
        CartModel cartForExpressCheckout = defaultAdyenExpressCheckoutFacade.createCartForExpressCheckout(customerModel);

        //then
        assertEquals(customerModel, cartForExpressCheckout.getUser());
        verify(modelService, times(1)).save(cartModel);
    }

    @Test
    public void updatePaymentInfoWithCartAndUser() {
        //given
        PaymentInfoModel paymentInfoModel = new PaymentInfoModel();
        CustomerModel customerModel = new CustomerModel();
        AddressModel addressModel = new AddressModel();
        CartModel cartModel = new CartModel();

        cartModel.setCode("cartCode");

        //when
        PaymentInfoModel result = defaultAdyenExpressCheckoutFacade.updatePaymentInfoWithCartAndUser(paymentInfoModel, customerModel, addressModel, cartModel);

        //then

        assertEquals(customerModel, result.getUser());
        assertEquals(addressModel, result.getBillingAddress());
        assertTrue(result.getCode().contains("cartCode"));

        verify(modelService, times(1)).save(paymentInfoModel);
    }

    @Test(expected = IllegalArgumentException.class)
    public void updatePaymentInfoWithCartAndUserPINull() {
        //given
        PaymentInfoModel paymentInfoModel = null;
        CustomerModel customerModel = new CustomerModel();
        AddressModel addressModel = new AddressModel();
        CartModel cartModel = new CartModel();

        cartModel.setCode("cartCode");

        //when
        PaymentInfoModel result = defaultAdyenExpressCheckoutFacade.updatePaymentInfoWithCartAndUser(paymentInfoModel, customerModel, addressModel, cartModel);
    }
}