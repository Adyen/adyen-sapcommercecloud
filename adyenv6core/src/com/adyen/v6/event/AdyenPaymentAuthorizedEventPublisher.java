package com.adyen.v6.event;

import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.tx.Transaction;

/**
 * Announces an Adyen authorization to whoever is listening, but only once the transaction that persisted
 * it has committed.
 *
 * <p>Shared rather than inlined at each call site because there is more than one moment at which an order
 * becomes authorized-and-tokenized: an ordinary payment reaches it inside the place-order call, a 3DS or
 * redirect payment only when the shopper returns. Both have to announce the same thing under the same
 * durability rule, and a second hand-written copy of that rule is how the two would quietly stop agreeing.</p>
 */
public class AdyenPaymentAuthorizedEventPublisher {

    private EventService eventService;

    /**
     * Publishes once the surrounding transaction commits, or immediately when there is none.
     *
     * <p>EventService multicasts asynchronously by default, so the listeners run on another thread and read
     * the order and its PaymentInfo back from the database. Publishing while this request's transaction is
     * still open races that read: a listener can see the PaymentInfo as it was before the token and
     * networkTxReference were written, or act on an authorization whose transaction then rolls back and
     * leaves nothing behind. After-commit is the only point at which "the token is durable" is actually
     * true, and a rollback simply drops the registration - which is the right outcome for an authorization
     * the database never kept.</p>
     *
     * <p>With no transaction running every save has already committed on its own, so there is nothing to
     * wait for and the event goes out immediately.</p>
     *
     * @param orderModel the authorized order; {@code null} is tolerated and publishes nothing, because an
     *                   activation without an order could not be recorded or retried anyway
     */
    public void publishAuthorized(final OrderModel orderModel) {
        if (orderModel == null) {
            return;
        }

        final AdyenPaymentAuthorizedEvent event = new AdyenPaymentAuthorizedEvent(orderModel);
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
                // class name. Returning the event itself would not separate two authorizations committing
                // together: AbstractEvent.toString() is class + source + scope, and this event carries neither,
                // so every instance stringifies identically and the second registration would be dropped.
                return AdyenPaymentAuthorizedEvent.class.getName() + '@' + System.identityHashCode(event);
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
