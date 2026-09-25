package com.adyen.cockpit.setup.dto;

/** Request bodies the setup endpoints accept. */
public final class SetupRequests
{
	private SetupRequests()
	{
	}

	public record ManagementKey(String store, String apiKey)
	{
	}

	public record MerchantAccount(String store, String merchantAccount)
	{
	}

	/**
	 * @param notificationBaseUrl where Adyen can reach this installation, when that is not the storefront
	 *                            origin - a tunnel in local development, say. Optional.
	 */
	public record Provision(String store, String storefrontOrigin, String site, String notificationBaseUrl)
	{
	}
}
