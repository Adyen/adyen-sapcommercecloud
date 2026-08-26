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
package com.adyen.commerce.connector.activation;

import java.time.Instant;
import java.util.List;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.retry.RetryVerdict;

import de.hybris.platform.core.model.order.OrderModel;

/**
 * The journal of activation attempts: which key was sent for which order, and what came back.
 *
 * <p>Every attempt goes through {@link #begin} and ends in exactly one of {@link #succeeded} or
 * {@link #failed}, so an order that has been tried always has a row saying so. That is the difference
 * between "the subscription is not there" and "the subscription is not there and nobody noticed".</p>
 */
public interface BillingActivationAttemptService
{
	/** An attempt is in flight right now. */
	String STATUS_PENDING = "PENDING";

	/** The platform created the subscription; {@code subscriptionRef} points at the result. */
	String STATUS_SUCCEEDED = "SUCCEEDED";

	/** Transient failure, still due another attempt at {@code nextAttemptAt}. The retry job's queue. */
	String STATUS_FAILED = "FAILED";

	/** Given up on, either as terminal or out of retries. Nothing will touch it again but an operator. */
	String STATUS_DEAD_LETTER = "DEAD_LETTER";

	/**
	 * The order turned out not to be a subscription order after all. Reached only from a row opened because
	 * the rule could not classify a product: once it can, and the answer is "no", the row has to be closed as
	 * a non-event rather than left in {@link #STATUS_FAILED} for the retry job to abandon. Without it a single
	 * resolver blip on an ordinary order ends as a {@code DEAD_LETTER} announcing that a shopper was charged
	 * for a subscription they never bought.
	 */
	String STATUS_NOT_APPLICABLE = "NOT_APPLICABLE";

	/**
	 * Opens or re-opens the record for this order and counts the attempt about to run. Written before the
	 * platform is called, not after, so a crash mid-call still leaves evidence that something was tried.
	 */
	BillingActivationAttemptModel begin(OrderModel order, BillingPlatform platform, String productCode,
			String idempotencyKey);

	/**
	 * Records the outcome that closes the record.
	 */
	void succeeded(BillingActivationAttemptModel attempt, BillingSubscriptionRefModel subscriptionRef);

	/**
	 * Records a failed attempt and applies the retry policy to it: either a due date, or the dead letter.
	 *
	 * @return what was decided, so the caller can log the reason rather than guess at it
	 */
	RetryVerdict failed(BillingActivationAttemptModel attempt, Throwable failure);

	/**
	 * Dead-letters an attempt that could not even be tried again, without counting it as a failure.
	 *
	 * <p>Distinct from {@link #failed} because there is no exception to classify: the retry ran and the
	 * activation declined to happen at all. Something about the order or its store no longer adds up, and
	 * leaving the row queued would mean re-reading it on every run of the job for ever.</p>
	 */
	void abandon(BillingActivationAttemptModel attempt, String reason);

	/**
	 * Closes any still-open row for this order as {@link #STATUS_NOT_APPLICABLE}. A no-op when there is no
	 * row, which is the ordinary case: nothing is written for an order that was never a subscription order.
	 */
	void notApplicable(OrderModel order, BillingPlatform platform, String reason);

	/**
	 * The retry queue: attempts whose next try has come round, oldest first.
	 *
	 * <p>Also returns attempts still marked {@code PENDING} whose last attempt started before
	 * {@code stalePendingBefore}. Those are in-flight records whose thread never came back — the node was
	 * killed, the JVM went down — and without this they would sit at {@code PENDING} with no due date and
	 * never be looked at again, which is the exact disappearance this journal exists to prevent.</p>
	 *
	 * @param limit the most to return, so one job run cannot take on an unbounded queue
	 */
	List<BillingActivationAttemptModel> findDue(Instant now, Instant stalePendingBefore, int limit);
}
