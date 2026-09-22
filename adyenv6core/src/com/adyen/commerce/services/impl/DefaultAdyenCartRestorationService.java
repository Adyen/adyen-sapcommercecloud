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
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.services.impl;

import com.adyen.commerce.data.PaymentMethodsCartData;
import com.adyen.v6.constants.Adyenv6coreConstants;
import com.adyen.commerce.facades.AdyenOrderFacade;
import com.adyen.v6.repository.OrderRepository;
import com.adyen.v6.service.AdyenBusinessProcessService;
import com.adyen.commerce.services.AdyenCartRestorationService;
import com.adyen.v6.service.ThreeDSAuthorizationService;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.order.CalculationService;
import de.hybris.platform.order.CartFactory;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static de.hybris.platform.order.impl.DefaultCartService.SESSION_CART_PARAMETER_NAME;

/**
 * Default implementation of {@link AdyenCartRestorationService}.
 * Handles locking and restoring the session cart around Adyen payment flows.
 */
public class DefaultAdyenCartRestorationService implements AdyenCartRestorationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAdyenCartRestorationService.class);

    private static final String SESSION_LOCKED_CART = "adyen_cart";
    private static final String SESSION_PENDING_ORDER_CODE = "adyen_pending_order_code";
    private static final String SESSION_PAYMENT_METHODS_CART_DATA = "adyen_payment_methods_cart_data";

    private SessionService sessionService;
    private CartService cartService;
    private CartFactory cartFactory;
    private ModelService modelService;
    private CalculationService calculationService;
    private CheckoutFacade checkoutFacade;
    private CheckoutCustomerStrategy checkoutCustomerStrategy;
    private OrderRepository orderRepository;
    private AdyenOrderFacade adyenOrderFacade;
    private AdyenBusinessProcessService adyenBusinessProcessService;
    private ThreeDSAuthorizationService threeDSAuthorizationService;
    private Populator<AddressModel, AddressData> addressPopulator;

    @Override
    public void lockSessionCart() {
        sessionService.setAttribute(SESSION_LOCKED_CART, cartService.getSessionCart());
        sessionService.removeAttribute(SESSION_CART_PARAMETER_NAME);

        // Refresh session for registered users
        if (!checkoutCustomerStrategy.isAnonymousCheckout()) {
            cartService.getSessionCart();
        }
    }

    @Override
    public CartModel restoreSessionCart() throws InvalidCartException {
        CartModel cartModel = sessionService.getAttribute(SESSION_LOCKED_CART);
        if (cartModel == null) {
            throw new InvalidCartException("Cart does not exist!");
        }

        cartService.setSessionCart(cartModel);
        sessionService.removeAttribute(SESSION_LOCKED_CART);
        threeDSAuthorizationService.clear3DSSessionTokens();

        return cartModel;
    }

    @Override
    public void restoreCartFromOrder(final String orderCode) throws CalculationException, InvalidCartException {
        LOGGER.info("Restoring cart from order");

        final OrderModel orderModel = orderRepository.getOrderModel(orderCode);
        if (orderModel == null) {
            LOGGER.error("Could not restore cart to session, order with code '{}' not found!", orderCode);
            return;
        }

        restoreCartFromOrderInternal(orderModel);
    }

    @Override
    public void restoreCartFromOrderOCC(final String orderCode) throws CalculationException, InvalidCartException {
        LOGGER.info("Restoring cart from order");

        final OrderModel orderModel = adyenOrderFacade.getOrderModelForCodeOCC(orderCode);
        if (orderModel == null) {
            LOGGER.error("Could not restore cart to session, order with code '{}' not found!", orderCode);
            return;
        }

        restoreCartFromOrderInternal(orderModel);
    }

    @Override
    public void restoreCartFromOrderCodeInSession() throws InvalidCartException, CalculationException {
        final String orderCode = sessionService.getAttribute(SESSION_PENDING_ORDER_CODE);
        if (orderCode == null) {
            LOGGER.info("OrderCode not in session, no cart will be restored");
            return;
        }

        final OrderModel orderModel = retrievePendingOrder(orderCode);

        orderModel.setStatus(OrderStatus.PROCESSING_ERROR);
        orderModel.setStatusInfo("AdyenException");
        modelService.save(orderModel);
        adyenBusinessProcessService.triggerOrderProcessEvent(orderModel, Adyenv6coreConstants.PROCESS_EVENT_ADYEN_PAYMENT_RESULT);

        sessionService.removeAttribute(SESSION_PENDING_ORDER_CODE);
        threeDSAuthorizationService.clear3DSSessionTokens();

        restoreCartFromOrder(orderCode);
    }

    protected OrderModel retrievePendingOrder(final String orderCode) throws InvalidCartException {
        if (orderCode == null || orderCode.isEmpty()) {
            throw new InvalidCartException("Could not retrieve pending order: missing orderCode!");
        }

        final OrderModel orderModel = orderRepository.getOrderModel(orderCode);
        if (orderModel == null) {
            throw new InvalidCartException("Order '" + orderCode + "' does not exist!");
        }

        sessionService.removeAttribute(SESSION_PENDING_ORDER_CODE);
        threeDSAuthorizationService.clear3DSSessionTokens();

        return orderModel;
    }

    protected void restoreCartFromOrderInternal(final OrderModel orderModel) throws CalculationException, InvalidCartException {
        CartModel cartModel;
        if (cartService.hasSessionCart()) {
            cartModel = cartService.getSessionCart();
        } else {
            cartModel = cartFactory.createCart();
            cartService.setSessionCart(cartModel);
        }

        final boolean isAnonymousCheckout = checkoutCustomerStrategy.isAnonymousCheckout();

        if (!isAnonymousCheckout && hasUserContextChanged(orderModel, cartModel)) {
            throw new InvalidCartException("Cart from order '" + orderModel.getCode()
                    + "' not restored to session, since user or store in session changed.");
        }

        // Populate cart entries
        for (final AbstractOrderEntryModel entryModel : orderModel.getEntries()) {
            cartService.addNewEntry(cartModel, entryModel.getProduct(), entryModel.getQuantity(), entryModel.getUnit());
        }

        final PaymentMethodsCartData paymentMethodsCartData = sessionService.getAttribute(SESSION_PAYMENT_METHODS_CART_DATA);
        restorePaymentMethodsDataOnCart(paymentMethodsCartData, cartModel);
        sessionService.removeAttribute(SESSION_PAYMENT_METHODS_CART_DATA);

        modelService.save(cartModel);

        if (isAnonymousCheckout) {
            cartModel.setUser(orderModel.getUser());
            cartModel.setDeliveryAddress(orderModel.getDeliveryAddress().getOriginal());
            cartModel.setDeliveryMode(orderModel.getDeliveryMode());
            if (orderModel.getPaymentAddress() != null) {
                cartModel.setPaymentAddress(orderModel.getPaymentAddress().getOriginal());
            }
            modelService.save(cartModel);
        } else {
            // Populate delivery address and mode
            final AddressData deliveryAddressData = new AddressData();
            addressPopulator.populate(orderModel.getDeliveryAddress().getOriginal(), deliveryAddressData);
            checkoutFacade.setDeliveryAddress(deliveryAddressData);
            checkoutFacade.setDeliveryMode(orderModel.getDeliveryMode().getCode());
        }

        calculationService.calculate(cartModel);
    }

    protected boolean hasUserContextChanged(final OrderModel orderModel, final CartModel cartModel) {
        return !orderModel.getUser().equals(cartModel.getUser())
                || !orderModel.getStore().equals(cartModel.getStore());
    }

    protected void restorePaymentMethodsDataOnCart(final PaymentMethodsCartData paymentMethodsCartData, final CartModel cartModel) {
        if (paymentMethodsCartData != null) {
            cartModel.setAdyenDfValue(paymentMethodsCartData.getAdyenDfValue());
            cartModel.setAdyenStoredCards(paymentMethodsCartData.getAdyenStoredCards());
            cartModel.setAdyenApplePayMerchantName(paymentMethodsCartData.getAdyenApplePayMerchantName());
            cartModel.setAdyenAmazonPayConfiguration(paymentMethodsCartData.getAdyenAmazonPayConfiguration());
        } else {
            LOGGER.warn("Empty payment methods cart data in session");
        }
    }

    // --- Getters / Setters ---

    public SessionService getSessionService() {
        return sessionService;
    }

    public void setSessionService(final SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public CartService getCartService() {
        return cartService;
    }

    public void setCartService(final CartService cartService) {
        this.cartService = cartService;
    }

    public CartFactory getCartFactory() {
        return cartFactory;
    }

    public void setCartFactory(final CartFactory cartFactory) {
        this.cartFactory = cartFactory;
    }

    public ModelService getModelService() {
        return modelService;
    }

    public void setModelService(final ModelService modelService) {
        this.modelService = modelService;
    }

    public CalculationService getCalculationService() {
        return calculationService;
    }

    public void setCalculationService(final CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    public CheckoutFacade getCheckoutFacade() {
        return checkoutFacade;
    }

    public void setCheckoutFacade(final CheckoutFacade checkoutFacade) {
        this.checkoutFacade = checkoutFacade;
    }

    public CheckoutCustomerStrategy getCheckoutCustomerStrategy() {
        return checkoutCustomerStrategy;
    }

    public void setCheckoutCustomerStrategy(final CheckoutCustomerStrategy checkoutCustomerStrategy) {
        this.checkoutCustomerStrategy = checkoutCustomerStrategy;
    }

    public OrderRepository getOrderRepository() {
        return orderRepository;
    }

    public void setOrderRepository(final OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public AdyenOrderFacade getAdyenOrderFacade() {
        return adyenOrderFacade;
    }

    public void setAdyenOrderFacade(final AdyenOrderFacade adyenOrderFacade) {
        this.adyenOrderFacade = adyenOrderFacade;
    }

    public AdyenBusinessProcessService getAdyenBusinessProcessService() {
        return adyenBusinessProcessService;
    }

    public void setAdyenBusinessProcessService(final AdyenBusinessProcessService adyenBusinessProcessService) {
        this.adyenBusinessProcessService = adyenBusinessProcessService;
    }

    public ThreeDSAuthorizationService getThreeDSAuthorizationService() {
        return threeDSAuthorizationService;
    }

    public void setThreeDSAuthorizationService(final ThreeDSAuthorizationService threeDSAuthorizationService) {
        this.threeDSAuthorizationService = threeDSAuthorizationService;
    }

    public Populator<AddressModel, AddressData> getAddressPopulator() {
        return addressPopulator;
    }

    public void setAddressPopulator(final Populator<AddressModel, AddressData> addressPopulator) {
        this.addressPopulator = addressPopulator;
    }
}
