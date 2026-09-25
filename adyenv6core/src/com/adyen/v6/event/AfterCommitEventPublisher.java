package com.adyen.v6.event;

import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.event.events.AbstractEvent;
import de.hybris.platform.tx.Transaction;

/**
 * Publishes an event only once the transaction that produced the fact it announces has committed.
 *
 * <p>EventService multicasts asynchronously by default, so listeners run on another thread and read the
 * order back from the database. Publishing while this request's transaction is still open races that read:
 * a listener can see the order as it was before the write, or act on something whose transaction then rolls
 * back and leaves nothing behind. After-commit is the only point at which the fact is actually durable, and
 * a rollback simply drops the registration — which is the right outcome for a change the database never
 * kept.</p>
 *
 * <p>Shared by every Adyen publisher rather than copied into each: the reasoning below about registration
 * identity is subtle enough that a second hand-written copy is how two publishers quietly stop agreeing.</p>
 */
public abstract class AfterCommitEventPublisher {

    private EventService eventService;

    /**
     * Publishes once the surrounding transaction commits, or immediately when there is none.
     *
     * <p>With no transaction running every save has already committed on its own, so there is nothing to
     * wait for and the event goes out at once.</p>
     */
    protected void publishAfterCommit(final AbstractEvent event) {
        final Transaction transaction = runningTransaction();
        if (transaction == null) {
            eventService.publishEvent(event);
            return;
        }

        transaction.executeOnCommit(new Transaction.TransactionAwareExecution() {
            @Override
            public void execute(final Transaction committed) {
                eventService.publishEvent(event);
            }

            @Override
            public Object getId() {
                // The registrations live in a Set whose key is String.valueOf(getId()) + the execution's own
                // class name. Returning the event itself would not separate two of them committing together:
                // AbstractEvent.toString() is class + source + scope, and these events carry neither, so every
                // instance stringifies identically and the second registration would be dropped.
                return event.getClass().getName() + '@' + System.identityHashCode(event);
            }
        });
    }

    /**
     * The transaction this thread is inside, or {@code null} when it is inside none.
     *
     * <p>A seam as much as a shorthand: {@code Transaction.current()} needs the platform's transaction
     * factory to hand out a transaction at all, which a plain unit test has no way to provide.</p>
     */
    protected Transaction runningTransaction() {
        final Transaction transaction = Transaction.current();
        return transaction != null && transaction.isRunning() ? transaction : null;
    }

    public void setEventService(final EventService eventService) {
        this.eventService = eventService;
    }
}
