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
package com.adyen.commerce.connector.payment;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.adyen.commerce.connector.cart.CartAdjustment;
import com.adyen.commerce.connector.cart.CartAssessment;
import com.adyen.commerce.connector.cart.SubscriptionCartGuard;
import com.adyen.commerce.connector.cart.SubscriptionCartStatus;
import com.adyen.commerce.connector.duplicate.DuplicateCheckRequest;
import com.adyen.commerce.connector.duplicate.DuplicateCheckStage;
import com.adyen.commerce.connector.duplicate.Freshness;
import com.adyen.commerce.connector.duplicate.SubscriptionOwnershipService;
import com.adyen.v6.service.PaymentAuthorisationGuard;
import com.adyen.v6.service.PaymentAuthorisationRefusedException;
import com.adyen.v6.service.PaymentAuthorisationStage;

import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;

/**
 * The last line of defence against buying a subscription twice: refuses the payment before Adyen is called when the
 * cart holds more than one subscription product or a quantity above one, or a subscription the customer already
 * owns. It is what stops a second browser tab, a merged cart that skipped validation and a direct API call; the
 * cart hooks are the courtesy in front of it.
 *
 * <p>The per-cart limits come from {@link SubscriptionCartGuard} and the cart is left as it is. Ownership is asked
 * with {@link Freshness#REFRESH}, so a stale local record is checked against the billing platform first; a platform
 * that does not answer leaves the decision to local data (the ownership service never throws on that). An anonymous
 * or guest customer owns nothing, so only the limits apply to them.</p>
 *
 * <p>The gift-card path runs this check up to three times for one purchase and PayPal twice, each with a vendor
 * listing. A REFRESH check that found no block is therefore remembered in the session, per cart and per fingerprint
 * of its subscription entries (product codes and quantities), for {@code refreshReuseSeconds}; later checks of the
 * same cart and fingerprint within that time ask with {@link Freshness#LOCAL}. A block is never remembered, and a
 * changed entry, another cart or an expired mark brings the REFRESH back.</p>
 *
 * <p>No transaction is open around the payment request on any caller (the checkout facades use short transaction
 * templates around their saves only), so the refresh the ownership service reconciles with its own transaction
 * cannot mark an outer one rollback-only.</p>
 */
public class AdyenSubscriptionPaymentAuthorisationGuard implements PaymentAuthorisationGuard
{
	/** Where My Subscriptions lives on the accelerator storefront; Spartacus has no such page yet. */
	public static final String MY_SUBSCRIPTIONS_PATH = "/my-account/subscriptions";

	/** Session attribute prefix of the "no block found by a REFRESH check" mark; the cart code is appended. */
	public static final String REFRESH_MARK_ATTRIBUTE_PREFIX = "adyen.subscription.paymentGuard.refreshed.";

	private int refreshReuseSeconds = 120;
	private SessionService sessionService;
	private SubscriptionCartGuard subscriptionCartGuard;
	private SubscriptionOwnershipService subscriptionOwnershipService;
	private UserService userService;

	@Override
	public void beforeAuthorisation(final AbstractOrderModel cart, final PaymentAuthorisationStage stage)
	{
		if (!(cart instanceof CartModel cartModel))
		{
			return;
		}
		final CartAssessment assessment = getSubscriptionCartGuard().assessForAuthorisation(cartModel);
		if (!assessment.hasSubscription())
		{
			return;
		}
		final List<CartAdjustment> violations = assessment.limitViolations();
		if (!violations.isEmpty())
		{
			throw refusal(violations.get(0).statusCode());
		}
		final CustomerModel customer = ownerOf(cart);
		if (customer == null)
		{
			return;
		}
		final String fingerprint = fingerprintOf(customer, assessment.subscriptionEntries());
		final boolean reuse = isRefreshReusable(cart, fingerprint);
		final Freshness freshness = reuse ? Freshness.LOCAL : Freshness.REFRESH;
		final Set<String> asked = new HashSet<>();
		for (final AbstractOrderEntryModel entry : assessment.subscriptionEntries())
		{
			if (asked.add(entry.getProduct().getCode())
					&& getSubscriptionOwnershipService().findBlocking(new DuplicateCheckRequest(customer, entry.getProduct(),
							assessment.store(), freshness, cart, DuplicateCheckStage.PRE_AUTH)).isPresent())
			{
				forgetRefresh(cart);
				throw refusal(SubscriptionCartStatus.ALREADY_OWNED);
			}
		}
		if (!reuse)
		{
			rememberRefresh(cart, fingerprint);
		}
	}

	/** Whether a REFRESH check of this very cart content found no block recently enough to be trusted again. */
	protected boolean isRefreshReusable(final AbstractOrderModel cart, final String fingerprint)
	{
		if (refreshReuseSeconds <= 0)
		{
			return false;
		}
		final Object mark = getSessionService().getAttribute(REFRESH_MARK_ATTRIBUTE_PREFIX + cart.getCode());
		return mark instanceof RefreshMark refreshMark && refreshMark.fingerprint().equals(fingerprint)
				&& currentTimeMillis() < refreshMark.expiresAtMillis();
	}

	protected void rememberRefresh(final AbstractOrderModel cart, final String fingerprint)
	{
		if (refreshReuseSeconds > 0)
		{
			getSessionService().setAttribute(REFRESH_MARK_ATTRIBUTE_PREFIX + cart.getCode(),
					new RefreshMark(fingerprint, currentTimeMillis() + refreshReuseSeconds * 1000L));
		}
	}

	protected void forgetRefresh(final AbstractOrderModel cart)
	{
		getSessionService().removeAttribute(REFRESH_MARK_ATTRIBUTE_PREFIX + cart.getCode());
	}

	/** The customer and the subscription entries (product code and quantity) the REFRESH result holds for. */
	protected String fingerprintOf(final CustomerModel customer, final List<AbstractOrderEntryModel> subscriptionEntries)
	{
		final StringBuilder fingerprint = new StringBuilder(StringUtils.defaultString(customer.getUid()));
		subscriptionEntries.stream()
				.map(entry -> entry.getProduct().getCode() + "x" + entry.getQuantity())
				.sorted()
				.forEach(part -> fingerprint.append('|').append(part));
		return fingerprint.toString();
	}

	protected long currentTimeMillis()
	{
		return System.currentTimeMillis();
	}

	/** What a REFRESH check that found no block leaves in the session. */
	public record RefreshMark(String fingerprint, long expiresAtMillis) implements Serializable
	{
	}

	/** The customer whose subscriptions count, or {@code null} for an anonymous or guest cart. */
	protected CustomerModel ownerOf(final AbstractOrderModel cart)
	{
		if (cart.getUser() instanceof CustomerModel customer && !getUserService().isAnonymousUser(customer)
				&& !CustomerType.GUEST.equals(customer.getType()))
		{
			return customer;
		}
		return null;
	}

	protected PaymentAuthorisationRefusedException refusal(final String errorCode)
	{
		final String path = SubscriptionCartStatus.ALREADY_OWNED.equals(errorCode) ? MY_SUBSCRIPTIONS_PATH : null;
		return new PaymentAuthorisationRefusedException(errorCode,
				PaymentAuthorisationRefusedException.MESSAGE_KEY_PREFIX + errorCode, List.of(), path);
	}

	protected SubscriptionCartGuard getSubscriptionCartGuard()
	{
		return subscriptionCartGuard;
	}

	protected SubscriptionOwnershipService getSubscriptionOwnershipService()
	{
		return subscriptionOwnershipService;
	}

	protected UserService getUserService()
	{
		return userService;
	}

	protected SessionService getSessionService()
	{
		return sessionService;
	}

	public void setRefreshReuseSeconds(final int refreshReuseSeconds)
	{
		this.refreshReuseSeconds = refreshReuseSeconds;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setSubscriptionCartGuard(final SubscriptionCartGuard subscriptionCartGuard)
	{
		this.subscriptionCartGuard = subscriptionCartGuard;
	}

	public void setSubscriptionOwnershipService(final SubscriptionOwnershipService subscriptionOwnershipService)
	{
		this.subscriptionOwnershipService = subscriptionOwnershipService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}
}
