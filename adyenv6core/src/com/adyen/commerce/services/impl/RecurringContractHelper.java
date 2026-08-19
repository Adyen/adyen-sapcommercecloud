package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.v6.enums.RecurringContractMode;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.user.CustomerModel;
import org.apache.commons.lang3.StringUtils;

/**
 * Single place that decides whether a card payment should leave a token behind, and expresses that
 * decision on the outgoing payment request.
 * <p>
 * The decision is carried exclusively by {@code storePaymentMethod}, complemented by
 * {@code recurringProcessingModel} and {@code shopperInteraction}. The deprecated {@code enableRecurring}
 * and {@code enableOneClick} flags are never set: the Checkout API refuses a request that carries
 * {@code storePaymentMethod} next to either of them.
 */
public class RecurringContractHelper {

    private RecurringContractHelper() {
        // utility class
    }

    /**
     * Applies the store's recurring contract configuration to a request that carries card details.
     * Safe to call more than once; the outcome only depends on the arguments.
     */
    public static void applyRecurringContract(final PaymentRequest paymentRequest,
                                              final CartData cartData,
                                              final RecurringContractMode recurringContractMode,
                                              final CustomerModel customerModel,
                                              final Boolean guestUserTokenizationEnabled) {
        if (paymentRequest == null) {
            return;
        }

        final boolean storePaymentMethod = shouldStorePaymentMethod(paymentRequest, cartData,
                recurringContractMode, customerModel, guestUserTokenizationEnabled);

        paymentRequest.setStorePaymentMethod(storePaymentMethod);

        // Storing a token and paying with one are both card-on-file transactions and have to say so.
        if (storePaymentMethod || isStoredPaymentMethodReused(paymentRequest)) {
            applyCardOnFileDefaults(paymentRequest);
        }
    }

    /**
     * Fills in the card-on-file contract only where nothing has spoken for it yet - a decorator that has
     * already declared, say, a SUBSCRIPTION model knows better than this default.
     */
    static void applyCardOnFileDefaults(final PaymentRequest paymentRequest) {
        if (paymentRequest.getRecurringProcessingModel() == null) {
            paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.CARDONFILE);
        }
        if (paymentRequest.getShopperInteraction() == null) {
            paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);
        }
    }

    /**
     * Resolves the single truth behind {@code storePaymentMethod} for a card payment.
     */
    public static boolean shouldStorePaymentMethod(final PaymentRequest paymentRequest,
                                                   final CartData cartData,
                                                   final RecurringContractMode recurringContractMode,
                                                   final CustomerModel customerModel,
                                                   final Boolean guestUserTokenizationEnabled) {
        if (recurringContractMode == null || RecurringContractMode.NONE.equals(recurringContractMode)) {
            return false;
        }

        // A token that is being reused cannot be stored again - the reference already exists.
        if (isStoredPaymentMethodReused(paymentRequest)) {
            return false;
        }

        // A guest is never shown the consent checkbox, so storing for one is opt-in on the base store.
        if (isGuest(customerModel) && !Boolean.TRUE.equals(guestUserTokenizationEnabled)) {
            return false;
        }

        return isTokenizationMandatory(recurringContractMode)
                || (isTokenizationOptional(recurringContractMode) && isShopperConsentGiven(paymentRequest, cartData));
    }

    /**
     * Modes that store a token no matter what the shopper ticked - the store needs it to be able to
     * charge the shopper again later.
     */
    static boolean isTokenizationMandatory(final RecurringContractMode recurringContractMode) {
        return RecurringContractMode.RECURRING.equals(recurringContractMode)
                || RecurringContractMode.ONECLICK_RECURRING.equals(recurringContractMode);
    }

    /**
     * Modes that store a token only when the shopper asked for their card to be remembered.
     */
    static boolean isTokenizationOptional(final RecurringContractMode recurringContractMode) {
        return RecurringContractMode.ONECLICK.equals(recurringContractMode)
                || RecurringContractMode.ONECLICK_RECURRING.equals(recurringContractMode);
    }

    /**
     * The consent reaches the backend over two different routes: the component sends
     * {@code storePaymentMethod} on the payment request, the JSP form persists it on the payment info
     * and it arrives on the cart. Either one counts.
     */
    static boolean isShopperConsentGiven(final PaymentRequest paymentRequest, final CartData cartData) {
        return Boolean.TRUE.equals(paymentRequest.getStorePaymentMethod())
                || (cartData != null && Boolean.TRUE.equals(cartData.getAdyenRememberTheseDetails()));
    }

    static boolean isStoredPaymentMethodReused(final PaymentRequest paymentRequest) {
        final CheckoutPaymentMethod checkoutPaymentMethod = paymentRequest.getPaymentMethod();
        if (checkoutPaymentMethod == null) {
            return false;
        }

        if (checkoutPaymentMethod.getActualInstance() instanceof CardDetails cardDetails) {
            return StringUtils.isNotBlank(cardDetails.getStoredPaymentMethodId())
                    || StringUtils.isNotBlank(cardDetails.getRecurringDetailReference());
        }

        return false;
    }

    static boolean isGuest(final CustomerModel customerModel) {
        return customerModel != null && CustomerType.GUEST.equals(customerModel.getType());
    }
}
