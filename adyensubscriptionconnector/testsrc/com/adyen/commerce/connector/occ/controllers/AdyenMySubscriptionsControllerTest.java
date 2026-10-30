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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;

import com.adyen.commerce.connector.facades.MySubscriptionsFacade;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeReport;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeResult;
import com.adyen.commerce.connector.facades.data.SubscriptionOverviewData;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionCancelResultWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionEnrollmentUrlWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionOverviewWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionPaymentMethodChangeRequestWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionPaymentMethodChangeResultWsDTO;
import com.adyen.commerce.connector.occ.exceptions.SubscriptionActionRefusedException;
import com.adyen.commerce.connector.occ.mapper.AdyenMySubscriptionsWsMapper;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * Unit test for {@link AdyenMySubscriptionsController}. It pins what the contract promises and the facade
 * cannot: who may call which method, that every refusal is one indistinguishable error, that nothing a
 * platform said reaches the body, and that a missing connector is a 404 on every route.
 */
@UnitTest
public class AdyenMySubscriptionsControllerTest
{
	private static final String PLATFORM = "RECURLY";
	private static final String CODE = "sub-1";
	private static final String SECRET_URL = "https://example.invalid/hosted?token=SECRET";

	@Mock
	private MySubscriptionsFacade facade;

	private AdyenMySubscriptionsController controller;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		controller = new AdyenMySubscriptionsController();
		controller.setMySubscriptionsFacade(facade);
		controller.setMapper(new AdyenMySubscriptionsWsMapper());
		when(facade.hasAvailablePlatforms()).thenReturn(true);
	}

	// ---- roles -------------------------------------------------------------------------------------

	@Test
	public void getAllowsClientAndTrustedClient_mutationsDoNot() throws Exception
	{
		assertRoles("getSubscriptions", new String[] { "ROLE_CUSTOMERGROUP", "ROLE_CLIENT",
				"ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT" });
		final String[] customerOnly = { "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" };
		assertRoles("cancel", customerOnly, String.class, String.class);
		assertRoles("changePaymentMethod", customerOnly, String.class,
				AdyenSubscriptionPaymentMethodChangeRequestWsDTO.class);
		assertRoles("enrollment", customerOnly, String.class, String.class);
	}

	private void assertRoles(final String name, final String[] expected, final Class<?>... params) throws Exception
	{
		final Method method = AdyenMySubscriptionsController.class.getMethod(name, params);
		final Secured secured = method.getAnnotation(Secured.class);
		assertNotNull(name + " must be @Secured", secured);
		assertArrayEquals(name, expected, secured.value());
	}

	// ---- 404 ---------------------------------------------------------------------------------------

	@Test
	public void everyRouteAnswers404WhenNoPlatformIsLoaded()
	{
		when(facade.hasAvailablePlatforms()).thenReturn(false);
		final AdyenSubscriptionPaymentMethodChangeRequestWsDTO body = changeRequest(false);

		assertEquals(HttpStatus.NOT_FOUND, controller.getSubscriptions().getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.cancel(CODE, PLATFORM).getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.changePaymentMethod(CODE, body).getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.enrollment(CODE, PLATFORM).getStatusCode());

		verify(facade, never()).getSubscriptionsForCurrentCustomer();
		verify(facade, never()).cancelForCurrentCustomer(anyString(), anyString());
		verify(facade, never()).paymentMethodEnrollmentUrlForCurrentCustomer(anyString(), anyString());
	}

	// ---- list --------------------------------------------------------------------------------------

	@Test
	public void getReturnsTheMappedOverview()
	{
		when(facade.getSubscriptionsForCurrentCustomer()).thenReturn(new SubscriptionOverviewData());

		final ResponseEntity<AdyenSubscriptionOverviewWsDTO> response = controller.getSubscriptions();

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().getSections().isEmpty());
	}

	// ---- one refusal -------------------------------------------------------------------------------

	@Test
	public void everyCancelRefusalIsTheSameError()
	{
		final Supplier<Object> unknownCode = () -> controller.cancel("nope", PLATFORM);
		when(facade.cancelForCurrentCustomer(PLATFORM, "nope")).thenReturn(false);
		final Supplier<Object> platformFailure = () -> controller.cancel("boom", PLATFORM);
		when(facade.cancelForCurrentCustomer(PLATFORM, "boom"))
				.thenThrow(new IllegalStateException("Recurly said: account 123 is closed"));
		final Supplier<Object> noPlatform = () -> controller.cancel(CODE, "  ");

		final SubscriptionActionRefusedException first = refusal(unknownCode);
		final SubscriptionActionRefusedException second = refusal(platformFailure);
		final SubscriptionActionRefusedException third = refusal(noPlatform);

		for (final SubscriptionActionRefusedException refusal : List.of(first, second, third))
		{
			assertEquals("SubscriptionActionRefusedError", refusal.getType());
			assertEquals(first.getMessage(), refusal.getMessage());
			assertFalse(refusal.getMessage().contains("Recurly"));
		}
		verify(facade, never()).cancelForCurrentCustomer(anyString(), eq(CODE));
	}

	@Test
	public void cancelSucceedsWithAnEmptyBody()
	{
		when(facade.cancelForCurrentCustomer(PLATFORM, CODE)).thenReturn(true);

		final ResponseEntity<AdyenSubscriptionCancelResultWsDTO> response = controller.cancel(CODE, PLATFORM);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
	}

	// ---- payment method change ---------------------------------------------------------------------

	@Test
	public void changeForOneSubscriptionReportsTheResultWithNoCounts()
	{
		when(facade.changePaymentMethodForCurrentCustomer(PLATFORM, CODE, "pm-1"))
				.thenReturn(PaymentMethodChangeResult.CHANGED_THIS_SUBSCRIPTION);

		final AdyenSubscriptionPaymentMethodChangeResultWsDTO body = controller
				.changePaymentMethod(CODE, changeRequest(false)).getBody();

		assertEquals("CHANGED_THIS_SUBSCRIPTION", body.getResult());
		assertEquals(0, body.getMoved());
		assertEquals(0, body.getFailed());
		assertFalse(body.isPartial());
		verify(facade, never()).changePaymentMethodForAllSubscriptions(anyString(), anyString(), anyString());
	}

	@Test
	public void changeForAllSubscriptionsCarriesCountsAndPartial()
	{
		when(facade.changePaymentMethodForAllSubscriptions(PLATFORM, CODE, "pm-1")).thenReturn(
				new PaymentMethodChangeReport(PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS, 2, 1));

		final AdyenSubscriptionPaymentMethodChangeResultWsDTO body = controller
				.changePaymentMethod(CODE, changeRequest(true)).getBody();

		assertEquals("CHANGED_ALL_SUBSCRIPTIONS", body.getResult());
		assertEquals(2, body.getMoved());
		assertEquals(1, body.getFailed());
		assertTrue(body.isPartial());
		verify(facade, never()).changePaymentMethodForCurrentCustomer(anyString(), anyString(), anyString());
	}

	@Test
	public void changeTurnsAnUnexpectedFailureIntoFailedWithoutItsMessage()
	{
		when(facade.changePaymentMethodForCurrentCustomer(any(), any(), any()))
				.thenThrow(new IllegalStateException("platform text"));

		final AdyenSubscriptionPaymentMethodChangeResultWsDTO body = controller
				.changePaymentMethod(CODE, changeRequest(false)).getBody();

		assertEquals("FAILED", body.getResult());
	}

	@Test
	public void changeWithoutPlatformOrMethodIsRefusedBeforeTheFacade()
	{
		final AdyenSubscriptionPaymentMethodChangeRequestWsDTO noMethod = changeRequest(false);
		noMethod.setStoredPaymentMethodId(null);
		final AdyenSubscriptionPaymentMethodChangeRequestWsDTO noPlatform = changeRequest(false);
		noPlatform.setPlatform("");

		refusal(() -> controller.changePaymentMethod(CODE, noMethod));
		refusal(() -> controller.changePaymentMethod(CODE, noPlatform));

		verify(facade, never()).changePaymentMethodForCurrentCustomer(any(), any(), any());
		verify(facade, never()).changePaymentMethodForAllSubscriptions(any(), any(), any());
	}

	// ---- enrollment --------------------------------------------------------------------------------

	@Test
	public void enrollmentReturnsTheUrlInTheBody()
	{
		when(facade.paymentMethodEnrollmentUrlForCurrentCustomer(PLATFORM, CODE)).thenReturn(SECRET_URL);

		final ResponseEntity<AdyenSubscriptionEnrollmentUrlWsDTO> response = controller.enrollment(CODE, PLATFORM);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(SECRET_URL, response.getBody().getUrl());
	}

	@Test
	public void enrollmentWithNoUrlOrAFailureIsTheSameRefusalAsCancel()
	{
		when(facade.paymentMethodEnrollmentUrlForCurrentCustomer(PLATFORM, "none")).thenReturn(null);
		when(facade.paymentMethodEnrollmentUrlForCurrentCustomer(PLATFORM, "blank")).thenReturn(" ");
		when(facade.paymentMethodEnrollmentUrlForCurrentCustomer(PLATFORM, "boom"))
				.thenThrow(new IllegalStateException(SECRET_URL));
		final SubscriptionActionRefusedException cancelRefusal = refusal(() -> controller.cancel("x", " "));

		for (final String code : Arrays.asList("none", "blank", "boom"))
		{
			final SubscriptionActionRefusedException refusal = refusal(() -> controller.enrollment(code, PLATFORM));
			assertEquals(cancelRefusal.getType(), refusal.getType());
			assertEquals(cancelRefusal.getMessage(), refusal.getMessage());
			assertFalse(refusal.getMessage().contains("SECRET"));
		}
		refusal(() -> controller.enrollment(CODE, null));
		// The null platform was refused before the facade was asked.
		verify(facade, never()).paymentMethodEnrollmentUrlForCurrentCustomer(isNull(), anyString());
	}

	// ---- helpers -----------------------------------------------------------------------------------

	private static AdyenSubscriptionPaymentMethodChangeRequestWsDTO changeRequest(final boolean applyToAll)
	{
		final AdyenSubscriptionPaymentMethodChangeRequestWsDTO request = new AdyenSubscriptionPaymentMethodChangeRequestWsDTO();
		request.setPlatform(PLATFORM);
		request.setStoredPaymentMethodId("pm-1");
		request.setApplyToAll(applyToAll);
		return request;
	}

	private static SubscriptionActionRefusedException refusal(final Supplier<Object> call)
	{
		try
		{
			call.get();
		}
		catch (final SubscriptionActionRefusedException e)
		{
			return e;
		}
		fail("expected a SubscriptionActionRefusedException");
		return null;
	}
}
