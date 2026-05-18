package com.adyen.commerce.facades.impl;

import com.adyen.commerce.dto.OrderPaymentResult;
import com.adyen.commerce.facades.AdyenCheckoutFacade;
import com.adyen.commerce.facades.AdyenExpressCheckoutFacade;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.constants.StorefrontType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;
import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNullStandardMessage;

/**
 * Default implementation of {@link AdyenExpressCheckoutFacade}.
 * <p>
 * Orchestrates express checkout flows for PDP and Cart, both Accelerator and OCC variants.
 * </p>
 */
public class DefaultAdyenExpressCheckoutFacade extends DefaultCheckoutFacade implements AdyenExpressCheckoutFacade {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAdyenExpressCheckoutFacade.class);

    public static final String USER_NAME = "ExpressCheckoutGuest";
    protected static final String DELIVERY_MODE_CODE = "adyen-express-checkout";
    protected static final String ANONYMOUS_CHECKOUT_GUID = "anonymous_checkout_guid";
    protected static final String ANONYMOUS_CHECKOUT = "anonymous_checkout";
    protected static final String REDIRECT_RETURN_URL_BASE = "/checkout/express/checkout-adyen-response";

    private static final String EXPRESS_CART_SESSION_KEY_PREFIX = "expressCartCode-";

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
    protected com.adyen.commerce.facades.AdyenCheckoutApiFacade adyenCheckoutApiFacade;
    protected Converter<AddressData, AddressModel> addressReverseConverter;
    protected Converter<CartModel, CartData> cartConverter;
    protected CartRepository cartRepository;
    protected SiteBaseUrlResolutionService siteBaseUrlResolutionService;
    protected BaseSiteService baseSiteService;
    protected OccPaymentRedirectReturnUrlResolver occPaymentRedirectReturnUrlResolver;
    protected AdyenShopperIpResolverService adyenShopperIpResolverService;

    @Override
    public PaymentResponse expressCheckoutPDP(String cartId, PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                              HttpServletRequest request) throws Exception {
        PaymentInfoModel paymentInfoModel = preparePaymentInfo(paymentMethod, addressData);
        return expressPDPCheckout(paymentRequest, addressData, paymentInfoModel, cartId, request);
    }

    @Override
    public OrderPaymentResult expressCheckoutPDPOCC(String cartId, PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                                    HttpServletRequest request) throws Exception {
        PaymentInfoModel paymentInfoModel = preparePaymentInfo(paymentMethod, addressData);
        return expressPDPCheckoutOCC(paymentRequest, addressData, paymentInfoModel, cartId, request);
    }

    @Override
    public PaymentResponse expressCheckoutCart(PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                               HttpServletRequest request) throws Exception {
        PaymentInfoModel paymentInfoModel = preparePaymentInfo(paymentMethod, addressData);
        return expressCartCheckout(paymentRequest, addressData, paymentInfoModel, request);
    }

    @Override
    public OrderPaymentResult expressCheckoutCartOCC(PaymentRequest paymentRequest, String paymentMethod, AddressData addressData,
                                                     HttpServletRequest request) throws Exception {
        PaymentInfoModel paymentInfoModel = preparePaymentInfo(paymentMethod, addressData);
        return expressCartCheckoutOCC(paymentRequest, addressData, paymentInfoModel, request);
    }


    /**
     * Validates the payment method and address, then creates and initialises a {@link PaymentInfoModel}.
     *
     * @param paymentMethod the payment method code
     * @param addressData   the address data
     * @return a pre-populated {@link PaymentInfoModel}
     */
    protected PaymentInfoModel preparePaymentInfo(String paymentMethod, AddressData addressData) {
        Assert.notNull(paymentMethod, "Payment method must not be null");
        validateAddress(addressData);
        updateRegionData(addressData);
        PaymentInfoModel paymentInfoModel = getModelService().create(PaymentInfoModel.class);
        paymentInfoModel.setAdyenPaymentMethod(paymentMethod);
        return paymentInfoModel;
    }

    // -------------------------------------------------------------------------
    // Protected checkout implementations
    // -------------------------------------------------------------------------

    protected PaymentResponse expressPDPCheckout(PaymentRequest paymentRequest, AddressData addressData, PaymentInfoModel paymentInfoModel, String cartId,
                                                 HttpServletRequest request) throws Exception {

        CustomerModel user = resolveCustomerModel();
        boolean guestUser = false;
        if (userService.isAnonymousUser(userService.getCurrentUser())) {
            user = createGuestCustomer(addressData.getEmail());
            guestUser = true;
        }
        final boolean isGuestUser = guestUser;

        CartModel cart = prepareCartForPDPExpressCheckout(addressData, paymentInfoModel, cartId, user);

        if (cartHasEntries(cart)) {
            calculateCart(cart);

            return withTemporarySessionCart(cart, () -> {
                CartData cartData = cartConverter.convert(cart);

                List<OrderEntryData> entries = cartData.getEntries();
                if (entries.size() == 1) {
                    OrderEntryData entry = entries.get(0);
                    String productCode = entry.getProduct().getCode();
                    paymentRequest.setReturnUrl(getReturnUrlForProductCheckout(productCode));
                } else {
                    LOG.error("Wrong entries number in PDP express cart");
                    throw new IllegalArgumentException("Wrong entries number in PDP express cart");
                }

                PaymentResponse paymentsResponse = adyenCheckoutFacade.componentPayment(request, cartData, paymentRequest);

                if (isGuestUser) {
                    markSessionAsAnonymousCheckout(cart);
                }

                return paymentsResponse;
            });
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }

    protected OrderPaymentResult expressPDPCheckoutOCC(PaymentRequest paymentRequest, AddressData addressData, PaymentInfoModel paymentInfoModel, String cartId,
                                                       HttpServletRequest request) throws Exception {
        CustomerModel user = resolveCustomerModel();
        if (userService.isAnonymousUser(userService.getCurrentUser())) {
            user = createGuestCustomer(addressData.getEmail());
        }

        CartModel cart = prepareCartForPDPExpressCheckout(addressData, paymentInfoModel, cartId, user);

        if (cartHasEntries(cart)) {
            calculateCart(cart);

            return withTemporarySessionCart(cart, () -> {
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
                RequestInfo requestInfo = new RequestInfo(request, shopperIp);
                requestInfo.setStorefrontType(StorefrontType.EXPRESSOCC);

                return adyenCheckoutApiFacade.placeOrderWithPaymentOCC(request, cartData, paymentRequest, requestInfo);
            });
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }

    protected PaymentResponse expressCartCheckout(PaymentRequest paymentRequest, AddressData addressData, PaymentInfoModel paymentInfoModel,
                                                  HttpServletRequest request) throws Exception {
        CustomerModel user = resolveCustomerModel();
        boolean isGuestUser = false;
        if (userService.isAnonymousUser(userService.getCurrentUser())) {
            user = createGuestCustomer(addressData.getEmail());
            cartService.changeCurrentCartUser(user);
            isGuestUser = true;
        }

        CartModel cart = prepareCartForCartExpressCheckout(addressData, paymentInfoModel, user);

        if (cartHasEntries(cart)) {
            CartData cartData = cartConverter.convert(cart);

            if (isGuestUser) {
                markSessionAsAnonymousCheckout(cart);
            }

            paymentRequest.setReturnUrl(getReturnUrlForCartCheckout());

            return adyenCheckoutFacade.componentPayment(request, cartData, paymentRequest);
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }

    protected OrderPaymentResult expressCartCheckoutOCC(PaymentRequest paymentRequest, AddressData addressData, PaymentInfoModel paymentInfoModel,
                                                        HttpServletRequest request) throws Exception {
        CustomerModel user = resolveCustomerModel();
        if (userService.isAnonymousUser(userService.getCurrentUser())) {
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

            return adyenCheckoutApiFacade.placeOrderWithPayment(request, cartData, paymentRequest, requestInfo);
        } else {
            throw new InvalidCartException("Checkout attempt on empty cart");
        }
    }

    /**
     * Executes {@code action} with {@code tempCart} set as the session cart,
     * then restores the previous session cart in a {@code finally} block to
     * guarantee the original cart is never lost even if an exception is thrown.
     *
     * @param tempCart the cart to set temporarily as the session cart
     * @param action   the action to execute
     * @param <T>      the return type
     * @return the result of the action
     * @throws Exception if the action throws
     */
    protected <T> T withTemporarySessionCart(CartModel tempCart, ThrowingSupplier<T> action) throws Exception {
        CartModel previousCart = cartService.hasSessionCart() ? cartService.getSessionCart() : null;
        cartService.setSessionCart(tempCart);
        try {
            return action.get();
        } finally {
            if (previousCart != null) {
                cartService.setSessionCart(previousCart);
            }
        }
    }

    /**
     * Functional interface for suppliers that may throw checked exceptions.
     */
    @FunctionalInterface
    protected interface ThrowingSupplier<T> {
        T get() throws Exception;
    }


    /**
     * Sets the anonymous checkout session attributes for the given guest cart's user.
     *
     * @param cart the cart whose user UID is used to derive the checkout GUID
     */
    protected void markSessionAsAnonymousCheckout(CartModel cart) {
        sessionService.setAttribute(ANONYMOUS_CHECKOUT_GUID,
                StringUtils.substringBefore(cart.getUser().getUid(), "|"));
        sessionService.setAttribute(ANONYMOUS_CHECKOUT, Boolean.TRUE);
    }


    /**
     * Returns the current user as a {@link CustomerModel}, or {@code null} if the user is anonymous.
     *
     * @throws IllegalStateException if the current user is neither a {@link CustomerModel} nor anonymous
     */
    protected CustomerModel resolveCustomerModel() {
        UserModel rawUser = userService.getCurrentUser();
        if (userService.isAnonymousUser(rawUser)) {
            return null;
        }
        if (!(rawUser instanceof CustomerModel)) {
            throw new IllegalStateException("Express checkout is only supported for customer or anonymous users, but got: "
                    + rawUser.getClass().getName());
        }
        return (CustomerModel) rawUser;
    }

    // -------------------------------------------------------------------------
    // Cart preparation helpers
    // -------------------------------------------------------------------------

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
        if (cart.getDeliveryMode() == null) {
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

    public void updateCart(CartModel cart, AddressModel addressModel, PaymentInfoModel paymentInfo) {
        cart.setDeliveryAddress(addressModel);
        cart.setPaymentAddress(addressModel);
        cart.setPaymentInfo(paymentInfo);
        getModelService().save(cart);
    }

    public AddressModel prepareAddressModel(AddressData addressData, CustomerModel user) {
        AddressModel addressModel = getModelService().create(AddressModel.class);
        addressReverseConverter.convert(addressData, addressModel);
        validateParameterNotNull(addressModel, "Empty address");
        addressModel.setOwner(user);
        addressModel.setBillingAddress(true);
        addressModel.setShippingAddress(true);

        getModelService().save(addressModel);
        return addressModel;
    }


    /**
     * Adds the product identified by {@code productCode} to the cart.
     * {@code productService.getProductForCode()} throws {@code UnknownIdentifierException}
     * if the product is not found — it never returns {@code null}.
     *
     * @param productCode the product code
     * @param cart        the cart to add the product to
     */
    public void addProductToCart(String productCode, CartModel cart) {
        ProductModel product = productService.getProductForCode(productCode);
        cartService.addNewEntry(cart, product, 1L, product.getUnit());
        getModelService().save(cart);
    }


    /**
     * Resolves the full {@link RegionData} for the region short code in {@code addressData}.
     * Sets the region to {@code null} if the short code is absent or not found in the country's region list.
     *
     * @param addressData the address data to update in-place
     */
    public void updateRegionData(AddressData addressData) {
        if (addressData.getRegion() == null || StringUtils.isEmpty(addressData.getRegion().getIsocodeShort())) {
            addressData.setRegion(null);
            return;
        }
        List<RegionData> regionsForCountry = i18NFacade.getRegionsForCountryIso(addressData.getCountry().getIsocode());
        addressData.setRegion(regionsForCountry.stream()
                .filter(r -> r.getIsocodeShort().equals(addressData.getRegion().getIsocodeShort()))
                .findFirst()
                .orElse(null));
    }


    @Override
    public Optional<ZoneDeliveryModeValueModel> getExpressDeliveryModePrice() {
        DeliveryModeModel rawMode = deliveryModeService.getDeliveryModeForCode(DELIVERY_MODE_CODE);
        if (!(rawMode instanceof ZoneDeliveryModeModel)) {
            LOG.warn("Delivery mode '{}' is not a ZoneDeliveryModeModel or not configured", DELIVERY_MODE_CODE);
            return Optional.empty();
        }
        ZoneDeliveryModeModel deliveryMode = (ZoneDeliveryModeModel) rawMode;
        CurrencyModel currentCurrency = commonI18NService.getCurrentCurrency();
        return deliveryMode.getValues().stream()
                .filter(valueModel -> valueModel.getCurrency().equals(currentCurrency))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // Guest customer creation
    // -------------------------------------------------------------------------

    public CustomerModel createGuestCustomer(String emailAddress) throws DuplicateUidException {
        Assert.isTrue(isValidEmail(emailAddress), "Invalid email address");
        return createGuestUserForAnonymousCheckout(emailAddress, USER_NAME);
    }

    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    protected CustomerModel createGuestUserForAnonymousCheckout(final String email, final String name) throws DuplicateUidException {
        validateParameterNotNullStandardMessage("email", email);
        final CustomerModel guestCustomer = getModelService().create(CustomerModel.class);
        final String guid = customerFacade.generateGUID();

        guestCustomer.setUid(guid + "|" + email);
        guestCustomer.setName(name);
        guestCustomer.setType(CustomerType.valueOf(CustomerType.GUEST.getCode()));
        guestCustomer.setSessionLanguage(commonI18NService.getCurrentLanguage());
        guestCustomer.setSessionCurrency(commonI18NService.getCurrentCurrency());

        getCustomerAccountService().registerGuestForAnonymousCheckout(guestCustomer, guid);

        return guestCustomer;
    }

    public CartModel createCartForExpressCheckout(CustomerModel user) {
        CartModel cart = cartFactory.createCart();
        cart.setUser(user);
        getModelService().save(cart);
        return cart;
    }

    public PaymentInfoModel updatePaymentInfoWithCartAndUser(PaymentInfoModel paymentInfo, CustomerModel customerModel, AddressModel addressModel, CartModel cartModel) {
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


    @Override
    public CartData createOrGetCartForExpressCheckout(String productCode) {
        UserModel currentUser = userService.getCurrentUser();
        String currencyCode = commonI18NService.getCurrentCurrency().getIsocode();
        String sessionKey = buildExpressCartSessionKey(productCode, currencyCode);

        String expressCartCode = sessionService.getCurrentSession().getAttribute(sessionKey);
        if (expressCartCode != null) {
            CartModel cartForExpressCheckout = cartRepository.getCart(expressCartCode);
            if (cartForExpressCheckout != null) {
                return cartConverter.convert(cartForExpressCheckout);
            }
        }

        if (!(currentUser instanceof CustomerModel)) {
            throw new IllegalStateException("Express checkout is only supported for customer or anonymous users");
        }
        CartModel cartForExpressCheckout = createCartForExpressCheckout((CustomerModel) currentUser);
        sessionService.getCurrentSession().setAttribute(sessionKey, cartForExpressCheckout.getCode());
        return cartConverter.convert(cartForExpressCheckout);
    }

    private String buildExpressCartSessionKey(String productCode, String currencyCode) {
        return EXPRESS_CART_SESSION_KEY_PREFIX + productCode + "-" + currencyCode;
    }

    @Override
    public CartData prepareCartForExpressCheckoutWithProduct(String cartId, String productCode, Integer quantity) throws CalculationException {
        final CartModel cartModel = cartRepository.getCart(cartId);

        // Remove all entries from the cart before adding the new product
        cartModel.setEntries(new ArrayList<>());
        getModelService().save(cartModel);

        ProductModel product = productService.getProductForCode(productCode);
        cartService.addNewEntry(cartModel, product, quantity, product.getUnit());
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

        UserModel rawUser = userService.getCurrentUser();
        if (!(rawUser instanceof CustomerModel)) {
            throw new IllegalStateException("Express checkout is only supported for customer users");
        }
        final CustomerModel currentCustomer = (CustomerModel) rawUser;

        final AddressModel newAddress = getModelService().create(AddressModel.class);
        getAddressReversePopulator().populate(addressData, newAddress);

        getCustomerAccountService().saveAddressEntry(currentCustomer, newAddress);

        addressData.setId(newAddress.getPk().toString());
        return newAddress;
    }


    @Override
    public List<DeliveryModeData> getDeliveryModes(final String cartId) {
        final CartModel cartModel = cartRepository.getCart(cartId);
        return getDeliveryService().getSupportedDeliveryModeListForOrder(cartModel).stream()
                .map(mode -> convert(mode, cartModel))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    protected DeliveryModeData convert(final DeliveryModeModel deliveryModeModel, final CartModel cartModel) {
        if (deliveryModeModel instanceof ZoneDeliveryModeModel) {
            final ZoneDeliveryModeModel zoneDeliveryModeModel = (ZoneDeliveryModeModel) deliveryModeModel;
            if (cartModel != null) {
                final ZoneDeliveryModeData zoneDeliveryModeData = getZoneDeliveryModeConverter().convert(zoneDeliveryModeModel);
                final PriceValue deliveryCost = getDeliveryService().getDeliveryCostForDeliveryModeAndAbstractOrder(deliveryModeModel, cartModel);
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

    @Override
    public CartData setDeliveryModeForCart(final String deliveryModeCode, final String cartId) throws CalculationException {
        final CartModel cartModel = cartRepository.getCart(cartId);
        final DeliveryModeModel deliveryMode = deliveryModeService.getDeliveryModeForCode(deliveryModeCode);
        return setDeliveryModeForCart(deliveryMode, cartModel);
    }

    public CartData setDeliveryModeForCart(final DeliveryModeModel deliveryModeModel, final CartModel cartModel) throws CalculationException {
        final CommerceCheckoutParameter parameter = createCommerceCheckoutParameter(cartModel, true);
        parameter.setDeliveryMode(deliveryModeModel);
        if (getCommerceCheckoutService().setDeliveryMode(parameter)) {
            return cartConverter.convert(cartModel);
        }
        throw new IllegalStateException("Failed to set delivery mode: " + deliveryModeModel.getCode());
    }

    @Override
    public CartData getSessionCart() {
        return getCartFacade().getSessionCart();
    }

// -------------------------------------------------------------------------
// Setters
// -------------------------------------------------------------------------

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

    public void setAdyenCheckoutApiFacade(com.adyen.commerce.facades.AdyenCheckoutApiFacade adyenCheckoutApiFacade) {
        this.adyenCheckoutApiFacade = adyenCheckoutApiFacade;
    }

    public void setCartRepository(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
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

