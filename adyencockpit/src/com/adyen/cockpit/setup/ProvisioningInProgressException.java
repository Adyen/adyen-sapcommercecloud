package com.adyen.cockpit.setup;

/** A second provisioning run was started for a store whose first run has not finished. */
public class ProvisioningInProgressException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	public ProvisioningInProgressException(final String storeUid)
	{
		super("Provisioning is already running for base store " + storeUid);
	}
}
