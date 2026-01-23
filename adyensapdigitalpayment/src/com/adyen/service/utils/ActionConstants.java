package com.adyen.service.utils;

public class ActionConstants {
	public static final String RAW_DATA = "Raw data: %s \n";
	public static final String DPA_SUCCESS_RESULT = "01";
	public static final String REGISTER_CARD = "Register Card";
	public static final String REGISTER_CAPTURE = "Register Capture";
	public static final String RETRIEVE_AUTH_WITH_DPA = "Retrieve Auth With DPA";
	public static final String REGISTER_AUTHORIZATION = "Register Authorization";

	private ActionConstants() {

	}

	public static final String AUTH_INFO_TEMPLATE = """
            --- AUTHORIZATION DETAILS ---
            Authorization Amount: %s
            Authorization Currency: %s
            Authorization Date: %s
            Authorization Expiration Date: %s
            Authorization DPA: %s
            Authorization PSP: %s
            Authorization Status: %s
            DigitalPaymentTransaction ID: %s
            DigitalPaymentTransaction status: %s
            DigitalPaymentTransaction description: %s
            """;

	public static final String CARD_INFO_TEMPLATE = """
            --- CARD DETAILS ---
            Payment Service Provider: %s
            DigitalPaymentTransaction ID: %s
            DigitalPaymentTransaction status: %s
            DigitalPaymentTransaction description: %s
            Card token by payment service provider: %s
            """;

	public static final String CAPTURE_INFO_TEMPLATE = """
            --- CAPTURE DETAILS ---
            MerchantAccount: %s
            Payment Service Provider: %s
            PaymentByPaymentServicePrvdr: %s
            DigitalPaymentTransaction ID: %s
            DigitalPaymentTransaction status: %s
            DigitalPaymentTransaction description: %s
            """;
}