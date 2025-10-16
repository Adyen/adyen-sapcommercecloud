package com.adyen.commerce.services.impl;

import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.IdealDetails;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.SepaDirectDebitDetails;
import com.adyen.v6.enums.RecurringContractMode;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.user.CustomerModel;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

public class IdealSubscriptionHandler implements PaymentMethodHandler {
    @Override
    public boolean canHandle(String paymentMethod) {
        return Arrays.stream(IdealDetails.TypeEnum.values()).map(IdealDetails.TypeEnum::toString).anyMatch(type -> type.equalsIgnoreCase(paymentMethod));

    }

    @Override
    public void updatePaymentRequest(PaymentRequest paymentRequest, CartData cartData, RecurringContractMode recurringContractMode, CustomerModel customerModel, Boolean is3DS2Allowed, Boolean guestUserTokenizationEnabled) {
        if (StringUtils.isNotEmpty(cartData.getAdyenSelectedReference())) {


            SepaDirectDebitDetails sepaDirectDebitDetails = new SepaDirectDebitDetails();


            sepaDirectDebitDetails.setStoredPaymentMethodId(cartData.getAdyenSelectedReference());
            CheckoutPaymentMethod checkoutPaymentMethod = new CheckoutPaymentMethod();
            checkoutPaymentMethod.setActualInstance(sepaDirectDebitDetails);
            paymentRequest.setPaymentMethod(checkoutPaymentMethod);

            paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);

            paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.CONTAUTH);
        }
    }
}
