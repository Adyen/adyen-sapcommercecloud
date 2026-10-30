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
package com.adyen.commerce.connector.interceptor;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.readiness.BillingPlatformReadinessCheck;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.model.ItemModelContext;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Unit test for {@link ActiveBillingPlatformValidateInterceptor}.
 */
@UnitTest
public class ActiveBillingPlatformValidateInterceptorTest
{
	private static final BillingPlatform RECURLY = BillingPlatform.valueOf("RECURLY");
	private static final BillingPlatform CHARGEBEE = BillingPlatform.valueOf("CHARGEBEE");
	private static final String STORE_UID = "electronics";

	@Mock
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	@Mock
	private SubscriptionBillingConnector recurlyConnector;
	@Mock
	private BillingPlatformReadinessCheck recurlyCheck;
	@Mock
	private BillingPlatformReadinessCheck chargebeeCheck;
	@Mock
	private BaseStoreModel store;
	@Mock
	private ItemModelContext itemModelContext;
	@Mock
	private InterceptorContext ctx;

	private ActiveBillingPlatformValidateInterceptor interceptor;

	@Before
	public void setUp()
	{
		MockitoAnnotations.openMocks(this);
		when(store.getUid()).thenReturn(STORE_UID);
		when(store.getItemModelContext()).thenReturn(itemModelContext);
		when(recurlyCheck.platform()).thenReturn(RECURLY);
		when(recurlyCheck.missingSettings(store)).thenReturn(List.of());
		when(chargebeeCheck.platform()).thenReturn(CHARGEBEE);
		when(connectorRegistry.findConnector(any())).thenReturn(Optional.empty());
		when(connectorRegistry.findConnector(RECURLY)).thenReturn(Optional.of(recurlyConnector));
		when(connectorRegistry.getAvailablePlatforms()).thenReturn(Set.of(RECURLY));

		interceptor = new ActiveBillingPlatformValidateInterceptor();
		interceptor.setConnectorRegistry(connectorRegistry);
		interceptor.setReadinessChecks(new ArrayList<>(List.of(recurlyCheck, chargebeeCheck)));
	}

	@Test
	public void ignoresAStoreWithoutPlatform() throws Exception
	{
		when(store.getActiveBillingPlatform()).thenReturn(null);
		when(ctx.isNew(store)).thenReturn(true);

		interceptor.onValidate(store, ctx);

		verifyNoInteractions(connectorRegistry, recurlyCheck, chargebeeCheck);
	}

	@Test
	public void checksANewStoreThatSetsAPlatform() throws Exception
	{
		when(store.getActiveBillingPlatform()).thenReturn(RECURLY);
		when(ctx.isNew(store)).thenReturn(true);

		interceptor.onValidate(store, ctx);

		verify(connectorRegistry).findConnector(RECURLY);
		verify(recurlyCheck).missingSettings(store);
	}

	@Test
	public void rejectsANewStoreWhosePlatformHasNoConnector()
	{
		when(store.getActiveBillingPlatform()).thenReturn(CHARGEBEE);
		when(ctx.isNew(store)).thenReturn(true);

		assertThrows(InterceptorException.class, () -> interceptor.onValidate(store, ctx));
	}

	/** A store whose platform lost its connector must still save when something else changes. */
	@Test
	public void leavesAnUnchangedPlatformAlone() throws Exception
	{
		when(store.getActiveBillingPlatform()).thenReturn(CHARGEBEE);
		when(ctx.isModified(store, BaseStoreModel.ACTIVEBILLINGPLATFORM)).thenReturn(false);

		interceptor.onValidate(store, ctx);

		verifyNoInteractions(connectorRegistry, recurlyCheck, chargebeeCheck);
	}

	@Test
	public void acceptsTheSamePlatformSetAgain() throws Exception
	{
		when(store.getActiveBillingPlatform()).thenReturn(CHARGEBEE);
		when(ctx.isModified(store, BaseStoreModel.ACTIVEBILLINGPLATFORM)).thenReturn(true);
		when(itemModelContext.getOriginalValue(BaseStoreModel.ACTIVEBILLINGPLATFORM)).thenReturn(CHARGEBEE);

		interceptor.onValidate(store, ctx);

		verifyNoInteractions(connectorRegistry, recurlyCheck, chargebeeCheck);
	}

	@Test
	public void rejectsASwitchToAPlatformWithoutConnector()
	{
		switchPlatform(RECURLY, CHARGEBEE);

		final InterceptorException e = assertThrows(InterceptorException.class, () -> interceptor.onValidate(store, ctx));

		assertTrue(e.getMessage(), e.getMessage().contains("CHARGEBEE"));
		assertTrue(e.getMessage(), e.getMessage().contains(STORE_UID));
		assertTrue(e.getMessage(), e.getMessage().contains("Available platforms: RECURLY"));
		verify(chargebeeCheck, never()).missingSettings(store);
	}

	@Test
	public void acceptsASwitchToALoadedPlatformOnAReadyStore() throws Exception
	{
		switchPlatform(null, RECURLY);

		interceptor.onValidate(store, ctx);

		verify(recurlyCheck).missingSettings(store);
		verify(chargebeeCheck, never()).missingSettings(store);
	}

	@Test
	public void rejectsASwitchWhileSettingsAreMissing()
	{
		switchPlatform(CHARGEBEE, RECURLY);
		when(recurlyCheck.missingSettings(store)).thenReturn(List.of("subscriptionApiKey", "recurlyWebhookSigningKey"));

		final InterceptorException e = assertThrows(InterceptorException.class, () -> interceptor.onValidate(store, ctx));

		assertTrue(e.getMessage(), e.getMessage().contains("RECURLY"));
		assertTrue(e.getMessage(), e.getMessage().contains(STORE_UID));
		assertTrue(e.getMessage(), e.getMessage().contains("subscriptionApiKey"));
		assertTrue(e.getMessage(), e.getMessage().contains("recurlyWebhookSigningKey"));
	}

	@Test
	public void needsOnlyTheConnectorWhenThePlatformHasNoReadinessCheck() throws Exception
	{
		interceptor.setReadinessChecks(new ArrayList<>(List.of(chargebeeCheck)));
		switchPlatform(CHARGEBEE, RECURLY);

		interceptor.onValidate(store, ctx);

		verify(chargebeeCheck, never()).missingSettings(store);
	}

	private void switchPlatform(final BillingPlatform from, final BillingPlatform to)
	{
		when(store.getActiveBillingPlatform()).thenReturn(to);
		when(ctx.isNew(store)).thenReturn(false);
		when(ctx.isModified(store, BaseStoreModel.ACTIVEBILLINGPLATFORM)).thenReturn(true);
		when(itemModelContext.getOriginalValue(BaseStoreModel.ACTIVEBILLINGPLATFORM)).thenReturn(from);
	}
}
