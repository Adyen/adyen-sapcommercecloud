package com.adyen.commerce.handler;

import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.IdealDetails;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.SepaDirectDebitDetails;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

public class IdealSubscriptionHandler implements SubscriptionPaymentMethodHandler {
    @Override
    public boolean canHandle(String paymentMethod) {
        return Arrays.stream(IdealDetails.TypeEnum.values()).map(IdealDetails.TypeEnum::toString).anyMatch(type -> type.equalsIgnoreCase(paymentMethod));

    }

    @Override
    public void updatePaymentRequest(PaymentRequest paymentRequest, AbstractOrderModel orderModel) {
        if (StringUtils.isNotEmpty(orderModel.getPaymentInfo().getAdyenSelectedReference())) {


            SepaDirectDebitDetails sepaDirectDebitDetails = new SepaDirectDebitDetails();


            sepaDirectDebitDetails.setStoredPaymentMethodId(orderModel.getPaymentInfo().getAdyenSelectedReference());
            CheckoutPaymentMethod checkoutPaymentMethod = new CheckoutPaymentMethod();
            checkoutPaymentMethod.setActualInstance(sepaDirectDebitDetails);
            paymentRequest.setPaymentMethod(checkoutPaymentMethod);

        }
    }
}
