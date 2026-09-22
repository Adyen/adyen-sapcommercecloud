package com.adyen.commerce.handler;

import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.KlarnaDetails;
import com.adyen.model.checkout.PaymentRequest;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Objects;

public class KlarnaSubscriptionHandler implements SubscriptionPaymentMethodHandler {
    @Override
    public boolean canHandle(String paymentMethod) {
        return Arrays.stream(KlarnaDetails.TypeEnum.values()).map(KlarnaDetails.TypeEnum::toString).anyMatch(type -> type.equalsIgnoreCase(paymentMethod));
    }

    @Override
    public void updatePaymentRequest(PaymentRequest paymentRequest, AbstractOrderModel orderModel) {
        if (StringUtils.isNotEmpty(orderModel.getPaymentInfo().getAdyenSelectedReference())) {

            KlarnaDetails klarnaDetails;

            if (Objects.isNull(paymentRequest.getPaymentMethod()) || Objects.isNull(paymentRequest.getPaymentMethod().getKlarnaDetails())) {
                klarnaDetails = new KlarnaDetails();
            } else {
                klarnaDetails = paymentRequest.getPaymentMethod().getKlarnaDetails();
            }

            klarnaDetails.setStoredPaymentMethodId(orderModel.getPaymentInfo().getAdyenSelectedReference());
            CheckoutPaymentMethod checkoutPaymentMethod = new CheckoutPaymentMethod();
            checkoutPaymentMethod.setActualInstance(klarnaDetails);
            paymentRequest.setPaymentMethod(checkoutPaymentMethod);
        }
    }
}
