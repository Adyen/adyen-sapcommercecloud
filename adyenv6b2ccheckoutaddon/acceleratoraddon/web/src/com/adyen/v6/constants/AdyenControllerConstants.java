/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.constants;

public interface AdyenControllerConstants
{
	String ADDON_PREFIX = "addon:/adyenv6b2ccheckoutaddon/";
	String CART_PREFIX = "/cart";
	String SELECT_PAYMENT_METHOD_PREFIX = "/checkout/multi/adyen/select-payment-method";
	String SUMMARY_CHECKOUT_PREFIX = "/checkout/multi/adyen/summary";
	String COMPONENT_PREFIX = "/adyen/component";
	String AMAZON_RETURN_URL = "/checkout/multi/adyen/summary/amazonpay/placeorder";
	String CHECKOUT_RESULT_URL = "/checkout-adyen-response";
	/**
	 * Class with view name constants
	 */
	interface Views
	{

		interface Pages
		{

			interface MultiStepCheckout
			{
				String CheckoutSummaryPage = ADDON_PREFIX + "pages/checkout/multi/checkoutSummaryPage";
				String SelectPaymentMethod = ADDON_PREFIX + "pages/checkout/multi/selectPaymentMethodPage";
				String Validate3DSPaymentPage = ADDON_PREFIX + "pages/checkout/multi/3ds_payment";
				String BillingAddressformPage = ADDON_PREFIX + "pages/checkout/multi/billingAddressForm";
				String AddEditDeliveryAddressPage = ADDON_PREFIX + "pages/checkout/multi/addEditDeliveryAddressPage";
				String CountryAddressForm = ADDON_PREFIX + "pages/checkout/multi/countryAddressForm";
			}
		}

	}
}
