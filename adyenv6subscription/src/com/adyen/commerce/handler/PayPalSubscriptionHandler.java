package com.adyen.commerce.handler;

import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PayPalDetails;
import com.adyen.model.checkout.PaymentRequest;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Objects;

public class PayPalSubscriptionHandler implements SubscriptionPaymentMethodHandler {
    @Override
    public boolean canHandle(String paymentMethod) {
        return Arrays.stream(PayPalDetails.TypeEnum.values()).map(PayPalDetails.TypeEnum::toString).anyMatch(type -> type.equalsIgnoreCase(paymentMethod));
    }

    @Override
    public void updatePaymentRequest(PaymentRequest paymentRequest, AbstractOrderModel orderModel) {
        if (StringUtils.isNotEmpty(orderModel.getPaymentInfo().getAdyenSelectedReference())) {

            PayPalDetails payPalDetails;

            if (Objects.isNull(paymentRequest.getPaymentMethod()) || Objects.isNull(paymentRequest.getPaymentMethod().getPayPalDetails())) {
                payPalDetails = new PayPalDetails();
            } else {
                payPalDetails = paymentRequest.getPaymentMethod().getPayPalDetails();
            }

            payPalDetails.setStoredPaymentMethodId(orderModel.getPaymentInfo().getAdyenSelectedReference());
            CheckoutPaymentMethod checkoutPaymentMethod = new CheckoutPaymentMethod();
            checkoutPaymentMethod.setActualInstance(payPalDetails);
            paymentRequest.setPaymentMethod(checkoutPaymentMethod);
        }
    }
}
