package com.adyen.v6.listeners;

import com.adyen.commerce.data.TokenWebhookRequestData;
import com.adyen.v6.events.TokenizationEvent;
import com.adyen.v6.repository.PaymentTransactionRepository;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;
import org.apache.commons.lang.NotImplementedException;

import java.util.Objects;


public class TokenizationWebhookEventListener extends AbstractEventListener<TokenizationEvent> {
    private PaymentTransactionRepository paymentTransactionRepository;

    protected static String TOKEN_CREATED = "recurring.token.created";

    public TokenizationWebhookEventListener() {
        super();
    }

    @Override
    protected void onEvent(TokenizationEvent tokenizationEvent) {

        TokenWebhookRequestData data = tokenizationEvent.getData();
        PaymentTransactionModel transactionModel = paymentTransactionRepository.getTransactionModel(data.getEventId());
        if (Objects.isNull(transactionModel)) {
            throw new IllegalStateException("No PaymentTransactionModel found for eventId(pspReference): " + data.getEventId());
        }

        AbstractOrderModel order = transactionModel.getOrder();

        if (Objects.isNull(order)) {
            throw new IllegalStateException("No Order connected to PaymentTransaction with code: " + data.getEventId());
        }

        crosscheckWithOrder(order, data);

        if (TOKEN_CREATED.equals(data.getEventType())) {
            order.getPaymentInfo().setAdyenSelectedReference(data.getStoredPaymentMethodId());
        } else {
            throw new NotImplementedException("TokenizationWebhookEventListener not implemented for type " + data.getEventType());
        }

    }

    protected void crosscheckWithOrder(final AbstractOrderModel order, final TokenWebhookRequestData tokenWebhookRequestData) {
        boolean validationResult = true;
        validationResult &= order.getPaymentInfo().getUser().getUid().equals(tokenWebhookRequestData.getShopperReference());
        validationResult &= order.getStore().getAdyenMerchantAccount().equals(tokenWebhookRequestData.getMerchantAccount());
        validationResult &= (order.getStore().getAdyenTestMode() && "test".equals(tokenWebhookRequestData.getEnvironment())) ||
                (!order.getStore().getAdyenTestMode() && "live".equals(tokenWebhookRequestData.getEnvironment()));

        if (!validationResult) {
            throw new IllegalArgumentException("Token webhook request is not valid. EventId (pspReference): " + tokenWebhookRequestData.getEventId() +
                    " type: " + tokenWebhookRequestData.getEventType() + " shopperReference: " + tokenWebhookRequestData.getShopperReference() +
                    " createdAt: " + tokenWebhookRequestData.getCreatedAt());
        }

    }


    public void setPaymentTransactionRepository(PaymentTransactionRepository paymentTransactionRepository) {
        this.paymentTransactionRepository = paymentTransactionRepository;
    }
}
