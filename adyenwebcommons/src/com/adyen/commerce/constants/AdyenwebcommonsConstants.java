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

	public static final String ADYEN_CHECKOUT_API_PREFIX = "/api/checkout";
	public static final String AUTHORISE_3D_SECURE_PAYMENT_URL = "/authorise-3d-adyen-response";



	private AdyenwebcommonsConstants()
	{
		//empty to avoid instantiating this constant class
	}

	// implement here constants used by this extension

	public static final String PLATFORM_LOGO_CODE = "adyenwebcommonsPlatformLogo";
}
