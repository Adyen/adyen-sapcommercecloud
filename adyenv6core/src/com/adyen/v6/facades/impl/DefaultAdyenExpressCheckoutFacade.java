package com.adyen.v6.facades.impl;

import com.adyen.commerce.facades.AdyenCheckoutApiFacade;
import com.adyen.model.checkout.*;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.adyen.v6.facades.AdyenExpressCheckoutFacade;
import de.hybris.platform.commercefacades.customer.CustomerFacade;
import de.hybris.platform.commercefacades.i18n.I18NFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.RegionData;
import de.hybris.platform.commerceservices.customer.CustomerAccountService;
import de.hybris.platform.commerceservices.customer.DuplicateUidException;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.delivery.DeliveryModeModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.deliveryzone.model.ZoneDeliveryModeModel;
import de.hybris.platform.deliveryzone.model.ZoneDeliveryModeValueModel;
import de.hybris.platform.order.CartFactory;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.DeliveryModeService;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.product.ProductService;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.log4j.Logger;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;
import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNullStandardMessage;

public class DefaultAdyenExpressCheckoutFacade implements AdyenExpressCheckoutFacade {
    private static final Logger LOG = Logger.getLogger(DefaultAdyenExpressCheckoutFacade.class);
    protected static final String USER_NAME = "ExpressCheckoutGuest";
    private static final String DELIVERY_MODE_CODE = "adyen-express-checkout";
    protected static final String ANONYMOUS_CHECKOUT_GUID = "anonymous_checkout_guid";

    private CartFactory cartFactory;
    private CartService cartService;
    private ProductService productService;
    private ModelService modelService;
    private CustomerFacade customerFacade;
    private CommonI18NService commonI18NService;
    private I18NFacade i18NFacade;
    private CustomerAccountService customerAccountService;
    private CommerceCartService commerceCartService;
    private DeliveryModeService deliveryModeService;
    private AdyenCheckoutFacade adyenCheckoutFacade;
    private SessionService sessionService;
    private UserService userService;
    private AdyenCheckoutApiFacade adyenCheckoutApiFacade;
    private Converter<AddressData, AddressModel> addressReverseConverter;
    private Converter<CartModel, CartData> cartConverter;

    public PaymentResponse expressCheckoutPDP(String productCode, PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                              HttpServletRequest request) throws Exception {
        Assert.notNull(paymentMethod, "Payment method must not be null");

        PaymentInfoModel paymentInfoModel = modelService.create(PaymentInfoModel.class);
        paymentInfoModel.setAdyenPaymentMethod(paymentMethod);

        updateRegionData(addressData);

        return expressPDPCheckout(paymentRequest,addressData,paymentInfoModel,productCode,request);
    }

    public OrderData expressCheckoutPDPOCC(String productCode, PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                            HttpServletRequest request) throws Exception {
        Assert.notNull(paymentMethod, "Payment method must not be null");

        PaymentInfoModel paymentInfoModel = modelService.create(PaymentInfoModel.class);
        paymentInfoModel.setAdyenPaymentMethod(paymentMethod);

        updateRegionData(addressData);

        return expressPDPCheckoutOCC(paymentRequest,addressData,paymentInfoModel,productCode,request);
    }


    public PaymentResponse expressCheckoutCart(PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                               HttpServletRequest request) throws Exception {
        Assert.notNull(paymentMethod, "Payment method must not be null");

        PaymentInfoModel paymentInfoModel = modelService.create(PaymentInfoModel.class);
        paymentInfoModel.setAdyenPaymentMethod(paymentMethod);

        updateRegionData(addressData);

        return expressCartCheckout(paymentRequest,addressData,paymentInfoModel,request);
    }

    public OrderData expressCheckoutCartOCC(PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                             HttpServletRequest request) throws Exception {
        Assert.notNull(paymentMethod, "Payment method must not be null");

        PaymentInfoModel paymentInfoModel = modelService.create(PaymentInfoModel.class);
        paymentInfoModel.setAdyenPaymentMethod(paymentMethod);

        updateRegionData(addressData);

        return expressCartCheckoutOCC(paymentRequest,addressData,paymentInfoModel,request);
    }

    protected PaymentResponse expressPDPCheckout(PaymentRequest paymentRequest, AddressData addressData, PaymentInfoModel paymentInfoModel, String productCode,
                                              HttpServletRequest request) throws Exception {
        validateParameterNotNull(addressData, "Empty address");
        if (StringUtils.isEmpty(addressData.getEmail())) {
            throw new IllegalArgumentException("Empty email address");
        }
        CustomerModel user = (CustomerModel) userService.getCurrentUser();
        if (userService.isAnonymousUser(user)) {
            user = createGuestCustomer(addressData.getEmail());
        }

        CartModel cart = createCartForExpressCheckout(user);

        DeliveryModeModel deliveryMode = deliveryModeService.getDeliveryModeForCode(DELIVERY_MODE_CODE);
        validateParameterNotNull(deliveryMode, "Delivery mode for Adyen express checkout not configured");

        AddressModel addressModel = prepareAddressModel(addressData, user);
        updatePaymentInfoWithCartAndUser(paymentInfoModel, user,addressModel,cart);

        prepareCart(cart, deliveryMode, addressModel, paymentInfoModel);

        addProductToCart(productCode, cart);

        if (cartHasEntries(cart)) {
            CommerceCartParameter commerceCartParameter = new CommerceCartParameter();
            commerceCartParameter.setCart(cart);
            commerceCartService.calculateCart(commerceCartParameter);

            CartModel sessionCart = null;
            if (cartService.hasSessionCart()) {
                sessionCart = cartService.getSessionCart();
            }
            cartService.setSessionCart(cart);

            CartData cartData = cartConverter.convert(cart);

            PaymentResponse paymentsResponse = adyenCheckoutFacade.componentPayment(request, cartData, paymentRequest);

            if (userService.isAnonymousUser(user)) {
                sessionService.setAttribute(ANONYMOUS_CHECKOUT_GUID,
                        org.apache.commons.lang.StringUtils.substringBefore(cart.getUser().getUid(), "|"));
            }

            if (sessionCart != null) {
                cartService.setSessionCart(sessionCart);
            }

            return paymentsResponse;
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }

    protected OrderData expressPDPCheckoutOCC(PaymentRequest paymentRequest, AddressData addressData, PaymentInfoModel paymentInfoModel, String productCode,
                                              HttpServletRequest request) throws Exception {
        validateParameterNotNull(addressData, "Empty address");
        if (StringUtils.isEmpty(addressData.getEmail())) {
            throw new IllegalArgumentException("Empty email address");
        }
        CustomerModel user = (CustomerModel) userService.getCurrentUser();
        if (userService.isAnonymousUser(user)) {
            user = createGuestCustomer(addressData.getEmail());
        }

        CartModel cart = createCartForExpressCheckout(user);

        DeliveryModeModel deliveryMode = deliveryModeService.getDeliveryModeForCode(DELIVERY_MODE_CODE);
        validateParameterNotNull(deliveryMode, "Delivery mode for Adyen express checkout not configured");

        AddressModel addressModel = prepareAddressModel(addressData, user);
        updatePaymentInfoWithCartAndUser(paymentInfoModel, user,addressModel,cart);

        prepareCart(cart, deliveryMode, addressModel, paymentInfoModel);

        addProductToCart(productCode, cart);

        if (cartHasEntries(cart)) {
            CommerceCartParameter commerceCartParameter = new CommerceCartParameter();
            commerceCartParameter.setCart(cart);
            commerceCartService.calculateCart(commerceCartParameter);

            CartModel sessionCart = null;
            if (cartService.hasSessionCart()) {
                sessionCart = cartService.getSessionCart();
            }
            cartService.setSessionCart(cart);

            CartData cartData = cartConverter.convert(cart);

            OrderData orderData = adyenCheckoutApiFacade.placeOrderWithPayment(request, cartData, paymentRequest);


            if (sessionCart != null) {
                cartService.setSessionCart(sessionCart);
            }

            return orderData;
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }


    //TODO: Remove after applepay
    public PaymentResponse appleExpressPDPCheckout(AddressData addressData, String productCode, String merchantId, String merchantName,
                                                   String applePayToken, HttpServletRequest request) throws Exception {
        validateParameterNotNull(addressData, "Empty address");
        if (StringUtils.isEmpty(addressData.getEmail())) {
            throw new IllegalArgumentException("Empty email address");
        }
        CustomerModel user = (CustomerModel) userService.getCurrentUser();
        if (userService.isAnonymousUser(user)) {
            user = createGuestCustomer(addressData.getEmail());
        }

        CartModel cart = createCartForExpressCheckout(user);

        DeliveryModeModel deliveryMode = deliveryModeService.getDeliveryModeForCode(DELIVERY_MODE_CODE);
        validateParameterNotNull(deliveryMode, "Delivery mode for Adyen express checkout not configured");

        AddressModel addressModel = prepareAddressModel(addressData, user);
        PaymentInfoModel paymentInfo = createPaymentInfoForCart(user, addressModel, cart,
                Adyenv6coreConstants.PAYMENT_METHOD_APPLEPAY, merchantId, merchantName);

        prepareCart(cart, deliveryMode, addressModel, paymentInfo);

        addProductToCart(productCode, cart);

        if (cartHasEntries(cart)) {
            CommerceCartParameter commerceCartParameter = new CommerceCartParameter();
            commerceCartParameter.setCart(cart);
            commerceCartService.calculateCart(commerceCartParameter);

            CartModel sessionCart = null;
            if (cartService.hasSessionCart()) {
                sessionCart = cartService.getSessionCart();
            }
            cartService.setSessionCart(cart);

            CartData cartData = cartConverter.convert(cart);

            ApplePayDetails applePayDetails = new ApplePayDetails();
            applePayDetails.setApplePayToken(applePayToken);
            CheckoutPaymentMethod checkoutPaymentMethod = new CheckoutPaymentMethod(applePayDetails);
            PaymentRequest paymentRequest = new PaymentRequest();
            paymentRequest.setPaymentMethod(checkoutPaymentMethod);
            PaymentResponse paymentsResponse = adyenCheckoutFacade.componentPayment(request, cartData, paymentRequest);

            sessionService.setAttribute(ANONYMOUS_CHECKOUT_GUID,
                    org.apache.commons.lang.StringUtils.substringBefore(cart.getUser().getUid(), "|"));

            if (sessionCart != null) {
                cartService.setSessionCart(sessionCart);
            }

            return paymentsResponse;
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }

    protected PaymentResponse expressCartCheckout(PaymentRequest paymentRequest, AddressData addressData, PaymentInfoModel paymentInfoModel,
                                               HttpServletRequest request) throws Exception {
        CustomerModel user = (CustomerModel) userService.getCurrentUser();
        if (userService.isAnonymousUser(user)) {
            user = createGuestCustomer(addressData.getEmail());
            cartService.changeCurrentCartUser(user);
        }

        CartModel cart = cartService.getSessionCart();

        DeliveryModeModel deliveryMode = deliveryModeService.getDeliveryModeForCode(DELIVERY_MODE_CODE);
        validateParameterNotNull(deliveryMode, "Delivery mode for Adyen express checkout not configured");

        AddressModel addressModel = prepareAddressModel(addressData, user);
        updatePaymentInfoWithCartAndUser(paymentInfoModel, user,addressModel,cart);

        prepareCart(cart, deliveryMode, addressModel, paymentInfoModel);

        CommerceCartParameter commerceCartParameter = new CommerceCartParameter();
        commerceCartParameter.setCart(cart);
        commerceCartService.recalculateCart(commerceCartParameter);

        if (cartHasEntries(cart)) {
            CartData cartData = cartConverter.convert(cart);

            if (userService.isAnonymousUser(user)) {
                sessionService.setAttribute(ANONYMOUS_CHECKOUT_GUID,
                        org.apache.commons.lang.StringUtils.substringBefore(cart.getUser().getUid(), "|"));
            }

            return adyenCheckoutFacade.componentPayment(request, cartData, paymentRequest);
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }

    protected OrderData expressCartCheckoutOCC(PaymentRequest paymentRequest, AddressData addressData, PaymentInfoModel paymentInfoModel,
                                                  HttpServletRequest request) throws Exception {
        CustomerModel user = (CustomerModel) userService.getCurrentUser();
        if (userService.isAnonymousUser(user)) {
            user = createGuestCustomer(addressData.getEmail());
            cartService.changeCurrentCartUser(user);
        }

        CartModel cart = cartService.getSessionCart();

        DeliveryModeModel deliveryMode = deliveryModeService.getDeliveryModeForCode(DELIVERY_MODE_CODE);
        validateParameterNotNull(deliveryMode, "Delivery mode for Adyen express checkout not configured");

        AddressModel addressModel = prepareAddressModel(addressData, user);
        updatePaymentInfoWithCartAndUser(paymentInfoModel, user,addressModel,cart);

        prepareCart(cart, deliveryMode, addressModel, paymentInfoModel);

        CommerceCartParameter commerceCartParameter = new CommerceCartParameter();
        commerceCartParameter.setCart(cart);
        commerceCartService.recalculateCart(commerceCartParameter);

        if (cartHasEntries(cart)) {
            CartData cartData = cartConverter.convert(cart);

            return adyenCheckoutApiFacade.placeOrderWithPayment(request, cartData, paymentRequest);
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }

    //TODO: Remove after applepay
    public PaymentResponse appleEexpressCartCheckout(AddressData addressData, String merchantId, String merchantName,
                                                     String applePayToken, HttpServletRequest request) throws Exception {
        CustomerModel user = (CustomerModel) userService.getCurrentUser();
        if (userService.isAnonymousUser(user)) {
            user = createGuestCustomer(addressData.getEmail());
            cartService.changeCurrentCartUser(user);
        }

        CartModel cart = cartService.getSessionCart();

        DeliveryModeModel deliveryMode = deliveryModeService.getDeliveryModeForCode(DELIVERY_MODE_CODE);
        validateParameterNotNull(deliveryMode, "Delivery mode for Adyen express checkout not configured");

        AddressModel addressModel = prepareAddressModel(addressData, user);

        PaymentInfoModel paymentInfo = createPaymentInfoForCart(user, addressModel, cart,
                Adyenv6coreConstants.PAYMENT_METHOD_APPLEPAY, merchantId, merchantName);

        prepareCart(cart, deliveryMode, addressModel, paymentInfo);

        CommerceCartParameter commerceCartParameter = new CommerceCartParameter();
        commerceCartParameter.setCart(cart);
        commerceCartService.recalculateCart(commerceCartParameter);

        if (cartHasEntries(cart)) {
            CartData cartData = cartConverter.convert(cart);

            ApplePayDetails applePayDetails = new ApplePayDetails();
            applePayDetails.setApplePayToken(applePayToken);

            sessionService.setAttribute(ANONYMOUS_CHECKOUT_GUID,
                    org.apache.commons.lang.StringUtils.substringBefore(cart.getUser().getUid(), "|"));
            PaymentRequest paymentRequest = new PaymentRequest();
            paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(applePayDetails));
            return adyenCheckoutFacade.componentPayment(request, cartData, paymentRequest);
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }

    public void removeDeliveryModeFromSessionCart() throws CalculationException {
        if (cartService.hasSessionCart()) {
            CartModel sessionCart = cartService.getSessionCart();
            sessionCart.setDeliveryMode(null);
            modelService.save(sessionCart);

            CommerceCartParameter commerceCartParameter = new CommerceCartParameter();
            commerceCartParameter.setCart(sessionCart);
            commerceCartService.recalculateCart(commerceCartParameter);
        }
    }

    protected void prepareCart(CartModel cart, DeliveryModeModel deliveryMode, AddressModel addressModel, PaymentInfoModel paymentInfo) {
        cart.setDeliveryMode(deliveryMode);
        cart.setDeliveryAddress(addressModel);
        cart.setPaymentAddress(addressModel);
        cart.setPaymentInfo(paymentInfo);
        modelService.save(cart);
    }

    protected AddressModel prepareAddressModel(AddressData addressData, CustomerModel user) {
        AddressModel addressModel = modelService.create(AddressModel.class);
        addressReverseConverter.convert(addressData, addressModel);
        validateParameterNotNull(addressModel, "Empty address");
        addressModel.setOwner(user);
        addressModel.setBillingAddress(true);
        addressModel.setShippingAddress(true);

        modelService.save(addressModel);
        return addressModel;
    }

    protected void addProductToCart(String productCode, CartModel cart) {
        ProductModel product = productService.getProductForCode(productCode);

        if (product != null) {
            cartService.addNewEntry(cart, product, 1L, product.getUnit());
        }
        modelService.save(cart);
    }

    protected void updateRegionData(AddressData addressData) {
        if (addressData.getRegion() != null) {
            if (StringUtils.isNotEmpty(addressData.getRegion().getIsocodeShort())) {
                List<RegionData> regionsForCountry = i18NFacade.getRegionsForCountryIso(addressData.getCountry().getIsocode());
                Optional<RegionData> regionData = regionsForCountry.stream()
                        .filter(region -> region.getIsocodeShort().equals(addressData.getRegion().getIsocodeShort()))
                        .findFirst();

                if (regionData.isPresent()) {
                    addressData.setRegion(regionData.get());
                } else {
                    addressData.setRegion(null);
                }

            } else {
                addressData.setRegion(null);
            }
        }
    }

    public Optional<ZoneDeliveryModeValueModel> getExpressDeliveryModePrice() {
        ZoneDeliveryModeModel deliveryMode = (ZoneDeliveryModeModel) deliveryModeService.getDeliveryModeForCode(DELIVERY_MODE_CODE);
        CurrencyModel currentCurrency = commonI18NService.getCurrentCurrency();

        return deliveryMode.getValues().stream().filter(valueModel -> valueModel.getCurrency().equals(currentCurrency)).findFirst();
    }

    protected CustomerModel createGuestCustomer(String emailAddress) throws DuplicateUidException {
        Assert.isTrue(EmailValidator.getInstance().isValid(emailAddress), "Invalid email address");

        return createGuestUserForAnonymousCheckout(emailAddress, USER_NAME);
    }

    protected CustomerModel createGuestUserForAnonymousCheckout(final String email, final String name) throws DuplicateUidException {
        validateParameterNotNullStandardMessage("email", email);
        final CustomerModel guestCustomer = modelService.create(CustomerModel.class);
        final String guid = customerFacade.generateGUID();

        //takes care of localizing the name based on the site language
        guestCustomer.setUid(guid + "|" + email);
        guestCustomer.setName(name);
        guestCustomer.setType(CustomerType.valueOf(CustomerType.GUEST.getCode()));
        guestCustomer.setSessionLanguage(commonI18NService.getCurrentLanguage());
        guestCustomer.setSessionCurrency(commonI18NService.getCurrentCurrency());

        customerAccountService.registerGuestForAnonymousCheckout(guestCustomer, guid);

        return guestCustomer;
    }

    protected CartModel createCartForExpressCheckout(CustomerModel guestUser) {
        CartModel cart = cartFactory.createCart();
        cart.setUser(guestUser);
        modelService.save(cart);
        return cart;
    }

    protected PaymentInfoModel updatePaymentInfoWithCartAndUser(PaymentInfoModel paymentInfo, CustomerModel customerModel, AddressModel addressModel, CartModel cartModel) {
        Assert.notNull(paymentInfo, "Payment info must not be null");

        paymentInfo.setUser(customerModel);
        paymentInfo.setCode(generateCcPaymentInfoCode(cartModel));
        paymentInfo.setBillingAddress(addressModel);

        modelService.save(paymentInfo);

        return paymentInfo;
    }

    protected PaymentInfoModel createPaymentInfoForCart(CustomerModel customerModel, AddressModel addressModel, CartModel cartModel, String paymentMethod, String merchantId, String merchantName) {
        final PaymentInfoModel paymentInfo = modelService.create(PaymentInfoModel.class);
        paymentInfo.setUser(customerModel);
        paymentInfo.setCode(generateCcPaymentInfoCode(cartModel));
        paymentInfo.setBillingAddress(addressModel);
        paymentInfo.setAdyenPaymentMethod(paymentMethod);

        paymentInfo.setAdyenApplePayMerchantName(merchantName);
        paymentInfo.setAdyenApplePayMerchantIdentifier(merchantId);

        modelService.save(paymentInfo);

        return paymentInfo;
    }

    protected String generateCcPaymentInfoCode(final CartModel cartModel) {
        return cartModel.getCode() + "_" + UUID.randomUUID();
    }

    protected boolean cartHasEntries(CartModel cartModel) {
        return cartModel != null && !CollectionUtils.isEmpty(cartModel.getEntries());
    }

    public void setCartFactory(CartFactory cartFactory) {
        this.cartFactory = cartFactory;
    }

    public void setCartService(CartService cartService) {
        this.cartService = cartService;
    }

    public void setProductService(ProductService productService) {
        this.productService = productService;
    }

    public void setAddressReverseConverter(Converter<AddressData, AddressModel> addressReverseConverter) {
        this.addressReverseConverter = addressReverseConverter;
    }

    public void setModelService(ModelService modelService) {
        this.modelService = modelService;
    }

    public void setCustomerFacade(CustomerFacade customerFacade) {
        this.customerFacade = customerFacade;
    }

    public void setCommonI18NService(CommonI18NService commonI18NService) {
        this.commonI18NService = commonI18NService;
    }

    public void setCustomerAccountService(CustomerAccountService customerAccountService) {
        this.customerAccountService = customerAccountService;
    }

    public void setCommerceCartService(CommerceCartService commerceCartService) {
        this.commerceCartService = commerceCartService;
    }

    public void setDeliveryModeService(DeliveryModeService deliveryModeService) {
        this.deliveryModeService = deliveryModeService;
    }

    public void setAdyenCheckoutFacade(AdyenCheckoutFacade adyenCheckoutFacade) {
        this.adyenCheckoutFacade = adyenCheckoutFacade;
    }

    public void setCartConverter(Converter<CartModel, CartData> cartConverter) {
        this.cartConverter = cartConverter;
    }

    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public void setI18NFacade(I18NFacade i18NFacade) {
        this.i18NFacade = i18NFacade;
    }

    public void setAdyenCheckoutApiFacade(AdyenCheckoutApiFacade adyenCheckoutApiFacade) {
        this.adyenCheckoutApiFacade = adyenCheckoutApiFacade;
    }
}
