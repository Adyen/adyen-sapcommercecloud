package com.adyen.cockpit.setup.dto;

import java.util.List;

/**
 * What Adyen said about a Management API key.
 *
 * @param rejection why Adyen did not describe the key at all, or {@code null} when it did. One of
 *                  {@code missing} (no key was sent, Adyen was not asked), {@code unrecognised} (401),
 *                  {@code forbidden} (403), {@code unreachable}, {@code misconfigured}, {@code rejected}.
 * @param missingRoles required roles the key lacks. Empty when Adyen never described the key, since
 *                     nothing is then known about its roles.
 */
public record CredentialCheck(
		boolean usable,
		boolean active,
		String username,
		String companyName,
		List<String> missingRoles,
		String rejection)
{
}
