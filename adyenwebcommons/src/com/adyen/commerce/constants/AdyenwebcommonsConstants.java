/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.commerce.constants;

/**
 * Global class for all Adyenwebcommons constants. You can add global constants for your extension into this class.
 */
public final class AdyenwebcommonsConstants extends GeneratedAdyenwebcommonsConstants
{
	public static final String EXTENSIONNAME = "adyenwebcommons";
	public static final String REDIRECT_PREFIX = "redirect:";

	public static final String CHECKOUT_ERROR_AUTHORIZATION_FAILED = "checkout.error.authorization.failed";
	public static final String CHECKOUT_ERROR_FORM_ENTRY_INVALID = "checkout.error.paymentethod.formentry.invalid";

	public static final String ADYEN_CHECKOUT_API_PREFIX = "/api/checkout";
	public static final String AUTHORISE_3D_SECURE_PAYMENT_URL = "/authorise-3d-adyen-response";

	// 3DS Flow Constants
	public static final String REDIRECT_RESULT_PARAM = "redirectResult";
	public static final String PAYLOAD_PARAM = "payload";
	public static final String ACTION_PARAM = "action";
	public static final String RESULT_CODE_PARAM = "resultCode";
	public static final String MERCHANT_REFERENCE_PARAM = "merchantReference";
	public static final String IS_RESULT_ERROR_PARAM = "isResultError";

	// Error Messages
	public static final String CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_REFUSED = "checkout.error.authorization.payment.refused";
	public static final String CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_CANCELLED = "checkout.error.authorization.payment.cancelled";
	public static final String CHECKOUT_ERROR_AUTHORIZATION_PAYMENT_ERROR = "checkout.error.authorization.payment.error";
	public static final String PAYMENT_NOT_SUPPORTED = "checkout.error.payment.not.supported";
	public static final String CART_NOT_VALID = "checkout.error.cart.not.valid";

	// Logging Messages
	public static final String REDIRECTING_TO_CONFIRMATION = "Redirecting to confirmation!";
	public static final String API_EXCEPTION_START_MESSAGE = "API exception ";
	public static final String HANDLING_ADYEN_NON_AUTHORIZED_PAYMENT_EXCEPTION = "Handling AdyenNonAuthorizedPaymentException";
	public static final String REDIRECTING_TO_CART_PAGE = "Redirecting to cart page...";
	public static final String NON_AUTHORIZED_ERROR = "Handling AdyenNonAuthorizedPaymentException. Checking PaymentResponse.";
	public static final String SESSION_PAYMENT_LINK = "adyenPaymentLinkUrl";

	private AdyenwebcommonsConstants()
	{
		//empty to avoid instantiating this constant class
	}

	// implement here constants used by this extension

	public static final String PLATFORM_LOGO_CODE = "adyenwebcommonsPlatformLogo";
}
