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
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import com.adyen.commerce.connector.facades.MySubscriptionsFacade;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeReport;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeResult;
import com.adyen.commerce.connector.facades.data.SubscriptionOverviewData;

import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractSearchPageController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;

/**
 * The shopper's own subscriptions, one section per billing platform, and what they can do to them.
 *
 * <p>Every action carries the platform of the section it came from, and the facade refuses a subscription
 * that is not on it, or a request naming none, which a page rendered before sections existed would send. Mutations are {@code POST} because the storefront's {@code CsrfProtectionMatcher}
 * protects {@code POST} and nothing else.</p>
 *
 * <p>{@code @RequireHardLogIn} is not what guards the page: the real barrier is the storefront's URL rule
 * confining {@code /my-account/**} to {@code ROLE_CUSTOMERGROUP}. The facade independently refuses to answer
 * for anyone who is not a signed-in customer.</p>
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

	/**
	 * Not found while no billing platform connector is loaded, the same condition that hides the menu entry,
	 * so a bookmarked address does not open an empty page.
	 */
	@RequestMapping(method = RequestMethod.GET)
	@RequireHardLogIn
	public String listSubscriptions(final Model model, final HttpServletResponse response)
			throws CMSItemNotFoundException
	{
		if (!mySubscriptionsFacade.hasAvailablePlatforms())
		{
			prepareNotFoundPage(model, response);
			return getViewForPage(model);
		}

		final SubscriptionOverviewData overview = mySubscriptionsFacade.getSubscriptionsForCurrentCustomer();

		storeCmsPageInModel(model, getContentPageForLabelOrId(SUBSCRIPTIONS_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(SUBSCRIPTIONS_CMS_PAGE));

		model.addAttribute("subscriptionSections", overview.getSections());
		model.addAttribute("breadcrumbs", accountBreadcrumbBuilder.getBreadcrumbs("text.account.subscriptions"));
		// A page listing what somebody is paying for every month has no business in a search index.
		model.addAttribute("metaRobots", "no-index,no-follow");

		return getViewForPage(model);
	}

	/**
	 * Stops a subscription at the end of the period already paid for.
	 *
	 * <p>Redirects rather than rendering, so a refresh cannot replay the cancellation. One message for every
	 * kind of no: telling "not yours" apart from "too late" would tell somebody probing for codes which ones
	 * exist.</p>
	 */
	@RequestMapping(value = "/cancel", method = RequestMethod.POST)
	@RequireHardLogIn
	public String cancelSubscription(@RequestParam(value = "platform", required = false) final String platform,
			@RequestParam("code") final String code, final RedirectAttributes redirectAttributes)
	{
		if (mySubscriptionsFacade.cancelForCurrentCustomer(platform, code))
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
	 * Points billing at another payment method the shopper holds. The subscription code establishes
	 * ownership, the store and, for an account-wide change, the account.
	 */
	@RequestMapping(value = "/payment-method", method = RequestMethod.POST)
	@RequireHardLogIn
	public String changePaymentMethod(@RequestParam(value = "platform", required = false) final String platform,
			@RequestParam("code") final String code,
			@RequestParam("storedPaymentMethodId") final String storedPaymentMethodId,
			@RequestParam(value = "applyToAll", required = false, defaultValue = "false") final boolean applyToAll,
			final RedirectAttributes redirectAttributes)
	{
		if (applyToAll)
		{
			return report(mySubscriptionsFacade.changePaymentMethodForAllSubscriptions(platform, code,
					storedPaymentMethodId), redirectAttributes);
		}
		return report(mySubscriptionsFacade.changePaymentMethodForCurrentCustomer(platform, code,
				storedPaymentMethodId), redirectAttributes);
	}

	/**
	 * Says what a fan-out actually did, counts included. A partial result is reported as a failure even
	 * though something moved: what the shopper asked for did not happen in full.
	 */
	protected String report(final PaymentMethodChangeReport report, final RedirectAttributes redirectAttributes)
	{
		if (report.isPartial())
		{
			GlobalMessages.addFlashMessage(redirectAttributes, GlobalMessages.ERROR_MESSAGES_HOLDER,
					"text.account.subscriptions.paymentMethod.success.some",
					new Object[] { Integer.valueOf(report.moved()), Integer.valueOf(report.failed()) });
			return REDIRECT_TO_SUBSCRIPTIONS;
		}
		// More than one moved only when the fan-out found siblings; one alone reads better as the
		// single-subscription message.
		if (report.result() == PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS && report.moved() > 1)
		{
			GlobalMessages.addFlashMessage(redirectAttributes, GlobalMessages.CONF_MESSAGES_HOLDER,
					"text.account.subscriptions.paymentMethod.success.counted",
					new Object[] { Integer.valueOf(report.moved()) });
			return REDIRECT_TO_SUBSCRIPTIONS;
		}
		return report(report.result(), redirectAttributes);
	}

	protected String report(final PaymentMethodChangeResult result, final RedirectAttributes redirectAttributes)
	{
		// A switch expression, not a statement: only the expression form is checked for exhaustiveness.
		final String messageKey = switch (result)
		{
			case CHANGED_THIS_SUBSCRIPTION -> "text.account.subscriptions.paymentMethod.success.subscription";
			case CHANGED_ALL_SUBSCRIPTIONS -> "text.account.subscriptions.paymentMethod.success.customer";
			case NOT_SUPPORTED_HERE -> "text.account.subscriptions.paymentMethod.unsupported";
			case FAILED -> "text.account.subscriptions.paymentMethod.error";
		};
		// A refusal that no retry can turn into a success is a plain message; a genuine failure stays red.
		final String holder = result == PaymentMethodChangeResult.FAILED
				? GlobalMessages.ERROR_MESSAGES_HOLDER
				: GlobalMessages.CONF_MESSAGES_HOLDER;
		GlobalMessages.addFlashMessage(redirectAttributes, holder, messageKey);

		return REDIRECT_TO_SUBSCRIPTIONS;
	}

	/**
	 * Sends the shopper to their billing platform's own page for giving it a payment method.
	 *
	 * <p>{@code GET} because nothing changes here. The address is resolved per request and never rendered into
	 * the page, because on some platforms it carries a token that opens the account; for the same reason
	 * nothing from this page is appended to it. The facade refuses a code the shopper does not own.</p>
	 */
	@RequestMapping(value = "/payment-method/add", method = RequestMethod.GET)
	@RequireHardLogIn
	public View addPaymentMethod(@RequestParam(value = "platform", required = false) final String platform,
			@RequestParam("code") final String code, final RedirectAttributes redirectAttributes)
	{
		final String url = mySubscriptionsFacade.paymentMethodEnrollmentUrlForCurrentCustomer(platform, code);
		if (StringUtils.isBlank(url))
		{
			GlobalMessages.addFlashMessage(redirectAttributes, GlobalMessages.ERROR_MESSAGES_HOLDER,
					"text.account.subscriptions.paymentMethod.add.unavailable");
			return new RedirectView("/my-account/subscriptions", true);
		}

		final RedirectView redirect = new RedirectView(url);
		redirect.setExposeModelAttributes(false);
		return redirect;
	}
}
