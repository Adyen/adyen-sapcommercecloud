/*
 * Copyright (c) 2019 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.adyen.commerce.constants;

/**
 * Global class for all adyencheckoutaddonapi web constants. You can add global constants for your extension into this class.
 */
public final class AdyencheckoutaddonapiWebConstants
{
	private AdyencheckoutaddonapiWebConstants()
	{
		//empty to avoid instantiating this constant class
	}

	public static final String ADYEN_CHECKOUT_PAGE_PREFIX = "/checkout/multi";
	public static final String ADYEN_CHECKOUT_ORDER_CONFIRMATION = "/adyen/order-confirmation";
	public static final String ADYEN_CHECKOUT_SELECT_PAYMENT = "/adyen/payment-method";

	public static final String CART_PREFIX = "/cart";

}
