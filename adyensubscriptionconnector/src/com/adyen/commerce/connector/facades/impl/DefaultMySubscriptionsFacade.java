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
package com.adyen.commerce.connector.facades.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.connector.dto.PaymentMethodChangeScope;
import com.adyen.commerce.connector.dto.PaymentMethodChangeSupport;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentEffect;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentPage;
import com.adyen.commerce.connector.dto.PaymentMethodEnrollmentSupport;
import com.adyen.commerce.connector.dto.PaymentMethodSource;
import com.adyen.commerce.connector.dto.PlatformPaymentMethod;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.exception.BillingException;
import com.adyen.commerce.connector.exception.CapabilityUnsupportedException;
import com.adyen.commerce.connector.exception.PreconditionFailedException;
import com.adyen.commerce.connector.facades.MySubscriptionsFacade;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeOfferData;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeReport;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeResult;
import com.adyen.commerce.connector.facades.data.PaymentMethodEnrollmentNotice;
import com.adyen.commerce.connector.facades.data.PaymentMethodEnrollmentOfferData;
import com.adyen.commerce.connector.facades.data.PaymentMethodOptionData;
import com.adyen.commerce.connector.facades.data.SubscriptionDisplayState;
import com.adyen.commerce.connector.facades.data.SubscriptionEntryData;
import com.adyen.commerce.connector.facades.data.SubscriptionOverviewData;
import com.adyen.commerce.connector.facades.data.SubscriptionPlatformSectionData;
import com.adyen.commerce.connector.model.BillingActivationAttemptModel;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.selfservice.CustomerSubscriptionService;
import com.adyen.commerce.connector.selfservice.PaymentMethodNotOwnedException;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.product.ProductService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Default implementation: maps what {@link CustomerSubscriptionService} decides into one section per
 * platform, and decides only where on the page each control goes.
 *
 * <p>The customer always comes from the session and is never accepted as an argument.</p>
 */
public class DefaultMySubscriptionsFacade implements MySubscriptionsFacade
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultMySubscriptionsFacade.class);

	private UserService userService;
	private ProductService productService;
	private EnumerationService enumerationService;
	private CustomerSubscriptionService customerSubscriptionService;

	@Override
	public boolean hasAvailablePlatforms()
	{
		return !customerSubscriptionService.getAvailablePlatforms().isEmpty();
	}

	@Override
	public SubscriptionOverviewData getSubscriptionsForCurrentCustomer()
	{
		final SubscriptionOverviewData overview = new SubscriptionOverviewData();
		final CustomerModel customer = currentCustomer();
		if (customer == null)
		{
			return overview;
		}

		final Set<BillingPlatform> platforms = customerSubscriptionService.getAvailablePlatforms();
		final List<BillingSubscriptionRefModel> subscriptions = customerSubscriptionService.getSubscriptions(customer);
		final List<BillingActivationAttemptModel> abandoned =
				customerSubscriptionService.getAbandonedActivations(customer);
		for (final BillingPlatform platform : platforms)
		{
			final List<BillingSubscriptionRefModel> onPlatform = subscriptions.stream()
					.filter(subscription -> isOn(platform, subscription.getPlatform()))
					.toList();
			final List<String> awaitingSetup = orderCodes(abandoned.stream()
					.filter(attempt -> isOn(platform, attempt.getPlatform()))
					.toList());
			// A platform the shopper has nothing on gets no section.
			if (!onPlatform.isEmpty() || !awaitingSetup.isEmpty())
			{
				overview.getSections().add(toSection(customer, platform, onPlatform, awaitingSetup));
			}
		}
		reportUnlisted(platforms, subscriptions, abandoned);
		return overview;
	}

	/**
	 * One platform's section. Everything in it is built from that platform's rows alone, so no control can
	 * reach a subscription of another section.
	 */
	protected SubscriptionPlatformSectionData toSection(final CustomerModel customer, final BillingPlatform platform,
			final List<BillingSubscriptionRefModel> subscriptions, final List<String> ordersAwaitingSetup)
	{
		final SubscriptionPlatformSectionData section = new SubscriptionPlatformSectionData();
		section.setPlatformCode(platform.getCode());
		section.setPlatformName(platformName(platform));
		section.setOrdersAwaitingSetup(new ArrayList<>(ordersAwaitingSetup));

		final AccountLookups lookups = newAccountLookups();
		final List<Row> rows = new ArrayList<>();
		for (final BillingSubscriptionRefModel subscription : subscriptions)
		{
			rows.add(toRow(customer, subscription, lookups));
		}
		section.setSubscriptions(rows.stream().map(Row::entry).collect(Collectors.toCollection(ArrayList::new)));
		section.setPaymentMethodChangeSupported(rows.stream().anyMatch(row -> row.support().isSupported()));
		section.setRowPaymentMethodControlOffered(rows.stream().anyMatch(row -> row.entry().isPaymentMethodControl()));

		final Set<String> accountsWithControl = applyPaymentMethodChangeOffers(section, customer, rows, lookups);
		applyPaymentMethodEnrollmentOffers(section, rows);
		markRowsThatCouldShareOneMethod(rows);
		markRowsCoveredByAControl(rows, accountsWithControl);
		reportWhatWasNotOffered(section, rows);
		return section;
	}

	/** A row and what the facade needs to remember about it while the section is assembled. */
	protected record Row(BillingSubscriptionRefModel subscription, SubscriptionEntryData entry,
			PaymentMethodChangeSupport support, String account)
	{
	}

	/**
	 * What the page has already asked about each billing account. A platform's payment methods and the
	 * shopper's vault belong to the account, not the row, and reading either is a remote call.
	 */
	protected static class AccountLookups
	{
		private final Map<String, List<PlatformPaymentMethod>> platformMethods = new HashMap<>();
		private final Map<String, List<PlatformPaymentMethod>> vaultedCards = new HashMap<>();

		public List<PlatformPaymentMethod> platformMethods(final String account,
				final Supplier<List<PlatformPaymentMethod>> read)
		{
			return platformMethods.computeIfAbsent(account, key -> read.get());
		}

		public List<PlatformPaymentMethod> vaultedCards(final String account,
				final Supplier<List<PlatformPaymentMethod>> read)
		{
			return vaultedCards.computeIfAbsent(account, key -> read.get());
		}
	}

	protected AccountLookups newAccountLookups()
	{
		return new AccountLookups();
	}

	protected Row toRow(final CustomerModel customer, final BillingSubscriptionRefModel subscription,
			final AccountLookups lookups)
	{
		final SubscriptionEntryData entry = new SubscriptionEntryData();
		entry.setCode(subscription.getCode());
		entry.setProductName(displayName(subscription));
		final SubscriptionDisplayState state =
				SubscriptionDisplayState.of(customerSubscriptionService.getState(subscription));
		entry.setState(state);
		entry.setEffectiveDate(effectiveDate(subscription, state));
		entry.setQuantity(subscription.getQuantity());

		// Every action needs the originating store's credentials, so a row with no store is described only.
		final boolean manageable = customerSubscriptionService.getStore(subscription) != null;
		entry.setManageable(manageable);

		final PaymentMethodChangeSupport support =
				customerSubscriptionService.getPaymentMethodChangeSupport(subscription);
		entry.setPaymentMethodChangeable(manageable && support.isSupported() && state.isPaymentMethodChangeable()
				&& StringUtils.isNotBlank(subscription.getCode()));
		final String account = customerSubscriptionService.getBillingAccount(subscription);

		// Remote calls, so only for a row that will carry a control of its own.
		if (entry.isPaymentMethodChangeable() && support.scope() == PaymentMethodChangeScope.SUBSCRIPTION)
		{
			final MethodChoices choices = oncePerMethod(
					lookups.platformMethods(account, () -> platformMethodsOf(subscription)),
					lookups.vaultedCards(account,
							() -> customerSubscriptionService.getAcceptedVaultedCards(customer, subscription)));
			// One option is no choice: it is what is already billing.
			if (choices.size() >= 2)
			{
				final String current = customerSubscriptionService.getCurrentPaymentMethodId(subscription);
				entry.setPaymentMethodOptions(options(choices.onPlatform(),
						method -> StringUtils.equals(method.id(), current)));
				entry.setAdyenVaultOptions(options(choices.vaultOnly(), method -> false));
				entry.setPaymentMethodControl(true);
			}
		}

		final AbstractOrderModel order = subscription.getOrder();
		if (order != null)
		{
			entry.setOrderCode(order.getCode());
			entry.setOrderDate(order.getDate());
			entry.setPaymentMethodSummary(cardSummary(order.getPaymentInfo()));
		}
		return new Row(subscription, entry, support, account);
	}

	/** The methods the platform holds, or none when they cannot be listed: the row then offers no control. */
	protected List<PlatformPaymentMethod> platformMethodsOf(final BillingSubscriptionRefModel subscription)
	{
		try
		{
			return customerSubscriptionService.getPlatformPaymentMethods(subscription);
		}
		catch (final BillingException | RuntimeException e)
		{
			LOG.warn("Could not list the payment methods platform {} holds for subscription '{}'; offering none.",
					subscription.getPlatform(), subscription.getCode(), e);
			return List.of();
		}
	}

	/** The platform's methods, and the vaulted cards it does not already hold under its own identifier. */
	protected record MethodChoices(List<PlatformPaymentMethod> onPlatform, List<PlatformPaymentMethod> vaultOnly)
	{
		public int size()
		{
			return onPlatform.size() + vaultOnly.size();
		}
	}

	/**
	 * Puts each physical card on the picker once. A vaulted card the platform has already imported appears
	 * in both lists; it is offered as the platform's own, because pointing at something the platform holds
	 * imports nothing, and it borrows the vault's label when the platform cannot describe the card itself.
	 */
	protected MethodChoices oncePerMethod(final List<PlatformPaymentMethod> onPlatform,
			final List<PlatformPaymentMethod> inVault)
	{
		final Set<String> alreadyImported = onPlatform.stream()
				.map(PlatformPaymentMethod::importedTokenId)
				.filter(StringUtils::isNotBlank)
				.collect(Collectors.toSet());
		return new MethodChoices(
				onPlatform.stream().map(method -> describedFromVault(method, inVault)).toList(),
				inVault.stream().filter(card -> !alreadyImported.contains(card.id())).toList());
	}

	/** Only where the platform gave no card detail of its own: then it is the better authority. */
	protected PlatformPaymentMethod describedFromVault(final PlatformPaymentMethod method,
			final List<PlatformPaymentMethod> inVault)
	{
		if (StringUtils.isBlank(method.importedTokenId())
				|| (method.card() != null && StringUtils.isNotBlank(method.card().last4())))
		{
			return method;
		}
		return inVault.stream()
				.filter(card -> StringUtils.equals(method.importedTokenId(), card.id()))
				.findFirst()
				.map(card -> new PlatformPaymentMethod(method.id(), card.displayLabel(), card.card(),
						method.defaultForCustomer(), method.importedTokenId()))
				.orElse(method);
	}

	protected List<PaymentMethodOptionData> options(final List<PlatformPaymentMethod> methods,
			final Predicate<PlatformPaymentMethod> current)
	{
		return methods.stream()
				.map(method -> new PaymentMethodOptionData(method.id(), method.displayLabel(), current.test(method)))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * One account-wide control per billing account whose platform changes the method for the whole account,
	 * built from what that platform declares it accepts. Named by the first row of the account that can be
	 * named in a form; any of them identifies the account.
	 *
	 * @return the accounts that got a control
	 */
	protected Set<String> applyPaymentMethodChangeOffers(final SubscriptionPlatformSectionData section,
			final CustomerModel customer, final List<Row> rows, final AccountLookups lookups)
	{
		final Set<String> examined = new HashSet<>();
		final Set<String> offered = new HashSet<>();
		for (final Row row : rows)
		{
			if (!row.entry().isPaymentMethodChangeable() || row.support().scope() != PaymentMethodChangeScope.CUSTOMER
					|| !examined.add(row.account()))
			{
				continue;
			}
			final List<PlatformPaymentMethod> onPlatform = row.support().accepts(PaymentMethodSource.ALREADY_ON_PLATFORM)
					? lookups.platformMethods(row.account(), () -> platformMethodsOf(row.subscription()))
					: List.of();
			final MethodChoices choices = oncePerMethod(onPlatform, lookups.vaultedCards(row.account(),
					() -> customerSubscriptionService.getAcceptedVaultedCards(customer, row.subscription())));
			if (choices.size() == 0)
			{
				continue;
			}
			final PaymentMethodChangeOfferData offer = new PaymentMethodChangeOfferData();
			offer.setSubscriptionCode(row.entry().getCode());
			offer.setAccountName(accountName(row.subscription()));
			offer.setPaymentMethodOptions(options(choices.onPlatform(), PlatformPaymentMethod::defaultForCustomer));
			offer.setAdyenVaultOptions(options(choices.vaultOnly(), method -> false));
			section.getPaymentMethodChangeOffers().add(offer);
			offered.add(row.account());
		}
		return offered;
	}

	/**
	 * One invitation per billing account whose platform hosts a payment-method page, against the first row
	 * of the account carrying a public code. Answered from declared capability alone, so rendering the page
	 * mints no address.
	 */
	protected void applyPaymentMethodEnrollmentOffers(final SubscriptionPlatformSectionData section,
			final List<Row> rows)
	{
		final Set<String> offered = new HashSet<>();
		for (final Row row : rows)
		{
			if (StringUtils.isBlank(row.entry().getCode()) || offered.contains(row.account()))
			{
				continue;
			}
			final PaymentMethodEnrollmentSupport enrollment =
					customerSubscriptionService.getPaymentMethodEnrollmentSupport(row.subscription());
			if (!enrollment.isOffered())
			{
				continue;
			}
			final PaymentMethodEnrollmentOfferData offer = new PaymentMethodEnrollmentOfferData();
			offer.setSubscriptionCode(row.entry().getCode());
			offer.setAccountName(accountName(row.subscription()));
			offer.setNotice(noticeOf(enrollment.effect()));
			section.getPaymentMethodEnrollmentOffers().add(offer);
			offered.add(row.account());
		}
	}

	protected PaymentMethodEnrollmentNotice noticeOf(final PaymentMethodEnrollmentEffect effect)
	{
		return switch (effect)
		{
			case ADDS_METHOD -> PaymentMethodEnrollmentNotice.ADDS_METHOD;
			case REPLACES_METHOD_ON_FILE -> PaymentMethodEnrollmentNotice.REPLACES_METHOD_ON_FILE;
		};
	}

	/**
	 * Rows whose own control may also offer to move the shopper's other subscriptions on the same account.
	 * Only where the platform pins the method per subscription, and only when there is another row to move.
	 */
	protected void markRowsThatCouldShareOneMethod(final List<Row> rows)
	{
		rows.stream()
				.filter(row -> row.entry().isPaymentMethodChangeable()
						&& row.support().scope() == PaymentMethodChangeScope.SUBSCRIPTION)
				.collect(Collectors.groupingBy(Row::account))
				.values().stream()
				.filter(group -> group.size() > 1)
				.flatMap(List::stream)
				.forEach(row -> row.entry().setPaymentMethodShareable(true));
	}

	/**
	 * A row the shopper can act on directly is covered by definition. An account-wide row is covered when its
	 * own account has a control, whether or not the row could be named in a form itself: the change goes at
	 * the account and takes every unpinned subscription with it. A control of another account covers nothing.
	 */
	protected void markRowsCoveredByAControl(final List<Row> rows, final Set<String> accountsWithControl)
	{
		for (final Row row : rows)
		{
			row.entry().setPaymentMethodChangeCovered(row.entry().isPaymentMethodChangeable()
					|| (row.support().scope() == PaymentMethodChangeScope.CUSTOMER
							&& accountsWithControl.contains(row.account())));
		}
	}

	/**
	 * Writes down every way of offering nothing. The missing-code case is a data gap with a known remedy, so
	 * the warning names it.
	 */
	protected void reportWhatWasNotOffered(final SubscriptionPlatformSectionData section, final List<Row> rows)
	{
		int unsupported = 0;
		int wrongState = 0;
		int missingCode = 0;
		for (final Row row : rows)
		{
			final SubscriptionEntryData entry = row.entry();
			if (entry.isPaymentMethodChangeable())
			{
				continue;
			}
			if (!row.support().isSupported())
			{
				unsupported++;
			}
			else if (entry.getState() == null || !entry.getState().isPaymentMethodChangeable())
			{
				wrongState++;
			}
			else if (StringUtils.isBlank(entry.getCode()))
			{
				missingCode++;
			}
		}
		if (missingCode > 0)
		{
			LOG.warn("{} of this shopper's subscription(s) on platform {} could have their payment method changed "
					+ "but carry no public code. Run a system update with essential data for the subscription "
					+ "connector, which assigns one to every reference predating the column.",
					Integer.valueOf(missingCode), section.getPlatformCode());
		}
		if (!section.isPaymentMethodChangeOffered() && (unsupported > 0 || wrongState > 0))
		{
			LOG.info("Not offering a payment-method change on platform {}: {} subscription(s) on a store or "
					+ "platform that does not support it, {} in a state where it would achieve nothing.",
					section.getPlatformCode(), Integer.valueOf(unsupported), Integer.valueOf(wrongState));
		}
	}

	/** Subscriptions whose connector is gone cannot be acted on and are not listed; the log says so. */
	protected void reportUnlisted(final Set<BillingPlatform> platforms,
			final List<BillingSubscriptionRefModel> subscriptions, final List<BillingActivationAttemptModel> abandoned)
	{
		final Set<String> available = platforms.stream().map(BillingPlatform::getCode).collect(Collectors.toSet());
		final long unlistedSubscriptions = subscriptions.stream()
				.filter(subscription -> !isAvailable(available, subscription.getPlatform()))
				.count();
		final long unlistedOrders = abandoned.stream()
				.filter(attempt -> !isAvailable(available, attempt.getPlatform()))
				.count();
		if (unlistedSubscriptions > 0 || unlistedOrders > 0)
		{
			LOG.info("Not listing {} subscription(s) and {} order(s) awaiting setup of this shopper: their billing "
					+ "platform has no loaded connector.", Long.valueOf(unlistedSubscriptions),
					Long.valueOf(unlistedOrders));
		}
	}

	protected boolean isAvailable(final Set<String> available, final BillingPlatform platform)
	{
		return platform != null && available.contains(platform.getCode());
	}

	protected boolean isOn(final BillingPlatform platform, final BillingPlatform other)
	{
		return other != null && StringUtils.equals(platform.getCode(), other.getCode());
	}

	/** The platform's name as its connector extension localizes it, or its code. */
	protected String platformName(final BillingPlatform platform)
	{
		try
		{
			return StringUtils.defaultIfBlank(enumerationService.getEnumerationName(platform), platform.getCode());
		}
		catch (final RuntimeException e)
		{
			return platform.getCode();
		}
	}

	/** The store the account belongs to, as the shopper knows it. */
	protected String accountName(final BillingSubscriptionRefModel subscription)
	{
		final BaseStoreModel store = customerSubscriptionService.getStore(subscription);
		return store == null ? null : StringUtils.defaultIfBlank(store.getName(), store.getUid());
	}

	protected List<String> orderCodes(final List<BillingActivationAttemptModel> attempts)
	{
		final List<String> codes = new ArrayList<>();
		for (final BillingActivationAttemptModel attempt : attempts)
		{
			final AbstractOrderModel order = attempt.getOrder();
			if (order != null && StringUtils.isNotBlank(order.getCode()))
			{
				codes.add(order.getCode());
			}
		}
		return codes;
	}

	// --- actions ---

	@Override
	public String paymentMethodEnrollmentUrlForCurrentCustomer(final String platformCode,
			final String subscriptionCode)
	{
		final BillingSubscriptionRefModel subscription = ownSubscription(currentCustomer(), platformCode,
				subscriptionCode);
		if (subscription == null)
		{
			return null;
		}
		try
		{
			return customerSubscriptionService.getPaymentMethodEnrollmentPage(subscription)
					.map(PaymentMethodEnrollmentPage::url)
					.orElse(null);
		}
		catch (final BillingException | RuntimeException e)
		{
			// Without the exception's message: where the page is reached by a credential in the URL, a failure
			// while building it can carry that credential.
			LOG.warn("Could not build the payment-method page for platform {} and subscription '{}': {}",
					subscription.getPlatform(), subscriptionCode, e.getClass().getName());
			return null;
		}
	}

	@Override
	public boolean cancelForCurrentCustomer(final String platformCode, final String subscriptionCode)
	{
		final BillingSubscriptionRefModel subscription = ownSubscription(currentCustomer(), platformCode,
				subscriptionCode);
		if (subscription == null)
		{
			return false;
		}
		try
		{
			customerSubscriptionService.cancelAtPeriodEnd(subscription);
			return true;
		}
		catch (final PreconditionFailedException e)
		{
			LOG.info("Refusing to cancel subscription '{}': {}", subscriptionCode, e.getMessage());
			return false;
		}
		catch (final BillingException | RuntimeException e)
		{
			// The platform's own error text stays in the log rather than reaching the shopper.
			LOG.error("Could not cancel subscription '{}' for the current customer.", subscriptionCode, e);
			return false;
		}
	}

	@Override
	public PaymentMethodChangeResult changePaymentMethodForCurrentCustomer(final String platformCode,
			final String subscriptionCode, final String paymentMethodId)
	{
		final CustomerModel customer = currentCustomer();
		if (StringUtils.isBlank(paymentMethodId))
		{
			LOG.warn("Refusing a payment-method change that names no payment method.");
			return PaymentMethodChangeResult.FAILED;
		}
		final BillingSubscriptionRefModel subscription = ownSubscription(customer, platformCode, subscriptionCode);
		if (subscription == null)
		{
			return PaymentMethodChangeResult.FAILED;
		}
		try
		{
			return customerSubscriptionService.changePaymentMethod(customer, subscription, paymentMethodId)
					== PaymentMethodChangeScope.CUSTOMER
					? PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS
					: PaymentMethodChangeResult.CHANGED_THIS_SUBSCRIPTION;
		}
		catch (final CapabilityUnsupportedException e)
		{
			LOG.info("Payment-method change refused for subscription '{}': {}", subscriptionCode, e.getMessage());
			return PaymentMethodChangeResult.NOT_SUPPORTED_HERE;
		}
		catch (final PaymentMethodNotOwnedException e)
		{
			// Not an error: a method the shopper does not hold is refused like a subscription that is not theirs.
			LOG.info("The requested payment method is not one the current shopper holds; refusing to change "
					+ "subscription '{}'.", subscriptionCode);
			return PaymentMethodChangeResult.FAILED;
		}
		catch (final PreconditionFailedException e)
		{
			LOG.info("Refusing to change the payment method of subscription '{}': {}", subscriptionCode,
					e.getMessage());
			return PaymentMethodChangeResult.FAILED;
		}
		catch (final BillingException | RuntimeException e)
		{
			LOG.error("Could not change the payment method for subscription '{}'.", subscriptionCode, e);
			return PaymentMethodChangeResult.FAILED;
		}
	}

	/**
	 * The named row goes through the single-subscription path first, with every guard it applies, and
	 * nothing fans out until it has succeeded.
	 */
	@Override
	public PaymentMethodChangeReport changePaymentMethodForAllSubscriptions(final String platformCode,
			final String subscriptionCode, final String paymentMethodId)
	{
		final PaymentMethodChangeResult first =
				changePaymentMethodForCurrentCustomer(platformCode, subscriptionCode, paymentMethodId);
		if (first != PaymentMethodChangeResult.CHANGED_THIS_SUBSCRIPTION)
		{
			// An account-wide change already moved everything, and a refusal has nothing to fan out.
			return PaymentMethodChangeReport.of(first);
		}

		final CustomerModel customer = currentCustomer();
		final BillingSubscriptionRefModel named = ownSubscription(customer, platformCode, subscriptionCode);
		if (named == null)
		{
			return PaymentMethodChangeReport.of(first);
		}

		int moved = 1;
		int failed = 0;
		for (final BillingSubscriptionRefModel other :
				customerSubscriptionService.getSubscriptionsSharingBillingAccount(customer, named))
		{
			try
			{
				customerSubscriptionService.changePaymentMethod(customer, other, paymentMethodId);
				moved++;
			}
			catch (final BillingException | RuntimeException e)
			{
				// One refusing says nothing about the others, so it is counted rather than thrown.
				LOG.warn("Could not also move subscription '{}' onto the chosen payment method.", other.getCode(), e);
				failed++;
			}
		}

		final PaymentMethodChangeResult result = failed == 0 && moved > 1
				? PaymentMethodChangeResult.CHANGED_ALL_SUBSCRIPTIONS
				: PaymentMethodChangeResult.CHANGED_THIS_SUBSCRIPTION;
		if (failed > 0)
		{
			LOG.warn("Put one payment method behind {} of this shopper's subscriptions on platform {}; {} could "
					+ "not be moved.", Integer.valueOf(moved), named.getPlatform(), Integer.valueOf(failed));
		}
		return new PaymentMethodChangeReport(result, moved, failed);
	}

	/**
	 * The customer's subscription with this code on this platform, or {@code null}. An unknown code, somebody
	 * else's code and a code from another platform's section are refused alike, so nothing tells a caller
	 * probing for codes which ones exist.
	 */
	protected BillingSubscriptionRefModel ownSubscription(final CustomerModel customer, final String platformCode,
			final String subscriptionCode)
	{
		if (customer == null || StringUtils.isAnyBlank(platformCode, subscriptionCode))
		{
			return null;
		}
		final BillingSubscriptionRefModel subscription =
				customerSubscriptionService.findSubscription(customer, subscriptionCode).orElse(null);
		if (subscription == null)
		{
			LOG.info("No subscription matching the requested code belongs to the current customer; refusing.");
			return null;
		}
		if (subscription.getPlatform() == null || !platformCode.equals(subscription.getPlatform().getCode()))
		{
			// The platform the request named is not logged: it is free text from the request.
			LOG.info("A subscription of the current customer was named from the section of another platform; "
					+ "refusing.");
			return null;
		}
		return subscription;
	}

	// --- mapping ---

	/**
	 * What the shopper reads instead of a plan code. A subscription outlives the catalogue entry it was sold
	 * from, and {@code getProductForCode} throws rather than returning null when the product is gone.
	 */
	protected String displayName(final BillingSubscriptionRefModel subscription)
	{
		final String productCode = subscription.getProductCode();
		if (StringUtils.isBlank(productCode))
		{
			return null;
		}
		try
		{
			final ProductModel product = productService.getProductForCode(productCode);
			return StringUtils.defaultIfBlank(product.getName(), productCode);
		}
		catch (final RuntimeException e)
		{
			LOG.debug("Product '{}' could not be resolved for a subscription listing; showing the code.",
					productCode, e);
			return productCode;
		}
	}

	protected Date effectiveDate(final BillingSubscriptionRefModel subscription, final SubscriptionDisplayState state)
	{
		return switch (state)
		{
			case ACTIVE, ENDING, PAST_DUE, PAST_DUE_ENDING, ENDED -> subscription.getCurrentPeriodEnd();
			case STARTING_SOON -> subscription.getCurrentPeriodStart();
			case SETTING_UP, PAUSED, UNAVAILABLE -> null;
		};
	}

	protected String cardSummary(final PaymentInfoModel paymentInfo)
	{
		return paymentInfo == null ? null : paymentInfo.getAdyenCardSummary();
	}

	/**
	 * The signed-in customer, or {@code null} for anyone else. A guest never reaches the page: the accelerator
	 * confines {@code /my-account/**} to registered customers.
	 */
	protected CustomerModel currentCustomer()
	{
		if (userService.isAnonymousUser(userService.getCurrentUser()))
		{
			return null;
		}
		return userService.getCurrentUser() instanceof CustomerModel customer ? customer : null;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setProductService(final ProductService productService)
	{
		this.productService = productService;
	}

	public void setEnumerationService(final EnumerationService enumerationService)
	{
		this.enumerationService = enumerationService;
	}

	public void setCustomerSubscriptionService(final CustomerSubscriptionService customerSubscriptionService)
	{
		this.customerSubscriptionService = customerSubscriptionService;
	}
}
