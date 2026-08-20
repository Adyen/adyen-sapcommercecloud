package com.adyen.commerce.connector.reconciliation.impl;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.PK;
import de.hybris.platform.servicelayer.model.ModelService;

@UnitTest
public class DefaultSubscriptionReconciliationServiceTest
{
	private static final Instant NOW = Instant.parse("2026-08-06T10:00:00Z");
	private static final Instant PLATFORM_UPDATED = Instant.parse("2026-08-06T09:30:00Z");

	@Mock
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	@Mock
	private SubscriptionBillingConnector connector;
	@Mock
	private ModelService modelService;
	@Mock
	private BillingSubscriptionRefModel model;

	private DefaultSubscriptionReconciliationService service;

	@Before
	public void setUp() throws Exception
	{
		MockitoAnnotations.openMocks(this);
		service = new DefaultSubscriptionReconciliationService();
		service.setConnectorRegistry(connectorRegistry);
		service.setModelService(modelService);
		service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
		when(model.getPlatform()).thenReturn(BillingPlatform.RECURLY);
		when(model.getExternalSubscriptionId()).thenReturn("uuid-sub");
		when(connectorRegistry.getConnector(BillingPlatform.RECURLY)).thenReturn(connector);
	}

	@Test
	public void appliesAuthoritativeSnapshotAfterMissedWebhook() throws Exception
	{
		final BillingSubscriptionRef ref = new BillingSubscriptionRef(BillingPlatform.RECURLY, "uuid-sub");
		final NormalizedSubscription snapshot = new NormalizedSubscription(ref,
				NormalizedSubscriptionStatus.PAST_DUE, "annual", 3,
				Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2027-08-01T00:00:00Z"), true,
				PLATFORM_UPDATED);
		when(connector.fetchSubscription(ref)).thenReturn(snapshot);

		assertSame(snapshot, service.reconcile(model));

		verify(model).setStatus("PAST_DUE");
		verify(model).setPlanCode("annual");
		verify(model).setQuantity(3);
		verify(modelService).setAttributeValue(model, "cancelAtPeriodEnd", Boolean.TRUE);
		verify(modelService).setAttributeValue(model, "platformUpdatedAt", Date.from(PLATFORM_UPDATED));
		verify(modelService).setAttributeValue(model, "lastReconciledAt", Date.from(NOW));
		verify(modelService).save(model);
	}

	@Test
	public void olderPlatformSnapshotCannotOverwriteNewerLocalProjection() throws Exception
	{
		when(model.getPlatformUpdatedAt()).thenReturn(Date.from(PLATFORM_UPDATED));
		final NormalizedSubscription older = new NormalizedSubscription(
				new BillingSubscriptionRef(BillingPlatform.RECURLY, "uuid-sub"),
				NormalizedSubscriptionStatus.CANCELLED, "old-plan", 1, null, null, true,
				PLATFORM_UPDATED.minusSeconds(60));
		when(connector.fetchSubscription(anyRef())).thenReturn(older);

		assertSame(older, service.reconcile(model));

		verify(model, never()).setStatus("CANCELLED");
		verify(model, never()).setPlanCode("old-plan");
		verify(modelService).setAttributeValue(model, "lastReconciledAt", Date.from(NOW));
		verify(modelService).setAttributeValue(model, "lastSyncedAt", Date.from(NOW));
		verify(modelService).save(model);
	}

	@Test
	public void skipsExplicitRowLockWhenDatabaseDoesNotSupportIt() throws Exception
	{
		final PK pk = PK.fromLong(8796093022208L);
		when(model.getPk()).thenReturn(pk);
		when(connector.fetchSubscription(anyRef())).thenReturn(snapshot());
		service.setExplicitRowLockingSupported(false);

		service.reconcile(model);

		verify(modelService, never()).lock(pk);
		verify(modelService).refresh(model);
	}

	@Test
	public void locksPersistedSubscriptionWhenDatabaseSupportsIt() throws Exception
	{
		final PK pk = PK.fromLong(8796093022208L);
		when(model.getPk()).thenReturn(pk);
		when(connector.fetchSubscription(anyRef())).thenReturn(snapshot());
		service.setExplicitRowLockingSupported(true);

		service.reconcile(model);

		verify(modelService).lock(pk);
		verify(modelService).refresh(model);
	}

	private NormalizedSubscription snapshot()
	{
		return new NormalizedSubscription(
				new BillingSubscriptionRef(BillingPlatform.RECURLY, "uuid-sub"),
				NormalizedSubscriptionStatus.ACTIVE, "monthly", 1, null, null, false,
				PLATFORM_UPDATED);
	}

	private BillingSubscriptionRef anyRef()
	{
		return org.mockito.ArgumentMatchers.any(BillingSubscriptionRef.class);
	}
}
