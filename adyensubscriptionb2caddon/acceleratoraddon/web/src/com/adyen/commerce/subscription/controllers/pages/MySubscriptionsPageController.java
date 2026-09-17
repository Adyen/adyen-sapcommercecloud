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
 * <p>Both mutations are {@code POST} because the storefront's {@code CsrfProtectionMatcher} protects
 * {@code POST} and nothing else, so a {@code DELETE} or a {@code PUT} here would carry no CSRF token.</p>
 *
 * <p>{@code @RequireHardLogIn} is not what guards the page: the real barrier is the storefront's URL rule
 * confining {@code /my-account/**} to {@code ROLE_CUSTOMERGROUP}, since the annotation's evaluator lets an
 * anonymous shopper through when anonymous checkout is enabled. The facade independently refuses to answer
 * for anyone who is not a signed-in customer, so the page is empty rather than broken if it is reached
 * another way.</p>
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
		// The cards Adyen has already vaulted for this shopper. Offering only these is what makes the
		// feature work without 3DS - see the facade.
		model.addAttribute("storedCards", adyenStoredCardsFacade.getStoredCardsPageDataForCurrentCustomer()
				.getStoredCards());
		// Chosen by the facade, not by the view: it has to be a Chargebee row that actually carries a public
		// identifier, which "the first one on screen" is not.
		model.addAttribute("paymentMethodSubscriptionCode", overview.getPaymentMethodSubscriptionCode());
		// The scope, not the platform's name: it decides which sentence under the control is true, and the
		// view never learns which billing platform is behind the page.
		model.addAttribute("paymentMethodChangeScope", overview.getPaymentMethodChangeScope().name());
		// Distinct from the scope above, which describes only the control above the list. Without this the
		// page cannot tell "no control up here" from "cannot be done at all".
		model.addAttribute("anyPaymentMethodChangeable",
				Boolean.valueOf(overview.isAnyPaymentMethodChangeable()));
		// Whether any provider on this page can do it at all, as opposed to whether anything can be done
		// right now: a subscription too new to act on must not be described as one whose provider is
		// incapable.
		model.addAttribute("paymentMethodChangeSupportedSomewhere",
				Boolean.valueOf(overview.isPaymentMethodChangeSupportedSomewhere()));
		// Whether any row will show a control of its own. The page-level control additionally needs the
		// Adyen vault, which only this controller knows about, so the two halves meet in the view.
		model.addAttribute("anyRowPaymentMethodControl",
				Boolean.valueOf(overview.isAnyRowPaymentMethodControl()));
		model.addAttribute("breadcrumbs", accountBreadcrumbBuilder.getBreadcrumbs("text.account.subscriptions"));
		// A page listing what somebody is paying for every month has no business in a search index.
		model.addAttribute("metaRobots", "no-index,no-follow");

		return getViewForPage(model);
	}

	/**
	 * Stops a subscription at the end of the period already paid for.
	 *
	 * <p>Redirects rather than rendering, so a refresh cannot replay the cancellation: the Chargebee adapter
	 * sends no idempotency key, so a replayed POST would be a second real call to the platform.</p>
	 *
	 * <p>One message for every kind of no. Telling "not yours" apart from "too late" would tell somebody
	 * probing for codes which ones exist.</p>
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
	 * Points billing at a different card the shopper has already stored.
	 *
	 * <p>POST for the same reason as the cancellation. The subscription code travels so the facade can
	 * establish ownership and the store; on Chargebee the change itself is per customer, which the
	 * confirmation message says out loud.</p>
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
		// A refusal that no retry can turn into a success is a plain message rather than a red one; a
		// genuine failure stays red.
		final String holder = result == PaymentMethodChangeResult.FAILED
				? GlobalMessages.ERROR_MESSAGES_HOLDER
				: GlobalMessages.CONF_MESSAGES_HOLDER;
		GlobalMessages.addFlashMessage(redirectAttributes, holder, messageKey);

		return REDIRECT_TO_SUBSCRIPTIONS;
	}
}
