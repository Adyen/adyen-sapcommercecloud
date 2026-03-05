package com.adyen.v6.controllers.checkout;

import com.adyen.model.checkout.CardDetails;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.v6.controllers.checkout.dto.ZeroAuthRequest;
import org.apache.commons.lang3.StringUtils;

public final class ZeroAuthMapper {

	private ZeroAuthMapper() {}

	public static CheckoutPaymentMethod toCheckoutPaymentMethod(final ZeroAuthRequest req) {
		if (req == null) {
			throw new IllegalArgumentException("request is missing");
		}
		if (req.getPaymentMethodDto() == null) {
			throw new IllegalArgumentException("paymentMethodDto is missing");
		}

		final ZeroAuthRequest.PaymentMethodDto pm = req.getPaymentMethodDto();

		final String type = StringUtils.trimToEmpty(pm.getType());
		if (StringUtils.isBlank(type)) {
			throw new IllegalArgumentException("paymentMethodDto.type is missing");
		}
		if (!"scheme".equalsIgnoreCase(type)) {
			throw new IllegalArgumentException("Unsupported paymentMethodDto.type: " + type + " (only 'scheme' is supported)");
		}

		requireNotBlank(pm.getEncryptedCardNumber(), "paymentMethodDto.encryptedCardNumber");
		requireNotBlank(pm.getEncryptedExpiryMonth(), "paymentMethodDto.encryptedExpiryMonth");
		requireNotBlank(pm.getEncryptedExpiryYear(), "paymentMethodDto.encryptedExpiryYear");
		requireNotBlank(pm.getEncryptedSecurityCode(), "paymentMethodDto.encryptedSecurityCode");

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

	private static void requireNotBlank(final String value, final String fieldName) {
		if (StringUtils.isBlank(value)) {
			throw new IllegalArgumentException(fieldName + " is missing");
		}
	}
}