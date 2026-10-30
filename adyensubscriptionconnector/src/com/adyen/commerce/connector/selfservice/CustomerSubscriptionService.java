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
package com.adyen.commerce.connector.selfservice;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;
import com.adyen.commerce.connector.dto.PaymentMethodChangeSupport;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentPage;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentSupport;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;

import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.store.BaseStoreModel;

/**
 * What a shopper may see of and do to their own subscriptions.
 *
 * <p>Every call that reaches a billing platform runs with the subscription's own store in context, because
 * connectors read their credentials from it and a customer is global across stores. Capabilities are read
 * from the subscription's own platform, never from the store's active one: after a store switches platform
 * its earlier subscriptions still live on the old one.</p>
 *
 * <p>Ownership is the caller's to establish through {@link #findSubscription}; the methods taking a
 * subscription trust that it is the customer's.</p>
 */
public interface CustomerSubscriptionService
{
	/** Platforms with a loaded connector, in registration order; empty when none is loaded. */
	Set<BillingPlatform> getAvailablePlatforms();

	/** The customer's subscriptions, latest period end first. */
	List<BillingSubscriptionRefModel> getSubscriptions(CustomerModel customer);

	/**
	 * The customer's subscription with this public code. Empty for a code that is unknown or belongs to
	 * somebody else, which are not told apart.
	 */
	Optional<BillingSubscriptionRefModel> findSubscription(CustomerModel customer, String code);

	/**
	 * Activations given up on for this customer's orders: paid for, never created, no longer retried. An
	 * attempt still being retried is expected to succeed and is not included.
	 */
	List<BillingActivationAttemptModel> getAbandonedActivations(CustomerModel customer);

	SubscriptionLifecycleState getState(BillingSubscriptionRefModel subscription);

	/** The store of the subscription's originating order, or {@code null} when there is none to act with. */
	BaseStoreModel getStore(BillingSubscriptionRefModel subscription);

	/**
	 * What makes two subscriptions share one set of payment methods: the store selecting the platform
	 * account, the platform, and the customer reference under it. The customer reference alone is not
	 * enough, since it is minted from the shopper's global id and repeats on every store of a platform.
	 */
	String getBillingAccount(BillingSubscriptionRefModel subscription);

	/** What the subscription's connector declares; {@code NONE} when it has no store or no loaded connector. */
	PaymentMethodChangeSupport getPaymentMethodChangeSupport(BillingSubscriptionRefModel subscription);

	/** What the subscription's connector declares; {@code NONE} when it has no store or no loaded connector. */
	PaymentMethodEnrollmentSupport getPaymentMethodEnrollmentSupport(BillingSubscriptionRefModel subscription);

	/** The methods the platform holds for the subscription's account and accepts back. */
	List<PlatformPaymentMethod> getPlatformPaymentMethods(BillingSubscriptionRefModel subscription)
			throws BillingException;

	/**
	 * The shopper's Adyen-vaulted cards the subscription's platform would accept, read against the
	 * subscription's store. Empty when the connector takes no vaulted card or the vault cannot be read.
	 * A platform needing a network transaction id is offered only cards that have one.
	 */
	List<PlatformPaymentMethod> getAcceptedVaultedCards(CustomerModel customer,
			BillingSubscriptionRefModel subscription);

	/**
	 * Which of {@link #getPlatformPaymentMethods} bills the subscription now, or {@code null} when nothing
	 * is recorded or the connector cannot say.
	 */
	String getCurrentPaymentMethodId(BillingSubscriptionRefModel subscription);

	/**
	 * Points the subscription's billing at a payment method the shopper holds, the id read as the
	 * connector's declared sources allow.
	 *
	 * @return the scope the platform applied
	 * @throws PaymentMethodNotOwnedException when the id is not one of the shopper's
	 * @throws com.adyen.commerce.connector.exception.CapabilityUnsupportedException when the platform cannot
	 *         change it at all
	 * @throws com.adyen.commerce.connector.exception.PreconditionFailedException when the subscription has
	 *         no store or is in a state where a change achieves nothing
	 */
	PaymentMethodChangeScope changePaymentMethod(CustomerModel customer, BillingSubscriptionRefModel subscription,
			String paymentMethodId) throws BillingException;

	/**
	 * The customer's other subscriptions on the same billing account whose payment method could be changed
	 * now. Rows that could not be acted on are left out rather than reported.
	 */
	List<BillingSubscriptionRefModel> getSubscriptionsSharingBillingAccount(CustomerModel customer,
			BillingSubscriptionRefModel subscription);

	/**
	 * Stops the subscription at the end of the period already paid for. Never immediately: an immediate
	 * cancellation ends service at once and no refund instruction is sent.
	 *
	 * @throws com.adyen.commerce.connector.exception.PreconditionFailedException when it has no store or is
	 *         not in a cancellable state
	 */
	void cancelAtPeriodEnd(BillingSubscriptionRefModel subscription) throws BillingException;

	/**
	 * The platform's own page for giving it a payment method. Asked for only when the shopper wants to go
	 * there: on some platforms the address is a credential.
	 */
	Optional<PaymentMethodEnrollmentPage> getPaymentMethodEnrollmentPage(BillingSubscriptionRefModel subscription)
			throws BillingException;
}
