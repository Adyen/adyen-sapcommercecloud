package com.adyen.v6.controllers.checkout;

import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.v6.controllers.checkout.dto.ZeroAuthRequest;
import org.apache.commons.lang3.StringUtils;

public final class ZeroAuthMapper {

	private ZeroAuthMapper() {}

	public static CheckoutPaymentMethod toCheckoutPaymentMethod(final ZeroAuthRequest req) {

		final ZeroAuthRequest.PaymentMethodDto pm = req.getPaymentMethodDto();

		if (!"scheme".equalsIgnoreCase(pm.getType())) {
			throw new IllegalArgumentException(
					"Unsupported paymentMethodDto.type: " + pm.getType() + " (only 'scheme' is supported)"
			);
		}

		final CardDetails cardDetails = new CardDetails()
				.encryptedCardNumber(pm.getEncryptedCardNumber())
				.encryptedExpiryMonth(pm.getEncryptedExpiryMonth())
				.encryptedExpiryYear(pm.getEncryptedExpiryYear())
				.encryptedSecurityCode(pm.getEncryptedSecurityCode());

		if (StringUtils.isNotBlank(pm.getHolderName())) {
			cardDetails.holderName(pm.getHolderName());
		}

		return new CheckoutPaymentMethod(cardDetails);
	}
}