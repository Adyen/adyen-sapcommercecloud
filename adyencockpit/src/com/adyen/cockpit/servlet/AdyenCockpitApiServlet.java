package com.adyen.cockpit.servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.adyen.cockpit.setup.CockpitSetupService;
import com.adyen.cockpit.setup.ProvisioningInProgressException;
import com.adyen.cockpit.setup.dto.CredentialCheck;
import com.adyen.cockpit.setup.dto.ProvisionReport;
import com.adyen.cockpit.setup.dto.SetupRequests;
import com.adyen.cockpit.setup.dto.StoreSetupView;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Backs the cockpit application. It runs inside the Backoffice webapp, behind Backoffice's own security
 * chain, so the caller is whoever is signed in to Backoffice and there is no second login to get wrong.
 *
 * <p>The setup endpoints write live credentials into store configuration, so they additionally require an
 * administrator, and every write must arrive as JSON from the same site - see {@link #isTrustedWrite}.</p>
 */
public class AdyenCockpitApiServlet extends HttpServlet
{
	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(AdyenCockpitApiServlet.class);

	private static final ObjectMapper JSON = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException
	{
		handle(request, response, false);
	}

	@Override
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws IOException
	{
		handle(request, response, true);
	}

	private void handle(final HttpServletRequest request, final HttpServletResponse response, final boolean write)
			throws IOException
	{
		final String path = StringUtils.defaultString(request.getPathInfo());
		try
		{
			if (!write && "/bootstrap".equals(path))
			{
				send(response, 200, bootstrap());
				return;
			}
			if (!path.startsWith("/setup/"))
			{
				send(response, 404, error("Unknown endpoint."));
				return;
			}
			if (!isAdmin())
			{
				send(response, 403, error("Connecting a store to Adyen requires a Backoffice administrator."));
				return;
			}
			if (write && !isTrustedWrite(request))
			{
				send(response, 415, error("Send the request as JSON from the cockpit."));
				return;
			}
			route(request, response, path, write);
		}
		catch (final RuntimeException e)
		{
			// The exception type only: a message here could carry a submitted key or Adyen's reply.
			LOG.error("Cockpit request {} {} failed ({}).", request.getMethod(), path, e.getClass().getSimpleName());
			send(response, 500, error("The request could not be completed."));
		}
	}

	private void route(final HttpServletRequest request, final HttpServletResponse response, final String path,
			final boolean write) throws IOException
	{
		final CockpitSetupService setup = bean("adyencockpitSetupService", CockpitSetupService.class);

		if (!write && "/setup/stores".equals(path))
		{
			final Map<String, Object> body = new LinkedHashMap<>();
			body.put("notificationPath", setup.notificationPath("{site}"));
			body.put("stores", setup.stores());
			send(response, 200, body);
			return;
		}

		if (!write && "/setup/merchant-accounts".equals(path))
		{
			final BaseStoreModel store = store(request.getParameter("store"));
			if (store == null)
			{
				send(response, 404, error("Unknown base store."));
				return;
			}
			send(response, 200, setup.merchantAccounts(store));
			return;
		}

		if (write && "/setup/management-key".equals(path))
		{
			final SetupRequests.ManagementKey body = read(request, SetupRequests.ManagementKey.class);
			final BaseStoreModel store = body == null ? null : store(body.store());
			if (store == null || StringUtils.isBlank(body.apiKey()))
			{
				send(response, 400, error("Name a base store and paste a key."));
				return;
			}
			final CredentialCheck check = setup.saveManagementKey(store, body.apiKey());
			send(response, check.usable() ? 200 : 422, check);
			return;
		}

		if (write && "/setup/merchant-account".equals(path))
		{
			final SetupRequests.MerchantAccount body = read(request, SetupRequests.MerchantAccount.class);
			final BaseStoreModel store = body == null ? null : store(body.store());
			if (store == null)
			{
				send(response, 400, error("Name a base store and a merchant account."));
				return;
			}
			if (!setup.saveMerchantAccount(store, body.merchantAccount()))
			{
				send(response, 422, error("That merchant account is not one this store's key can see."));
				return;
			}
			send(response, 200, setup.describe(store));
			return;
		}

		if (write && "/setup/provision".equals(path))
		{
			final SetupRequests.Provision body = read(request, SetupRequests.Provision.class);
			final BaseStoreModel store = body == null ? null : store(body.store());
			if (store == null)
			{
				send(response, 400, error("Name a base store."));
				return;
			}
			final Optional<String> problem = setup.provisionProblem(store, body);
			if (problem.isPresent())
			{
				send(response, 422, error(problem.get()));
				return;
			}
			final ProvisionReport report;
			try
			{
				report = setup.provision(store, body);
			}
			catch (final ProvisioningInProgressException e)
			{
				send(response, 409, error("Credentials are already being created for this store. Wait for that run to finish."));
				return;
			}
			final Map<String, Object> result = new LinkedHashMap<>();
			result.put("report", report);
			result.put("store", setup.describe(store));
			send(response, report.complete() ? 200 : 422, result);
			return;
		}

		send(response, 404, error("Unknown endpoint."));
	}

	/**
	 * Backoffice runs with CSRF protection off and its session cookie carries no SameSite attribute, so a
	 * write is only trusted when it could not have come from another site: it must be JSON, which an HTML
	 * form cannot send and a cross-site fetch cannot send without a preflight this servlet never answers,
	 * and, where the browser says where the request came from, it must say the same origin. The Origin
	 * header is not compared with the Host header because a reverse proxy may rewrite the latter.
	 */
	private static boolean isTrustedWrite(final HttpServletRequest request)
	{
		final String contentType = StringUtils.defaultString(request.getContentType()).toLowerCase();
		if (!contentType.startsWith("application/json"))
		{
			return false;
		}
		final String fetchSite = request.getHeader("Sec-Fetch-Site");
		return fetchSite == null || "same-origin".equals(fetchSite);
	}

	private Map<String, Object> bootstrap()
	{
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("user", currentUser().getUid());
		body.put("admin", isAdmin());
		final List<Map<String, Object>> stores = bean("baseStoreService", BaseStoreService.class).getAllBaseStores()
				.stream()
				.map(store -> Map.<String, Object> of("uid", store.getUid(),
						"configured", StringUtils.isNotBlank(store.getAdyenManagementApiKey())))
				.toList();
		body.put("stores", stores);
		return body;
	}

	/** Granted by membership of admingroup; the authority Backoffice itself requires for /admin/**. */
	private static final String ADMIN_AUTHORITY = "ROLE_ADMINGROUP";

	/**
	 * The same rule Backoffice applies to its own administration area. UserService.isAdmin is not used:
	 * inside Backoffice it resolves to BackofficeUserService, which also counts backofficeadmingroup, and
	 * that group includes roles such as customer support administrators.
	 */
	private static boolean isAdmin()
	{
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated())
		{
			return false;
		}
		for (final GrantedAuthority authority : authentication.getAuthorities())
		{
			if (ADMIN_AUTHORITY.equals(authority.getAuthority()))
			{
				return true;
			}
		}
		return false;
	}

	private UserModel currentUser()
	{
		return bean("userService", UserService.class).getCurrentUser();
	}

	private BaseStoreModel store(final String uid)
	{
		if (StringUtils.isBlank(uid))
		{
			return null;
		}
		try
		{
			return bean("baseStoreService", BaseStoreService.class).getBaseStoreForUid(uid);
		}
		catch (final RuntimeException e)
		{
			return null;
		}
	}

	/**
	 * Looked up per request on purpose. The Backoffice root context refreshes lazily on the first HTTP
	 * session, so a reference taken in {@code init()} can be taken too early.
	 */
	private <T> T bean(final String name, final Class<T> type)
	{
		final ApplicationContext context = WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
		return context.getBean(name, type);
	}

	private static <T> T read(final HttpServletRequest request, final Class<T> type)
	{
		try
		{
			return JSON.readValue(request.getInputStream(), type);
		}
		catch (final IOException e)
		{
			return null;
		}
	}

	private static Map<String, Object> error(final String message)
	{
		return Map.of("error", message);
	}

	private static void send(final HttpServletResponse response, final int status, final Object body) throws IOException
	{
		response.setStatus(status);
		response.setContentType("application/json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setHeader("Cache-Control", "no-store");
		JSON.writeValue(response.getOutputStream(), body);
	}
}
