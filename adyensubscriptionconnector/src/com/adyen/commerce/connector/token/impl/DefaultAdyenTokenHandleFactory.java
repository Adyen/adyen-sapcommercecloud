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
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.connector.token.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.dto.CardMetadata;
import com.adyen.commerce.connector.exception.TokenContractException;
import com.adyen.commerce.connector.token.AdyenTokenHandleFactory;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;

/**
 * Default factory. Maps the Adyen plugin's persisted token artifacts onto {@link AdyenTokenHandle}:
 * <ul>
 *   <li>{@code shopperReference} &larr; {@code Customer.customerID}</li>
 *   <li>{@code storedPaymentMethodId} &larr; {@code PaymentInfo.adyenSelectedReference}
 *       (the plugin normalizes modern {@code storedPaymentMethodId} and legacy
 *       {@code recurringDetailReference} into this single attribute)</li>
 *   <li>{@code merchantAccount} &larr; {@link AdyenMerchantAccountStrategy}</li>
 *   <li>{@code networkTransactionId} &larr; {@code PaymentInfo.adyenNetworkTxReference}, captured from
 *       the authorisation response's {@code additionalData.networkTxReference}</li>
 *   <li>card metadata &larr; the {@code PaymentInfo} adyen card attributes</li>
 * </ul>
 * The network transaction id stays optional: schemes return it for card authorisations, but not for
 * every payment method, and only connectors that advertise
 * {@code ConnectorCapabilities.requiresNetworkTransactionId()} need one. Orders authorised before that
 * attribute existed have none, so a token minted back then cannot be imported into such a platform
 * without a fresh authorisation.
 */
public class DefaultAdyenTokenHandleFactory implements AdyenTokenHandleFactory
{
	private static final String EXPIRY_FORMAT = "MM/yyyy";

	private AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;

	@Override
	public AdyenTokenHandle create(final AbstractOrderModel order) throws TokenContractException
	{
		if (order == null)
		{
			throw new TokenContractException("Cannot build an Adyen token handle without an order");
		}

		final PaymentInfoModel paymentInfo = order.getPaymentInfo();
		if (paymentInfo == null)
		{
			throw new TokenContractException("Order '" + order.getCode() + "' has no payment info");
		}

		final String storedPaymentMethodId = paymentInfo.getAdyenSelectedReference();
		if (StringUtils.isBlank(storedPaymentMethodId))
		{
			throw new TokenContractException("Order '" + order.getCode()
					+ "' has no adyenSelectedReference (storedPaymentMethodId); the shopper was not tokenized");
		}

		final UserModel user = order.getUser();
		if (!(user instanceof CustomerModel))
		{
			throw new TokenContractException(
					"Order '" + order.getCode() + "' is not owned by a customer; cannot derive a shopperReference");
		}
		final String shopperReference = ((CustomerModel) user).getCustomerID();
		if (StringUtils.isBlank(shopperReference))
		{
			throw new TokenContractException("Customer on order '" + order.getCode() + "' has no customerID/shopperReference");
		}

		final String merchantAccount = adyenMerchantAccountStrategy.getWebMerchantAccount(order.getStore());
		if (StringUtils.isBlank(merchantAccount))
		{
			throw new TokenContractException(
					"Base store of order '" + order.getCode() + "' has no Adyen merchant account configured");
		}

		return new AdyenTokenHandle(merchantAccount, shopperReference, storedPaymentMethodId,
				StringUtils.trimToNull(paymentInfo.getAdyenNetworkTxReference()), buildCardMetadata(paymentInfo));
	}

	protected CardMetadata buildCardMetadata(final PaymentInfoModel paymentInfo)
	{
		return new CardMetadata(paymentInfo.getCardBrand(), paymentInfo.getAdyenCardSummary(),
				paymentInfo.getAdyenCardHolder(), formatExpiry(paymentInfo.getAdyenCardExpiry()), paymentInfo.getCardType());
	}

	protected String formatExpiry(final Date expiry)
	{
		return expiry == null ? null : new SimpleDateFormat(EXPIRY_FORMAT, Locale.ROOT).format(expiry);
	}

	public void setAdyenMerchantAccountStrategy(final AdyenMerchantAccountStrategy adyenMerchantAccountStrategy)
	{
		this.adyenMerchantAccountStrategy = adyenMerchantAccountStrategy;
	}
}
