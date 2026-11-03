/*
 *                       ######
 *                       ######
 * ############    ####( ######  #####. ######  ############   ############
 * #############  #####( ######  #####. ######  #############  #############
 *        ######  #####( ######  #####. ######  #####  ######  #####  ######
 * ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 * ###### ######  #####( ######  #####. ######  #####          #####  ######
 * #############  #############  #############  #############  #####  ######
 *  ############   ############  #############   ############  #####  ######
 *                                      ######
 *                               #############
 *                               ############
 *
 * Adyen Hybris Extension
 *
 * Copyright (c) 2020 Adyen B.V.
 * This file is open source and available under the MIT license.
 * See the LICENSE file for more info.
 */

package com.adyen.v6.actions.order;

import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.actions.AbstractWaitableAction;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.orderprocessing.model.OrderProcessModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import org.apache.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Checks the order after the wait for the Adyen payment result. Orders waiting for a voucher payment
 * (resultCode PresentToShopper, e.g. Konbini) go back to waiting (WAIT) until the voucher expiry plus a grace period
 * (or, when the expiry is unknown, until the fallback maximum wait after order creation). Other pending orders
 * end in PROCESSING_ERROR (NOK).
 */
public class AdyenCheckPendingOrderAction extends AbstractWaitableAction<OrderProcessModel> {
	private static final Logger LOG = Logger.getLogger(AdyenCheckPendingOrderAction.class);

	public static final String GRACE_PERIOD_PROPERTY = "adyen.voucher.pending.grace.period";
	public static final String FALLBACK_MAX_WAIT_PROPERTY = "adyen.voucher.pending.fallback.max.wait";
	public static final String MAX_WAIT_PROPERTY = "adyen.voucher.pending.max.wait";
	public static final Duration DEFAULT_MAX_WAIT = Duration.ofDays(30);
	public static final Duration DEFAULT_GRACE_PERIOD = Duration.ofHours(6);
	public static final Duration DEFAULT_FALLBACK_MAX_WAIT = Duration.ofDays(3);

	private ConfigurationService configurationService;

	@Override
	public String execute(final OrderProcessModel process) {
		LOG.debug("Process: " + process.getCode() + " in step " + getClass().getSimpleName());

		final OrderModel order = process.getOrder();

		if (order == null) {
			LOG.error("Missing the order, exiting the process");
			return Transition.NOK.toString();
		}

		if (OrderStatus.PAYMENT_PENDING.equals(order.getStatus()) && order.getPaymentTransactions().isEmpty()) {
			if (isVoucherPending(order)) {
				final Instant deadline = calculateDeadline(order);
				if (deadline != null && now().isBefore(deadline)) {
					LOG.info("Order " + order.getCode() + " is waiting for a voucher payment, will wait until " + deadline);
					return Transition.WAIT.toString();
				}
			}
			LOG.debug("Process: " + process.getCode()
					+ " Order still on Payment Pending, will update status to PROCESSING_ERROR...");
			order.setStatus(OrderStatus.PROCESSING_ERROR);
			modelService.save(order);
			return Transition.NOK.toString();

		} else if (OrderStatus.CANCELLED.equals(order.getStatus())
				|| OrderStatus.PROCESSING_ERROR.equals(order.getStatus())) {
			LOG.debug("Process: " + process.getCode() + " Order Cancelled");
			return Transition.NOK.toString();
		}

		return Transition.OK.toString();
	}

	protected boolean isVoucherPending(final OrderModel order) {
		return PaymentResponse.ResultCodeEnum.PRESENTTOSHOPPER.getValue().equals(order.getStatusInfo());
	}

	/**
	 * @return the moment after which the order is no longer waiting for the voucher payment, or null if unknown
	 */
	protected Instant calculateDeadline(final OrderModel order) {
		final Date created = order.getCreationtime() != null ? order.getCreationtime() : order.getDate();
		final Date expiresAt = order.getAdyenVoucherExpiresAt();
		final Instant cap = created == null ? null : created.toInstant().plus(getMaxWait());
		if (expiresAt != null) {
			final Instant deadline = expiresAt.toInstant().plus(getGracePeriod());
			return cap != null && cap.isBefore(deadline) ? cap : deadline;
		}
		if (created == null) {
			return null;
		}
		final Instant fallback = created.toInstant().plus(getFallbackMaxWait());
		return cap.isBefore(fallback) ? cap : fallback;
	}

	protected Duration getMaxWait() {
		return readDuration(MAX_WAIT_PROPERTY, DEFAULT_MAX_WAIT);
	}

	protected Duration getGracePeriod() {
		return readDuration(GRACE_PERIOD_PROPERTY, DEFAULT_GRACE_PERIOD);
	}

	protected Duration getFallbackMaxWait() {
		return readDuration(FALLBACK_MAX_WAIT_PROPERTY, DEFAULT_FALLBACK_MAX_WAIT);
	}

	protected Duration readDuration(final String property, final Duration defaultValue) {
		final String value = configurationService == null ? null
				: configurationService.getConfiguration().getString(property);
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		try {
			return Duration.parse(value.trim());
		} catch (final RuntimeException e) {
			LOG.warn("Invalid duration '" + value + "' in property " + property + ", using default " + defaultValue);
			return defaultValue;
		}
	}

	protected Instant now() {
		return Instant.now();
	}

	public ConfigurationService getConfigurationService() {
		return configurationService;
	}

	public void setConfigurationService(final ConfigurationService configurationService) {
		this.configurationService = configurationService;
	}
}
