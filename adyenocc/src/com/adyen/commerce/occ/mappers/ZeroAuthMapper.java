package com.adyen.commerce.occ.mappers;

import com.adyen.commerce.occ.request.ZeroAuthRequest;
import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import org.apache.commons.lang3.StringUtils;

public final class ZeroAuthMapper {

    public static final String SCHEME = "scheme";

    private ZeroAuthMapper() {}

    public static CheckoutPaymentMethod toCheckoutPaymentMethod(final ZeroAuthRequest req) {

        final ZeroAuthRequest.PaymentMethodDto pm = req.getPaymentMethodDto();

        if (!SCHEME.equalsIgnoreCase(pm.getType())) {
            throw new IllegalArgumentException(
                    "Unsupported paymentMethodDto.type: " + pm.getType() + " (only 'scheme' is supported)"
            );
        }

        final CardDetails cardDetails = new CardDetails()
                .encryptedCardNumber(pm.getEncryptedCardNumber())
                .encryptedExpiryMonth(pm.getEncryptedExpiryMonth())
                .encryptedExpiryYear(pm.getEncryptedExpiryYear())
                .encryptedSecurityCode(pm.getEncryptedSecurityCode())
                .type(CardDetails.TypeEnum.SCHEME);

        if (StringUtils.isNotBlank(pm.getHolderName())) {
            cardDetails.holderName(pm.getHolderName());
        }

        return new CheckoutPaymentMethod(cardDetails);
    }
}
