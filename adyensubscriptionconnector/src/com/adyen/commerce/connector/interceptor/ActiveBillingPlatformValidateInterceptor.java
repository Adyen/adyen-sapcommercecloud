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
package com.adyen.commerce.connector.interceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.readiness.BillingPlatformReadinessCheck;
import com.adyen.commerce.connector.registry.SubscriptionBillingConnectorRegistry;

import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.ValidateInterceptor;
import de.hybris.platform.servicelayer.model.ModelContextUtils;
import de.hybris.platform.store.BaseStoreModel;

/**
 * Lets a base store switch to a billing platform only when that platform's connector is loaded and the store's
 * configuration for it passes the platform's {@link BillingPlatformReadinessCheck}s. Applies to every writer
 * (Backoffice, ImpEx, OCC, code); a platform without a readiness check needs only its connector.
 *
 * <p>Only a switch is checked, not every save: a store keeps saving normally even if its current platform
 * later loses its connector or part of its configuration.</p>
 */
public class ActiveBillingPlatformValidateInterceptor implements ValidateInterceptor<BaseStoreModel>
{
	private SubscriptionBillingConnectorRegistry connectorRegistry;
	// Mutable: connector extensions add their checks by list merge.
	private List<BillingPlatformReadinessCheck> readinessChecks = new ArrayList<>();

	@Override
	public void onValidate(final BaseStoreModel store, final InterceptorContext ctx) throws InterceptorException
	{
		if (!isChangingPlatform(store, ctx))
		{
			return;
		}
		final BillingPlatform platform = store.getActiveBillingPlatform();
		validateConnectorLoaded(store, platform);
		validateStoreReady(store, platform);
	}

	/** Whether this save sets a platform on a new store, or a different one on an existing store. */
	protected boolean isChangingPlatform(final BaseStoreModel store, final InterceptorContext ctx)
	{
		final BillingPlatform platform = store.getActiveBillingPlatform();
		if (platform == null)
		{
			return false;
		}
		if (ctx.isNew(store))
		{
			return true;
		}
		return ctx.isModified(store, BaseStoreModel.ACTIVEBILLINGPLATFORM)
				&& !platform.equals(getOriginalPlatform(store));
	}

	/** The value the store was loaded with, so re-setting the same platform does not count as a switch. */
	protected BillingPlatform getOriginalPlatform(final BaseStoreModel store)
	{
		return ModelContextUtils.getItemModelContext(store).getOriginalValue(BaseStoreModel.ACTIVEBILLINGPLATFORM);
	}

	protected void validateConnectorLoaded(final BaseStoreModel store, final BillingPlatform platform)
			throws InterceptorException
	{
		if (getConnectorRegistry().findConnector(platform).isEmpty())
		{
			throw new InterceptorException(String.format(
					"Base store '%s' cannot use billing platform %s: no connector for it is loaded. "
							+ "Available platforms: %s.",
					store.getUid(), platform.getCode(), describeAvailablePlatforms()), this);
		}
	}

	protected void validateStoreReady(final BaseStoreModel store, final BillingPlatform platform)
			throws InterceptorException
	{
		final List<String> missing = findMissingSettings(store, platform);
		if (!missing.isEmpty())
		{
			throw new InterceptorException(String.format(
					"Base store '%s' cannot use billing platform %s until its configuration is complete. Missing: %s.",
					store.getUid(), platform.getCode(), String.join("; ", missing)), this);
		}
	}

	/** Missing settings reported by every readiness check registered for the platform. */
	protected List<String> findMissingSettings(final BaseStoreModel store, final BillingPlatform platform)
	{
		return getReadinessChecks().stream().filter(check -> platform.equals(check.platform()))
				.flatMap(check -> Stream.ofNullable(check.missingSettings(store)).flatMap(List::stream))
				.filter(Objects::nonNull).distinct().collect(Collectors.toList());
	}

	protected String describeAvailablePlatforms()
	{
		final List<String> codes = getConnectorRegistry().getAvailablePlatforms().stream().map(BillingPlatform::getCode)
				.sorted().collect(Collectors.toList());
		return codes.isEmpty() ? "none" : String.join(", ", codes);
	}

	protected SubscriptionBillingConnectorRegistry getConnectorRegistry()
	{
		return connectorRegistry;
	}

	public void setConnectorRegistry(final SubscriptionBillingConnectorRegistry connectorRegistry)
	{
		this.connectorRegistry = connectorRegistry;
	}

	protected List<BillingPlatformReadinessCheck> getReadinessChecks()
	{
		return readinessChecks;
	}

	public void setReadinessChecks(final List<BillingPlatformReadinessCheck> readinessChecks)
	{
		this.readinessChecks = readinessChecks;
	}
}
