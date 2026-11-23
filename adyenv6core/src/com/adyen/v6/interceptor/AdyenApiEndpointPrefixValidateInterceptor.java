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

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.BiFunction;

import de.hybris.platform.servicelayer.i18n.L10NService;
import de.hybris.platform.servicelayer.i18n.exceptions.ExceptionLocalizer;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.ValidateInterceptor;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.util.localization.Localization;

/**
 * Rejects a live endpoint prefix that is not the bare prefix shown in the Adyen Customer Area
 * (format {@code [random]-[company name]}, for example {@code 1797a841fbb37ca7-AdyenDemo}). The Adyen
 * library builds {@code https://{prefix}-checkout-live.adyenpayments.com} itself, so a pasted URL or host
 * would produce a broken endpoint. The rule is deliberately lenient: no alphabet is enforced.
 *
 * <p>Only a new store or a modified attribute is checked, so unrelated saves of a store that holds a
 * legacy value keep working.</p>
 */
public class AdyenApiEndpointPrefixValidateInterceptor implements ValidateInterceptor<BaseStoreModel>
{
	public static final String MESSAGE_KEY = "adyen.basestore.adyenAPIEndpointPrefix.invalid";
	static final String FALLBACK_MESSAGE = "Base store {0}: the live endpoint prefix must be the bare prefix shown in the "
			+ "Adyen Customer Area under Developers > API URLs (for example 1797a841fbb37ca7-AdyenDemo), without "
			+ "https://, a host name, dots, slashes, spaces or the -checkout-live suffix. Leave it empty for test mode.";
	private static final String CHECKOUT_LIVE = "-checkout-live";

	@Override
	public void onValidate(final BaseStoreModel store, final InterceptorContext ctx) throws InterceptorException
	{
		if (!ctx.isNew(store) && !ctx.isModified(store, BaseStoreModel.ADYENAPIENDPOINTPREFIX))
		{
			return;
		}
		if (!isValidPrefix(store.getAdyenAPIEndpointPrefix()))
		{
			throw createException(store.getUid());
		}
	}

	/**
	 * Backoffice shows the message of an InterceptorException only when it carries an ExceptionLocalizer
	 * ({@code LocalizableExceptionTranslationHandler}); otherwise it reports "unexpected error".
	 */
	protected InterceptorException createException(final String storeUid)
	{
		// Always attach the localizer: without it Backoffice shows "unexpected error". In a session language
		// without the key the adapter answers with the English fallback instead of the bare key.
		return new InterceptorException(buildMessage(storeUid), null, this,
				new ExceptionLocalizer(MESSAGE_KEY, new Object[] { storeUid },
						new LocalizationAdapter((key, args) -> buildMessage(storeUid))));
	}

	/** The translated message, or null when the key does not resolve. */
	protected String lookupMessage(final String storeUid)
	{
		final String localized = localize(MESSAGE_KEY, new Object[] { storeUid });
		return localized == null || localized.isEmpty() || MESSAGE_KEY.equals(localized) ? null : localized;
	}

	/**
	 * Resolves a key from the extensions' {@code resources/localization/*-locales_*.properties} in the session
	 * language. {@link Localization} reads those files; L10NService and an ExceptionLocalizer without a service
	 * returned the bare key at runtime (checked on 2211-jdk21).
	 */
	protected String localize(final String key, final Object[] args)
	{
		return Localization.getLocalizedString(key, args);
	}

	/**
	 * Lets the ExceptionLocalizer resolve its key the same way as {@link #localize(String, Object[])}. Passing a
	 * service also keeps the ExceptionLocalizer from looking one up in the platform's application context.
	 */
	static class LocalizationAdapter implements L10NService
	{
		private final BiFunction<String, Object[], String> localizer;

		LocalizationAdapter(final BiFunction<String, Object[], String> localizer)
		{
			this.localizer = localizer;
		}

		@Override
		public String getLocalizedString(final String key, final Object[] args)
		{
			return localizer.apply(key, args);
		}

		@Override
		public String getLocalizedString(final String key)
		{
			return localizer.apply(key, null);
		}

		@Override
		public ResourceBundle getResourceBundle()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public ResourceBundle getResourceBundle(final Locale[] locales)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public ResourceBundle getResourceBundle(final String baseName)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public ResourceBundle getResourceBundle(final String baseName, final Locale[] locales)
		{
			throw new UnsupportedOperationException();
		}
	}

	protected String fallbackMessage(final String storeUid)
	{
		return java.text.MessageFormat.format(FALLBACK_MESSAGE, storeUid);
	}

	/** Pure rule: null and empty are valid; a scheme, colon, dot, slash, whitespace or -checkout-live is not. */
	protected boolean isValidPrefix(final String prefix)
	{
		if (prefix == null || prefix.isEmpty())
		{
			return true;
		}
		if (prefix.toLowerCase(Locale.ROOT).contains(CHECKOUT_LIVE))
		{
			return false;
		}
		for (int i = 0; i < prefix.length(); i++)
		{
			final char c = prefix.charAt(i);
			if (c == ':' || c == '.' || c == '/' || c == '\\' || Character.isWhitespace(c) || Character.isSpaceChar(c))
			{
				return false;
			}
		}
		return true;
	}

	/** The localized message; the rejected value is intentionally not part of it. */
	protected String buildMessage(final String storeUid)
	{
		final String localized = lookupMessage(storeUid);
		return localized != null ? localized : fallbackMessage(storeUid);
	}
}
