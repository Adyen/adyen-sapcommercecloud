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
package com.adyen.commerce.connector.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Test;
import org.slf4j.MDC;

import com.adyen.commerce.connector.enums.BillingPlatform;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * The scope runs on platform worker threads that are handed back to a pool, so what it puts into the
 * MDC has to come back out again - a leaked {@code operation} would relabel whatever that thread does
 * next.
 */
@UnitTest
public class ConnectorLogContextTest
{
	private static final BillingPlatform RECURLY = BillingPlatform.valueOf("RECURLY");
	private static final BillingPlatform CHARGEBEE = BillingPlatform.valueOf("CHARGEBEE");

	@After
	public void clearContext()
	{
		MDC.clear();
	}

	@Test
	public void publishesPlatformAndOperationForTheDurationOfTheScope()
	{
		try (ConnectorLogContext scope = ConnectorLogContext.open(RECURLY, "create_subscription"))
		{
			assertEquals("RECURLY", ConnectorLogContext.current(ConnectorLogContext.PLATFORM));
			assertEquals("create_subscription", ConnectorLogContext.current(ConnectorLogContext.OPERATION));
		}
		assertNull(ConnectorLogContext.current(ConnectorLogContext.PLATFORM));
		assertNull(ConnectorLogContext.current(ConnectorLogContext.OPERATION));
	}

	@Test
	public void anInnerScopeRestoresTheOuterOneRatherThanClearingIt()
	{
		try (ConnectorLogContext outer = ConnectorLogContext.open(RECURLY, "import_token"))
		{
			try (ConnectorLogContext inner = ConnectorLogContext.open(RECURLY, "ensure_customer"))
			{
				assertEquals("ensure_customer", ConnectorLogContext.current(ConnectorLogContext.OPERATION));
			}
			assertEquals("import_token", ConnectorLogContext.current(ConnectorLogContext.OPERATION));
		}
		assertNull(ConnectorLogContext.current(ConnectorLogContext.OPERATION));
	}

	@Test
	public void leavesAValueTheHostApplicationPutThereIntact()
	{
		MDC.put(ConnectorLogContext.CORRELATION_ID, "order-4711");
		try (ConnectorLogContext scope = ConnectorLogContext.open(CHARGEBEE, "cancel_subscription"))
		{
			assertEquals("order-4711", ConnectorLogContext.current(ConnectorLogContext.CORRELATION_ID));
		}
		assertEquals("order-4711", ConnectorLogContext.current(ConnectorLogContext.CORRELATION_ID));
	}

	@Test
	public void aCorrelationScopeRestoresWhateverWasThereBefore()
	{
		MDC.put(ConnectorLogContext.CORRELATION_ID, "outer");
		try (ConnectorLogContext scope = ConnectorLogContext.correlate("inner"))
		{
			assertEquals("inner", ConnectorLogContext.current(ConnectorLogContext.CORRELATION_ID));
		}
		assertEquals("outer", ConnectorLogContext.current(ConnectorLogContext.CORRELATION_ID));
	}

	@Test
	public void aNullOperationPublishesNothingAndRestoresNothing()
	{
		MDC.put(ConnectorLogContext.OPERATION, "pre-existing");
		try (ConnectorLogContext scope = ConnectorLogContext.open(RECURLY, null))
		{
			assertEquals("pre-existing", ConnectorLogContext.current(ConnectorLogContext.OPERATION));
		}
		assertEquals("pre-existing", ConnectorLogContext.current(ConnectorLogContext.OPERATION));
	}

	@Test
	public void readsTheEnumCodeRatherThanAGeneratedToString()
	{
		assertEquals("CHARGEBEE", ConnectorLogContext.code(CHARGEBEE));
		assertEquals("plain", ConnectorLogContext.code("plain"));
		assertNull(ConnectorLogContext.code(null));
	}
}
