package com.adyen.cockpit.servlet;

import static com.adyen.cockpit.servlet.ServletTestSupport.call;
import static com.adyen.cockpit.servlet.ServletTestSupport.get;
import static com.adyen.cockpit.servlet.ServletTestSupport.json;
import static com.adyen.cockpit.servlet.ServletTestSupport.post;
import static com.adyen.cockpit.servlet.ServletTestSupport.signIn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.adyen.cockpit.api.routes.BootstrapRoute;
import com.adyen.cockpit.setup.CockpitSetupService;
import com.adyen.cockpit.setup.ProvisioningInProgressException;
import com.adyen.cockpit.setup.dto.CredentialCheck;
import com.adyen.cockpit.setup.dto.MerchantAccountView;
import com.adyen.cockpit.setup.dto.MerchantAccounts;
import com.adyen.cockpit.setup.dto.ProvisionReport;
import com.adyen.cockpit.setup.dto.ProvisionStep;
import com.adyen.cockpit.setup.dto.SetupRequests;
import com.adyen.cockpit.setup.dto.StoreSetupView;
import com.adyen.cockpit.setup.routes.AbstractSetupRoute;
import com.adyen.cockpit.setup.routes.SetupManagementKeyRoute;
import com.adyen.cockpit.setup.routes.SetupMerchantAccountRoute;
import com.adyen.cockpit.setup.routes.SetupMerchantAccountsRoute;
import com.adyen.cockpit.setup.routes.SetupProvisionRoute;
import com.adyen.cockpit.setup.routes.SetupStoresRoute;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;

/**
 * The six cockpit routes through the servlet: the status codes and success bodies are the ones the servlet
 * answered before the route registry existed, and the refusals use the error envelope.
 */
@UnitTest
public class SetupRoutesServletTest
{
	private static final String ADMIN = "ROLE_ADMINGROUP";

	private CockpitSetupService setup;
	private BaseStoreService stores;
	private UserService users;
	private BaseStoreModel store;
	private AdyenCockpitApiServlet servlet;

	@Before
	public void setUp()
	{
		setup = mock(CockpitSetupService.class);
		stores = mock(BaseStoreService.class);
		users = mock(UserService.class);
		store = mock(BaseStoreModel.class);
		when(store.getUid()).thenReturn("us");
		when(stores.getBaseStoreForUid("us")).thenReturn(store);
		when(stores.getBaseStoreForUid("nowhere")).thenThrow(new IllegalArgumentException("no such store"));

		final BootstrapRoute bootstrap = new BootstrapRoute();
		bootstrap.setUserService(users);
		bootstrap.setBaseStoreService(stores);
		servlet = ServletTestSupport.servlet(List.of(bootstrap, wire(new SetupStoresRoute()),
				wire(new SetupMerchantAccountsRoute()), wire(new SetupManagementKeyRoute()),
				wire(new SetupMerchantAccountRoute()), wire(new SetupProvisionRoute())));
	}

	private <T extends AbstractSetupRoute> T wire(final T route)
	{
		route.setSetupService(setup);
		route.setBaseStoreService(stores);
		return route;
	}

	@After
	public void clearSecurity()
	{
		SecurityContextHolder.clearContext();
	}

	private static StoreSetupView view()
	{
		return new StoreSetupView("us", "US store", true, "AcmeECOM", true, false, false, false, false, List.of());
	}

	private static void assertRefusal(final MockHttpServletResponse response, final int status, final String code,
			final String message) throws IOException
	{
		assertEquals(status, response.getStatus());
		assertEquals(code, json(response).get("code").asText());
		assertEquals(message, json(response).get("message").asText());
		assertFalse(json(response).has("error"));
	}

	// ---- GET /bootstrap

	@Test
	public void bootstrapAnswersUserAdminAndStores() throws IOException
	{
		signIn(ADMIN);
		final UserModel user = mock(UserModel.class);
		when(user.getUid()).thenReturn("admin");
		when(users.getCurrentUser()).thenReturn(user);
		final BaseStoreModel configured = mock(BaseStoreModel.class);
		when(configured.getUid()).thenReturn("us");
		when(configured.getAdyenManagementApiKey()).thenReturn("key");
		final BaseStoreModel bare = mock(BaseStoreModel.class);
		when(bare.getUid()).thenReturn("eu");
		when(bare.getAdyenManagementApiKey()).thenReturn(" ");
		when(stores.getAllBaseStores()).thenReturn(List.of(configured, bare));

		final MockHttpServletResponse response = call(servlet, get("/bootstrap"));

		assertEquals(200, response.getStatus());
		assertEquals(json("{\"user\":\"admin\",\"admin\":true,\"stores\":[{\"uid\":\"us\",\"configured\":true},"
				+ "{\"uid\":\"eu\",\"configured\":false}]}"), json(response));
	}

	@Test
	public void bootstrapIsOpenToANonAdministratorWhoIsToldSo() throws IOException
	{
		signIn("ROLE_BACKOFFICEADMINGROUP");
		final UserModel user = mock(UserModel.class);
		when(user.getUid()).thenReturn("support");
		when(users.getCurrentUser()).thenReturn(user);
		when(stores.getAllBaseStores()).thenReturn(List.of());

		final MockHttpServletResponse response = call(servlet, get("/bootstrap"));

		assertEquals(200, response.getStatus());
		assertEquals(json("{\"user\":\"support\",\"admin\":false,\"stores\":[]}"), json(response));
	}

	@Test
	public void bootstrapRefusesAnonymous() throws IOException
	{
		assertRefusal(call(servlet, get("/bootstrap")), 403, "PERMISSION_DENIED",
				"You do not have permission to do this.");
	}

	// ---- GET /setup/stores

	@Test
	public void storesAnswersNotificationPathAndStores() throws IOException
	{
		signIn(ADMIN);
		when(setup.notificationPath("{site}")).thenReturn("/adyen/v6/notification/{site}/json");
		when(setup.stores()).thenReturn(List.of(view()));

		final MockHttpServletResponse response = call(servlet, get("/setup/stores"));

		assertEquals(200, response.getStatus());
		assertEquals(json("{\"notificationPath\":\"/adyen/v6/notification/{site}/json\",\"stores\":[{\"uid\":\"us\","
				+ "\"name\":\"US store\",\"testMode\":true,\"merchantAccount\":\"AcmeECOM\",\"hasManagementKey\":true,"
				+ "\"hasCheckoutKey\":false,\"hasClientKey\":false,\"hasWebhookCredentials\":false,\"hasHmacKey\":false,"
				+ "\"sites\":[]}]}"), json(response));
	}

	@Test
	public void storesRefusesANonAdministrator() throws IOException
	{
		signIn("ROLE_CUSTOMERMANAGERGROUP");
		assertRefusal(call(servlet, get("/setup/stores")), 403, "PERMISSION_DENIED",
				"Connecting a store to Adyen requires a Backoffice administrator.");
		verify(setup, never()).stores();
	}

	// ---- GET /setup/merchant-accounts

	@Test
	public void merchantAccountsAnswersTheAccountsOfTheStore() throws IOException
	{
		signIn(ADMIN);
		when(setup.merchantAccounts(store)).thenReturn(
				new MerchantAccounts(List.of(new MerchantAccountView("AcmeECOM", "Acme shop", "active")), null));

		final MockHttpServletRequestHolder holder = new MockHttpServletRequestHolder("/setup/merchant-accounts", "us");
		final MockHttpServletResponse response = call(servlet, holder.request);

		assertEquals(200, response.getStatus());
		assertEquals(json("{\"accounts\":[{\"id\":\"AcmeECOM\",\"name\":\"Acme shop\",\"status\":\"active\"}],\"failure\":null}"), json(response));
	}

	@Test
	public void merchantAccountsOfAnUnknownStoreAnswers404UnknownStoreNamingTheField() throws IOException
	{
		signIn(ADMIN);
		final MockHttpServletResponse response = call(servlet,
				new MockHttpServletRequestHolder("/setup/merchant-accounts", "nowhere").request);
		assertRefusal(response, 404, "UNKNOWN_STORE", "Unknown base store.");
		assertEquals("store", json(response).get("field").asText());
	}

	@Test
	public void merchantAccountsWithoutAStoreParameterIsAnUnknownStore() throws IOException
	{
		signIn(ADMIN);
		assertRefusal(call(servlet, get("/setup/merchant-accounts")), 404, "UNKNOWN_STORE", "Unknown base store.");
	}

	@Test
	public void merchantAccountsRefusesANonAdministrator() throws IOException
	{
		signIn("ROLE_OTHER");
		assertRefusal(call(servlet, new MockHttpServletRequestHolder("/setup/merchant-accounts", "us").request), 403,
				"PERMISSION_DENIED", "Connecting a store to Adyen requires a Backoffice administrator.");
	}

	// ---- POST /setup/management-key

	@Test
	public void managementKeyUsableAnswers200WithTheCheck() throws IOException
	{
		signIn(ADMIN);
		final CredentialCheck check = new CredentialCheck(true, true, "ws@Company.Acme", "Acme", List.of(), null);
		when(setup.saveManagementKey(store, "k")).thenReturn(check);

		final MockHttpServletResponse response = call(servlet,
				post("/setup/management-key", "{\"store\":\"us\",\"apiKey\":\"k\"}"));

		assertEquals(200, response.getStatus());
		assertEquals(json("{\"usable\":true,\"active\":true,\"username\":\"ws@Company.Acme\",\"companyName\":\"Acme\","
				+ "\"missingRoles\":[],\"rejection\":null}"), json(response));
	}

	@Test
	public void managementKeyNotUsableAnswers422WithTheCheckNotAnEnvelope() throws IOException
	{
		signIn(ADMIN);
		when(setup.saveManagementKey(store, "k")).thenReturn(
				new CredentialCheck(false, false, null, null, List.of(), "unrecognised"));

		final MockHttpServletResponse response = call(servlet,
				post("/setup/management-key", "{\"store\":\"us\",\"apiKey\":\"k\"}"));

		assertEquals(422, response.getStatus());
		assertEquals("unrecognised", json(response).get("rejection").asText());
		assertFalse(json(response).has("code"));
	}

	@Test
	public void managementKeyForAnUnknownStoreOrBlankKeyAnswers400() throws IOException
	{
		signIn(ADMIN);
		assertRefusal(call(servlet, post("/setup/management-key", "{\"store\":\"nowhere\",\"apiKey\":\"k\"}")), 400,
				"INVALID_REQUEST", "Name a base store and paste a key.");
		assertRefusal(call(servlet, post("/setup/management-key", "{\"store\":\"us\",\"apiKey\":\" \"}")), 400,
				"INVALID_REQUEST", "Name a base store and paste a key.");
		assertRefusal(call(servlet, post("/setup/management-key", "not json")), 400, "INVALID_REQUEST",
				"Name a base store and paste a key.");
		verify(setup, never()).saveManagementKey(any(), anyString());
	}

	@Test
	public void managementKeyRefusesANonAdministratorBeforeReadingTheBody() throws IOException
	{
		signIn("ROLE_OTHER");
		assertRefusal(call(servlet, post("/setup/management-key", "{\"store\":\"us\",\"apiKey\":\"k\"}")), 403,
				"PERMISSION_DENIED", "Connecting a store to Adyen requires a Backoffice administrator.");
		verify(setup, never()).saveManagementKey(any(), anyString());
	}

	@Test
	public void managementKeyRefusesAnUntrustedWrite() throws IOException
	{
		signIn(ADMIN);
		final org.springframework.mock.web.MockHttpServletRequest request = post("/setup/management-key", "{}");
		request.setContentType("text/plain");
		assertRefusal(call(servlet, request), 415, "UNTRUSTED_WRITE", "Send the request as JSON from the cockpit.");
	}

	// ---- POST /setup/merchant-account

	@Test
	public void merchantAccountSavedAnswers200WithTheStoreView() throws IOException
	{
		signIn(ADMIN);
		when(setup.saveMerchantAccount(store, "AcmeECOM")).thenReturn(true);
		when(setup.describe(store)).thenReturn(view());

		final MockHttpServletResponse response = call(servlet,
				post("/setup/merchant-account", "{\"store\":\"us\",\"merchantAccount\":\"AcmeECOM\"}"));

		assertEquals(200, response.getStatus());
		assertEquals("AcmeECOM", json(response).get("merchantAccount").asText());
		assertEquals("us", json(response).get("uid").asText());
	}

	@Test
	public void merchantAccountTheKeyCannotSeeAnswers422RequestRejected() throws IOException
	{
		signIn(ADMIN);
		when(setup.saveMerchantAccount(store, "Other")).thenReturn(false);
		assertRefusal(call(servlet, post("/setup/merchant-account", "{\"store\":\"us\",\"merchantAccount\":\"Other\"}")),
				422, "REQUEST_REJECTED", "That merchant account is not one this store's key can see.");
	}

	@Test
	public void merchantAccountForAnUnknownStoreAnswers400() throws IOException
	{
		signIn(ADMIN);
		assertRefusal(call(servlet, post("/setup/merchant-account", "{\"store\":\"nowhere\",\"merchantAccount\":\"A\"}")),
				400, "INVALID_REQUEST", "Name a base store and a merchant account.");
	}

	@Test
	public void merchantAccountRefusesANonAdministrator() throws IOException
	{
		signIn("ROLE_OTHER");
		assertRefusal(call(servlet, post("/setup/merchant-account", "{\"store\":\"us\",\"merchantAccount\":\"A\"}")), 403,
				"PERMISSION_DENIED", "Connecting a store to Adyen requires a Backoffice administrator.");
		verify(setup, never()).saveMerchantAccount(any(), anyString());
	}

	// ---- POST /setup/provision

	private static final String PROVISION_BODY = "{\"store\":\"us\",\"storefrontOrigin\":\"https://shop.example\","
			+ "\"site\":\"apparel\",\"notificationBaseUrl\":null}";

	@Test
	public void provisionCompleteAnswers200WithReportAndStore() throws IOException
	{
		signIn(ADMIN);
		when(setup.provisionProblem(eq(store), any(SetupRequests.Provision.class))).thenReturn(Optional.empty());
		when(setup.provision(eq(store), any(SetupRequests.Provision.class))).thenReturn(
				new ProvisionReport(true, List.of(new ProvisionStep("credential", true, "created"))));
		when(setup.describe(store)).thenReturn(view());

		final MockHttpServletResponse response = call(servlet, post("/setup/provision", PROVISION_BODY));

		assertEquals(200, response.getStatus());
		assertEquals(json("{\"complete\":true,\"steps\":[{\"name\":\"credential\",\"done\":true,\"detail\":\"created\"}]}"),
				json(response).get("report"));
		assertEquals("us", json(response).get("store").get("uid").asText());
		assertEquals(List.of("report", "store"), ServletTestSupport.names(json(response)));
	}

	@Test
	public void provisionPartialAnswers422WithReportAndStoreNotAnEnvelope() throws IOException
	{
		signIn(ADMIN);
		when(setup.provisionProblem(eq(store), any(SetupRequests.Provision.class))).thenReturn(Optional.empty());
		when(setup.provision(eq(store), any(SetupRequests.Provision.class))).thenReturn(
				new ProvisionReport(false, List.of(new ProvisionStep("credential", false, "denied"))));
		when(setup.describe(store)).thenReturn(view());

		final MockHttpServletResponse response = call(servlet, post("/setup/provision", PROVISION_BODY));

		assertEquals(422, response.getStatus());
		assertFalse(json(response).get("report").get("complete").asBoolean());
		assertFalse(json(response).has("code"));
	}

	@Test
	public void provisionProblemAnswers422RequestRejectedWithTheProblemText() throws IOException
	{
		signIn(ADMIN);
		when(setup.provisionProblem(eq(store), any(SetupRequests.Provision.class)))
				.thenReturn(Optional.of("Save a merchant account first."));
		assertRefusal(call(servlet, post("/setup/provision", PROVISION_BODY)), 422, "REQUEST_REJECTED",
				"Save a merchant account first.");
		verify(setup, never()).provision(any(), any());
	}

	@Test
	public void provisionAlreadyRunningAnswers409OperationInProgress() throws IOException
	{
		signIn(ADMIN);
		when(setup.provisionProblem(eq(store), any(SetupRequests.Provision.class))).thenReturn(Optional.empty());
		when(setup.provision(eq(store), any(SetupRequests.Provision.class)))
				.thenThrow(new ProvisioningInProgressException("us"));
		assertRefusal(call(servlet, post("/setup/provision", PROVISION_BODY)), 409, "OPERATION_IN_PROGRESS",
				"Credentials are already being created for this store. Wait for that run to finish.");
	}

	@Test
	public void provisionForAnUnknownStoreAnswers400() throws IOException
	{
		signIn(ADMIN);
		assertRefusal(call(servlet, post("/setup/provision", "{\"store\":\"nowhere\"}")), 400, "INVALID_REQUEST",
				"Name a base store.");
	}

	@Test
	public void provisionRefusesANonAdministrator() throws IOException
	{
		signIn("ROLE_OTHER");
		assertRefusal(call(servlet, post("/setup/provision", PROVISION_BODY)), 403, "PERMISSION_DENIED",
				"Connecting a store to Adyen requires a Backoffice administrator.");
		verify(setup, never()).provision(any(), any());
	}

	@Test
	public void unexpectedFailureInASetupServiceAnswers500WithoutItsText() throws IOException
	{
		signIn(ADMIN);
		when(setup.stores()).thenThrow(new IllegalStateException("secret key leaked"));
		final MockHttpServletResponse response = call(servlet, get("/setup/stores"));
		assertRefusal(response, 500, "INTERNAL_ERROR", "The request could not be completed.");
		assertFalse(response.getContentAsString().contains("secret"));
	}

	/** A GET with the {@code store} query parameter. */
	private static final class MockHttpServletRequestHolder
	{
		private final org.springframework.mock.web.MockHttpServletRequest request;

		MockHttpServletRequestHolder(final String path, final String storeUid)
		{
			request = get(path);
			request.setParameter("store", storeUid);
		}
	}
}
