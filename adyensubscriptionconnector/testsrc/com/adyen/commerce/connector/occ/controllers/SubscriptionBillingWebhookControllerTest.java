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
package com.adyen.commerce.connector.occ.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.RetryableBillingException;
import com.adyen.commerce.connector.exception.TerminalBillingException;
import com.adyen.commerce.connector.webhook.SubscriptionBillingWebhookDispatcher;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.site.BaseSiteService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit test for {@link SubscriptionBillingWebhookController}. The base-site activation and the
 * unknown-platform rejection are the two things worth pinning here: the connectors cannot read their
 * credentials without a current base store, and BillingPlatform is a dynamic enum whose {@code valueOf}
 * silently mints a value for any string rather than throwing.
 */
@UnitTest
public class SubscriptionBillingWebhookControllerTest
{
	private static final String SITE_UID = "electronics";

	@Mock
	private SubscriptionBillingWebhookDispatcher webhookDispatcher;
	@Mock
	private BaseSiteService baseSiteService;
	@Mock
	private EnumerationService enumerationService;
	@Mock
	private BaseSiteModel baseSite;
	@Mock
	private HttpServletRequest request;

	private SubscriptionBillingWebhookController controller;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		controller = new SubscriptionBillingWebhookController();
		controller.setWebhookDispatcher(webhookDispatcher);
		controller.setBaseSiteService(baseSiteService);
		controller.setEnumerationService(enumerationService);

		when(enumerationService.<BillingPlatform> getEnumerationValues(BillingPlatform._TYPECODE))
				.thenReturn(List.of(BillingPlatform.CHARGEBEE, BillingPlatform.RECURLY));
		when(baseSiteService.getBaseSiteForUID(SITE_UID)).thenReturn(baseSite);
		when(request.getHeaderNames()).thenReturn(emptyHeaderNames());
	}

	@Test
	public void activatesTheBaseSiteBeforeDispatching() throws Exception
	{
		final ResponseEntity<String> response = controller.receive(SITE_UID, "chargebee", "{}", request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(baseSiteService).setCurrentBaseSite(baseSite, false);
		verify(webhookDispatcher).dispatch(eq(BillingPlatform.CHARGEBEE), any());
	}

	@Test
	public void platformIsMatchedCaseInsensitively() throws Exception
	{
		final ResponseEntity<String> response = controller.receive(SITE_UID, "ChArGeBeE", "{}", request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(webhookDispatcher).dispatch(eq(BillingPlatform.CHARGEBEE), any());
	}

	/**
	 * The regression this guards: the previous implementation called {@code BillingPlatform.valueOf} and
	 * caught IllegalArgumentException, which a dynamic enum never throws — so this returned 400 from the
	 * dispatcher instead of 404, and every junk name added an entry to the enum's static cache.
	 */
	@Test
	public void unknownPlatformIsRejectedWithoutTouchingTheSiteOrTheDispatcher() throws Exception
	{
		final ResponseEntity<String> response = controller.receive(SITE_UID, "not-a-platform", "{}", request);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		verify(baseSiteService, never()).setCurrentBaseSite(any(BaseSiteModel.class), eq(false));
		verify(webhookDispatcher, never()).dispatch(any(), any());
	}

	@Test
	public void unknownBaseSiteIsRejectedWithoutDispatching() throws Exception
	{
		when(baseSiteService.getBaseSiteForUID("nope")).thenReturn(null);

		final ResponseEntity<String> response = controller.receive("nope", "chargebee", "{}", request);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		verify(webhookDispatcher, never()).dispatch(any(), any());
	}

	/**
	 * Public and unauthenticated, so a bad uid must not surface as a 500.
	 */
	@Test
	public void baseSiteLookupFailureIsRejectedAsNotFound() throws Exception
	{
		when(baseSiteService.getBaseSiteForUID("nope")).thenThrow(new UnknownIdentifierException("no such site"));

		final ResponseEntity<String> response = controller.receive("nope", "chargebee", "{}", request);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		verify(webhookDispatcher, never()).dispatch(any(), any());
	}

	@Test
	public void terminalFailureAnswersBadRequest() throws Exception
	{
		when(webhookDispatcher.dispatch(any(), any())).thenThrow(new TerminalBillingException("bad signature"));

		final ResponseEntity<String> response = controller.receive(SITE_UID, "chargebee", "{}", request);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	public void retryableFailureAnswersServiceUnavailable() throws Exception
	{
		when(webhookDispatcher.dispatch(any(), any())).thenThrow(new RetryableBillingException("upstream down"));

		final ResponseEntity<String> response = controller.receive(SITE_UID, "chargebee", "{}", request);

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
	}

	/**
	 * The endpoint is public and unauthenticated, so its error bodies must be constant. Echoing the path
	 * variables back made every 404 a reflected-XSS sink and a probe oracle for site/platform names; echoing
	 * the exception message leaked connector-side detail such as "bad signature".
	 */
	@Test
	public void rejectionBodiesEchoNothingBackToTheCaller() throws Exception
	{
		final String script = "<script>alert(1)</script>";

		final ResponseEntity<String> badPlatform = controller.receive(SITE_UID, script, "{}", request);
		assertEquals(HttpStatus.NOT_FOUND, badPlatform.getStatusCode());
		assertFalse(badPlatform.getBody().contains(script));

		when(baseSiteService.getBaseSiteForUID(script)).thenReturn(null);
		final ResponseEntity<String> unknownSite = controller.receive(script, "chargebee", "{}", request);
		assertEquals(HttpStatus.NOT_FOUND, unknownSite.getStatusCode());
		assertFalse(unknownSite.getBody().contains(script));

		when(baseSiteService.getBaseSiteForUID("boom")).thenThrow(new UnknownIdentifierException(script));
		final ResponseEntity<String> lookupFailure = controller.receive("boom", "chargebee", "{}", request);
		assertEquals(HttpStatus.NOT_FOUND, lookupFailure.getStatusCode());
		assertFalse(lookupFailure.getBody().contains(script));
	}

	@Test
	public void terminalFailureBodyDoesNotLeakTheExceptionMessage() throws Exception
	{
		when(webhookDispatcher.dispatch(any(), any())).thenThrow(new TerminalBillingException("bad signature"));

		assertFalse(controller.receive(SITE_UID, "chargebee", "{}", request).getBody().contains("bad signature"));
	}

	@Test
	public void retryableFailureBodyDoesNotLeakTheExceptionMessage() throws Exception
	{
		when(webhookDispatcher.dispatch(any(), any())).thenThrow(new RetryableBillingException("upstream down"));

		assertFalse(controller.receive(SITE_UID, "chargebee", "{}", request).getBody().contains("upstream down"));
	}

	@Test
	public void missingBodyIsDispatchedAsAnEmptyPayload() throws Exception
	{
		final ResponseEntity<String> response = controller.receive(SITE_UID, "chargebee", null, request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private static Enumeration<String> emptyHeaderNames()
	{
		return Collections.enumeration(Collections.emptyList());
	}
}
