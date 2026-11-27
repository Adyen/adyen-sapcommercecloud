package com.adyen.cockpit.api;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Decides whether the signed-in Backoffice user holds the permission a route declares. Kept apart from the
 * servlet so a route can ask the same question (the bootstrap answers whether the user is an administrator),
 * and non-static so a merchant can override it.
 */
public class CockpitAccess
{
	private static final AuthenticationTrustResolver TRUST = new AuthenticationTrustResolverImpl();

	/** Granted by membership of admingroup; the authority Backoffice itself requires for /admin/**. */
	public static final String ADMIN_AUTHORITY = "ROLE_ADMINGROUP";

	/**
	 * The same rule Backoffice applies to its own administration area, for any authority.
	 * UserService.isAdmin is not used: inside Backoffice it resolves to BackofficeUserService, which also
	 * counts backofficeadmingroup, and that group includes roles such as customer support administrators.
	 *
	 * @param permission an authority, or {@link CockpitRoute#AUTHENTICATED} for any authenticated user
	 */
	public boolean hasPermission(final String permission)
	{
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		// An anonymous token is "authenticated" to Spring Security but is nobody: AUTHENTICATED must refuse it.
		if (authentication == null || !authentication.isAuthenticated() || TRUST.isAnonymous(authentication))
		{
			return false;
		}
		if (CockpitRoute.AUTHENTICATED.equals(permission))
		{
			return true;
		}
		for (final GrantedAuthority authority : authentication.getAuthorities())
		{
			if (permission != null && permission.equals(authority.getAuthority()))
			{
				return true;
			}
		}
		return false;
	}

	public boolean isAdmin()
	{
		return hasPermission(ADMIN_AUTHORITY);
	}
}
