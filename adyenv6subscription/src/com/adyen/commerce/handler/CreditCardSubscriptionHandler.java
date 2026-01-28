package com.adyen.commerce.handler;

import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Objects;

import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_METHOD_CC;

public class CreditCardSubscriptionHandler implements SubscriptionPaymentMethodHandler {
    @Override
    public boolean canHandle(String paymentMethod) {
        return Arrays.stream(CardDetails.TypeEnum.values()).map(CardDetails.TypeEnum::toString).anyMatch(type -> type.equalsIgnoreCase(paymentMethod)) || PAYMENT_METHOD_CC.equals(paymentMethod);
    }

    @Override
    public void updatePaymentRequest(PaymentRequest paymentRequest, AbstractOrderModel orderModel) {
        if (StringUtils.isNotEmpty(orderModel.getPaymentInfo().getAdyenSelectedReference())) {

            CardDetails cardDetails;

            if (Objects.isNull(paymentRequest.getPaymentMethod()) || Objects.isNull(paymentRequest.getPaymentMethod().getCardDetails())) {
                cardDetails = new CardDetails();
            } else {
                cardDetails = paymentRequest.getPaymentMethod().getCardDetails();
            }

            cardDetails.setStoredPaymentMethodId(orderModel.getPaymentInfo().getAdyenSelectedReference());
            CheckoutPaymentMethod checkoutPaymentMethod = new CheckoutPaymentMethod();
            checkoutPaymentMethod.setActualInstance(cardDetails);
            paymentRequest.setPaymentMethod(checkoutPaymentMethod);
        }
    }
}
