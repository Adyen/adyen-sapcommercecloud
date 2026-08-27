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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.slf4j.Logger;
import org.slf4j.MDC;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.ConnectorNotConfiguredException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.exception.SubscriptionProductUndecidableException;
import com.adyen.commerce.connector.exception.TerminalBillingException;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * The rendered line is a parsing contract, so these assert the rendering rather than the builder.
 */
@UnitTest
public class ConnectorLogEventTest
{
	@After
	public void clearContext()
	{
		MDC.clear();
	}

	@Test
	public void rendersFieldsInInsertionOrder()
	{
		final String line = render(ConnectorLogEvent.of("connector_operation")
				.platform(BillingPlatform.RECURLY)
				.operation("create_subscription")
				.outcome(ConnectorLogEvent.OUTCOME_SUCCESS)
				.field("subscription_id", "uuid-1"));

		assertEquals("event=connector_operation platform=RECURLY operation=create_subscription "
				+ "outcome=success subscription_id=uuid-1", line);
	}

	@Test
	public void quotesAValueThatWouldOtherwiseSplitIntoTwoKeys()
	{
		final String line = render(ConnectorLogEvent.of("vendor_api_error").field("plan_id", "gold monthly"));

		assertEquals("event=vendor_api_error plan_id=\"gold monthly\"", line);
	}

	@Test
	public void quotesAValueCarryingAnEqualsSign()
	{
		assertEquals("event=e product_code=\"a=b\"", render(ConnectorLogEvent.of("e").field("product_code", "a=b")));
	}

	/**
	 * Webhook payloads and vendor error bodies are attacker-influenced. A newline inside one of them
	 * would otherwise end the line and let the rest be read as a second, fabricated log entry.
	 */
	@Test
	public void aNewlineCannotForgeASecondLine()
	{
		final String line = render(ConnectorLogEvent.of("webhook_processing")
				.field("vendor_event_type", "payment_succeeded\nevent=merchant_account_mismatch outcome=failure"));

		// The whole injected string stays inside one quoted value on one line: no newline survives, so
		// nothing after it can be read as a log entry of its own.
		assertFalse(line.contains("\n"));
		assertFalse(line.contains("\r"));
		assertEquals("event=webhook_processing vendor_event_type=\"payment_succeeded "
				+ "event=merchant_account_mismatch outcome=failure\"", line);
	}

	/**
	 * A newline is the obvious forging vector, but not the only one a log reader honours: DEL and the
	 * C1 controls are invisible, and U+2028/U+2029 end the line for a JSON viewer.
	 */
	@Test
	public void neutralisesTheLessObviousLineBreaks()
	{
		assertEquals("event=e reason=\"a b c d e\"",
				render(ConnectorLogEvent.of("e").reason("a\u007Fb\u0085c\u2028d\u2029e")));
	}

	@Test
	public void quotesAndEscapesEmbeddedQuotesAndBackslashes()
	{
		assertEquals("event=e reason=\"say \\\"hi\\\" \\\\ now\"",
				render(ConnectorLogEvent.of("e").reason("say \"hi\" \\ now")));
	}

	@Test
	public void anEmptyValueIsVisiblyEmptyRatherThanADanglingKey()
	{
		assertEquals("event=e account_id=\"\"", render(ConnectorLogEvent.of("e").field("account_id", "")));
	}

	@Test
	public void aNullValueDropsItsKey()
	{
		assertEquals("event=e kept=yes",
				render(ConnectorLogEvent.of("e").field("missing", null).field("kept", "yes")));
	}

	@Test
	public void anOversizedValueIsTruncatedRatherThanFloodingTheLine()
	{
		final String line = render(ConnectorLogEvent.of("e")
				.field("resolved_subscription_ids", "x".repeat(ConnectorLogEvent.MAX_VALUE_LENGTH + 50)));

		assertTrue(line.endsWith("..."));
		assertTrue(line.length() < ConnectorLogEvent.MAX_VALUE_LENGTH + 50);
	}

	@Test
	public void aCollectionRendersAsOneQuotedValue()
	{
		assertEquals("event=e ids=\"[uuid-1, uuid-2]\"",
				render(ConnectorLogEvent.of("e").field("ids", List.of("uuid-1", "uuid-2"))));
	}

	@Test
	public void inheritsPlatformAndOperationFromTheOpenScope()
	{
		try (ConnectorLogContext scope = ConnectorLogContext.open(BillingPlatform.CHARGEBEE, "import_token"))
		{
			assertEquals("event=connector_call platform=CHARGEBEE operation=import_token method=POST",
					render(ConnectorLogEvent.of("connector_call").field("method", "POST")));
		}
	}

	/**
	 * The transport states a fallback platform for the case where it is used without a scope. When a
	 * scope is open, the scope is the authority - otherwise the fallback could contradict it.
	 */
	@Test
	public void theOpenScopeWinsOverAFallbackStatedAtTheCallSite()
	{
		try (ConnectorLogContext scope = ConnectorLogContext.open(BillingPlatform.CHARGEBEE, "import_token"))
		{
			final String line = render(ConnectorLogEvent.of("connector_call")
					.platform(BillingPlatform.RECURLY)
					.operation("ensure_customer"));

			assertEquals("event=connector_call platform=CHARGEBEE operation=import_token", line);
		}
	}

	@Test
	public void picksUpACorrelationIdSetByTheCaller()
	{
		try (ConnectorLogContext correlation = ConnectorLogContext.correlate("order-4711"))
		{
			assertEquals("event=connector_operation correlation_id=order-4711",
					render(ConnectorLogEvent.of("connector_operation")));
		}
	}

	@Test
	public void successAndFailureCarryTheSameErrorClassField()
	{
		assertTrue(render(ConnectorLogEvent.of("e").success(System.nanoTime())).contains("error_class=none"));
		assertTrue(render(ConnectorLogEvent.of("e").failure(System.nanoTime(),
				new PreconditionFailedException("no"))).contains("error_class=validation"));
	}

	@Test
	public void failureToleratesANullException()
	{
		assertTrue(render(ConnectorLogEvent.of("e").failure(System.nanoTime(), null)).contains("outcome=failure"));
	}

	/**
	 * Classification follows the retryable flag and the type, not the class name.
	 * {@code SubscriptionProductUndecidableException} is retryable and is named nothing like it, so a
	 * name-matching classifier labels it terminal - which is the opposite of what it is.
	 */
	@Test
	public void classifiesARetryableSubtypeThatIsNotNamedRetryable()
	{
		assertEquals(ConnectorLogEvent.ERROR_CLASS_RETRYABLE,
				ConnectorLogEvent.errorClass(new SubscriptionProductUndecidableException("undecidable", null)));
	}

	@Test
	public void classifiesTheRemainingExceptionKinds()
	{
		assertEquals(ConnectorLogEvent.ERROR_CLASS_RETRYABLE,
				ConnectorLogEvent.errorClass(new RetryableBillingException("later")));
		assertEquals(ConnectorLogEvent.ERROR_CLASS_CONFIGURATION,
				ConnectorLogEvent.errorClass(new ConnectorNotConfiguredException("no config")));
		assertEquals(ConnectorLogEvent.ERROR_CLASS_VALIDATION,
				ConnectorLogEvent.errorClass(new PreconditionFailedException("bad request")));
		assertEquals(ConnectorLogEvent.ERROR_CLASS_TERMINAL,
				ConnectorLogEvent.errorClass(new TerminalBillingException("never")));
	}

	@Test
	public void classifiesHttpStatuses()
	{
		assertEquals(ConnectorLogEvent.ERROR_CLASS_NONE, ConnectorLogEvent.httpErrorClass(201));
		assertEquals(ConnectorLogEvent.ERROR_CLASS_RATE_LIMIT, ConnectorLogEvent.httpErrorClass(429));
		assertEquals(ConnectorLogEvent.ERROR_CLASS_REMOTE_5XX, ConnectorLogEvent.httpErrorClass(503));
		assertEquals(ConnectorLogEvent.ERROR_CLASS_REMOTE_4XX, ConnectorLogEvent.httpErrorClass(404));
		assertEquals(ConnectorLogEvent.ERROR_CLASS_UNEXPECTED_STATUS, ConnectorLogEvent.httpErrorClass(100));
	}

	/**
	 * The three terminal methods are what production calls; nothing else in these tests exercises them.
	 */
	@Test
	public void handsThePatternAndArgumentsToSlf4j()
	{
		final Logger log = mock(Logger.class);
		when(log.isWarnEnabled()).thenReturn(Boolean.TRUE.booleanValue());

		ConnectorLogEvent.of("vendor_api_error").field("http_status", Integer.valueOf(429)).warn(log);

		final ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(log).warn(eq("event={} http_status={}"), arguments.capture());
		assertEquals(2, arguments.getValue().length);
		assertEquals("429", String.valueOf(arguments.getValue()[1]));
	}

	@Test
	public void buildsNothingWhenTheLevelIsOff()
	{
		final Logger log = mock(Logger.class);
		when(log.isInfoEnabled()).thenReturn(Boolean.FALSE.booleanValue());

		ConnectorLogEvent.of("connector_call").info(log);

		verify(log, never()).info(anyString(), ArgumentMatchers.<Object[]> any());
	}

	@Test
	public void logRoutesByOutcome()
	{
		final Logger log = mock(Logger.class);
		when(log.isWarnEnabled()).thenReturn(Boolean.TRUE.booleanValue());
		when(log.isInfoEnabled()).thenReturn(Boolean.TRUE.booleanValue());

		ConnectorLogEvent.of("connector_call").log(log, true);
		ConnectorLogEvent.of("connector_call").log(log, false);

		verify(log).warn(anyString(), ArgumentMatchers.<Object[]> any());
		verify(log).info(anyString(), ArgumentMatchers.<Object[]> any());
	}

	/**
	 * Renders exactly the way SLF4J will: substitute each {@code {}} with the corresponding argument's
	 * {@code toString()}, which is where the escaping happens.
	 */
	private static String render(final ConnectorLogEvent event)
	{
		final String pattern = event.pattern();
		final Object[] values = event.values();
		final StringBuilder rendered = new StringBuilder(pattern.length() + 64);
		int from = 0;
		int index = 0;
		int placeholder = pattern.indexOf("{}", from);
		while (placeholder >= 0)
		{
			rendered.append(pattern, from, placeholder).append(values[index++]);
			from = placeholder + 2;
			placeholder = pattern.indexOf("{}", from);
		}
		return rendered.append(pattern.substring(from)).toString();
	}
}
