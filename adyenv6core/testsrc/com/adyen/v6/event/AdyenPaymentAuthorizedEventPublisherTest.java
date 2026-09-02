package com.adyen.v6.event;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.tx.Transaction;

/**
 * When an authorization actually reaches the listeners: never before the transaction that wrote the token
 * has committed.
 */
@UnitTest
public class AdyenPaymentAuthorizedEventPublisherTest
{
    private TestablePublisher publisher;
    private EventService eventService;
    private OrderModel order;

    @Before
    public void setUp()
    {
        eventService = mock(EventService.class);
        order = mock(OrderModel.class);

        publisher = new TestablePublisher();
        publisher.setEventService(eventService);
    }

    /**
     * The listeners read the token back from the database on another thread, so publishing while the
     * transaction that wrote it is still open is the bug this guards against.
     */
    @Test
    public void holdsTheEventBackUntilTheTransactionCommits() throws Exception
    {
        publisher.transaction = mock(Transaction.class);

        publisher.publishAuthorized(order);

        verify(eventService, never()).publishEvent(any(AdyenPaymentAuthorizedEvent.class));

        final ArgumentCaptor<Transaction.TransactionAwareExecution> onCommit =
                ArgumentCaptor.forClass(Transaction.TransactionAwareExecution.class);
        verify(publisher.transaction).executeOnCommit(onCommit.capture());
        onCommit.getValue().execute(publisher.transaction);

        final ArgumentCaptor<AdyenPaymentAuthorizedEvent> published =
                ArgumentCaptor.forClass(AdyenPaymentAuthorizedEvent.class);
        verify(eventService).publishEvent(published.capture());
        assertSame(order, published.getValue().getOrder());
    }

    @Test
    public void publishesImmediatelyWhenNoTransactionIsRunning()
    {
        publisher.transaction = null;

        publisher.publishAuthorized(order);

        verify(eventService).publishEvent(any(AdyenPaymentAuthorizedEvent.class));
    }

    /**
     * Two authorizations committing together must both be announced. The platform keys its on-commit
     * registrations on the execution's id, and this event stringifies identically for every instance, so an
     * id derived from the event's identity is what keeps the second registration from replacing the first.
     */
    @Test
    public void keepsTwoAuthorizationsInTheSameTransactionApart() throws Exception
    {
        publisher.transaction = mock(Transaction.class);

        publisher.publishAuthorized(order);
        publisher.publishAuthorized(mock(OrderModel.class));

        final ArgumentCaptor<Transaction.TransactionAwareExecution> onCommit =
                ArgumentCaptor.forClass(Transaction.TransactionAwareExecution.class);
        verify(publisher.transaction, org.mockito.Mockito.times(2)).executeOnCommit(onCommit.capture());

        assertNotEquals("the two registrations must not collapse into one",
                String.valueOf(onCommit.getAllValues().get(0).getId()),
                String.valueOf(onCommit.getAllValues().get(1).getId()));
    }

    /**
     * An activation is journalled against its order, so one without an order could not be recorded, retried
     * or found afterwards. Nothing is announced rather than announcing something nobody can act on.
     */
    @Test
    public void publishesNothingWithoutAnOrder()
    {
        publisher.transaction = null;

        publisher.publishAuthorized(null);

        verify(eventService, never()).publishEvent(any(AdyenPaymentAuthorizedEvent.class));
    }

    /**
     * Supplies the transaction rather than letting the publisher ask the platform for it: with no booted
     * platform there is no transaction factory for {@code Transaction.current()} to use.
     */
    private static class TestablePublisher extends AdyenPaymentAuthorizedEventPublisher
    {
        private Transaction transaction;

        @Override
        protected Transaction runningTransaction()
        {
            return transaction;
        }
    }
}
