package com.adyen.sapdigitalpaymentbackoffice.widgets.actions.utils;

public class ActionConstants {
	public static final String PAYMENT_SERVICE_PROVIDER = "Payment Service Provider: %s \n";
	public static final String RAW_DATA = "Raw data: %s \n";
	public static final String DPA_SUCCESS_RESULT = "01";
	public static final String ERROR_DETAILS = "Error Details";
	public static final String DIGITAL_PAYMENT_TRANSACTION = "Digital payment transaction: %s \n";
	public static final String DIGITAL_PAYT_TRANSACTION_RESULT = "Digital payt transaction result: %s \n";
	public static final String DIGITAL_PAYT_TRANS_RSLT_DESC = "DigitalPaytTransRsltDesc : %s \n";
	public static final String PAYT_CARD_BY_PAYT_SERVICE_PROVIDER = "PaytCardByPaytServiceProvider: %s \n";

	public static final String REGISTER_AUTHORIZATION = "Register Authorization";
	public static final String RESULT_MODEL_IS_EMPTY = "Result model is empty.";
	public static final String RETRIVED_CARD_DETAILS = "Retrived Card Details";
	public static final String RECEIVED_AUTHORIZATION_DETAILS = "Received Authorization Details";
	public static final String AUTHORIZATION_TRANSACTION_NOT_FOUND = "AUTHORIZATION transaction not found.";
	public static final String CARD_REGISTERED_SUCCESSFULLY_IN_DPA = "Card registered successfully in DPA.";
	public static final String CARD_AUTHORIZATION_REGISTERED_SUCCESSFULLY_IN_DPA = "Card authorization registered successfully in DPA.";
	public static final String AUTHORIZATION_DOESN_T_EXIST = "Authorization doesn't exist.";
	public static final String CARD_DOESN_T_EXIST = "Card doesn't exist.";
	public static final String CAPTURE_REGISTERED_SUCCESSFULLY_IN_DPA = "Capture registered successfully in DPA.";
	public static final String NO_CAPTURE_TRANSACTION = "No Capture Transaction";
	public static final String RETRIEVE_CARD_WITH_DPA = "RetrieveCardWithDPA";

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