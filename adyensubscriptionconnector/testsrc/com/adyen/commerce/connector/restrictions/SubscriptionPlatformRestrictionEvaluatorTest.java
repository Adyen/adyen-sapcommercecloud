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
package com.adyen.commerce.connector.restrictions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import com.adyen.commerce.connector.facades.MySubscriptionsFacade;
import com.adyen.commerce.connector.model.AdyenSubscriptionPlatformRestrictionModel;

import de.hybris.bootstrap.annotations.UnitTest;

/**
 * Unit test for {@link SubscriptionPlatformRestrictionEvaluator}: the My Account entry follows the loaded
 * connectors, in both directions, and the restriction describes itself without being read from storage.
 */
@UnitTest
public class SubscriptionPlatformRestrictionEvaluatorTest
{
	@Test
	public void passesOnlyWhileAPlatformIsLoaded()
	{
		final MySubscriptionsFacade facade = mock(MySubscriptionsFacade.class);
		final SubscriptionPlatformRestrictionEvaluator evaluator = new SubscriptionPlatformRestrictionEvaluator();
		evaluator.setMySubscriptionsFacade(facade);
		final AdyenSubscriptionPlatformRestrictionModel restriction = mock(AdyenSubscriptionPlatformRestrictionModel.class);

		when(facade.hasAvailablePlatforms()).thenReturn(true);
		assertTrue(evaluator.evaluate(restriction, null));
		when(facade.hasAvailablePlatforms()).thenReturn(false);
		assertFalse(evaluator.evaluate(restriction, null));
	}

	@Test
	public void descriptionIsFixedAndReadOnly()
	{
		final SubscriptionPlatformRestrictionDescriptionHandler handler = new SubscriptionPlatformRestrictionDescriptionHandler();
		final AdyenSubscriptionPlatformRestrictionModel restriction = mock(AdyenSubscriptionPlatformRestrictionModel.class);

		assertEquals(SubscriptionPlatformRestrictionDescriptionHandler.DESCRIPTION, handler.get(restriction));
		try
		{
			handler.set(restriction, "x");
			org.junit.Assert.fail("expected UnsupportedOperationException");
		}
		catch (final UnsupportedOperationException expected)
		{
			// the description is derived, not stored
		}
	}
}
