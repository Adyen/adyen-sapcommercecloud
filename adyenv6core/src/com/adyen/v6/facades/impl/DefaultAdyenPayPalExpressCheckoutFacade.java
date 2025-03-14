package com.adyen.v6.facades.impl;

import com.adyen.model.checkout.*;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.facades.AdyenPayPalExpressCheckoutFacade;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.response.PayPalExpressSubmitResponse;
import com.adyen.v6.service.AdyenUtilityApiService;
import com.adyen.v6.util.AmountUtil;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.DeliveryModeData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commerceservices.customer.DuplicateUidException;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.delivery.DeliveryModeModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.order.CalculationService;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.util.Assert;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.hybris.platform.servicelayer.util.ServicesUtil.validateParameterNotNull;

public class DefaultAdyenPayPalExpressCheckoutFacade extends DefaultAdyenExpressCheckoutFacade implements AdyenPayPalExpressCheckoutFacade {
    private static final Logger LOG = Logger.getLogger(DefaultAdyenPayPalExpressCheckoutFacade.class);

    private CalculationService calculationService;
    private BaseSiteService baseSiteService;
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    @Override
    public PayPalExpressSubmitResponse onPayPalPDPSubmitOCC(PaymentRequest paymentRequest) throws IOException, ApiException {

        UserModel currentUser = userService.getCurrentUser();
        CartModel expressCart = commerceCartService.getCartForCodeAndUser(paymentRequest.getReference(), currentUser);
        Amount amount = AmountUtil.createAmount(BigDecimal.valueOf(expressCart.getTotalPrice()), expressCart.getCurrency().getIsocode());

        paymentRequest.setAmount(amount);

        PaymentResponse paymentResponse = adyenCheckoutFacade.getAdyenPaymentService().sendPaymentRequest(paymentRequest);
        PayPalExpressSubmitResponse payPalExpressSubmitResponse = new PayPalExpressSubmitResponse();
        payPalExpressSubmitResponse.setPaymentResponse(paymentResponse);
        return payPalExpressSubmitResponse;
    }

    @Override
    public PayPalExpressSubmitResponse onPayPalPDPSubmit(PaymentRequest paymentRequest, String productCode) throws IOException, ApiException {
        Assert.isTrue(StringUtils.isNotEmpty(productCode), "Product code must not be empty");

        ProductModel productModel = productService.getProductForCode(productCode);

        CartModel expressCart = cartFactory.createCart();

        expressCart.setDeliveryMode(getExpressDeliveryMode());

        cartService.addNewEntry(expressCart, productModel, 1L, productModel.getUnit());
        getModelService().save(expressCart);

        try {
            calculationService.calculate(expressCart);
        } catch (CalculationException e) {
            LOG.error("Express checkout cart calculation failed");
        }

        Amount amount = AmountUtil.createAmount(BigDecimal.valueOf(expressCart.getTotalPrice()), expressCart.getCurrency().getIsocode());


        paymentRequest.setReference(expressCart.getCode());
        paymentRequest.setAmount(amount);

        PaymentResponse paymentResponse = adyenCheckoutFacade.getAdyenPaymentService().sendPaymentRequest(paymentRequest);

        PayPalExpressSubmitResponse payPalExpressSubmitResponse = new PayPalExpressSubmitResponse();

        payPalExpressSubmitResponse.setPaymentResponse(paymentResponse);
        payPalExpressSubmitResponse.setExpressCartGuid(expressCart.getGuid());
        payPalExpressSubmitResponse.setPspReference(paymentResponse.getPspReference());

        return payPalExpressSubmitResponse;
    }

    @Override
    public PaymentResponse onPayPalCartSubmit(PaymentRequest paymentRequest) throws IOException, ApiException {
        CartModel sessionCart = cartService.getSessionCart();
        Assert.notNull(sessionCart, "Session cart must not be null");

        sessionCart.setDeliveryMode(getExpressDeliveryMode());

        Amount amount = AmountUtil.createAmount(BigDecimal.valueOf(sessionCart.getTotalPrice()), sessionCart.getCurrency().getIsocode());

        paymentRequest.setAmount(amount);
        paymentRequest.setReference(sessionCart.getCode());

        return adyenCheckoutFacade.getAdyenPaymentService().sendPaymentRequest(paymentRequest);
    }

    public void onPayPalAuthorizedPDP(String cartGuid, AddressData addressData, String paymentMethod) throws DuplicateUidException, InvalidCartException, CalculationException {
        validateAddress(addressData);

        updateRegionData(addressData);

        PaymentInfoModel paymentInfoModel = getModelService().create(PaymentInfoModel.class);
        paymentInfoModel.setAdyenPaymentMethod(paymentMethod);

        CustomerModel user = (CustomerModel) userService.getCurrentUser();
        boolean isGuestUser = false;
        if (userService.isAnonymousUser(user)) {
            user = createGuestCustomer(addressData.getEmail());
            isGuestUser = true;
        }

        CartModel expressCartForGuid = getExpressCartForGuid(cartGuid);

        if (expressCartForGuid != null && cartHasEntries(expressCartForGuid)) {
            prepareCartForPayPalExpressCheckout(addressData, expressCartForGuid, user, paymentInfoModel);

            CartModel sessionCart = null;
            if (cartService.hasSessionCart()) {
                sessionCart = cartService.getSessionCart();
            }
            cartService.setSessionCart(expressCartForGuid);

            adyenCheckoutFacade.placePendingOrder();

            if (isGuestUser) {
                sessionService.setAttribute(ANONYMOUS_CHECKOUT_GUID,
                        org.apache.commons.lang.StringUtils.substringBefore(expressCartForGuid.getUser().getUid(), "|"));
                sessionService.setAttribute(ANONYMOUS_CHECKOUT, Boolean.TRUE);
            }

            if (sessionCart != null) {
                cartService.setSessionCart(sessionCart);
            }
            return;

        }
        throw new InvalidCartException("No cart for checkout or empty cart");
    }

    public void onPayPalAuthorizedCart(AddressData addressData, String paymentMethod) throws DuplicateUidException, InvalidCartException, CalculationException {
        validateAddress(addressData);

        PaymentInfoModel paymentInfoModel = getModelService().create(PaymentInfoModel.class);
        paymentInfoModel.setAdyenPaymentMethod(paymentMethod);

        updateRegionData(addressData);

        CustomerModel user = (CustomerModel) userService.getCurrentUser();
        boolean isGuestUser = false;
        if (userService.isAnonymousUser(user)) {
            user = createGuestCustomer(addressData.getEmail());
            cartService.changeCurrentCartUser(user);
            isGuestUser = true;
        }

        CartModel sessionCart = cartService.getSessionCart();

        if (sessionCart != null && cartHasEntries(sessionCart)) {
            prepareCartForPayPalExpressCheckout(addressData, sessionCart, user, paymentInfoModel);

            adyenCheckoutFacade.placePendingOrder();

            if (isGuestUser) {
                sessionService.setAttribute(ANONYMOUS_CHECKOUT_GUID,
                        org.apache.commons.lang.StringUtils.substringBefore(sessionCart.getUser().getUid(), "|"));
                sessionService.setAttribute(ANONYMOUS_CHECKOUT, Boolean.TRUE);
            }
            return;
        }
        throw new InvalidCartException("No cart for checkout or empty cart");
    }

    public PaypalUpdateOrderResponse updateShippingAddress(final AddressData addressData, final String pspReference, final String paymentData, final String cartGuid) throws IOException, ApiException, DuplicateUidException, CalculationException {
        CartModel cart = getCartForPayPalCheckout(cartGuid);

        Assert.notNull(cart, "No cart found for guid: " + cartGuid);

        setDeliveryAddressForCart(addressData, cart.getCode());

        return patchPaypalOrder(cart, null, paymentData, pspReference);
    }

    public PaypalUpdateOrderResponse updateShippingMethod(final String shippingMethodCode, final String pspReference, final String paymentData, final String cartGuid) throws IOException, ApiException, CalculationException {
        CartModel cart = getCartForPayPalCheckout(cartGuid);

        Assert.notNull(cart, "No cart found for guid: " + cartGuid);

        return patchPaypalOrder(cart, shippingMethodCode, paymentData, pspReference);
    }

    public PaypalUpdateOrderResponse getPaypalUpdateOrderResponse(PaypalUpdateOrderRequest paypalUpdateOrderRequest) throws IOException, ApiException {
        BaseStoreModel currentBaseStore = getBaseStoreService().getCurrentBaseStore();
        AdyenUtilityApiService adyenUtilityApiService = adyenPaymentServiceFactory.createAdyenUtilityApiService(currentBaseStore);
        return adyenUtilityApiService.paypalUpdateOrder(paypalUpdateOrderRequest);

    }

    protected CartModel getCartForPayPalCheckout(String cartGuid) {
        if (StringUtils.isNotEmpty(cartGuid)) {
            return getExpressCartForGuid(cartGuid);
        } else {
            return cartService.getSessionCart();
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
                Amount amount = null;
                if (method.getDeliveryCost() != null) {
                    amount = AmountUtil.createAmount(method.getDeliveryCost().getValue(), method.getDeliveryCost().getCurrencyIso());
                } else {
                    LOG.warn("No method delivery cost found for delivery mode: " + method.getCode());
                }

                DeliveryMethod deliveryMethod = new DeliveryMethod();
                deliveryMethod.reference(method.getCode())
                        .description(method.getDescription())
                        .selected(selectedDeliveryMethod.get().getCode().equals(method.getCode()))
                        .setAmount(amount);
                deliveryMethods.add(deliveryMethod);
            }

            CartData cartData = setDeliveryModeForCart(selectedDeliveryMethod.get(), cart);

            Amount amount = AmountUtil.createAmount(cartData.getTotalPrice().getValue(), cartData.getTotalPrice().getCurrencyIso());

            PaypalUpdateOrderRequest paypalUpdateOrderRequest = new PaypalUpdateOrderRequest();
            paypalUpdateOrderRequest.amount(amount)
                    .deliveryMethods(deliveryMethods)
                    .paymentData(paymentData)
                    .setPspReference(pspReference);

            return adyenUtilityApiService.paypalUpdateOrder(paypalUpdateOrderRequest);
        }
        throw new IllegalArgumentException("No delivery method found for express checkout cart:  " + cart.getCode());
    }




    protected void prepareCartForPayPalExpressCheckout(AddressData addressData, CartModel sessionCart, CustomerModel user, PaymentInfoModel paymentInfoModel) throws CalculationException {
        sessionCart.setUser(user);

        AddressModel addressModel = prepareAddressModel(addressData, user);
        updatePaymentInfoWithCartAndUser(paymentInfoModel, user, addressModel, sessionCart);

        sessionCart.setDeliveryAddress(addressModel);
        sessionCart.setPaymentAddress(addressModel);
        sessionCart.setPaymentInfo(paymentInfoModel);
        getModelService().save(sessionCart);

        calculationService.recalculate(sessionCart);
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


    public void setCalculationService(CalculationService calculationService) {
        this.calculationService = calculationService;
    }


    public void setBaseSiteService(BaseSiteService baseSiteService) {
        this.baseSiteService = baseSiteService;
    }

    public void setAdyenPaymentServiceFactory(AdyenPaymentServiceFactory adyenPaymentServiceFactory) {
        this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
    }
}
