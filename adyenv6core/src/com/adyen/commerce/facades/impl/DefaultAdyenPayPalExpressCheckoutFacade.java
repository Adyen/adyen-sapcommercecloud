package com.adyen.commerce.facades.impl;

import com.adyen.commerce.facades.AdyenPayPalExpressCheckoutFacade;
import com.adyen.model.checkout.*;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.constants.StorefrontType;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.response.PayPalExpressSubmitResponse;
import com.adyen.v6.service.AdyenShopperIpResolverService;
import com.adyen.v6.service.AdyenUtilityApiService;
import com.adyen.v6.util.AmountUtil;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commerceservices.customer.DuplicateUidException;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.delivery.DeliveryModeModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;

/**
 * Default implementation of {@link AdyenPayPalExpressCheckoutFacade}.
 */
public class DefaultAdyenPayPalExpressCheckoutFacade extends DefaultAdyenExpressCheckoutFacade implements AdyenPayPalExpressCheckoutFacade {

    // Improvement #15: use SLF4J instead of Log4j 1.x
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAdyenPayPalExpressCheckoutFacade.class);

    private BaseSiteService baseSiteService;
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    private AdyenShopperIpResolverService adyenShopperIpResolverService;

    @Override
    public PayPalExpressSubmitResponse onPayPalPDPSubmitOCC(HttpServletRequest request, PaymentRequest paymentRequest) throws IOException, ApiException {

        UserModel currentUser = userService.getCurrentUser();
        CartModel expressCart = commerceCartService.getCartForCodeAndUser(paymentRequest.getReference(), currentUser);

        // PayPal requires sending amount without delivery cost on submit
        removeDeliveryMethodAndRecalculateCart(expressCart);

        Amount amount = AmountUtil.createAmount(BigDecimal.valueOf(expressCart.getTotalPrice()), expressCart.getCurrency().getIsocode());

        paymentRequest.setAmount(amount);

        String shopperIp = adyenShopperIpResolverService.resolveShopperIp(request);

        RequestInfo requestInfo = new RequestInfo(request, shopperIp);
        requestInfo.setStorefrontType(StorefrontType.EXPRESSOCC);
        requestInfo.setShopperLocale(adyenCheckoutFacade.getShopperLocale());

        PaymentResponse paymentResponse = adyenCheckoutFacade.getAdyenPaymentService().sendPaymentRequest(paymentRequest, requestInfo);
        PayPalExpressSubmitResponse payPalExpressSubmitResponse = new PayPalExpressSubmitResponse();
        payPalExpressSubmitResponse.setPaymentResponse(paymentResponse);
        return payPalExpressSubmitResponse;
    }

    @Override
    public PayPalExpressSubmitResponse onPayPalPDPSubmit(HttpServletRequest request, PaymentRequest paymentRequest, String productCode) throws IOException, ApiException {
        Assert.isTrue(StringUtils.isNotEmpty(productCode), "Product code must not be empty");

        ProductModel productModel = productService.getProductForCode(productCode);

        CartModel expressCart = cartFactory.createCart();

        // PayPal requires sending amount without delivery cost on submit
        expressCart.setDeliveryMode(null);

        cartService.addNewEntry(expressCart, productModel, 1L, productModel.getUnit());
        getModelService().save(expressCart);
        CommerceCartParameter commerceCartParameter = new CommerceCartParameter();
        commerceCartParameter.setCart(expressCart);
        commerceCartService.calculateCart(commerceCartParameter);

        Amount amount = AmountUtil.createAmount(BigDecimal.valueOf(expressCart.getTotalPrice()), expressCart.getCurrency().getIsocode());

        paymentRequest.setReference(expressCart.getCode());
        paymentRequest.setAmount(amount);

        String shopperIp = adyenShopperIpResolverService.resolveShopperIp(request);

        RequestInfo requestInfo = new RequestInfo(request, shopperIp);
        requestInfo.setStorefrontType(StorefrontType.ACCELERATOR);
        requestInfo.setShopperLocale(adyenCheckoutFacade.getShopperLocale());

        PaymentResponse paymentResponse = adyenCheckoutFacade.getAdyenPaymentService().sendPaymentRequest(paymentRequest, requestInfo);

        PayPalExpressSubmitResponse payPalExpressSubmitResponse = new PayPalExpressSubmitResponse();
        payPalExpressSubmitResponse.setPaymentResponse(paymentResponse);
        payPalExpressSubmitResponse.setExpressCartGuid(expressCart.getGuid());
        payPalExpressSubmitResponse.setPspReference(paymentResponse.getPspReference());

        return payPalExpressSubmitResponse;
    }

    @Override
    public PaymentResponse onPayPalCartSubmit(HttpServletRequest request, PaymentRequest paymentRequest) throws IOException, ApiException {
        CartModel sessionCart = cartService.getSessionCart();
        Assert.notNull(sessionCart, "Session cart must not be null");

        // PayPal requires sending amount without delivery cost on submit
        removeDeliveryMethodAndRecalculateCart(sessionCart);

        Amount amount = AmountUtil.createAmount(BigDecimal.valueOf(sessionCart.getTotalPrice()), sessionCart.getCurrency().getIsocode());

        paymentRequest.setAmount(amount);
        paymentRequest.setReference(sessionCart.getCode());

        String shopperIp = adyenShopperIpResolverService.resolveShopperIp(request);

        RequestInfo requestInfo = new RequestInfo(request, shopperIp);
        requestInfo.setStorefrontType(StorefrontType.ACCELERATOR);
        requestInfo.setShopperLocale(adyenCheckoutFacade.getShopperLocale());

        return adyenCheckoutFacade.getAdyenPaymentService().sendPaymentRequest(paymentRequest, requestInfo);
    }

    @Override
    public void onPayPalAuthorizedPDP(String cartGuid, AddressData addressData, String paymentMethod) throws DuplicateUidException, InvalidCartException, CalculationException {
        validateAddress(addressData);
        updateRegionData(addressData);

        PaymentInfoModel paymentInfoModel = getModelService().create(PaymentInfoModel.class);
        paymentInfoModel.setAdyenPaymentMethod(paymentMethod);

        // Improvement #5: guard before cast
        CustomerModel user = resolveCustomerModel();
        boolean isGuestUser = false;
        if (userService.isAnonymousUser(userService.getCurrentUser())) {
            user = createGuestCustomer(addressData.getEmail());
            isGuestUser = true;
        }

        CartModel expressCartForGuid = getExpressCartForGuid(cartGuid);

        if (expressCartForGuid != null && cartHasEntries(expressCartForGuid)) {
            prepareCartForPayPalExpressCheckout(addressData, expressCartForGuid, user, paymentInfoModel);

            // Improvement #2: session cart restore wrapped in try/finally
            final boolean guestUser = isGuestUser;
            try {
                withTemporarySessionCart(expressCartForGuid, () -> {
                    adyenCheckoutFacade.placePendingOrder();

                    // Improvement #3: extracted helper
                    if (guestUser) {
                        markSessionAsAnonymousCheckout(expressCartForGuid);
                    }
                    return null;
                });
            } catch (InvalidCartException e) {
                throw e;
            } catch (Exception e) {
                throw new InvalidCartException(e.getMessage());
            }
            return;
        }
        throw new InvalidCartException("No cart for checkout or empty cart");
    }

    @Override
    public void onPayPalAuthorizedCart(AddressData addressData, String paymentMethod) throws DuplicateUidException, InvalidCartException, CalculationException {
        validateAddress(addressData);

        PaymentInfoModel paymentInfoModel = getModelService().create(PaymentInfoModel.class);
        paymentInfoModel.setAdyenPaymentMethod(paymentMethod);

        updateRegionData(addressData);

        // Improvement #5: guard before cast
        CustomerModel user = resolveCustomerModel();
        boolean isGuestUser = false;
        if (userService.isAnonymousUser(userService.getCurrentUser())) {
            user = createGuestCustomer(addressData.getEmail());
            cartService.changeCurrentCartUser(user);
            isGuestUser = true;
        }

        CartModel sessionCart = cartService.getSessionCart();

        if (sessionCart != null && cartHasEntries(sessionCart)) {
            prepareCartForPayPalExpressCheckout(addressData, sessionCart, user, paymentInfoModel);

            adyenCheckoutFacade.placePendingOrder();

            // Improvement #3: extracted helper
            if (isGuestUser) {
                markSessionAsAnonymousCheckout(sessionCart);
            }
            return;
        }
        throw new InvalidCartException("No cart for checkout or empty cart");
    }

    @Override
    public PaypalUpdateOrderResponse updateShippingAddress(final AddressData addressData, final String pspReference, final String paymentData, final String cartGuid) throws IOException, ApiException, DuplicateUidException, CalculationException {
        CartModel cart = getCartForPayPalCheckout(cartGuid);

        Assert.notNull(cart, "No cart found for guid: " + cartGuid);

        setDeliveryAddressForCart(addressData, cart.getCode());

        return patchPaypalOrder(cart, null, paymentData, pspReference);
    }

    @Override
    public PaypalUpdateOrderResponse updateShippingMethod(final String shippingMethodCode, final String pspReference, final String paymentData, final String cartGuid) throws IOException, ApiException, CalculationException {
        CartModel cart = getCartForPayPalCheckout(cartGuid);

        Assert.notNull(cart, "No cart found for guid: " + cartGuid);

        return patchPaypalOrder(cart, shippingMethodCode, paymentData, pspReference);
    }

    @Override
    public PaypalUpdateOrderResponse getPaypalUpdateOrderResponse(PaypalUpdateOrderRequest paypalUpdateOrderOriginRequest) throws IOException, ApiException {
        BaseStoreModel currentBaseStore = getBaseStoreService().getCurrentBaseStore();
        final CartModel sessionCart = cartService.getSessionCart();
        if (sessionCart == null) {
            LOG.warn("No session cart found for the current user.");
            throw new IllegalStateException("Session cart is null and no cartGuid is provided.");
        }

        String selectedDeliveryMethodReference = paypalUpdateOrderOriginRequest.getDeliveryMethods()
                .stream()
                .filter(DeliveryMethod::getSelected)
                .map(DeliveryMethod::getReference)
                .findFirst().orElse(StringUtils.EMPTY);
        List<DeliveryModeModel> deliveryModes = getDeliveryService().getSupportedDeliveryModeListForOrder(sessionCart);

        List<DeliveryMethod> deliveryMethods = deliveryModes.stream()
                .map(deliveryModeModel -> convert(deliveryModeModel, sessionCart))
                .map(deliveryModeData -> getDeliveryMethod(deliveryModeData, selectedDeliveryMethodReference))
                .toList();

        Amount amount = AmountUtil.createAmount(BigDecimal.valueOf(sessionCart.getTotalPrice()), sessionCart.getCurrency().getIsocode());
        PaypalUpdateOrderRequest paypalUpdateOrderRequest = getPaypalUpdateOrderRequest(
                paypalUpdateOrderOriginRequest.getPaymentData(),
                paypalUpdateOrderOriginRequest.getPspReference(),
                amount,
                deliveryMethods
        );

        AdyenUtilityApiService adyenUtilityApiService = adyenPaymentServiceFactory.createAdyenUtilityApiService(currentBaseStore);

        return adyenUtilityApiService.paypalUpdateOrder(paypalUpdateOrderRequest);
    }

    protected CartModel getCartForPayPalCheckout(String cartGuid) {
        if (StringUtils.isNotEmpty(cartGuid)) {
            return getExpressCartForGuid(cartGuid);
        } else {
            CartModel sessionCart = cartService.getSessionCart();
            if (sessionCart == null) {
                LOG.warn("No session cart found for the current user.");
                throw new IllegalStateException("Session cart is null and no cartGuid is provided.");
            }
            return sessionCart;
        }
    }

    protected PaypalUpdateOrderResponse patchPaypalOrder(final CartModel cart, String shippingMethodCode, String paymentData, String pspReference) throws IOException, ApiException, CalculationException {
        BaseStoreModel currentBaseStore = getBaseStoreService().getCurrentBaseStore();
        AdyenUtilityApiService adyenUtilityApiService = adyenPaymentServiceFactory.createAdyenUtilityApiService(currentBaseStore);

        List<DeliveryModeModel> deliveryModes = getDeliveryService().getSupportedDeliveryModeListForOrder(cart);

        Optional<DeliveryModeModel> selectedDeliveryMethod;
        if (StringUtils.isNotEmpty(shippingMethodCode)) {
            selectedDeliveryMethod = deliveryModes.stream().filter(deliveryMode -> deliveryMode.getCode().equals(shippingMethodCode)).findFirst();
        } else {
            selectedDeliveryMethod = !deliveryModes.isEmpty() ? Optional.of(deliveryModes.get(0)) : Optional.empty();
        }

        if (selectedDeliveryMethod.isPresent()) {
            List<DeliveryMethod> deliveryMethods = new ArrayList<>();

            for (DeliveryModeModel methodModel : deliveryModes) {
                DeliveryModeData method = convert(methodModel, cart);
                DeliveryMethod deliveryMethod = getDeliveryMethod(method, selectedDeliveryMethod.get().getCode());
                deliveryMethods.add(deliveryMethod);
            }

            CartData cartData = setDeliveryModeForCart(selectedDeliveryMethod.get(), cart);
            Amount amount = AmountUtil.createAmount(cartData.getTotalPrice().getValue(), cartData.getTotalPrice().getCurrencyIso());
            PaypalUpdateOrderRequest paypalUpdateOrderRequest = getPaypalUpdateOrderRequest(paymentData, pspReference, amount, deliveryMethods);

            return adyenUtilityApiService.paypalUpdateOrder(paypalUpdateOrderRequest);
        }
        throw new IllegalArgumentException("No delivery method found for express checkout cart: " + cart.getCode());
    }

    private void removeDeliveryMethodAndRecalculateCart(CartModel expressCart) {
        expressCart.setDeliveryMode(null);
        getModelService().save(expressCart);
        CommerceCartParameter commerceCartParameter = new CommerceCartParameter();
        commerceCartParameter.setCart(expressCart);
        commerceCartService.calculateCart(commerceCartParameter);
    }

    private static DeliveryMethod getDeliveryMethod(DeliveryModeData method, String selectedDeliveryMethodCode) {
        Amount amount = null;
        if (method != null && method.getDeliveryCost() != null) {
            amount = AmountUtil.createAmount(method.getDeliveryCost().getValue(), method.getDeliveryCost().getCurrencyIso());
        } else {
            LOG.warn("No method delivery cost found for delivery mode: {}", method != null ? method.getCode() : "null");
        }

        DeliveryMethod deliveryMethod = new DeliveryMethod();
        if (method != null) {
            deliveryMethod.reference(method.getCode())
                    .description(method.getDescription())
                    .selected(selectedDeliveryMethodCode.equals(method.getCode()))
                    .setAmount(amount);
        }
        return deliveryMethod;
    }

    private static PaypalUpdateOrderRequest getPaypalUpdateOrderRequest(String paymentData, String pspReference, Amount amount, List<DeliveryMethod> deliveryMethods) {
        PaypalUpdateOrderRequest paypalUpdateOrderRequest = new PaypalUpdateOrderRequest();
        paypalUpdateOrderRequest.amount(amount)
                .deliveryMethods(deliveryMethods)
                .paymentData(paymentData)
                .setPspReference(pspReference);
        return paypalUpdateOrderRequest;
    }

    protected void prepareCartForPayPalExpressCheckout(AddressData addressData, CartModel sessionCart, CustomerModel user, PaymentInfoModel paymentInfoModel) throws CalculationException {
        sessionCart.setUser(user);

        AddressModel addressModel = prepareAddressModel(addressData, user);
        updatePaymentInfoWithCartAndUser(paymentInfoModel, user, addressModel, sessionCart);

        sessionCart.setDeliveryAddress(addressModel);
        sessionCart.setPaymentAddress(addressModel);
        sessionCart.setPaymentInfo(paymentInfoModel);
        getModelService().save(sessionCart);

        calculateCart(sessionCart);
    }

    protected CartModel getExpressCartForGuid(String expressCartGuid) {
        CartModel expressCart = null;
        if (StringUtils.isNotEmpty(expressCartGuid)) {
            UserModel currentUser = userService.getCurrentUser();
            BaseSiteModel currentBaseSite = baseSiteService.getCurrentBaseSite();
            expressCart = commerceCartService.getCartForGuidAndSiteAndUser(expressCartGuid, currentBaseSite, currentUser);
        }
        return expressCart;
    }

    protected DeliveryModeModel getExpressDeliveryMode() {
        DeliveryModeModel deliveryMode = deliveryModeService.getDeliveryModeForCode(DELIVERY_MODE_CODE);
        validateParameterNotNull(deliveryMode, "Delivery mode for Adyen express checkout not configured");
        return deliveryMode;
    }

    public void setBaseSiteService(BaseSiteService baseSiteService) {
        this.baseSiteService = baseSiteService;
    }

    public void setAdyenPaymentServiceFactory(AdyenPaymentServiceFactory adyenPaymentServiceFactory) {
        this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
    }

    @Override
    public void setAdyenShopperIpResolverService(AdyenShopperIpResolverService adyenShopperIpResolverService) {
        this.adyenShopperIpResolverService = adyenShopperIpResolverService;
    }
}
