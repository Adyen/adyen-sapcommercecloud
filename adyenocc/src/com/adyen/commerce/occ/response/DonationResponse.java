package com.adyen.commerce.occ.response;

import com.adyen.model.checkout.DonationPaymentResponse;
import com.adyen.model.checkout.PaymentResponse;

public class DonationResponse {
    private String id;
    private String status;
    private String donationAccount;
    private String merchantAccount;
    private String reference;
    private Amount amount;
    private Payment payment;

    public static DonationResponse from(final DonationPaymentResponse source) {
        final DonationResponse target = new DonationResponse();
        target.id = source.getId();
        target.status = source.getStatus() == null ? null : source.getStatus().getValue();
        target.donationAccount = source.getDonationAccount();
        target.merchantAccount = source.getMerchantAccount();
        target.reference = source.getReference();
        if (source.getAmount() != null) target.amount = new Amount(source.getAmount().getCurrency(), source.getAmount().getValue());
        if (source.getPayment() != null) target.payment = Payment.from(source.getPayment());
        return target;
    }
    public String getId() { return id; }
    public String getStatus() { return status; }
    public String getDonationAccount() { return donationAccount; }
    public String getMerchantAccount() { return merchantAccount; }
    public String getReference() { return reference; }
    public Amount getAmount() { return amount; }
    public Payment getPayment() { return payment; }
    public void setPayment(final Payment payment) { this.payment = payment; }
    public static class Amount {
        private String currency;
        private Long value;
        public Amount() { }
        public Amount(final String currency, final Long value) { this.currency = currency; this.value = value; }
        public String getCurrency() { return currency; }
        public void setCurrency(final String currency) { this.currency = currency; }
        public Long getValue() { return value; }
        public void setValue(final Long value) { this.value = value; }
    }

    public static class Payment {
        private String pspReference;
        private String resultCode;
        private Amount amount;
        private String merchantReference;
        public Payment() { }
        static Payment from(final PaymentResponse source) {
            final Payment target = new Payment();
            target.pspReference = source.getPspReference();
            target.resultCode = source.getResultCode() == null ? null : source.getResultCode().getValue();
            target.merchantReference = source.getMerchantReference();
            if (source.getAmount() != null) target.amount = new Amount(source.getAmount().getCurrency(), source.getAmount().getValue());
            return target;
        }
        public String getPspReference() { return pspReference; }
        public void setPspReference(final String pspReference) { this.pspReference = pspReference; }
        public String getResultCode() { return resultCode; }
        public void setResultCode(final String resultCode) { this.resultCode = resultCode; }
        public Amount getAmount() { return amount; }
        public void setAmount(final Amount amount) { this.amount = amount; }
        public String getMerchantReference() { return merchantReference; }
        public void setMerchantReference(final String merchantReference) { this.merchantReference = merchantReference; }
    }
}
