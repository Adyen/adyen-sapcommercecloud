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
package com.adyen.v6.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.i18n.L10NService;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Unit test for {@link AdyenApiEndpointPrefixValidateInterceptor}.
 */
@UnitTest
public class AdyenApiEndpointPrefixValidateInterceptorTest
{
	private static final String STORE_UID = "electronics";
	private static final String KEY = AdyenApiEndpointPrefixValidateInterceptor.MESSAGE_KEY;

	@Mock
	private InterceptorContext ctx;
	@Mock
	private BaseStoreModel store;
	@Mock
	private L10NService l10NService;

	private AdyenApiEndpointPrefixValidateInterceptor interceptor;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		when(store.getUid()).thenReturn(STORE_UID);
		// The platform's Localization needs a running platform; route lookups to a mock instead.
		interceptor = new AdyenApiEndpointPrefixValidateInterceptor()
		{
			@Override
			protected String localize(final String key, final Object[] args)
			{
				return l10NService.getLocalizedString(key, args);
			}
		};
	}

	@Test
	public void acceptsNullEmptyAndBarePrefixes()
	{
		assertTrue(interceptor.isValidPrefix(null));
		assertTrue(interceptor.isValidPrefix(""));
		assertTrue(interceptor.isValidPrefix("1797a841fbb37ca7-AdyenDemo"));
		assertTrue(interceptor.isValidPrefix("abc-Company_Name"));
		assertTrue(interceptor.isValidPrefix("ABC123-xyz"));
	}

	@Test
	public void rejectsUrlsHostsSeparatorsWhitespaceAndLiveSuffix()
	{
		final String[] invalid = { "https://1797a841fbb37ca7-AdyenDemo-checkout-live.adyenpayments.com",
				"1797a841fbb37ca7-AdyenDemo-checkout-live.adyenpayments.com", "1797a841fbb37ca7-AdyenDemo-checkout-live",
				"1797a841fbb37ca7-AdyenDemo-CHECKOUT-LIVE", "1797a841fbb37ca7-AdyenDemo/", "1797a841fbb37ca7-AdyenDemo\\",
				"1797a841fbb37ca7 AdyenDemo", " 1797a841fbb37ca7-AdyenDemo", "1797a841fbb37ca7-AdyenDemo\n", "   ",
				"abc.def", "http:abc" };
		for (final String value : invalid)
		{
			assertFalse("should be rejected: [" + value + "]", interceptor.isValidPrefix(value));
		}
	}

	@Test
	public void doesNotValidateAnUnchangedAttributeOnAnExistingStore() throws Exception
	{
		when(store.getAdyenAPIEndpointPrefix()).thenReturn("https://legacy.example.com");
		when(ctx.isNew(store)).thenReturn(false);
		when(ctx.isModified(store, BaseStoreModel.ADYENAPIENDPOINTPREFIX)).thenReturn(false);

		interceptor.onValidate(store, ctx);
	}

	@Test
	public void rejectsAnInvalidValueOnANewStore()
	{
		when(store.getAdyenAPIEndpointPrefix()).thenReturn("abc.def");
		when(ctx.isNew(store)).thenReturn(true);

		assertThrows(InterceptorException.class, () -> interceptor.onValidate(store, ctx));
	}

	@Test
	public void rejectsAnInvalidValueWhenTheAttributeIsModified()
	{
		when(store.getAdyenAPIEndpointPrefix()).thenReturn("abc/");
		when(ctx.isNew(store)).thenReturn(false);
		when(ctx.isModified(store, BaseStoreModel.ADYENAPIENDPOINTPREFIX)).thenReturn(true);

		assertThrows(InterceptorException.class, () -> interceptor.onValidate(store, ctx));
	}

	@Test
	public void acceptsAValidValueOnANewStore() throws Exception
	{
		when(store.getAdyenAPIEndpointPrefix()).thenReturn("1797a841fbb37ca7-AdyenDemo");
		when(ctx.isNew(store)).thenReturn(true);

		interceptor.onValidate(store, ctx);
	}

	@Test
	public void usesTheLocalizedMessageWithTheStoreUidAsArgument()
	{
		when(store.getAdyenAPIEndpointPrefix()).thenReturn("https://secret-value.example.com");
		when(ctx.isNew(store)).thenReturn(true);
		when(l10NService.getLocalizedString(eq(KEY), any(Object[].class))).thenReturn("localized text");

		final InterceptorException e = assertThrows(InterceptorException.class, () -> interceptor.onValidate(store, ctx));

		assertTrue(e.getMessage(), e.getMessage().contains("localized text"));
		assertFalse(e.getMessage(), e.getMessage().contains("secret-value"));
		final ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(l10NService).getLocalizedString(eq(KEY), args.capture());
		assertEquals(1, args.getValue().length);
		assertEquals(STORE_UID, args.getValue()[0]);
		// Backoffice shows the message only for an exception that carries a localizer, and asks it for the text.
		assertTrue(e.hasLocalizedMessage());
		assertEquals("localized text", e.getLocalizedMessage());
		assertEquals("localized text", e.getLocalizedMessage(param -> param));
	}

	@Test
	public void localizerFallsBackToEnglishWhenTheKeyDoesNotResolve()
	{
		// e.g. a Backoffice session in a language without the key: Backoffice must still get a readable text.
		when(store.getAdyenAPIEndpointPrefix()).thenReturn("abc.def");
		when(ctx.isNew(store)).thenReturn(true);
		when(l10NService.getLocalizedString(eq(KEY), any(Object[].class))).thenReturn(KEY);

		final InterceptorException e = assertThrows(InterceptorException.class, () -> interceptor.onValidate(store, ctx));

		assertTrue(e.hasLocalizedMessage());
		assertTrue(e.getLocalizedMessage(param -> param).contains("Base store " + STORE_UID + ":"));
		assertFalse(e.getLocalizedMessage(param -> param).contains(KEY));
	}

	@Test
	public void fallsBackToTheEnglishTextWhenTheLookupReturnsTheKey()
	{
		when(store.getAdyenAPIEndpointPrefix()).thenReturn("abc.def");
		when(ctx.isNew(store)).thenReturn(true);
		when(l10NService.getLocalizedString(eq(KEY), any(Object[].class))).thenReturn(KEY);

		final InterceptorException e = assertThrows(InterceptorException.class, () -> interceptor.onValidate(store, ctx));

		assertTrue(e.getMessage(), e.getMessage().contains("Base store " + STORE_UID + ":"));
		assertTrue(e.getMessage(), e.getMessage().contains("Developers > API URLs"));
	}

	@Test
	public void fallsBackToTheEnglishTextWhenTheLookupReturnsNull()
	{
		when(store.getAdyenAPIEndpointPrefix()).thenReturn("abc.def");
		when(ctx.isNew(store)).thenReturn(true);
		when(l10NService.getLocalizedString(eq(KEY), any(Object[].class))).thenReturn(null);

		final InterceptorException e = assertThrows(InterceptorException.class, () -> interceptor.onValidate(store, ctx));

		assertTrue(e.getMessage(), e.getMessage().contains("Base store " + STORE_UID + ":"));
	}
}
