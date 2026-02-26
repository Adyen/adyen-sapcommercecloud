package com.adyen.v6.controllers.checkout;

import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.v6.controllers.checkout.dto.*;

public final class ZeroAuthMapper {

	private ZeroAuthMapper() {}

	public static CheckoutPaymentMethod toCheckoutPaymentMethod(ZeroAuthRequest req) {
		if (req == null || req.getPaymentMethodDto() == null) {
			throw new IllegalArgumentException("paymentMethod is missing");
		}

		ZeroAuthRequest.PaymentMethodDto pm = req.getPaymentMethodDto();

		CardDetails cardDetails = new CardDetails()
				.encryptedCardNumber(pm.getEncryptedCardNumber())
				.encryptedExpiryMonth(pm.getEncryptedExpiryMonth())
				.encryptedExpiryYear(pm.getEncryptedExpiryYear())
				.encryptedSecurityCode(pm.getEncryptedSecurityCode())
				.holderName(pm.getHolderName()).type(CardDetails.TypeEnum.valueOf(pm.getType()));

		return new CheckoutPaymentMethod(cardDetails);
	}
}
