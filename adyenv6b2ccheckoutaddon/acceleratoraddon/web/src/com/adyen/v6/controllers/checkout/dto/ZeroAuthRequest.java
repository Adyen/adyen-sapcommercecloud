package com.adyen.v6.controllers.checkout.dto;

public class ZeroAuthRequest {
	private PaymentMethodDto paymentMethodDto;

	public PaymentMethodDto getPaymentMethodDto() {
		return paymentMethodDto;
	}

	public void setPaymentMethodDto(final PaymentMethodDto paymentMethodDto) {
		this.paymentMethodDto = paymentMethodDto;
	}

	public static class PaymentMethodDto {
		private String type;
		private String encryptedCardNumber;
		private String encryptedExpiryMonth;
		private String encryptedExpiryYear;
		private String encryptedSecurityCode;
		private String holderName;

		public String getType() {
			return type;
		}

		public void setType(final String type) {
			this.type = type;
		}

		public String getEncryptedCardNumber() {
			return encryptedCardNumber;
		}

		public void setEncryptedCardNumber(final String encryptedCardNumber) {
			this.encryptedCardNumber = encryptedCardNumber;
		}

		public String getEncryptedExpiryMonth() {
			return encryptedExpiryMonth;
		}

		public void setEncryptedExpiryMonth(final String encryptedExpiryMonth) {
			this.encryptedExpiryMonth = encryptedExpiryMonth;
		}

		public String getEncryptedExpiryYear() {
			return encryptedExpiryYear;
		}

		public void setEncryptedExpiryYear(final String encryptedExpiryYear) {
			this.encryptedExpiryYear = encryptedExpiryYear;
		}

		public String getEncryptedSecurityCode() {
			return encryptedSecurityCode;
		}

		public void setEncryptedSecurityCode(final String encryptedSecurityCode) {
			this.encryptedSecurityCode = encryptedSecurityCode;
		}

		public String getHolderName() {
			return holderName;
		}

		public void setHolderName(final String holderName) {
			this.holderName = holderName;
		}
	}
}