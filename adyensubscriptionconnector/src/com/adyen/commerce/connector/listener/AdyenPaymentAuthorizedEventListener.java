package com.adyen.commerce.connector.listener;

import com.adyen.commerce.connector.hook.impl.DefaultSubscriptionActivationPlaceOrderMethodHook;
import com.adyen.v6.event.AdyenPaymentAuthorizedEvent;

import de.hybris.platform.servicelayer.event.impl.AbstractEventListener;

/** Re-enters the idempotent subscription activation flow after 3DS/redirect authorization. */
public class AdyenPaymentAuthorizedEventListener extends AbstractEventListener<AdyenPaymentAuthorizedEvent>
{
	private DefaultSubscriptionActivationPlaceOrderMethodHook activationHook;

	@Override
	protected void onEvent(final AdyenPaymentAuthorizedEvent event)
	{
		activationHook.activateOrder(event == null ? null : event.getOrder());
	}

	public void setActivationHook(final DefaultSubscriptionActivationPlaceOrderMethodHook activationHook)
	{
		this.activationHook = activationHook;
	}
}
