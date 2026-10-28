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
package com.adyen.commerce.connector.occ.controllers;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.adyen.commerce.connector.facades.MySubscriptionsFacade;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeReport;
import com.adyen.commerce.connector.facades.data.PaymentMethodChangeResult;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionCancelResultWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionEnrollmentUrlWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionOverviewWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionPaymentMethodChangeRequestWsDTO;
import com.adyen.commerce.connector.occ.dto.AdyenSubscriptionPaymentMethodChangeResultWsDTO;
import com.adyen.commerce.connector.occ.exceptions.SubscriptionActionRefusedException;
import com.adyen.commerce.connector.occ.mapper.AdyenMySubscriptionsWsMapper;

import de.hybris.platform.webservicescommons.cache.CacheControl;
import de.hybris.platform.webservicescommons.cache.CacheControlDirective;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;

/**
 * The shopper's own subscriptions over OCC: the list, cancellation, change of payment method and the way
 * to the billing platform's hosted payment method page. Thin over {@link MySubscriptionsFacade}, which
 * scopes every call to the customer in session; the {@code {userId}} path segment together with OCC's
 * user matching filter decides who that is.
 *
 * <p>Reads are open to the trusted client as well; the three actions are not, because that role skips the
 * owner check. Every kind of refusal is the same 400 {@link SubscriptionActionRefusedException}, so a caller
 * cannot tell a code that is not theirs from one in the wrong state. Nothing a billing platform said is
 * passed on, and the enrollment address, which is a credential on some platforms, is never logged.</p>
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}/adyen/subscriptions")
@CacheControl(directive = CacheControlDirective.NO_CACHE)
@Tag(name = "Adyen Subscriptions")
public class AdyenMySubscriptionsController
{
	private static final Logger LOG = LoggerFactory.getLogger(AdyenMySubscriptionsController.class);

	@Resource(name = "mySubscriptionsFacade")
	private MySubscriptionsFacade mySubscriptionsFacade;

	@Resource(name = "adyenMySubscriptionsWsMapper")
	private AdyenMySubscriptionsWsMapper mapper;

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT" })
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	@Operation(operationId = "getAdyenSubscriptions", summary = "Returns the subscriptions of the user",
			description = "Returns the user's subscriptions grouped by billing platform, with the state of each and "
					+ "which actions are offered. Answers 404 when no billing platform connector is loaded.")
	@ApiBaseSiteIdAndUserIdParam
	public ResponseEntity<AdyenSubscriptionOverviewWsDTO> getSubscriptions()
	{
		if (!mySubscriptionsFacade.hasAvailablePlatforms())
		{
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(getMapper().toOverview(mySubscriptionsFacade.getSubscriptionsForCurrentCustomer()));
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@PostMapping(path = "/{code}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	@Operation(operationId = "cancelAdyenSubscription", summary = "Cancels a subscription at the end of the paid period",
			description = "Refuses with the same 400 SubscriptionActionRefusedError whatever the reason.")
	@ApiBaseSiteIdAndUserIdParam
	public ResponseEntity<AdyenSubscriptionCancelResultWsDTO> cancel(@PathVariable final String code,
			@RequestParam(value = "platform", required = false) final String platform)
	{
		if (!mySubscriptionsFacade.hasAvailablePlatforms())
		{
			return ResponseEntity.notFound().build();
		}
		final boolean cancelled;
		try
		{
			cancelled = !StringUtils.isBlank(platform) && mySubscriptionsFacade.cancelForCurrentCustomer(platform, code);
		}
		catch (final RuntimeException e)
		{
			throw refused("cancel", e);
		}
		if (!cancelled)
		{
			throw new SubscriptionActionRefusedException();
		}
		return ResponseEntity.ok(new AdyenSubscriptionCancelResultWsDTO());
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@PostMapping(path = "/{code}/payment-method", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	@Operation(operationId = "changeAdyenSubscriptionPaymentMethod", summary = "Changes the payment method of a subscription",
			description = "Points billing at a payment method the user holds, for this subscription or for all "
					+ "subscriptions of the account. The body reports what happened as result, moved, failed and partial.")
	@ApiBaseSiteIdAndUserIdParam
	public ResponseEntity<AdyenSubscriptionPaymentMethodChangeResultWsDTO> changePaymentMethod(
			@PathVariable final String code,
			@RequestBody final AdyenSubscriptionPaymentMethodChangeRequestWsDTO request)
	{
		if (!mySubscriptionsFacade.hasAvailablePlatforms())
		{
			return ResponseEntity.notFound().build();
		}
		if (request == null || StringUtils.isBlank(request.getPlatform())
				|| StringUtils.isBlank(request.getStoredPaymentMethodId()))
		{
			throw new SubscriptionActionRefusedException();
		}
		PaymentMethodChangeReport report;
		try
		{
			report = request.isApplyToAll()
					? mySubscriptionsFacade.changePaymentMethodForAllSubscriptions(request.getPlatform(), code,
							request.getStoredPaymentMethodId())
					: PaymentMethodChangeReport.of(mySubscriptionsFacade.changePaymentMethodForCurrentCustomer(
							request.getPlatform(), code, request.getStoredPaymentMethodId()));
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Payment method change failed unexpectedly ({})", e.getClass().getSimpleName());
			report = PaymentMethodChangeReport.of(PaymentMethodChangeResult.FAILED);
		}
		return ResponseEntity.ok(getMapper().toChangeResult(report));
	}

	@Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CUSTOMERMANAGERGROUP" })
	@PostMapping(path = "/{code}/payment-method/enrollment", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	@Operation(operationId = "getAdyenSubscriptionPaymentMethodEnrollmentUrl",
			summary = "Returns the address of the billing platform's payment method page",
			description = "A POST and never cached: on some platforms the address carries a token that opens the "
					+ "account. Refuses with 400 SubscriptionActionRefusedError when there is none to give.")
	@ApiBaseSiteIdAndUserIdParam
	public ResponseEntity<AdyenSubscriptionEnrollmentUrlWsDTO> enrollment(@PathVariable final String code,
			@RequestParam(value = "platform", required = false) final String platform)
	{
		if (!mySubscriptionsFacade.hasAvailablePlatforms())
		{
			return ResponseEntity.notFound().build();
		}
		final String url;
		try
		{
			url = StringUtils.isBlank(platform) ? null
					: mySubscriptionsFacade.paymentMethodEnrollmentUrlForCurrentCustomer(platform, code);
		}
		catch (final RuntimeException e)
		{
			throw refused("enrollment", e);
		}
		if (StringUtils.isBlank(url))
		{
			throw new SubscriptionActionRefusedException();
		}
		final AdyenSubscriptionEnrollmentUrlWsDTO body = new AdyenSubscriptionEnrollmentUrlWsDTO();
		body.setUrl(url);
		return ResponseEntity.ok(body);
	}

	/** Logs the kind of failure only: its message may quote the platform, or the address. */
	protected SubscriptionActionRefusedException refused(final String action, final RuntimeException cause)
	{
		LOG.warn("Subscription {} failed unexpectedly ({})", action, cause.getClass().getSimpleName());
		return new SubscriptionActionRefusedException();
	}

	protected AdyenMySubscriptionsWsMapper getMapper()
	{
		return mapper;
	}

	public void setMySubscriptionsFacade(final MySubscriptionsFacade mySubscriptionsFacade)
	{
		this.mySubscriptionsFacade = mySubscriptionsFacade;
	}

	public void setMapper(final AdyenMySubscriptionsWsMapper mapper)
	{
		this.mapper = mapper;
	}
}
