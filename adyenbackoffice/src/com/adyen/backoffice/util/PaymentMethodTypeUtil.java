package com.adyen.backoffice.util;

public class PaymentMethodTypeUtil {
    
    public static final String APPLE_PAY = "applepay";
    public static final String GOOGLE_PAY = "googlepay";
    public static final String PAYPAL = "paypal";
    public static final String SCHEME = "scheme"; // Credit/Debit cards
    public static final String VISA = "visa";
    public static final String MASTERCARD = "mc";
    public static final String AMERICAN_EXPRESS = "amex";
    public static final String KLARNA = "klarna";
    public static final String JCB = "jcb";
    public static final String SEPA_DIRECT_DEBIT = "sepadirectdebit";
    public static final String IDEAL = "ideal";
    public static final String MAESTRO = "maestro";
    public static final String DINERS = "diners";
    public static final String DISCOVER = "discover";
    public static final String GIROCARD = "girocard";
    public static final String BCMC = "bcmc";
    public static final String ACCEL = "accel";
    public static final String STAR = "star";
    public static final String PULSE = "pulse";
    public static final String NYCE = "nyce";
    
    /**
     * Checks if the payment method type is Apple Pay
     */
    public static boolean isApplePay(String type) {
        return APPLE_PAY.equalsIgnoreCase(type);
    }
    
    /**
     * Checks if the payment method type is Google Pay
     */
    public static boolean isGooglePay(String type) {
        return GOOGLE_PAY.equalsIgnoreCase(type);
    }
    
    /**
     * Checks if the payment method type is PayPal
     */
    public static boolean isPayPal(String type) {
        return PAYPAL.equalsIgnoreCase(type);
    }
    
    /**
     * Checks if the payment method type is Klarna
     */
    public static boolean isKlarna(String type) {
        return KLARNA.equalsIgnoreCase(type);
    }
    
    /**
     * Checks if the payment method type is JCB
     */
    public static boolean isJcb(String type) {
        return JCB.equalsIgnoreCase(type);
    }
    
    /**
     * Checks if the payment method type is SEPA Direct Debit
     */
    public static boolean isSepaDirectDebit(String type) {
        return SEPA_DIRECT_DEBIT.equalsIgnoreCase(type);
    }
    
    /**
     * Checks if the payment method type is a card-based payment method
     */
    public static boolean isCardPayment(String type) {
        return SCHEME.equalsIgnoreCase(type) ||
               VISA.equalsIgnoreCase(type) ||
               MASTERCARD.equalsIgnoreCase(type) ||
               AMERICAN_EXPRESS.equalsIgnoreCase(type) ||
               JCB.equalsIgnoreCase(type) ||
               MAESTRO.equalsIgnoreCase(type) ||
               DINERS.equalsIgnoreCase(type) ||
               DISCOVER.equalsIgnoreCase(type) ||
               GIROCARD.equalsIgnoreCase(type);
    }
    
    /**
     * Checks if the payment method type is a US debit network
     */
    public static boolean isUsDebitNetwork(String type) {
        return ACCEL.equalsIgnoreCase(type) ||
               STAR.equalsIgnoreCase(type) ||
               PULSE.equalsIgnoreCase(type) ||
               NYCE.equalsIgnoreCase(type);
    }
}