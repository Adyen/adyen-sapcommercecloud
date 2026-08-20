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
 * <p>
 * There are two ways in. {@link #applyRecurringContract} answers "may this payment leave a token behind",
 * which is a store configuration question and is asked by the payment method handlers.
 * {@link #applySubscriptionContract} answers "this payment <em>has</em> to leave one", which is a question
 * about what is in the cart and is asked by a decorator once the cart is known to fund a subscription.
 * Both write the same fields through this class rather than by hand, so the two never drift apart.
 */
public class RecurringContractHelper {

    /**
     * Message key for a payment method that cannot carry the contract the cart needs. An existing
     * "pick a different method" key rather than a new one: it is localised in
     * {@code adyenv6b2ccheckoutaddon-locales_en.properties} and in the checkout API addon's
     * {@code base_en.properties}. Spartacus resolves its own bundle, which ships in the Angular library, so
     * an OCC storefront has nothing to resolve the code against until the key is added there too.
     * <p>
     * Declared in this extension rather than in a web extension's constants class because everything that
     * raises it and everything that presents it depends on {@code adyenv6core}, while {@code adyenv6core}
     * depends on none of them - a copy over there could not be referenced from here.
     */
    public static final String PAYMENT_METHOD_NOT_SUPPORTED = "checkout.error.payment.not.supported";

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
     * Declares that this payment funds a subscription: the token has to survive checkout no matter what the
     * store's recurring contract configuration says, and Adyen has to be told the renewals are scheduled
     * rather than shopper-initiated.
     * <p>
     * This is the "knows better" case {@link #applyCardOnFileDefaults} defers to, which is why it is the one
     * place allowed to overwrite a processing model a handler already picked: the handler chose it while the
     * payment still looked like an ordinary one-off, the caller of this method knows it is not.
     * <p>
     * What it does <em>not</em> overrule is the rule that a token being reused cannot be stored again - the
     * reference already exists, and asking Adyen to mint a second one for the same card is how a shopper ends
     * up with duplicate stored cards. A subscription paid with a saved card therefore goes out with
     * {@code storePaymentMethod=false} and still declares the subscription contract.
     *
     * @param paymentRequest the request under construction; {@code null} is tolerated and does nothing
     */
    public static void applySubscriptionContract(final PaymentRequest paymentRequest) {
        if (paymentRequest == null) {
            return;
        }

        // Requests assembled by older storefront handlers can still carry the deprecated flags, and the
        // Checkout API rejects one that carries storePaymentMethod next to either of them (140_405).
        paymentRequest.setEnableRecurring(null);
        paymentRequest.setEnableOneClick(null);

        paymentRequest.setStorePaymentMethod(!isStoredPaymentMethodReused(paymentRequest));
        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
        if (paymentRequest.getShopperInteraction() == null) {
            paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);
        }
    }

    /**
     * Fills in the card-on-file contract only where nothing has spoken for it yet - a caller that has
     * already declared, say, a SUBSCRIPTION model knows better than this default. A caller that only learns
     * it knows better <em>after</em> the handlers have run says so through
     * {@link #applySubscriptionContract} instead of writing the fields itself.
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

    /**
     * Whether this request pays with a token that already exists, rather than creating one. Public because
     * it also decides, one extension out, whether a saved payment method is a saved <em>card</em>: the two
     * readings must not drift, or a payment admitted as tokenizable would go out asking to store a method
     * that is already stored.
     */
    public static boolean isStoredPaymentMethodReused(final PaymentRequest paymentRequest) {
        final CheckoutPaymentMethod checkoutPaymentMethod = paymentRequest == null ? null : paymentRequest.getPaymentMethod();
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

    /**
     * The cart can only be fulfilled by charging the shopper again later, and the payment method they picked
     * cannot leave a reusable token behind.
     * <p>
     * Unchecked because the only place that can detect this is
     * {@code AdyenPaymentRequestDecorator#decoratePaymentRequest}, which declares no checked exception. It
     * carries a message key rather than a sentence, following {@code PaymentRequestValidator} and the rest of
     * the pre-authorization validation in this codebase: a shopper who picked Klarna for a subscription has to
     * be told to pick a card, and an exception whose only content is an English sentence gets flattened into
     * "internal technical error" by every layer above.
     * <p>
     * It lives here, next to the class that owns the tokenization decision, because it has to be visible to
     * both the extension that raises it and the web extensions that present it, and this is the one place
     * both can see.
     */
    public static class TokenizationNotSupportedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String errorCode;

        public TokenizationNotSupportedException(final String message) {
            this(message, PAYMENT_METHOD_NOT_SUPPORTED);
        }

        public TokenizationNotSupportedException(final String message, final String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        /**
         * @return the message key the checkout layer should resolve and show, never {@code null}
         */
        public String getErrorCode() {
            return errorCode;
        }
    }
}
