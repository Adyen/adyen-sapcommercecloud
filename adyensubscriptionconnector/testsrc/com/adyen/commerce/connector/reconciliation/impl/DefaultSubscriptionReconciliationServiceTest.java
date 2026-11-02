package com.adyen.commerce.connector.reconciliation.impl;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.commerce.connector.context.SubscriptionStoreContext;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.NormalizedSubscription;
import com.adyen.commerce.connector.dto.NormalizedSubscriptionStatus;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.exception.SubscriptionNotFoundException;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.PK;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;

@UnitTest
public class DefaultSubscriptionReconciliationServiceTest
{
	private static final BillingPlatform RECURLY = BillingPlatform.valueOf("RECURLY");
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
	@Mock
	private SubscriptionStoreContext storeContext;
	@Mock
	private BaseStoreModel store;
	@Mock
	private ConfigurationService configurationService;
	@Mock
	private Configuration configuration;

	private DefaultSubscriptionReconciliationService service;

	@Before
	public void setUp() throws Exception
	{
		MockitoAnnotations.openMocks(this);
		service = new DefaultSubscriptionReconciliationService();
		service.setConnectorRegistry(connectorRegistry);
		service.setModelService(modelService);
		service.setStoreContext(storeContext);
		service.setConfigurationService(configurationService);
		when(configurationService.getConfiguration()).thenReturn(configuration);
		when(configuration.getInt(DefaultSubscriptionReconciliationService.NOT_FOUND_THRESHOLD, 3)).thenReturn(3);
		service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
		when(storeContext.callInStoreOf(eq(model), any())).thenAnswer(
				invocation -> invocation.<SubscriptionStoreContext.StoreBoundWork<?>> getArgument(1).call(store));
		when(model.getPlatform()).thenReturn(RECURLY);
		when(model.getExternalSubscriptionId()).thenReturn("uuid-sub");
		when(connectorRegistry.getConnector(RECURLY)).thenReturn(connector);
	}

	@Test
	public void appliesAuthoritativeSnapshotAfterMissedWebhook() throws Exception
	{
		final BillingSubscriptionRef ref = new BillingSubscriptionRef(RECURLY, "uuid-sub");
		final NormalizedSubscription snapshot = new NormalizedSubscription(ref,
				NormalizedSubscriptionStatus.PAST_DUE, "annual", 3,
				Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2027-08-01T00:00:00Z"), true,
				PLATFORM_UPDATED);
		when(connector.fetchSubscription(ref)).thenReturn(snapshot);

		assertSame(snapshot, service.reconcile(model));

		verify(model).setStatus("PAST_DUE");
		verify(model).setPlanCode("annual");
		verify(model).setQuantity(3);
		verify(model).setCancelAtPeriodEnd(Boolean.TRUE);
		verify(model).setPlatformUpdatedAt(Date.from(PLATFORM_UPDATED));
		verify(model).setLastReconciledAt(Date.from(NOW));
		verify(model).setLastSyncedAt(Date.from(NOW));
		verify(modelService).save(model);
	}

	/**
	 * Reflective writes type-check at runtime rather than at compile time, so a renamed attribute would only
	 * surface as a failing reconciliation. Every attribute this service writes has a generated setter.
	 */
	@Test
	public void writesEveryAttributeThroughGeneratedSettersRatherThanReflectively() throws Exception
	{
		when(connector.fetchSubscription(anyRef())).thenReturn(snapshot());

		service.reconcile(model);

		// The type witness picks the (Object, String, Object) overload. Left to infer, the value matcher
		// resolves to the localized Map overload, a different method after erasure, and the verification
		// would pass however many plain reflective writes the service made.
		verify(modelService, never())
				.setAttributeValue(any(), anyString(), org.mockito.ArgumentMatchers.<Object> any());
	}

	@Test
	public void olderPlatformSnapshotCannotOverwriteNewerLocalProjection() throws Exception
	{
		when(model.getPlatformUpdatedAt()).thenReturn(Date.from(PLATFORM_UPDATED));
		final NormalizedSubscription older = new NormalizedSubscription(
				new BillingSubscriptionRef(RECURLY, "uuid-sub"),
				NormalizedSubscriptionStatus.CANCELLED, "old-plan", 1, null, null, true,
				PLATFORM_UPDATED.minusSeconds(60));
		when(connector.fetchSubscription(anyRef())).thenReturn(older);

		assertSame(older, service.reconcile(model));

		verify(model, never()).setStatus("CANCELLED");
		verify(model, never()).setPlanCode("old-plan");
		verify(model).setLastReconciledAt(Date.from(NOW));
		verify(model).setLastSyncedAt(Date.from(NOW));
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

	@Test
	public void firstNotFoundIsCountedWithoutChangingTheProjection() throws Exception
	{
		when(connector.fetchSubscription(anyRef())).thenThrow(new SubscriptionNotFoundException("HTTP 404"));

		assertThrows(SubscriptionNotFoundException.class, () -> service.reconcile(model));

		verify(model).setNotFoundCount(1);
		verify(model).setNotFoundSince(Date.from(NOW));
		verify(model).setLastReconciledAt(Date.from(NOW));
		verify(model).setLastSyncedAt(Date.from(NOW));
		verify(model, never()).setStatus(any());
		verify(model, never()).setPlanCode(any());
		verify(model, never()).setQuantity(any());
		verify(model, never()).setCurrentPeriodStart(any());
		verify(model, never()).setCurrentPeriodEnd(any());
		verify(model, never()).setCancelAtPeriodEnd(any());
		verify(model, never()).setPlatformUpdatedAt(any());
		verify(model, never()).setExternalSubscriptionId(any());
		verify(model, never()).setOrder(any());
		verify(modelService).save(model);
	}

	@Test
	public void notFoundKeepsTheOriginalFirstMissTimestamp() throws Exception
	{
		final Date firstMiss = Date.from(NOW.minusSeconds(3600));
		when(model.getNotFoundCount()).thenReturn(1);
		when(model.getNotFoundSince()).thenReturn(firstMiss);
		when(connector.fetchSubscription(anyRef())).thenThrow(new SubscriptionNotFoundException("HTTP 404"));

		assertThrows(SubscriptionNotFoundException.class, () -> service.reconcile(model));

		verify(model).setNotFoundCount(2);
		verify(model, never()).setNotFoundSince(any());
		verify(model, never()).setStatus(any());
	}

	@Test
	public void thresholdNotFoundMarksTheReferenceUnknown() throws Exception
	{
		when(model.getNotFoundCount()).thenReturn(2);
		when(model.getNotFoundSince()).thenReturn(Date.from(NOW.minusSeconds(3600)));
		when(connector.fetchSubscription(anyRef())).thenThrow(new SubscriptionNotFoundException("HTTP 404"));

		assertThrows(SubscriptionNotFoundException.class, () -> service.reconcile(model));

		verify(model).setNotFoundCount(3);
		verify(model).setStatus("UNKNOWN");
		verify(model, never()).setPlanCode(any());
		verify(modelService).save(model);
	}

	@Test
	public void notFoundThresholdComesFromConfiguration() throws Exception
	{
		when(configuration.getInt(DefaultSubscriptionReconciliationService.NOT_FOUND_THRESHOLD, 3)).thenReturn(1);
		when(connector.fetchSubscription(anyRef())).thenThrow(new SubscriptionNotFoundException("HTTP 404"));

		assertThrows(SubscriptionNotFoundException.class, () -> service.reconcile(model));

		verify(model).setStatus("UNKNOWN");
	}

	@Test
	public void notFoundIsRecordedUnderTheRowLockAfterRefresh() throws Exception
	{
		final PK pk = PK.fromLong(8796093022208L);
		when(model.getPk()).thenReturn(pk);
		service.setExplicitRowLockingSupported(true);
		when(connector.fetchSubscription(anyRef())).thenThrow(new SubscriptionNotFoundException("HTTP 404"));

		assertThrows(SubscriptionNotFoundException.class, () -> service.reconcile(model));

		final org.mockito.InOrder order = inOrder(modelService, model);
		order.verify(modelService).lock(pk);
		order.verify(modelService).refresh(model);
		order.verify(model).setNotFoundCount(1);
		order.verify(modelService).save(model);
	}

	@Test
	public void successfulReadAfterMissesResetsTheNotFoundState() throws Exception
	{
		when(model.getNotFoundCount()).thenReturn(2);
		when(model.getNotFoundSince()).thenReturn(Date.from(NOW.minusSeconds(3600)));
		when(connector.fetchSubscription(anyRef())).thenReturn(snapshot());

		service.reconcile(model);

		verify(model).setNotFoundCount(null);
		verify(model).setNotFoundSince(null);
	}

	@Test
	public void staleSnapshotIsStillASuccessfulReadAndResetsTheNotFoundState() throws Exception
	{
		when(model.getPlatformUpdatedAt()).thenReturn(Date.from(PLATFORM_UPDATED));
		when(connector.fetchSubscription(anyRef())).thenReturn(new NormalizedSubscription(
				new BillingSubscriptionRef(RECURLY, "uuid-sub"), NormalizedSubscriptionStatus.ACTIVE, "p", 1,
				null, null, false, PLATFORM_UPDATED.minusSeconds(60)));

		service.reconcile(model);

		verify(model).setNotFoundCount(null);
		verify(model).setNotFoundSince(null);
	}

	@Test
	public void notFoundDoesNotOverwriteATerminalStatusButIsStillCounted() throws Exception
	{
		for (final String terminal : new String[] { "CANCELLED", "FAILED", "EXPIRED" })
		{
			org.mockito.Mockito.clearInvocations(model);
			when(model.getStatus()).thenReturn(terminal);
			when(model.getNotFoundCount()).thenReturn(5);
			when(connector.fetchSubscription(anyRef())).thenThrow(new SubscriptionNotFoundException("HTTP 404"));

			assertThrows(SubscriptionNotFoundException.class, () -> service.reconcile(model));

			verify(model).setNotFoundCount(6);
			verify(model).setNotFoundSince(Date.from(NOW));
			verify(model, never()).setStatus(any());
		}
	}

	@Test
	public void notFoundIsNotRecordedWhenAnotherReaderSucceededMeanwhile() throws Exception
	{
		when(model.getLastReconciledAt()).thenReturn(Date.from(NOW.plusSeconds(1)));
		when(connector.fetchSubscription(anyRef())).thenThrow(new SubscriptionNotFoundException("HTTP 404"));

		assertThrows(SubscriptionNotFoundException.class, () -> service.reconcile(model));

		verify(model, never()).setNotFoundCount(any());
		verify(model, never()).setStatus(any());
		verify(modelService, never()).save(any());
	}

	@Test
	public void notFoundIsRecordedWhenTheEarlierReadWasBeforeTheFetch() throws Exception
	{
		when(model.getLastReconciledAt()).thenReturn(Date.from(NOW.minusSeconds(60)));
		when(connector.fetchSubscription(anyRef())).thenThrow(new SubscriptionNotFoundException("HTTP 404"));

		assertThrows(SubscriptionNotFoundException.class, () -> service.reconcile(model));

		verify(model).setNotFoundCount(1);
	}

	@Test
	public void alreadyUnknownReferenceIsNotMarkedAgain() throws Exception
	{
		when(model.getStatus()).thenReturn("UNKNOWN");
		when(model.getNotFoundCount()).thenReturn(4);
		when(model.getNotFoundSince()).thenReturn(Date.from(NOW.minusSeconds(3600)));
		when(connector.fetchSubscription(anyRef())).thenThrow(new SubscriptionNotFoundException("HTTP 404"));

		assertThrows(SubscriptionNotFoundException.class, () -> service.reconcile(model));

		verify(model).setNotFoundCount(5);
		verify(model, never()).setStatus(any());
	}

	@Test
	public void loweredThresholdMarksAnAlreadyCountedActiveReferenceUnknown() throws Exception
	{
		when(model.getStatus()).thenReturn("ACTIVE");
		when(model.getNotFoundCount()).thenReturn(4);
		when(model.getNotFoundSince()).thenReturn(Date.from(NOW.minusSeconds(3600)));
		when(configuration.getInt(DefaultSubscriptionReconciliationService.NOT_FOUND_THRESHOLD, 3)).thenReturn(2);
		when(connector.fetchSubscription(anyRef())).thenThrow(new SubscriptionNotFoundException("HTTP 404"));

		assertThrows(SubscriptionNotFoundException.class, () -> service.reconcile(model));

		verify(model).setStatus("UNKNOWN");
	}

	@Test
	public void nonPositiveThresholdIsClampedToOne() throws Exception
	{
		for (final int configured : new int[] { 0, -5 })
		{
			org.mockito.Mockito.clearInvocations(model);
			when(configuration.getInt(DefaultSubscriptionReconciliationService.NOT_FOUND_THRESHOLD, 3))
					.thenReturn(configured);
			when(connector.fetchSubscription(anyRef())).thenThrow(new SubscriptionNotFoundException("HTTP 404"));

			assertThrows(SubscriptionNotFoundException.class, () -> service.reconcile(model));

			verify(model).setStatus("UNKNOWN");
		}
	}

	@Test
	public void failureWhileRecordingTheMissKeepsTheOriginalNotFoundAsSuppressed() throws Exception
	{
		final SubscriptionNotFoundException notFound = new SubscriptionNotFoundException("HTTP 404");
		when(connector.fetchSubscription(anyRef())).thenThrow(notFound);
		org.mockito.Mockito.doThrow(new IllegalStateException("db down")).when(modelService).save(model);

		final IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.reconcile(model));

		assertSame(notFound, thrown.getSuppressed()[0]);
	}

	private NormalizedSubscription snapshot()
	{
		return new NormalizedSubscription(
				new BillingSubscriptionRef(RECURLY, "uuid-sub"),
				NormalizedSubscriptionStatus.ACTIVE, "monthly", 1, null, null, false,
				PLATFORM_UPDATED);
	}

	private BillingSubscriptionRef anyRef()
	{
		return org.mockito.ArgumentMatchers.any(BillingSubscriptionRef.class);
	}

	/** Without its own store the platform is not read at all, rather than read with the caller's credentials. */
	@Test
	public void readsNothingWhenTheSubscriptionsStoreCannotBeEstablished() throws Exception
	{
		when(storeContext.callInStoreOf(eq(model), any())).thenThrow(new PreconditionFailedException("no store"));

		assertThrows(PreconditionFailedException.class, () -> service.reconcile(model));

		verify(connector, never()).fetchSubscription(any());
		verify(modelService, never()).save(any());
	}
}
