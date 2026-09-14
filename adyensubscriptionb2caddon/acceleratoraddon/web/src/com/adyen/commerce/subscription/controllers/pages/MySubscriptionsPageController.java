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
package com.adyen.commerce.subscription.controllers.pages;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.adyen.commerce.connector.facades.MySubscriptionsFacade;
import com.adyen.commerce.facades.AdyenStoredCardsFacade;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeResult;
import com.adyen.commerce.connector.facades.data.SubscriptionOverviewData;

import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractSearchPageController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;

/**
 * The shopper's own list of subscriptions, and the one thing they can do to them.
 *
 * <h3>Why the cancellation is a POST</h3>
 * <p>Not REST tidiness — the storefront's {@code CsrfProtectionMatcher} protects {@code POST} and nothing
 * else, so a {@code DELETE} or a {@code PUT} here would carry no CSRF token and be checked by nobody. The
 * stored-cards page in this codebase reaches the same conclusion for the same reason.</p>
 *
 * <h3>What actually guards this page</h3>
 * <p>{@code @RequireHardLogIn} is the polite half. The real barrier is the storefront's URL rule confining
 * {@code /my-account/**} to {@code ROLE_CUSTOMERGROUP}: the annotation's evaluator lets an anonymous shopper
 * through when anonymous checkout is enabled, and its handler does nothing at all over plain HTTP. The
 * facade then independently refuses to answer for anyone who is not a signed-in customer, so the page is
 * empty rather than broken if it is ever reached another way.</p>
 */
@Controller
@RequestMapping("/my-account/subscriptions")
public class MySubscriptionsPageController extends AbstractSearchPageController
{
	private static final String REDIRECT_TO_SUBSCRIPTIONS = REDIRECT_PREFIX + "/my-account/subscriptions";
	private static final String SUBSCRIPTIONS_CMS_PAGE = "adyenSubscriptions";

	@Resource(name = "accountBreadcrumbBuilder")
	private ResourceBreadcrumbBuilder accountBreadcrumbBuilder;

	@Resource(name = "mySubscriptionsFacade")
	private MySubscriptionsFacade mySubscriptionsFacade;

	/** The Adyen vault listing, reused as-is: the shopper picks from cards they already have. */
	@Resource(name = "adyenStoredCardsFacade")
	private AdyenStoredCardsFacade adyenStoredCardsFacade;

	@RequestMapping(method = RequestMethod.GET)
	@RequireHardLogIn
	public String listSubscriptions(final Model model) throws CMSItemNotFoundException
	{
		final SubscriptionOverviewData overview = mySubscriptionsFacade.getSubscriptionsForCurrentCustomer();

		storeCmsPageInModel(model, getContentPageForLabelOrId(SUBSCRIPTIONS_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(SUBSCRIPTIONS_CMS_PAGE));

		model.addAttribute("subscriptions", overview.getSubscriptions());
		model.addAttribute("ordersAwaitingSetup", overview.getOrdersAwaitingSetup());
		// Proof of concept: the cards Adyen has already vaulted for this shopper. Offering only these is
		// what makes the feature possible at all without 3DS — see the facade.
		model.addAttribute("storedCards", adyenStoredCardsFacade.getStoredCardsPageDataForCurrentCustomer()
				.getStoredCards());
		// Chosen by the facade, not by the view: it has to be a Chargebee row that actually carries a public
		// identifier, and "the first one on screen" is not the same thing.
		model.addAttribute("paymentMethodSubscriptionCode", overview.getPaymentMethodSubscriptionCode());
		// The scope, not the platform's name. It decides which sentence under the control is true; the view
		// never learns which billing platform is behind the page.
		model.addAttribute("paymentMethodChangeScope", overview.getPaymentMethodChangeScope().name());
		// Distinct from the scope above, which describes only the control above the list. Without this the
		// page cannot tell "no control up here" from "cannot be done at all", and a shopper whose
		// subscriptions are all changed per row would read that the change is unavailable.
		model.addAttribute("anyPaymentMethodChangeable",
				Boolean.valueOf(overview.isAnyPaymentMethodChangeable()));
		// Whether any provider on this page can do it at all, as opposed to whether anything can be done
		// right now. The page-wide "we can't do this online" sentence keys off this one: a subscription too
		// new to act on must not be described as one whose provider is incapable.
		model.addAttribute("paymentMethodChangeSupportedSomewhere",
				Boolean.valueOf(overview.isPaymentMethodChangeSupportedSomewhere()));
		model.addAttribute("breadcrumbs", accountBreadcrumbBuilder.getBreadcrumbs("text.account.subscriptions"));
		// A page listing what somebody is paying for every month has no business in a search index.
		model.addAttribute("metaRobots", "no-index,no-follow");

		return getViewForPage(model);
	}

	/**
	 * Stops a subscription at the end of the period already paid for.
	 *
	 * <p>Redirects rather than rendering, so a refresh cannot replay the cancellation. It matters more than
	 * the usual amount here: the Chargebee adapter sends no idempotency key, so a replayed POST would be a
	 * second real call to the platform. The facade refuses the second one on the state it re-reads, and this
	 * redirect is what stops the browser offering to send it.</p>
	 *
	 * <p>One message for every kind of no. Telling a caller apart "not yours" from "too late" would tell
	 * somebody probing for codes which ones exist.</p>
	 */
	@RequestMapping(value = "/cancel", method = RequestMethod.POST)
	@RequireHardLogIn
	public String cancelSubscription(@RequestParam("code") final String code,
			final RedirectAttributes redirectAttributes)
	{
		if (mySubscriptionsFacade.cancelForCurrentCustomer(code))
		{
			GlobalMessages.addFlashMessage(redirectAttributes, GlobalMessages.CONF_MESSAGES_HOLDER,
					"text.account.subscriptions.cancel.success");
		}
		else
		{
			GlobalMessages.addFlashMessage(redirectAttributes, GlobalMessages.ERROR_MESSAGES_HOLDER,
					"text.account.subscriptions.cancel.error");
		}

		return REDIRECT_TO_SUBSCRIPTIONS;
	}

	/**
	 * Points billing at a different card the shopper has already stored. Proof of concept.
	 *
	 * <p>POST for the same reason as the cancellation: this storefront's matcher checks POST and nothing
	 * else. The subscription code travels so the facade can establish ownership and the store — the change
	 * itself is per customer on Chargebee, which the confirmation message says out loud rather than leaving
	 * the shopper to discover that their other subscriptions moved too.</p>
	 */
	@RequestMapping(value = "/payment-method", method = RequestMethod.POST)
	@RequireHardLogIn
	public String changePaymentMethod(@RequestParam("code") final String code,
			@RequestParam("storedPaymentMethodId") final String storedPaymentMethodId,
			final RedirectAttributes redirectAttributes)
	{
		final PaymentMethodChangeResult result =
				mySubscriptionsFacade.changePaymentMethodForCurrentCustomer(code, storedPaymentMethodId);

		// A switch expression, not a statement: only the expression form is checked for exhaustiveness, so
		// a fifth result becomes a build failure here instead of a shopper reading nothing at all.
		final String messageKey = switch (result)
		{
			case CHANGED_THIS_SUBSCRIPTION -> "text.account.subscriptions.paymentMethod.success.subscription";
			case CHANGED_ALL_SUBSCRIPTIONS -> "text.account.subscriptions.paymentMethod.success.customer";
			case NOT_SUPPORTED_HERE -> "text.account.subscriptions.paymentMethod.unsupported";
			case FAILED -> "text.account.subscriptions.paymentMethod.error";
		};
		// "We cannot do this here" is not something to invite a retry on, so it is a plain message rather
		// than a red one; a genuine failure stays red.
		final String holder = result == PaymentMethodChangeResult.FAILED
				? GlobalMessages.ERROR_MESSAGES_HOLDER
				: GlobalMessages.CONF_MESSAGES_HOLDER;
		GlobalMessages.addFlashMessage(redirectAttributes, holder, messageKey);

		return REDIRECT_TO_SUBSCRIPTIONS;
	}
}
