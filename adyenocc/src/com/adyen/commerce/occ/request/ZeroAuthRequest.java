package com.adyen.commerce.occ.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import javax.validation.Valid;

public class ZeroAuthRequest {
    @Valid
    @NotNull(message = "paymentMethodDto is missing")
    private PaymentMethodDto paymentMethodDto;

    public PaymentMethodDto getPaymentMethodDto() {
        return paymentMethodDto;
    }

    public void setPaymentMethodDto(final PaymentMethodDto paymentMethodDto) {
        this.paymentMethodDto = paymentMethodDto;
    }

    public static class PaymentMethodDto {

        @NotBlank(message = "paymentMethodDto.type is missing")
        private String type;

        @NotBlank(message = "paymentMethodDto.encryptedCardNumber is missing")
        private String encryptedCardNumber;

        @NotBlank(message = "paymentMethodDto.encryptedExpiryMonth is missing")
        private String encryptedExpiryMonth;

        @NotBlank(message = "paymentMethodDto.encryptedExpiryYear is missing")
        private String encryptedExpiryYear;

        @NotBlank(message = "paymentMethodDto.encryptedSecurityCode is missing")
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
