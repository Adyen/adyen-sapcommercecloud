package com.adyen.cockpit.api.provider;

/** The payment and billing providers the cockpit talks to. The lower-case name is what the API puts on the wire. */
public enum Provider
{
	ADYEN,
	RECURLY,
	CHARGEBEE;

	public String wireName()
	{
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
