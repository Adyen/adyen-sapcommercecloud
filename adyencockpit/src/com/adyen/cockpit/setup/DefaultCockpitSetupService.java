package com.adyen.cockpit.setup;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.cockpit.setup.ManagementApiClient.Failure;
import com.adyen.cockpit.setup.ManagementApiClient.Response;
import com.adyen.cockpit.setup.dto.CredentialCheck;
import com.adyen.cockpit.setup.dto.MerchantAccountView;
import com.adyen.cockpit.setup.dto.MerchantAccounts;
import com.adyen.cockpit.setup.dto.ProvisionReport;
import com.adyen.cockpit.setup.dto.ProvisionStep;
import com.adyen.cockpit.setup.dto.SetupRequests;
import com.adyen.cockpit.setup.dto.SiteView;
import com.adyen.cockpit.setup.dto.StoreSetupView;

import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

public class DefaultCockpitSetupService implements CockpitSetupService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultCockpitSetupService.class);

	/**
	 * What the setup cannot do without, as Adyen names them. Creating the storefront credential needs the
	 * first, the webhook and its HMAC key the second, the merchant account list the third. A key missing one
	 * authenticates fine and then fails partway, which is why they are checked before the key is kept.
	 */
	static final List<String> REQUIRED_ROLES = List.of(
			"Management API—API credentials read and write",
			"Management API—Webhooks read and write",
			"Management API—Accounts read");

	/**
	 * Each required role once case, dash characters and spacing are normalised away. Adyen renders the
	 * dash in these names inconsistently, even within one response, so the raw names cannot be compared.
	 */
	private static final List<String> REQUIRED_ROLE_MARKERS = List.of(
			"api credentials read and write",
			"webhooks read and write",
			"accounts read");

	/** What the storefront's credential is for. Payments only; it never administers the account. */
	private static final List<String> CHECKOUT_ROLES = List.of("Checkout webservice role");

	static final String NOTIFICATION_PATH_PROPERTY = "adyencockpit.notification.path";
	/** Where adyenv6notificationv2 receives a site's notifications, under its own webapp context. */
	static final String DEFAULT_NOTIFICATION_PATH = "/adyenv6notificationv2/adyen/v6/notification/{site}/json";

	/** Scheme and host, optional port, nothing after. The form Adyen accepts as an allowed origin. */
	private static final Pattern ORIGIN = Pattern.compile("^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?$");
	/** Adyen merchant account ids; checked because the id becomes part of a request path. */
	private static final Pattern MERCHANT_ACCOUNT_ID = Pattern.compile("^[A-Za-z0-9_.-]{1,80}$");

	static final String STEP_CREDENTIAL = "credential";
	static final String STEP_ORIGIN = "allowedOrigin";
	static final String STEP_WEBHOOK = "webhook";
	static final String STEP_HMAC = "hmac";

	private BaseStoreService baseStoreService;
	private ModelService modelService;
	private ConfigurationService configurationService;
	private ManagementApiClient managementApiClient;
	private final SecureRandom random = new SecureRandom();

	/**
	 * Stores with a run in progress on this node. Two interleaved runs would leave the store holding one
	 * run's webhook password and the other's HMAC key, and every notification would then be rejected.
	 */
	private final Set<String> provisioning = ConcurrentHashMap.newKeySet();

	// --- status ----------------------------------------------------------------------------------------

	@Override
	public List<StoreSetupView> stores()
	{
		return baseStoreService.getAllBaseStores().stream()
				.sorted(Comparator.comparing(BaseStoreModel::getUid))
				.map(this::describe)
				.toList();
	}

	@Override
	public StoreSetupView describe(final BaseStoreModel store)
	{
		final List<SiteView> sites = store.getCmsSites() == null ? List.of()
				: store.getCmsSites().stream()
						.sorted(Comparator.comparing(BaseSiteModel::getUid))
						.map(site -> new SiteView(site.getUid(), StringUtils.defaultIfBlank(site.getName(), site.getUid())))
						.toList();

		return new StoreSetupView(
				store.getUid(),
				StringUtils.defaultIfBlank(store.getName(), store.getUid()),
				!Boolean.FALSE.equals(store.getAdyenTestMode()),
				store.getAdyenMerchantAccount(),
				StringUtils.isNotBlank(store.getAdyenManagementApiKey()),
				StringUtils.isNotBlank(store.getAdyenAPIKey()),
				StringUtils.isNotBlank(store.getAdyenClientKey()),
				StringUtils.isNotBlank(store.getAdyenNotificationUsername())
						&& StringUtils.isNotBlank(store.getAdyenNotificationPassword()),
				StringUtils.isNotBlank(store.getAdyenNotificationHMACKey()),
				sites);
	}

	@Override
	public String notificationPath(final String siteUid)
	{
		final String template = StringUtils.defaultIfBlank(
				configurationService.getConfiguration().getString(NOTIFICATION_PATH_PROPERTY), DEFAULT_NOTIFICATION_PATH);
		return template.replace("{site}", siteUid);
	}

	// --- step 1: Management API key --------------------------------------------------------------------

	@Override
	@SuppressWarnings("unchecked")
	public CredentialCheck saveManagementKey(final BaseStoreModel store, final String apiKey)
	{
		final String key = StringUtils.trimToNull(apiKey);
		if (key == null)
		{
			return new CredentialCheck(false, false, null, null, List.of(), "missing");
		}

		final Response me = managementApiClient.get(store, "/me", key);
		if (!me.ok())
		{
			return new CredentialCheck(false, false, null, null, List.of(), rejectionOf(me.failure()));
		}

		final Object rawRoles = me.body().get("roles");
		final List<String> roles = rawRoles instanceof List<?> list
				? list.stream().filter(Objects::nonNull).map(Object::toString).toList()
				: List.of();
		final List<String> missing = new ArrayList<>();
		for (int i = 0; i < REQUIRED_ROLES.size(); i++)
		{
			if (!holdsRole(roles, REQUIRED_ROLE_MARKERS.get(i)))
			{
				missing.add(REQUIRED_ROLES.get(i));
			}
		}

		final boolean active = Boolean.TRUE.equals(me.body().get("active"));
		final String username = (String) me.body().get("username");
		final String companyName = (String) me.body().get("companyName");
		final boolean usable = active && missing.isEmpty();

		if (usable)
		{
			store.setAdyenManagementApiKey(key);
			modelService.save(store);
			// Never the key: the company it administers is what makes the line useful.
			LOG.info("Stored a Management API key for base store '{}' (Adyen company '{}').", store.getUid(), companyName);
		}
		else if (!missing.isEmpty())
		{
			// Role names are not credentials, and they are the only way to tell a missing permission from a
			// name this code failed to recognise.
			LOG.info("Adyen credential '{}' reports roles {}, which do not cover {}.", username, roles, missing);
		}
		return new CredentialCheck(usable, active, username, companyName, missing, null);
	}

	// --- step 2: merchant account ----------------------------------------------------------------------

	@Override
	@SuppressWarnings("unchecked")
	public MerchantAccounts merchantAccounts(final BaseStoreModel store)
	{
		final String key = store.getAdyenManagementApiKey();
		if (StringUtils.isBlank(key))
		{
			return new MerchantAccounts(List.of(), "no-key");
		}

		// One page of a hundred. A company with more merchant accounts than that is rare enough that the
		// list says so rather than paging silently.
		final Response response = managementApiClient.get(store, "/merchants?pageSize=100", key);
		if (!response.ok())
		{
			return new MerchantAccounts(List.of(), rejectionOf(response.failure()));
		}

		final Object data = response.body().get("data");
		if (!(data instanceof List<?> items))
		{
			return new MerchantAccounts(List.of(), null);
		}
		final List<MerchantAccountView> accounts = items.stream()
				.filter(Map.class::isInstance)
				.map(item -> (Map<String, Object>) item)
				.map(item -> new MerchantAccountView(
						(String) item.get("id"),
						StringUtils.defaultIfBlank((String) item.get("name"), (String) item.get("id")),
						(String) item.get("status")))
				.filter(account -> account.id() != null)
				.sorted(Comparator.comparing(MerchantAccountView::id))
				.toList();
		return new MerchantAccounts(accounts, null);
	}

	@Override
	public boolean saveMerchantAccount(final BaseStoreModel store, final String merchantAccount)
	{
		if (merchantAccount == null || !MERCHANT_ACCOUNT_ID.matcher(merchantAccount).matches())
		{
			return false;
		}
		final MerchantAccounts visible = merchantAccounts(store);
		final boolean known = visible.failure() == null
				&& visible.accounts().stream().anyMatch(account -> account.id().equals(merchantAccount));
		if (!known)
		{
			return false;
		}
		store.setAdyenMerchantAccount(merchantAccount);
		modelService.save(store);
		return true;
	}

	// --- step 3: storefront credentials ----------------------------------------------------------------

	@Override
	public Optional<String> provisionProblem(final BaseStoreModel store, final SetupRequests.Provision request)
	{
		if (StringUtils.isBlank(store.getAdyenManagementApiKey()))
		{
			return Optional.of("Add a Management API key for this store first.");
		}
		final String merchantAccount = store.getAdyenMerchantAccount();
		if (merchantAccount == null || !MERCHANT_ACCOUNT_ID.matcher(merchantAccount).matches())
		{
			return Optional.of("Choose a merchant account for this store first.");
		}
		if (normaliseOrigin(request.storefrontOrigin()) == null)
		{
			return Optional.of("The storefront origin must be https:// followed by a host, with no path.");
		}
		if (StringUtils.isNotBlank(request.notificationBaseUrl()) && normaliseOrigin(request.notificationBaseUrl()) == null)
		{
			return Optional.of("The notification address must be https:// followed by a host, with no path.");
		}
		if (siteOf(store, request.site()) == null)
		{
			return Optional.of("Choose one of this store's sites to receive notifications.");
		}
		return Optional.empty();
	}

	@Override
	public ProvisionReport provision(final BaseStoreModel store, final SetupRequests.Provision request)
	{
		if (!provisioning.add(store.getUid()))
		{
			throw new ProvisioningInProgressException(store.getUid());
		}
		try
		{
			return runProvisioning(store, request);
		}
		finally
		{
			provisioning.remove(store.getUid());
		}
	}

	private ProvisionReport runProvisioning(final BaseStoreModel store, final SetupRequests.Provision request)
	{
		final List<ProvisionStep> steps = new ArrayList<>();
		final Optional<String> problem = provisionProblem(store, request);
		if (problem.isPresent())
		{
			steps.add(new ProvisionStep(STEP_CREDENTIAL, false, problem.get()));
			return new ProvisionReport(false, steps);
		}

		final String key = store.getAdyenManagementApiKey();
		final String merchantAccount = store.getAdyenMerchantAccount();
		final String origin = normaliseOrigin(request.storefrontOrigin());
		final String base = StringUtils.isBlank(request.notificationBaseUrl()) ? origin
				: normaliseOrigin(request.notificationBaseUrl());
		final String notificationUrl = base + notificationPath(siteOf(store, request.site()).getUid());

		if (!createCheckoutCredential(store, merchantAccount, origin, key, steps))
		{
			return new ProvisionReport(false, steps);
		}
		final String webhookId = createWebhook(store, merchantAccount, notificationUrl, key, steps);
		if (webhookId == null)
		{
			return new ProvisionReport(false, steps);
		}
		final boolean hmac = generateHmac(store, merchantAccount, webhookId, key, steps);
		return new ProvisionReport(hmac, steps);
	}

	/**
	 * Creates the storefront's credential and saves what came back before anything else is attempted:
	 * Adyen returns a new API key exactly once, so a key held until a later step succeeds is a key lost
	 * when that step fails. The allowed origin travels in the same request.
	 */
	private boolean createCheckoutCredential(final BaseStoreModel store, final String merchantAccount,
			final String origin, final String key, final List<ProvisionStep> steps)
	{
		final Response created = managementApiClient.post(store, "/merchants/" + merchantAccount + "/apiCredentials",
				Map.of("description", "SAP Commerce storefront - " + store.getUid(),
						"roles", CHECKOUT_ROLES,
						"allowedOrigins", List.of(origin)),
				key);
		if (!created.ok())
		{
			// Adyen only grants a role the company credential already holds, so that is where to look.
			steps.add(new ProvisionStep(STEP_CREDENTIAL, false, created.failure() == Failure.FORBIDDEN
					? "Adyen refused to create the credential. Check that ws@Company.<CompanyName> holds the Checkout webservice role."
					: "Adyen did not create the credential (" + describe(created) + ")."));
			return false;
		}

		final String apiKey = (String) created.body().get("apiKey");
		if (StringUtils.isBlank(apiKey))
		{
			steps.add(new ProvisionStep(STEP_CREDENTIAL, false, "Adyen created the credential but returned no API key."));
			return false;
		}
		store.setAdyenAPIKey(apiKey);
		store.setAdyenClientKey((String) created.body().get("clientKey"));
		modelService.save(store);

		steps.add(new ProvisionStep(STEP_CREDENTIAL, true, "Created API credential " + created.body().get("username") + "."));
		steps.add(new ProvisionStep(STEP_ORIGIN, true, "Registered " + origin + " as an allowed origin."));
		return true;
	}

	private String createWebhook(final BaseStoreModel store, final String merchantAccount, final String url,
			final String key, final List<ProvisionStep> steps)
	{
		final String username = "sapcc-" + store.getUid();
		final String password = newPassword();
		final Response created = managementApiClient.post(store, "/merchants/" + merchantAccount + "/webhooks",
				new WebhookRequest(url, username, password, "SAP Commerce - " + store.getUid()), key);
		if (!created.ok())
		{
			steps.add(new ProvisionStep(STEP_WEBHOOK, false, "Adyen did not create the webhook (" + describe(created) + ")."));
			return null;
		}

		store.setAdyenNotificationUsername(username);
		store.setAdyenNotificationPassword(password);
		modelService.save(store);
		steps.add(new ProvisionStep(STEP_WEBHOOK, true, "Created a webhook to " + url + "."));

		final String id = (String) created.body().get("id");
		if (StringUtils.isBlank(id))
		{
			// Without the id there is nothing to generate an HMAC key for. Reported as the HMAC step failing,
			// so the report does not simply end and look finished.
			steps.add(new ProvisionStep(STEP_HMAC, false,
					"Adyen did not return the webhook's id, so its HMAC key could not be generated. Generate one in the Customer Area."));
		}
		return StringUtils.trimToNull(id);
	}

	private boolean generateHmac(final BaseStoreModel store, final String merchantAccount, final String webhookId,
			final String key, final List<ProvisionStep> steps)
	{
		final Response created = managementApiClient.post(store,
				"/merchants/" + merchantAccount + "/webhooks/" + webhookId + "/generateHmac", Map.of(), key);
		final String hmacKey = created.ok() ? (String) created.body().get("hmacKey") : null;
		if (StringUtils.isBlank(hmacKey))
		{
			// A webhook that cannot be verified is worse than none: notifications arrive and are rejected.
			steps.add(new ProvisionStep(STEP_HMAC, false,
					"The webhook exists but Adyen returned no HMAC key. Notifications cannot be verified until one is generated."));
			return false;
		}
		store.setAdyenNotificationHMACKey(hmacKey);
		modelService.save(store);
		steps.add(new ProvisionStep(STEP_HMAC, true, "Generated the webhook's HMAC key."));
		return true;
	}

	/**
	 * The body of a webhook creation. A record rather than a map because RestTemplate logs the request
	 * body at DEBUG through toString(), and this one carries the webhook's password.
	 */
	record WebhookRequest(String type, String communicationFormat, boolean active, String url, String username,
			String password, String description)
	{
		WebhookRequest(final String url, final String username, final String password, final String description)
		{
			this("standard", "json", true, url, username, password, description);
		}

		@Override
		public String toString()
		{
			return "WebhookRequest[url=" + url + ", username=" + username + ", password=***]";
		}
	}

	// --- helpers ---------------------------------------------------------------------------------------

	static String normaliseOrigin(final String value)
	{
		final String trimmed = StringUtils.trimToEmpty(value).replaceAll("/+$", "");
		return ORIGIN.matcher(trimmed).matches() ? trimmed : null;
	}

	private static BaseSiteModel siteOf(final BaseStoreModel store, final String siteUid)
	{
		if (StringUtils.isBlank(siteUid) || store.getCmsSites() == null)
		{
			return null;
		}
		return store.getCmsSites().stream().filter(site -> siteUid.equals(site.getUid())).findFirst().orElse(null);
	}

	private static boolean holdsRole(final List<String> roles, final String marker)
	{
		return roles.stream().anyMatch(role -> normalise(role).contains(marker));
	}

	private static String normalise(final String role)
	{
		return role.toLowerCase(Locale.ROOT).replaceAll("\\p{Pd}", " ").replaceAll("\\s+", " ").trim();
	}

	private static String rejectionOf(final Failure failure)
	{
		return switch (failure)
		{
			case UNRECOGNISED -> "unrecognised";
			case FORBIDDEN -> "forbidden";
			case UNREACHABLE -> "unreachable";
			case MISCONFIGURED -> "misconfigured";
			case REJECTED -> "rejected";
		};
	}

	private static String describe(final Response response)
	{
		return switch (response.failure())
		{
			case UNRECOGNISED -> "the Management API key was not recognised";
			case FORBIDDEN -> "the Management API key lacks the permission";
			case UNREACHABLE -> "Adyen could not be reached";
			case MISCONFIGURED -> "the Management API endpoint is misconfigured";
			case REJECTED -> "Adyen answered " + response.status();
		};
	}

	protected String newPassword()
	{
		final byte[] bytes = new byte[24];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public void setBaseStoreService(final BaseStoreService baseStoreService)
	{
		this.baseStoreService = baseStoreService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setManagementApiClient(final ManagementApiClient managementApiClient)
	{
		this.managementApiClient = managementApiClient;
	}
}
