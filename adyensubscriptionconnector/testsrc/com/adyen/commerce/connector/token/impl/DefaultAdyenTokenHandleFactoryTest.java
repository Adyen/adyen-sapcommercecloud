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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.AdyenTokenHandle;
import com.adyen.commerce.connector.exception.TokenContractException;
import com.adyen.v6.strategy.AdyenMerchantAccountStrategy;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Unit test for {@link DefaultAdyenTokenHandleFactory} — asserts the token contract is assembled
 * field-by-field from the existing Adyen plugin artifacts (design P1.8 acceptance criterion).
 */
@UnitTest
public class DefaultAdyenTokenHandleFactoryTest
{
	@Mock
	private AdyenMerchantAccountStrategy adyenMerchantAccountStrategy;
	@Mock
	private AbstractOrderModel order;
	@Mock
	private PaymentInfoModel paymentInfo;
	@Mock
	private CustomerModel customer;
	@Mock
	private BaseStoreModel store;

	private DefaultAdyenTokenHandleFactory factory;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		factory = new DefaultAdyenTokenHandleFactory();
		factory.setAdyenMerchantAccountStrategy(adyenMerchantAccountStrategy);
	}

	@Test
	public void shouldBuildHandleFromOrder() throws Exception
	{
		when(order.getPaymentInfo()).thenReturn(paymentInfo);
		when(paymentInfo.getAdyenSelectedReference()).thenReturn("TOKEN-1");
		when(order.getUser()).thenReturn(customer);
		when(customer.getCustomerID()).thenReturn("shopper-1");
		when(order.getStore()).thenReturn(store);
		when(adyenMerchantAccountStrategy.getWebMerchantAccount(store)).thenReturn("MERCH");
		when(paymentInfo.getCardBrand()).thenReturn("visa");
		when(paymentInfo.getAdyenCardSummary()).thenReturn("1111");
		when(paymentInfo.getAdyenCardHolder()).thenReturn("Alice");
		when(paymentInfo.getCardType()).thenReturn("credit");
		when(paymentInfo.getAdyenCardExpiry()).thenReturn(null);

		final AdyenTokenHandle handle = factory.create(order);

		assertEquals("shopper-1", handle.shopperReference());
		assertEquals("TOKEN-1", handle.storedPaymentMethodId());
		assertEquals("MERCH", handle.merchantAccount());
		assertNull("plugin does not capture an NTID", handle.networkTransactionId());
		assertEquals("visa", handle.cardMetadata().brand());
		assertEquals("1111", handle.cardMetadata().last4());
	}

	@Test
	public void shouldFailWhenTokenMissing()
	{
		when(order.getPaymentInfo()).thenReturn(paymentInfo);
		when(paymentInfo.getAdyenSelectedReference()).thenReturn("  ");

		assertThrows(TokenContractException.class, () -> factory.create(order));
	}

	@Test
	public void shouldFailWhenNoPaymentInfo()
	{
		when(order.getPaymentInfo()).thenReturn(null);

		assertThrows(TokenContractException.class, () -> factory.create(order));
	}
}
