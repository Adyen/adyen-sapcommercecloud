package com.adyen.v6.service.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.adyen.model.checkout.StoredPaymentMethodResource;
import com.adyen.service.exception.ApiException;
import com.adyen.v6.exceptions.AdyenStoredCardsUnavailableException;
import com.adyen.v6.factory.AdyenPaymentServiceFactory;
import com.adyen.v6.service.AdyenCheckoutApiService;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * An empty stored-cards listing and a listing that could not be read are different answers, and only the
 * throwing read tells them apart.
 */
@UnitTest
public class DefaultAdyenStoredCardsServiceTest
{
	private DefaultAdyenStoredCardsService service;
	private AdyenCheckoutApiService adyen;
	private CustomerModel customer;

	@Before
	public void setUp()
	{
		final BaseStoreModel store = mock(BaseStoreModel.class);
		final BaseStoreService baseStoreService = mock(BaseStoreService.class);
		when(baseStoreService.getCurrentBaseStore()).thenReturn(store);
		adyen = mock(AdyenCheckoutApiService.class);
		final AdyenPaymentServiceFactory factory = mock(AdyenPaymentServiceFactory.class);
		when(factory.createAdyenCheckoutApiService(store)).thenReturn(adyen);

		service = new DefaultAdyenStoredCardsService();
		service.setBaseStoreService(baseStoreService);
		service.setAdyenPaymentServiceFactory(factory);

		customer = mock(CustomerModel.class);
		when(customer.getCustomerID()).thenReturn("customer-1");
	}

	@Test
	public void returnsWhatAdyenHolds() throws Exception
	{
		final List<StoredPaymentMethodResource> cards = List.of(card("card-1"));
		when(adyen.getStoredCards("customer-1")).thenReturn(cards);

		assertSame(cards, service.getStoredCardsOrThrow(customer));
	}

	@Test
	public void anEmptyAnswerIsNotAFailure() throws Exception
	{
		when(adyen.getStoredCards("customer-1")).thenReturn(List.of());

		assertTrue(service.getStoredCardsOrThrow(customer).isEmpty());
	}

	@Test
	public void saysSoWhenTheCallFails() throws Exception
	{
		final IOException cause = new IOException("timeout");
		when(adyen.getStoredCards("customer-1")).thenThrow(cause);

		try
		{
			service.getStoredCardsOrThrow(customer);
			fail("an unreadable listing must not look like an empty one");
		}
		catch (final AdyenStoredCardsUnavailableException e)
		{
			assertSame(cause, e.getCause());
		}
	}

	@Test
	public void saysSoWhenAdyenRefuses() throws Exception
	{
		final ApiException cause = new ApiException("forbidden", 403);
		when(adyen.getStoredCards("customer-1")).thenThrow(cause);

		try
		{
			service.getStoredCardsOrThrow(customer);
			fail("a refused listing must not look like an empty one");
		}
		catch (final AdyenStoredCardsUnavailableException e)
		{
			assertSame(cause, e.getCause());
		}
	}

	/** The display read keeps its contract: callers that only show cards still get an empty list. */
	@Test
	public void theDisplayReadStillAnswersEmptyWhenTheCallFails() throws Exception
	{
		when(adyen.getStoredCards("customer-1")).thenThrow(new IOException("timeout"));

		assertTrue(service.getStoredCards(customer).isEmpty());
	}

	@Test
	public void asksAdyenNothingForNoCustomer() throws Exception
	{
		assertTrue(service.getStoredCardsOrThrow(null).isEmpty());

		verify(adyen, never()).getStoredCards(any());
	}

	@Test
	public void disablesACardTheCustomerHolds() throws Exception
	{
		when(adyen.getStoredCards("customer-1")).thenReturn(List.of(card("card-1")));

		assertTrue(service.disableStoredCard(customer, "card-1"));

		verify(adyen).disableStoredCard("customer-1", "card-1");
	}

	/** Nothing is known about the card, so nothing is disabled. */
	@Test
	public void disablesNothingWhenTheListingCannotBeRead() throws Exception
	{
		when(adyen.getStoredCards("customer-1")).thenThrow(new IOException("timeout"));

		assertFalse(service.disableStoredCard(customer, "card-1"));

		verify(adyen, never()).disableStoredCard(any(), any());
	}

	private static StoredPaymentMethodResource card(final String id)
	{
		final StoredPaymentMethodResource card = new StoredPaymentMethodResource();
		card.setId(id);
		return card;
	}
}
