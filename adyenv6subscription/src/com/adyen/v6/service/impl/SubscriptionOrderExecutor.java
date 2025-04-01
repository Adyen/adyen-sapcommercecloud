package com.adyen.v6.service.impl;

import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.model.RequestInfo;
import com.adyen.v6.service.AdyenTransactionService;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commerceservices.enums.SalesApplication;
import de.hybris.platform.commerceservices.impersonation.ImpersonationService;
import de.hybris.platform.commerceservices.order.CommerceCheckoutService;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.keygenerator.KeyGenerator;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.type.TypeService;
import de.hybris.platform.store.services.BaseStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscriptionOrderExecutor implements ImpersonationService.Executor<OrderModel, Exception> {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionOrderExecutor.class);

    public static final String BEAN_NAME = "subscriptionOrderExecutor";

    private CartService cartService;
    private TypeService typeService;
    private AdyenPaymentServiceFactory adyenPaymentServiceFactory;
    private AdyenTransactionService adyenTransactionService;
    private Converter<CartModel, CartData> cartConverter;
    private CommerceCheckoutService commerceCheckoutService;
    private BaseStoreService baseStoreService;
    private ModelService modelService;
    private KeyGenerator keyGenerator;
    private final AbstractOrderModel subscriptionOrder;

    public SubscriptionOrderExecutor(final AbstractOrderModel subscriptionOrder)
    {
        this.subscriptionOrder = subscriptionOrder;
    }

    public OrderModel execute() throws Exception {
        LOG.info("Executing SubscriptionOrderExecutor for order: {}", subscriptionOrder.getCode());

        final CartModel cart = setupCart();
        LOG.debug("Cart setup completed with code: {}", cart.getCode());

        try {

            final RequestInfo request = new RequestInfo();
            final PaymentResponse paymentResponse = adyenPaymentServiceFactory
                    .createAdyenCheckoutApiService(baseStoreService.getCurrentBaseStore())
                    .authorisePayment(cartConverter.convert(cart), request, (CustomerModel) cart.getUser());


            final PaymentResponse.ResultCodeEnum resultCode = paymentResponse.getResultCode();
            if (PaymentResponse.ResultCodeEnum.AUTHORISED == resultCode) {
                LOG.info("Payment authorised for cart: {}", cart.getCode());
                adyenTransactionService.authorizeOrderModel(cart, cart.getCode(), paymentResponse.getPspReference());
                return placeOrder(cart);
            } else if (PaymentResponse.ResultCodeEnum.RECEIVED == resultCode) {
                LOG.info("Payment received for cart: {}", cart.getCode());
                return placeOrder(cart);
            } else if (PaymentResponse.ResultCodeEnum.PRESENTTOSHOPPER == resultCode) {
                LOG.info("Payment presented to shopper for cart: {}", cart.getCode());
                return placeOrder(cart);
            } else {
                LOG.warn("Unexpected payment result code: {} for cart: {}", resultCode, cart.getCode());
            }
        } finally {
            LOG.debug("Removing cart: {}", cart.getCode());
            modelService.remove(cart);
        }

        LOG.warn("Returning null as OrderModel for order: {}", subscriptionOrder.getCode());
        return null;
    }

    private CartModel setupCart() {
        LOG.debug("Setting up cart for order: {}", subscriptionOrder.getCode());
        final CartModel cart = cartService.clone(typeService.getComposedTypeForClass(CartModel.class),
                typeService.getComposedTypeForClass(CartEntryModel.class), subscriptionOrder, (String) keyGenerator.generate());
        cart.setSubscriptionOrder(Boolean.TRUE);
        modelService.save(cart);
        LOG.debug("Cart setup completed with code: {}", cart.getCode());
        return cart;
    }

    protected OrderModel placeOrder(final CartModel cartModel) throws InvalidCartException {
        LOG.debug("Placing order for cart: {}", cartModel.getCode());
        final CommerceCheckoutParameter parameter = createCommerceCheckoutParameter(cartModel, true);
        parameter.setSalesApplication(SalesApplication.WEB);
        return getCommerceCheckoutService().placeOrder(parameter).getOrder();
    }

    protected CommerceCheckoutParameter createCommerceCheckoutParameter(final CartModel cart, final boolean enableHooks)
    {
        final CommerceCheckoutParameter parameter = new CommerceCheckoutParameter();
        parameter.setEnableHooks(enableHooks);
        parameter.setCart(cart);
        return parameter;
    }


    public void setCartService(CartService cartService) {
        this.cartService = cartService;
    }

    public void setTypeService(TypeService typeService) {
        this.typeService = typeService;
    }

    public AdyenPaymentServiceFactory getAdyenPaymentServiceFactory() {
        return adyenPaymentServiceFactory;
    }

    public void setAdyenPaymentServiceFactory(AdyenPaymentServiceFactory adyenPaymentServiceFactory) {
        this.adyenPaymentServiceFactory = adyenPaymentServiceFactory;
    }

    public void setAdyenTransactionService(AdyenTransactionService adyenTransactionService) {
        this.adyenTransactionService = adyenTransactionService;
    }

    public void setCartConverter(Converter<CartModel, CartData> cartConverter) {
        this.cartConverter = cartConverter;
    }

    public CommerceCheckoutService getCommerceCheckoutService() {
        return commerceCheckoutService;
    }

    public void setCommerceCheckoutService(CommerceCheckoutService commerceCheckoutService) {
        this.commerceCheckoutService = commerceCheckoutService;
    }
    public BaseStoreService getBaseStoreService() {
        return baseStoreService;
    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }

    public void setModelService(ModelService modelService) {
        this.modelService = modelService;
    }

    public void setKeyGenerator(KeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;
    }

}
