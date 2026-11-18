package com.adyen.cockpit.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;

import com.adyen.cockpit.setup.ManagementApiClient.Failure;
import com.adyen.cockpit.setup.ManagementApiClient.Response;
import com.adyen.cockpit.setup.dto.CredentialCheck;
import com.adyen.cockpit.setup.dto.MerchantAccounts;
import com.adyen.cockpit.setup.dto.ProvisionReport;
import com.adyen.cockpit.setup.dto.SetupRequests;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

@UnitTest
public class DefaultCockpitSetupServiceTest
{
	/**
	 * Verbatim from a company-level credential in the test environment. Adyen mixes dash characters within
	 * one response, so the names are kept exactly as returned rather than tidied to one convention.
	 */
	private static final List<String> ROLES_AS_ADYEN_RETURNS_THEM = List.of(
			"Management API - Accounts read",
			"Management API - Webhooks read",
			"Cloud Device API role",
			"Management API - API credentials read and write",
			"Management API - Stores read",
			"Management API — Payment methods read",
			"Allow SDK download for POS developers",
			"API Clientside Encryption Payments role",
			"Management API - Stores read and write",
			"Management API - Webhooks read and write",
			"Checkout encrypted cardholder data",
			"Merchant Recurring role",
			"Data Protection API",
			"Management API - Payout Account Settings Read",
			"Checkout webservice role",
			"Adyen Payments App role",
			"Management API - Accounts read and write",
			"Merchant PAL Webservice role");

	/** Stands in for Adyen: answers per "METHOD path" and records what was sent. */
	private static class FakeAdyen extends ManagementApiClient
	{
		final Map<String, Response> answers = new HashMap<>();
		final List<String> calls = new ArrayList<>();
		final List<Object> bodies = new ArrayList<>();

		@Override
		protected Response exchange(final String url, final HttpMethod method, final Object body, final String apiKey)
		{
			final String call = method.name() + " " + url.substring(url.indexOf("/v3") + 3);
			calls.add(call);
			bodies.add(body);
			return answers.getOrDefault(call, Response.failed(Failure.REJECTED, 500));
		}

		void answer(final String call, final Map<String, Object> body)
		{
			answers.put(call, Response.of(body));
		}

		void refuse(final String call, final Failure failure, final int status)
		{
			answers.put(call, Response.failed(failure, status));
		}
	}

	private final ConfigurationService configurationService = mock(ConfigurationService.class);
	private final Configuration configuration = mock(Configuration.class);
	private final ModelService modelService = mock(ModelService.class);
	private final BaseStoreService baseStoreService = mock(BaseStoreService.class);

	private FakeAdyen adyen;
	private DefaultCockpitSetupService service;
	private BaseStoreModel store;
	private BaseSiteModel site;

	@Before
	public void setUp()
	{
		when(configurationService.getConfiguration()).thenReturn(configuration);

		adyen = new FakeAdyen();
		adyen.setConfigurationService(configurationService);

		service = new DefaultCockpitSetupService()
		{
			@Override
			protected String newPassword()
			{
				return "generated-password";
			}
		};
		service.setBaseStoreService(baseStoreService);
		service.setModelService(modelService);
		service.setConfigurationService(configurationService);
		service.setManagementApiClient(adyen);

		site = mock(BaseSiteModel.class);
		when(site.getUid()).thenReturn("electronics");
		when(site.getName()).thenReturn("Electronics Site");

		store = mock(BaseStoreModel.class);
		when(store.getUid()).thenReturn("electronics");
		when(store.getAdyenTestMode()).thenReturn(Boolean.TRUE);
		when(store.getCmsSites()).thenReturn(List.of(site));
	}

	private static Map<String, Object> me(final boolean active, final List<String> roles)
	{
		return Map.of("username", "ws_413377@Company.REPLYAccount", "companyName", "REPLYAccount",
				"active", active, "roles", roles);
	}

	// --- Management API key ------------------------------------------------------------------------------

	@Test
	public void acceptsAndStoresAKeyWithTheRolesAdyenActuallyReturns()
	{
		adyen.answer("GET /me", me(true, ROLES_AS_ADYEN_RETURNS_THEM));

		final CredentialCheck check = service.saveManagementKey(store, "  AQE-key  ");

		assertTrue(check.usable());
		assertTrue(check.missingRoles().isEmpty());
		assertNull(check.rejection());
		verify(store).setAdyenManagementApiKey("AQE-key");
		verify(modelService).save(store);
	}

	@Test
	public void refusesAKeyThatCannotListMerchantAccounts()
	{
		adyen.answer("GET /me", me(true, List.of(
				"Management API - API credentials read and write",
				"Management API - Webhooks read and write")));

		final CredentialCheck check = service.saveManagementKey(store, "key");

		assertFalse(check.usable());
		assertEquals(List.of("Management API—Accounts read"), check.missingRoles());
		verify(store, never()).setAdyenManagementApiKey(anyString());
	}

	@Test
	public void aRoleThatOnlyReadsDoesNotCountAsReadAndWrite()
	{
		adyen.answer("GET /me", me(true, List.of(
				"Management API - API credentials read",
				"Management API - Webhooks read and write",
				"Management API - Accounts read")));

		assertEquals(List.of("Management API—API credentials read and write"),
				service.saveManagementKey(store, "key").missingRoles());
	}

	@Test
	public void refusesAnInactiveCredentialEvenWithEveryRole()
	{
		adyen.answer("GET /me", me(false, ROLES_AS_ADYEN_RETURNS_THEM));

		assertFalse(service.saveManagementKey(store, "key").usable());
		verify(modelService, never()).save(any());
	}

	/** A 401 says nothing about roles; listing them as missing would send the merchant the wrong way. */
	@Test
	public void anUnrecognisedKeyReportsNoMissingRoles()
	{
		adyen.refuse("GET /me", Failure.UNRECOGNISED, 401);

		final CredentialCheck check = service.saveManagementKey(store, "key");

		assertEquals("unrecognised", check.rejection());
		assertTrue(check.missingRoles().isEmpty());
		verify(modelService, never()).save(any());
	}

	@Test
	public void aBlankKeyIsNotSentToAdyen()
	{
		assertEquals("missing", service.saveManagementKey(store, "   ").rejection());
		assertTrue(adyen.calls.isEmpty());
	}

	// --- environment -------------------------------------------------------------------------------------

	@Test
	public void aStoreTalksToTheEnvironmentItsTestModeNames()
	{
		when(store.getAdyenTestMode()).thenReturn(Boolean.TRUE);
		assertEquals(ManagementApiClient.TEST_ENDPOINT, adyen.endpointFor(store));

		when(store.getAdyenTestMode()).thenReturn(Boolean.FALSE);
		assertEquals(ManagementApiClient.LIVE_ENDPOINT, adyen.endpointFor(store));
	}

	@Test
	public void aStoreWithNoModeIsTreatedAsTest()
	{
		when(store.getAdyenTestMode()).thenReturn(null);
		assertEquals(ManagementApiClient.TEST_ENDPOINT, adyen.endpointFor(store));
	}

	@Test
	public void anExplicitEndpointOverridesTheStoresMode()
	{
		when(store.getAdyenTestMode()).thenReturn(Boolean.FALSE);
		when(configuration.getString(ManagementApiClient.ENDPOINT_OVERRIDE_PROPERTY)).thenReturn("https://example.test/v3/");
		assertEquals("https://example.test/v3", adyen.endpointFor(store));
	}

	// --- merchant account --------------------------------------------------------------------------------

	@Test
	public void listsTheMerchantAccountsTheKeyCanSeeInIdOrder()
	{
		when(store.getAdyenManagementApiKey()).thenReturn("key");
		adyen.answer("GET /merchants?pageSize=100", Map.of("data", List.of(
				Map.of("id", "REPLYAccount_B", "name", "B", "status", "Active"),
				Map.of("id", "REPLYAccount_A", "status", "Active"))));

		final MerchantAccounts accounts = service.merchantAccounts(store);

		assertNull(accounts.failure());
		assertEquals(List.of("REPLYAccount_A", "REPLYAccount_B"), accounts.accounts().stream().map(a -> a.id()).toList());
		assertEquals("REPLYAccount_A", accounts.accounts().get(0).name());
	}

	@Test
	public void cannotListMerchantAccountsWithoutAKey()
	{
		assertEquals("no-key", service.merchantAccounts(store).failure());
		assertTrue(adyen.calls.isEmpty());
	}

	@Test
	public void storesOnlyAMerchantAccountTheKeyCanSee()
	{
		when(store.getAdyenManagementApiKey()).thenReturn("key");
		adyen.answer("GET /merchants?pageSize=100", Map.of("data", List.of(Map.of("id", "REPLYAccount_A"))));

		assertFalse(service.saveMerchantAccount(store, "SomeoneElse"));
		verify(store, never()).setAdyenMerchantAccount(anyString());

		assertTrue(service.saveMerchantAccount(store, "REPLYAccount_A"));
		verify(store).setAdyenMerchantAccount("REPLYAccount_A");
	}

	/** The id becomes part of a request path, so anything that is not an id never reaches Adyen. */
	@Test
	public void aMerchantAccountThatIsNotAnIdIsRefusedWithoutAskingAdyen()
	{
		when(store.getAdyenManagementApiKey()).thenReturn("key");
		assertFalse(service.saveMerchantAccount(store, "../apiCredentials"));
		assertTrue(adyen.calls.isEmpty());
	}

	// --- provisioning ------------------------------------------------------------------------------------

	private SetupRequests.Provision request(final String origin, final String siteUid, final String notificationBase)
	{
		return new SetupRequests.Provision("electronics", origin, siteUid, notificationBase);
	}

	private void readyToProvision()
	{
		when(store.getAdyenManagementApiKey()).thenReturn("key");
		when(store.getAdyenMerchantAccount()).thenReturn("REPLYAccount_A");
	}

	@Test
	public void refusesToStartWithoutAKeyOrMerchantAccount()
	{
		assertTrue(service.provisionProblem(store, request("https://shop.example.com", "electronics", null)).isPresent());
		when(store.getAdyenManagementApiKey()).thenReturn("key");
		assertTrue(service.provisionProblem(store, request("https://shop.example.com", "electronics", null))
				.orElseThrow().contains("merchant account"));
	}

	@Test
	public void refusesAnOriginThatIsNotSchemeAndHost()
	{
		readyToProvision();
		assertTrue(service.provisionProblem(store, request("http://shop.example.com", "electronics", null)).isPresent());
		assertTrue(service.provisionProblem(store, request("https://shop.example.com/checkout", "electronics", null)).isPresent());
		assertTrue(service.provisionProblem(store, request("https://shop.example.com:9002/", "electronics", null)).isEmpty());
	}

	@Test
	public void refusesASiteThatIsNotTheStores()
	{
		readyToProvision();
		assertTrue(service.provisionProblem(store, request("https://shop.example.com", "apparel-uk", null))
				.orElseThrow().contains("sites"));
	}

	@Test
	public void createsTheCredentialWebhookAndHmacKeySavingEach()
	{
		readyToProvision();
		adyen.answer("POST /merchants/REPLYAccount_A/apiCredentials",
				Map.of("apiKey", "checkout-key", "clientKey", "test_CLIENT", "username", "ws_1@Company.REPLYAccount"));
		adyen.answer("POST /merchants/REPLYAccount_A/webhooks", Map.of("id", "WBHK1"));
		adyen.answer("POST /merchants/REPLYAccount_A/webhooks/WBHK1/generateHmac", Map.of("hmacKey", "HMAC"));

		final ProvisionReport report = service.provision(store, request("https://shop.example.com/", "electronics", null));

		assertTrue(report.complete());
		assertEquals(List.of("credential", "allowedOrigin", "webhook", "hmac"),
				report.steps().stream().map(s -> s.name()).toList());
		verify(store).setAdyenAPIKey("checkout-key");
		verify(store).setAdyenClientKey("test_CLIENT");
		verify(store).setAdyenNotificationUsername("sapcc-electronics");
		verify(store).setAdyenNotificationPassword("generated-password");
		verify(store).setAdyenNotificationHMACKey("HMAC");
	}

	/** The notification endpoint lives under its own webapp context; a URL without it is a 404. */
	@Test
	public void pointsTheWebhookAtTheNotificationWebappForTheChosenSite()
	{
		readyToProvision();
		adyen.answer("POST /merchants/REPLYAccount_A/apiCredentials", Map.of("apiKey", "k", "username", "u"));
		adyen.answer("POST /merchants/REPLYAccount_A/webhooks", Map.of("id", "WBHK1"));
		adyen.answer("POST /merchants/REPLYAccount_A/webhooks/WBHK1/generateHmac", Map.of("hmacKey", "H"));

		service.provision(store, request("https://shop.example.com", "electronics", "https://hooks.example.com"));

		final Map<String, Object> webhook = asJson(adyen.bodies.get(1));
		assertEquals("https://hooks.example.com/adyenv6notificationv2/adyen/v6/notification/electronics/json",
				webhook.get("url"));
		@SuppressWarnings("unchecked")
		final Map<String, Object> credential = (Map<String, Object>) adyen.bodies.get(0);
		assertEquals(List.of("https://shop.example.com"), credential.get("allowedOrigins"));
	}

	@Test
	public void aWebhookWithoutAnIdReportsTheHmacStepAsFailedRatherThanOmittingIt()
	{
		readyToProvision();
		adyen.answer("POST /merchants/REPLYAccount_A/apiCredentials", Map.of("apiKey", "k", "username", "u"));
		adyen.answer("POST /merchants/REPLYAccount_A/webhooks", Map.of());

		final ProvisionReport report = service.provision(store, request("https://shop.example.com", "electronics", null));

		assertFalse(report.complete());
		final var last = report.steps().get(report.steps().size() - 1);
		assertEquals("hmac", last.name());
		assertFalse(last.done());
	}

	@Test
	public void stopsAtTheCredentialWhenAdyenRefusesIt()
	{
		readyToProvision();
		adyen.refuse("POST /merchants/REPLYAccount_A/apiCredentials", Failure.FORBIDDEN, 403);

		final ProvisionReport report = service.provision(store, request("https://shop.example.com", "electronics", null));

		assertFalse(report.complete());
		assertEquals(1, report.steps().size());
		assertTrue(report.steps().get(0).detail().contains("ws@Company"));
		assertEquals(1, adyen.calls.size());
		verify(store, never()).setAdyenAPIKey(anyString());
	}

	/** What Adyen receives, serialised by the same Jackson RestTemplate uses. */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> asJson(final Object body)
	{
		try
		{
			final ObjectMapper json = new ObjectMapper();
			return json.readValue(json.writeValueAsString(body), Map.class);
		}
		catch (final Exception e)
		{
			throw new AssertionError(e);
		}
	}

	/** Every field Adyen's CreateMerchantWebhookRequest needs, under the names it defines. */
	@Test
	public void theWebhookRequestCarriesEveryFieldAdyenExpects()
	{
		final Map<String, Object> wire = asJson(new DefaultCockpitSetupService.WebhookRequest(
				"https://shop.example.com/hook", "sapcc-electronics", "s3cret", "SAP Commerce - electronics"));

		assertEquals("standard", wire.get("type"));
		assertEquals("json", wire.get("communicationFormat"));
		assertEquals(Boolean.TRUE, wire.get("active"));
		assertEquals("https://shop.example.com/hook", wire.get("url"));
		assertEquals("sapcc-electronics", wire.get("username"));
		assertEquals("s3cret", wire.get("password"));
		assertEquals("SAP Commerce - electronics", wire.get("description"));
		assertEquals(7, wire.size());
	}

	/** RestTemplate logs a request body at DEBUG through toString(). */
	@Test
	public void theWebhookPasswordNeverAppearsInTheRequestsTextForm()
	{
		final String text = new DefaultCockpitSetupService.WebhookRequest(
				"https://shop.example.com/hook", "sapcc-electronics", "s3cret", "d").toString();
		assertFalse(text.contains("s3cret"));
	}

	/** A second run started while the first is still talking to Adyen is refused, not interleaved. */
	@Test
	public void refusesASecondRunForAStoreWhileTheFirstIsInProgress()
	{
		readyToProvision();
		final SetupRequests.Provision request = request("https://shop.example.com", "electronics", null);
		final ManagementApiClient reentrant = new FakeAdyen()
		{
			@Override
			protected Response exchange(final String url, final HttpMethod method, final Object body, final String apiKey)
			{
				// Adyen is slow; meanwhile a second run for the same store arrives.
				assertThrows(ProvisioningInProgressException.class, () -> service.provision(store, request));
				return Response.failed(Failure.REJECTED, 500);
			}
		};
		reentrant.setConfigurationService(configurationService);
		service.setManagementApiClient(reentrant);

		service.provision(store, request);

		// Once the first run is over, the store is free again.
		service.setManagementApiClient(adyen);
		service.provision(store, request);
	}

	@Test
	public void stripsEveryTrailingSlashFromAnOrigin()
	{
		assertEquals("https://shop.example.com", DefaultCockpitSetupService.normaliseOrigin("https://shop.example.com//"));
		assertNull(DefaultCockpitSetupService.normaliseOrigin("https://shop.example.com/a/"));
	}

	@Test
	public void theNotificationPathDefaultsToTheNotificationWebapp()
	{
		assertEquals("/adyenv6notificationv2/adyen/v6/notification/electronics/json",
				service.notificationPath("electronics"));
	}
}
