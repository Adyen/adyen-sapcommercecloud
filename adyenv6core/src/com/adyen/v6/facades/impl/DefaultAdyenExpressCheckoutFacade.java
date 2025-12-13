package com.adyen.v6.facades.impl;

import com.adyen.commerce.dto.OrderPaymentResult;
import com.adyen.commerce.facades.AdyenCheckoutApiFacade;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.constants.StorefrontType;
import com.adyen.v6.facades.AdyenCheckoutFacade;
import com.adyen.v6.facades.AdyenExpressCheckoutFacade;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.repository.CartRepository;
import com.adyen.v6.resolver.OccPaymentRedirectReturnUrlResolver;
import com.adyen.v6.service.AdyenShopperIpResolverService;
import de.hybris.platform.acceleratorservices.urlresolver.SiteBaseUrlResolutionService;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.commercefacades.customer.CustomerFacade;
import de.hybris.platform.commercefacades.i18n.I18NFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.order.data.ZoneDeliveryModeData;
import de.hybris.platform.commercefacades.order.impl.DefaultCheckoutFacade;
import de.hybris.platform.commercefacades.product.data.PriceDataType;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.RegionData;
import de.hybris.platform.commerceservices.customer.DuplicateUidException;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.commerceservices.impersonation.ImpersonationService;
import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.delivery.DeliveryModeModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
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
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.util.PriceValue;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.log4j.Logger;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;
import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNullStandardMessage;

public class DefaultAdyenExpressCheckoutFacade extends DefaultCheckoutFacade implements AdyenExpressCheckoutFacade {
    private static final Logger LOG = Logger.getLogger(DefaultAdyenExpressCheckoutFacade.class);
    protected static final String USER_NAME = "ExpressCheckoutGuest";
    protected static final String DELIVERY_MODE_CODE = "adyen-express-checkout";
    protected static final String ANONYMOUS_CHECKOUT_GUID = "anonymous_checkout_guid";
    protected static final String ANONYMOUS_CHECKOUT = "anonymous_checkout";
    protected static final String REDIRECT_RETURN_URL_BASE = "/checkout/express/checkout-adyen-response";

    protected CartFactory cartFactory;
    protected CartService cartService;
    protected ProductService productService;
    protected CustomerFacade customerFacade;
    protected UserFacade userFacade;
    protected CommonI18NService commonI18NService;
    protected I18NFacade i18NFacade;
    protected CommerceCartService commerceCartService;
    protected DeliveryModeService deliveryModeService;
    protected AdyenCheckoutFacade adyenCheckoutFacade;
    protected SessionService sessionService;
    protected UserService userService;
    protected AdyenCheckoutApiFacade adyenCheckoutApiFacade;
    protected Converter<AddressData, AddressModel> addressReverseConverter;
    protected Converter<CartModel, CartData> cartConverter;
    protected CartRepository cartRepository;
    protected SiteBaseUrlResolutionService siteBaseUrlResolutionService;
    protected BaseSiteService baseSiteService;
    protected OccPaymentRedirectReturnUrlResolver occPaymentRedirectReturnUrlResolver;
    protected AdyenShopperIpResolverService adyenShopperIpResolverService;

    public PaymentResponse expressCheckoutPDP(String cartId, PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                              HttpServletRequest request) throws Exception {
        Assert.notNull(paymentMethod, "Payment method must not be null");
        validateAddress(addressData);

        PaymentInfoModel paymentInfoModel = getModelService().create(PaymentInfoModel.class);
        paymentInfoModel.setAdyenPaymentMethod(paymentMethod);

        updateRegionData(addressData);

        return expressPDPCheckout(paymentRequest, addressData, paymentInfoModel, cartId, request);
    }

    public OrderPaymentResult expressCheckoutPDPOCC(String cartId, PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                           HttpServletRequest request) throws Exception {
        Assert.notNull(paymentMethod, "Payment method must not be null");
        validateAddress(addressData);

        PaymentInfoModel paymentInfoModel = getModelService().create(PaymentInfoModel.class);
        paymentInfoModel.setAdyenPaymentMethod(paymentMethod);

        updateRegionData(addressData);

        return expressPDPCheckoutOCC(paymentRequest, addressData, paymentInfoModel, cartId, request);
    }


    public PaymentResponse expressCheckoutCart(PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                               HttpServletRequest request) throws Exception {
        Assert.notNull(paymentMethod, "Payment method must not be null");
        validateAddress(addressData);

        PaymentInfoModel paymentInfoModel = getModelService().create(PaymentInfoModel.class);
        paymentInfoModel.setAdyenPaymentMethod(paymentMethod);

        updateRegionData(addressData);

        return expressCartCheckout(paymentRequest, addressData, paymentInfoModel, request);
    }

    public OrderPaymentResult expressCheckoutCartOCC(PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                            HttpServletRequest request) throws Exception {
        Assert.notNull(paymentMethod, "Payment method must not be null");
        validateAddress(addressData);

        PaymentInfoModel paymentInfoModel = getModelService().create(PaymentInfoModel.class);
        paymentInfoModel.setAdyenPaymentMethod(paymentMethod);

        updateRegionData(addressData);

        return expressCartCheckoutOCC(paymentRequest, addressData, paymentInfoModel, request);
    }

    protected PaymentResponse expressPDPCheckout(PaymentRequest paymentRequest, AddressData addressData, PaymentInfoModel paymentInfoModel, String cartId,
                                                 HttpServletRequest request) throws Exception {
        CustomerModel user = (CustomerModel) userService.getCurrentUser();
        boolean isGuestUser = false;
        if (userService.isAnonymousUser(user)) {
            user = createGuestCustomer(addressData.getEmail());
            isGuestUser = true;
        }

        CartModel cart = prepareCartForPDPExpressCheckout(addressData, paymentInfoModel, cartId, user);

        if (cartHasEntries(cart)) {
            calculateCart(cart);

            CartModel sessionCart = null;
            if (cartService.hasSessionCart()) {
                sessionCart = cartService.getSessionCart();
            }
            cartService.setSessionCart(cart);

            CartData cartData = cartConverter.convert(cart);

            List<OrderEntryData> entries = cartData.getEntries();
            if (entries.size() == 1) {
                OrderEntryData entry = entries.get(0);
                String productCode = entry.getProduct().getCode();

                String returnUrl = getReturnUrlForProductCheckout(productCode);

                paymentRequest.setReturnUrl(returnUrl);
            } else {
                LOG.error("Wrong entries number in PDP express cart");
                throw new IllegalArgumentException("Wrong entries number in PDP express cart");
            }

            PaymentResponse paymentsResponse = adyenCheckoutFacade.componentPayment(request, cartData, paymentRequest);

            if (isGuestUser) {
                sessionService.setAttribute(ANONYMOUS_CHECKOUT_GUID,
                       StringUtils.substringBefore(cart.getUser().getUid(), "|"));
                sessionService.setAttribute(ANONYMOUS_CHECKOUT, Boolean.TRUE);
            }

            if (sessionCart != null) {
                cartService.setSessionCart(sessionCart);
            }

            return paymentsResponse;
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }

    protected OrderPaymentResult expressPDPCheckoutOCC(PaymentRequest paymentRequest, AddressData addressData, PaymentInfoModel paymentInfoModel, String cartId,
                                              HttpServletRequest request) throws Exception {
        CustomerModel user = (CustomerModel) userService.getCurrentUser();
        if (userService.isAnonymousUser(user)) {
            user = createGuestCustomer(addressData.getEmail());
        }

        CartModel cart = prepareCartForPDPExpressCheckout(addressData, paymentInfoModel, cartId, user);

        if (cartHasEntries(cart)) {
            calculateCart(cart);

            CartModel sessionCart = null;
            if (cartService.hasSessionCart()) {
                sessionCart = cartService.getSessionCart();
            }
            cartService.setSessionCart(cart);

            CartData cartData = cartConverter.convert(cart);

            List<OrderEntryData> entries = cartData.getEntries();
            if (entries.size() == 1) {
                OrderEntryData entry = entries.get(0);
                String productCode = entry.getProduct().getCode();

                paymentRequest.setReturnUrl(occPaymentRedirectReturnUrlResolver.resolvePaymentRedirectReturnUrlExpressPDPCheckout(productCode));
            } else {
                LOG.error("Wrong entries number in PDP express cart");
                throw new IllegalArgumentException("Wrong entries number in PDP express cart");
            }

            String shopperIp = adyenShopperIpResolverService.resolveShopperIp(request);

            OrderPaymentResult orderPaymentResult;
            RequestInfo requestInfo = new RequestInfo(request, shopperIp);
            requestInfo.setStorefrontType(StorefrontType.EXPRESSOCC);
            orderPaymentResult = adyenCheckoutApiFacade.placeOrderWithPaymentOCC(request, cartData, paymentRequest,requestInfo);
            if (sessionCart != null) {
                cartService.setSessionCart(sessionCart);
            }

            return orderPaymentResult;
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }

    protected PaymentResponse expressCartCheckout(PaymentRequest paymentRequest, AddressData addressData, PaymentInfoModel paymentInfoModel,
                                                  HttpServletRequest request) throws Exception {
        CustomerModel user = (CustomerModel) userService.getCurrentUser();
        boolean isGuestUser = false;
        if (userService.isAnonymousUser(user)) {
            user = createGuestCustomer(addressData.getEmail());
            cartService.changeCurrentCartUser(user);
            isGuestUser = true;
        }

        CartModel cart = prepareCartForCartExpressCheckout(addressData, paymentInfoModel, user);

        if (cartHasEntries(cart)) {
            CartData cartData = cartConverter.convert(cart);

            if (isGuestUser) {
                sessionService.setAttribute(ANONYMOUS_CHECKOUT_GUID,
                        StringUtils.substringBefore(cart.getUser().getUid(), "|"));
                sessionService.setAttribute(ANONYMOUS_CHECKOUT, Boolean.TRUE);

            }

            paymentRequest.setReturnUrl(getReturnUrlForCartCheckout());

            return adyenCheckoutFacade.componentPayment(request, cartData, paymentRequest);
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }

    protected OrderPaymentResult expressCartCheckoutOCC(PaymentRequest paymentRequest, AddressData addressData, PaymentInfoModel paymentInfoModel,
                                               HttpServletRequest request) throws Exception {
        CustomerModel user = (CustomerModel) userService.getCurrentUser();
        if (userService.isAnonymousUser(user)) {
            user = createGuestCustomer(addressData.getEmail());
            cartService.changeCurrentCartUser(user);
        }

        CartModel cart = prepareCartForCartExpressCheckout(addressData, paymentInfoModel, user);

        if (cartHasEntries(cart)) {
            String shopperIp = adyenShopperIpResolverService.resolveShopperIp(request);

            CartData cartData = cartConverter.convert(cart);
            RequestInfo requestInfo = new RequestInfo(request, shopperIp);
            requestInfo.setStorefrontType(StorefrontType.EXPRESSOCC);

            paymentRequest.setReturnUrl(occPaymentRedirectReturnUrlResolver.resolvePaymentRedirectReturnUrlExpressCartCheckout());

            OrderPaymentResult orderPaymentResult = adyenCheckoutApiFacade.placeOrderWithPayment(request, cartData, paymentRequest,requestInfo);
            return orderPaymentResult;
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }

    protected void validateAddress(AddressData addressData) {
        validateParameterNotNull(addressData, "Empty address");
        if (StringUtils.isEmpty(addressData.getEmail())) {
            throw new IllegalArgumentException("Empty email address");
        }
    }

    protected CartModel prepareCartForCartExpressCheckout(AddressData addressData, PaymentInfoModel paymentInfoModel, CustomerModel user) throws CalculationException {
        CartModel cart = cartService.getSessionCart();

        AddressModel addressModel = prepareAddressModel(addressData, user);
        updatePaymentInfoWithCartAndUser(paymentInfoModel, user, addressModel, cart);

        updateCart(cart, addressModel, paymentInfoModel);
        updateCartForLegacyExpressSupport(cart);
        calculateCart(cart);
        return cart;
    }

    protected CartModel prepareCartForPDPExpressCheckout(AddressData addressData, PaymentInfoModel paymentInfoModel, String cartId, CustomerModel user) {
        CartModel cart = cartRepository.getCart(cartId);
        cart.setUser(user);
        getModelService().save(cart);

        AddressModel addressModel = prepareAddressModel(addressData, user);
        updatePaymentInfoWithCartAndUser(paymentInfoModel, user, addressModel, cart);

        updateCart(cart, addressModel, paymentInfoModel);
        updateCartForLegacyExpressSupport(cart);

        return cart;
    }

    /* Prevent breaking current implementation. To be removed when implementation will be completed. */
    private void updateCartForLegacyExpressSupport(CartModel cart) {
        if(cart.getDeliveryMode() == null) {
            DeliveryModeModel deliveryMode = deliveryModeService.getDeliveryModeForCode(DELIVERY_MODE_CODE);
            validateParameterNotNull(deliveryMode, "Delivery mode for Adyen express checkout not configured");
            cart.setDeliveryMode(deliveryMode);
        }
    }

    protected void calculateCart(CartModel cart) {
        CommerceCartParameter commerceCartParameter = new CommerceCartParameter();
        commerceCartParameter.setCart(cart);
        commerceCartService.calculateCart(commerceCartParameter);
    }

    protected void updateCart(CartModel cart, AddressModel addressModel, PaymentInfoModel paymentInfo) {
        cart.setDeliveryAddress(addressModel);
        cart.setPaymentAddress(addressModel);
        cart.setPaymentInfo(paymentInfo);
        getModelService().save(cart);
    }

    protected AddressModel prepareAddressModel(AddressData addressData, CustomerModel user) {
        AddressModel addressModel = getModelService().create(AddressModel.class);
        addressReverseConverter.convert(addressData, addressModel);
        validateParameterNotNull(addressModel, "Empty address");
        addressModel.setOwner(user);
        addressModel.setBillingAddress(true);
        addressModel.setShippingAddress(true);

        getModelService().save(addressModel);
        return addressModel;
    }

    protected void addProductToCart(String productCode, CartModel cart) {
        ProductModel product = productService.getProductForCode(productCode);

        if (product != null) {
            cartService.addNewEntry(cart, product, 1L, product.getUnit());
        }
        getModelService().save(cart);
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
        final CustomerModel guestCustomer = getModelService().create(CustomerModel.class);
        final String guid = customerFacade.generateGUID();

        //takes care of localizing the name based on the site language
        guestCustomer.setUid(guid + "|" + email);
        guestCustomer.setName(name);
        guestCustomer.setType(CustomerType.valueOf(CustomerType.GUEST.getCode()));
        guestCustomer.setSessionLanguage(commonI18NService.getCurrentLanguage());
        guestCustomer.setSessionCurrency(commonI18NService.getCurrentCurrency());

        getCustomerAccountService().registerGuestForAnonymousCheckout(guestCustomer, guid);

        return guestCustomer;
    }

    protected CartModel createCartForExpressCheckout(CustomerModel user) {
        CartModel cart = cartFactory.createCart();
        cart.setUser(user);
        getModelService().save(cart);
        return cart;
    }

    protected PaymentInfoModel updatePaymentInfoWithCartAndUser(PaymentInfoModel paymentInfo, CustomerModel customerModel, AddressModel addressModel, CartModel cartModel) {
        Assert.notNull(paymentInfo, "Payment info must not be null");

        paymentInfo.setUser(customerModel);
        paymentInfo.setCode(generatePaymentInfoCode(cartModel));
        paymentInfo.setBillingAddress(addressModel);

        getModelService().save(paymentInfo);

        return paymentInfo;
    }

    protected String generatePaymentInfoCode(final CartModel cartModel) {
        return cartModel.getCode() + "_" + UUID.randomUUID();
    }

    protected boolean cartHasEntries(CartModel cartModel) {
        return cartModel != null && !CollectionUtils.isEmpty(cartModel.getEntries());
    }


    protected String getReturnUrlForProductCheckout(String productCode) {
        String url = REDIRECT_RETURN_URL_BASE + "?productCode=" + productCode;

        return getReturnUrlForBaseSite(url);
    }

    protected String getReturnUrlForCartCheckout() {
        return getReturnUrlForBaseSite(REDIRECT_RETURN_URL_BASE);
    }

    protected String getReturnUrlForBaseSite(String url) {
        BaseSiteModel currentBaseSite = baseSiteService.getCurrentBaseSite();

        return siteBaseUrlResolutionService.getWebsiteUrlForSite(currentBaseSite, true, url);
    }

    public CartData createOrGetCartForExpressCheckout(String productCode) {
        UserModel currentUser = userService.getCurrentUser();
        String currencyCode = commonI18NService.getCurrentCurrency().getIsocode();
        String expressCartCode = sessionService.getCurrentSession().getAttribute("expressCartCode-" + productCode + "-" + currencyCode);
        if (expressCartCode != null) {
            CartModel cartForExpressCheckout = cartRepository.getCart(expressCartCode);
            if(cartForExpressCheckout != null){
                return cartConverter.convert(cartForExpressCheckout);
            }
        }
        CartModel cartForExpressCheckout = createCartForExpressCheckout((CustomerModel) currentUser);
        sessionService.getCurrentSession().setAttribute("expressCartCode-" + productCode + "-" + currencyCode, cartForExpressCheckout.getCode());
        return cartConverter.convert(cartForExpressCheckout);
    }

    @Override
    public CartData prepareCartForExpressCheckoutWithProduct(String cartId, String productCode, Integer quantity) throws CalculationException {
        final CartModel cartModel = cartRepository.getCart(cartId);

        // Remove all entries from the cart
        cartModel.setEntries(new ArrayList<>());
        getModelService().save(cartModel);

        ProductModel product = productService.getProductForCode(productCode);
        if (product != null) {
            cartService.addNewEntry(cartModel, product, quantity, product.getUnit());
        }
        getModelService().save(cartModel);
        calculateCart(cartModel);
        return cartConverter.convert(cartModel);
    }

    @Override
    public boolean setDeliveryAddressForCart(final AddressData addressData, final String cartId) {
        final CartModel cartModel = cartRepository.getCart(cartId);

        addressData.setVisibleInAddressBook(false);
        addressData.setDefaultAddress(false);

        if (cartModel != null) {
            AddressModel addressModel = addAddressForExpress(addressData);

            final CommerceCheckoutParameter parameter = createCommerceCheckoutParameter(cartModel, true);
            parameter.setAddress(addressModel);
            parameter.setIsDeliveryAddress(true);
            return getCommerceCheckoutService().setDeliveryAddress(parameter);
        }
        return false;
    }

    public AddressModel addAddressForExpress(final AddressData addressData) {
        validateParameterNotNullStandardMessage("addressData", addressData);

        final CustomerModel currentCustomer = (CustomerModel) userService.getCurrentUser();

        // Create the new address model
        final AddressModel newAddress = getModelService().create(AddressModel.class);
        getAddressReversePopulator().populate(addressData, newAddress);

        // Store the address against the user
        getCustomerAccountService().saveAddressEntry(currentCustomer, newAddress);

        // Update the address ID in the newly created address
        addressData.setId(newAddress.getPk().toString());

        return newAddress;
    }

    public List<DeliveryModeData> getDeliveryModes(final String cartId) {
        final List<DeliveryModeData> result = new ArrayList<DeliveryModeData>();
        final CartModel cartModel = cartRepository.getCart(cartId);
        for (final DeliveryModeModel deliveryModeModel : getDeliveryService().getSupportedDeliveryModeListForOrder(cartModel)) {
            result.add(convert(deliveryModeModel, cartModel));
        }
        return result;
    }

    protected DeliveryModeData convert(final DeliveryModeModel deliveryModeModel,  final CartModel cartModel ) {
        if (deliveryModeModel instanceof ZoneDeliveryModeModel) {
            final ZoneDeliveryModeModel zoneDeliveryModeModel = (ZoneDeliveryModeModel) deliveryModeModel;
            if (cartModel != null) {
                final ZoneDeliveryModeData zoneDeliveryModeData = getZoneDeliveryModeConverter().convert(zoneDeliveryModeModel);
                final PriceValue deliveryCost = getDeliveryService().getDeliveryCostForDeliveryModeAndAbstractOrder(deliveryModeModel,
                        cartModel);
                if (deliveryCost != null) {
                    zoneDeliveryModeData.setDeliveryCost(getPriceDataFactory().create(PriceDataType.BUY,
                            BigDecimal.valueOf(deliveryCost.getValue()), deliveryCost.getCurrencyIso()));
                }
                return zoneDeliveryModeData;
            }
            return null;
        }
        return getDeliveryModeConverter().convert(deliveryModeModel);
    }

    public CartData setDeliveryModeForCart(final String deliveryModeCode, final String cartId) throws CalculationException {
        final CartModel cartModel = cartRepository.getCart(cartId);
        final DeliveryModeModel deliveryMode = deliveryModeService.getDeliveryModeForCode(deliveryModeCode);

        return setDeliveryModeForCart(deliveryMode, cartModel);
    }

    public CartData setDeliveryModeForCart(final DeliveryModeModel deliveryModeModel, final CartModel cartModel) throws CalculationException {
        final CommerceCheckoutParameter parameter = createCommerceCheckoutParameter(cartModel, true);
        parameter.setDeliveryMode(deliveryModeModel);
        if(getCommerceCheckoutService().setDeliveryMode(parameter)){
            return cartConverter.convert(cartModel);
        }
        throw new CalculationException("Failed to set delivery mode");
    }

    public CartData getSessionCart(){
        return getCartFacade().getSessionCart();
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

    public void setCustomerFacade(CustomerFacade customerFacade) {
        this.customerFacade = customerFacade;
    }

    public void setCommonI18NService(CommonI18NService commonI18NService) {
        this.commonI18NService = commonI18NService;
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

    public void setCartRepository(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    public void setImpersonationService(ImpersonationService impersonationService) {
    }

    public void setUserFacade(UserFacade userFacade) {
        this.userFacade = userFacade;
    }

    public void setSiteBaseUrlResolutionService(SiteBaseUrlResolutionService siteBaseUrlResolutionService) {
        this.siteBaseUrlResolutionService = siteBaseUrlResolutionService;
    }

    public void setBaseSiteService(BaseSiteService baseSiteService) {
        this.baseSiteService = baseSiteService;
    }

    public void setOccPaymentRedirectReturnUrlResolver(OccPaymentRedirectReturnUrlResolver occPaymentRedirectReturnUrlResolver) {
        this.occPaymentRedirectReturnUrlResolver = occPaymentRedirectReturnUrlResolver;
    }

    public void setAdyenShopperIpResolverService(AdyenShopperIpResolverService adyenShopperIpResolverService) {
        this.adyenShopperIpResolverService = adyenShopperIpResolverService;
    }
}
