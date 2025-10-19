package com.adyen.v6.service;

import com.adyen.model.checkout.Amount;
import com.adyen.v6.model.AdyenPartialPaymentOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;

import java.math.BigDecimal;

/**
 * Service for handling Adyen partial payments with gift cards
 */
public interface AdyenPartialPaymentService {

    /**
     * Check gift card balance
     *
     * @param giftCardNumber The gift card number
     * @param giftCardPin The gift card PIN
     * @param giftCardBrand The gift card brand
     * @param giftCardType The gift card type
     * @param amount The amount to check
     * @return BalanceCheckResult containing balance and transaction limit
     */
    BalanceCheckResult checkGiftCardBalance(
            String giftCardNumber,
            String giftCardPin,
            String giftCardBrand,
            String giftCardType,
            Amount amount
    );

    /**
     * Create partial payment order in Adyen
     *
     * @param amount The amount for the order
     * @param currency The currency code
     * @return OrderCreationResult containing order data and PSP reference
     */
    OrderCreationResult createPartialPaymentOrder(Amount amount, String currency);

    /**
     * Calculate charged amount based on balance and request
     *
     * @param requestAmount The requested amount
     * @param availableBalance The available balance on gift card
     * @param transactionLimit The transaction limit
     * @return The actual amount that can be charged
     */
    BigDecimal calculateChargedAmount(BigDecimal requestAmount, BigDecimal availableBalance, BigDecimal transactionLimit);

    /**
     * Update partial payment order status
     *
     * @param partialPaymentOrder The partial payment order to update
     * @param newStatus The new status
     */
    void updatePartialPaymentStatus(AdyenPartialPaymentOrderModel partialPaymentOrder, String newStatus);

    /**
     * Find partial payment order by PSP reference
     *
     * @param pspReference The PSP reference to search for
     * @return AdyenPartialPaymentOrderModel if found, null otherwise
     */
    AdyenPartialPaymentOrderModel findPartialPaymentOrderByPspReference(String pspReference);

    /**
     * Result class for balance check
     */
    class BalanceCheckResult {
        private final Amount balance;
        private final Amount transactionLimit;
        private final boolean success;
        private final String errorMessage;

        public BalanceCheckResult(Amount balance, Amount transactionLimit, boolean success, String errorMessage) {
            this.balance = balance;
            this.transactionLimit = transactionLimit;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public Amount getBalance() { return balance; }
        public Amount getTransactionLimit() { return transactionLimit; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Result class for order creation
     */
    class OrderCreationResult {
        private final String orderData;
        private final String pspReference;
        private final boolean success;
        private final String errorMessage;

        public OrderCreationResult(String orderData, String pspReference, boolean success, String errorMessage) {
            this.orderData = orderData;
            this.pspReference = pspReference;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public String getOrderData() { return orderData; }
        public String getPspReference() { return pspReference; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
}