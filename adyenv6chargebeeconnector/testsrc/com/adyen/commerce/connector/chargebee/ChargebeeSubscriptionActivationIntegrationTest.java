/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2026 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.commerce.connector.chargebee;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.adyen.commerce.connector.chargebee.model.ChargebeePlanMappingModel;
import com.adyen.commerce.connector.dto.BillingCustomerRef;
import com.adyen.commerce.connector.dto.BillingPaymentMethodRef;
import com.adyen.commerce.connector.dto.BillingSubscriptionRef;
import com.adyen.commerce.connector.dto.CancelReason;
import com.adyen.commerce.connector.dto.CancellationTiming;
import com.adyen.commerce.connector.dto.PlanRef;
import com.adyen.commerce.connector.dto.SubscriptionCancelRequest;
import com.adyen.commerce.connector.dto.SubscriptionCreateRequest;
import com.adyen.commerce.connector.dto.SubscriptionUpdateRequest;
import com.adyen.commerce.connector.enums.BillingPlatform;
import com.adyen.commerce.connector.model.BillingSubscriptionRefModel;
import com.adyen.commerce.connector.service.SubscriptionBillingService;
import com.adyen.commerce.connector.spi.SubscriptionBillingConnector;

import com.adyen.v6.model.ChargebeeConfigModel;

import de.hybris.bootstrap.annotations.IntegrationTest;
import de.hybris.platform.basecommerce.model.site.BaseSiteModel;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.ServicelayerTransactionalTest;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.store.BaseStoreModel;

import jakarta.annotation.Resource;

/**
 * End-to-end tests of {@code activateSubscription}/{@code updateSubscription}/{@code cancelSubscription}
 * against the live Chargebee sandbox over HTTPS, driving the real core orchestration rather than mocks:
 * {@link SubscriptionBillingService#activateSubscription} resolves the connector from
 * {@code BaseStore.activeBillingPlatform}, validates the merchant-account precondition, builds the
 * {@code AdyenTokenHandle} from the order and persists the refs on real SAP models.
 *
 * <p>{@code ant integrationtests} runs against the isolated "junit" tenant, so the fixture builds its own
 * Customer/BaseStore/PaymentInfo/Order. The customer's {@code customerID} and the token in
 * {@code adyenSelectedReference} must be real whichever tenant is calling: Chargebee validates the imported
 * token against Adyen using {@code reference_id = shopperReference/recurringDetailReference}. The fixture
 * also activates a {@code BaseSite} carrying the store, because the connector reads its credentials from
 * the current base store, and the whole class skips when the sandbox credentials are absent from local
 * configuration (see {@link #createChargebeeConfig()}).</p>
 *
 * <p>{@link ServicelayerTransactionalTest} rolls back the SAP persistence but not the Chargebee side
 * effects: token import is idempotent on a replayed {@code reference_id}, while
 * {@code createSubscription} uses a per-run order code as its idempotency key, so repeated runs accumulate
 * test subscriptions in the sandbox.</p>
 */
@IntegrationTest
public class ChargebeeSubscriptionActivationIntegrationTest extends ServicelayerTransactionalTest
{
	// The Adyen shopperReference (Customer.customerID) and storedPaymentMethodId this RECURRING token is
	// registered under with Adyen. Adyen's validation on import checks this exact pair, not anything
	// hybris-tenant-local.
	private static final String REAL_SHOPPER_REFERENCE = "26077f0d-7210-425c-9cc3-cdc83fca8e9e";
	private static final String REAL_ADYEN_TOKEN = "H55RW4QG9F9SKTV5";
	private static final String REAL_ADYEN_MERCHANT_ACCOUNT = "REPLYAccount_AlphaDev_TEST";
	private static final String TEST_ITEM_PRICE_ID = "test-subscription-plan-EUR-Monthly";

	@Resource(name = "subscriptionBillingService")
	private SubscriptionBillingService subscriptionBillingService;

	@Resource(name = "chargebeeSubscriptionBillingConnector")
	private SubscriptionBillingConnector chargebeeConnector;

	@Resource
	private ModelService modelService;

	@Resource
	private BaseSiteService baseSiteService;

	@Resource
	private ConfigurationService configurationService;

	private OrderModel order;
	private ProductModel subProduct;

	@Before
	public void setUp()
	{
		final long unique = System.nanoTime();

		final CurrencyModel currency = modelService.create(CurrencyModel.class);
		currency.setIsocode("EUR");
		currency.setSymbol("€");
		currency.setActive(Boolean.TRUE);
		modelService.save(currency);

		final CustomerModel customer = modelService.create(CustomerModel.class);
		// uid doubles as the email sent to Chargebee's create-customer call (buildCustomerSyncRequest).
		// Fixed rather than per-run unique: ensureCustomer's idempotency key is the customerId, which is
		// itself fixed for Adyen validation, so a varying email collides with Chargebee's idempotency lock.
		// A matching Chargebee customer already exists for this id, so ensureCustomer's GET-first check
		// finds it and never re-POSTs.
		customer.setUid("chargebee-p25-integration-test@example.com");
		customer.setCustomerID(REAL_SHOPPER_REFERENCE);
		modelService.save(customer);

		final BaseStoreModel store = modelService.create(BaseStoreModel.class);
		store.setUid("test-store-" + unique);
		store.setAdyenMerchantAccount(REAL_ADYEN_MERCHANT_ACCOUNT);
		store.setActiveBillingPlatform(BillingPlatform.CHARGEBEE);
		store.setChargebeeConfig(createChargebeeConfig());
		modelService.save(store);

		// DefaultChargebeeConfigService reads the configuration off baseStoreService.getCurrentBaseStore(),
		// and DefaultBaseStoreSelectorStrategy resolves that from the current base site's stores. Without an
		// active site every connector call fails with "No current base store".
		final BaseSiteModel site = modelService.create(BaseSiteModel.class);
		site.setUid("test-site-" + unique);
		site.setStores(List.of(store));
		modelService.save(site);
		baseSiteService.setCurrentBaseSite(site, false);

		final PaymentInfoModel paymentInfo = modelService.create(PaymentInfoModel.class);
		paymentInfo.setCode("test-payment-" + unique);
		paymentInfo.setUser(customer);
		paymentInfo.setAdyenSelectedReference(REAL_ADYEN_TOKEN);
		modelService.save(paymentInfo);

		order = modelService.create(OrderModel.class);
		order.setCode("test-order-" + unique);
		order.setDate(new Date());
		order.setCurrency(currency);
		order.setUser(customer);
		order.setStore(store);
		order.setPaymentInfo(paymentInfo);
		modelService.save(order);

		// subProduct is never persisted: activateSubscription only reads its code, for plan resolution and
		// metadata, and never stores it as a model reference.
		subProduct = modelService.create(ProductModel.class);
		subProduct.setCode("test-subscription-product-" + unique);

		final ChargebeePlanMappingModel planMapping = modelService.create(ChargebeePlanMappingModel.class);
		planMapping.setProductCode(subProduct.getCode());
		planMapping.setItemPriceId(TEST_ITEM_PRICE_ID);
		modelService.save(planMapping);
	}

	/**
	 * ServicelayerTransactionalTest rolls back persistence but not the session, so the base site activated
	 * in {@link #setUp()} would otherwise stay current for every later test in the same JVM.
	 */
	@After
	public void clearCurrentBaseSite()
	{
		baseSiteService.setCurrentBaseSite((BaseSiteModel) null, false);
	}

	/**
	 * Reads the sandbox credentials from the local configuration under {@code chargebee.*}, since they must
	 * not be committed; the test skips rather than fails when they are absent, because a missing sandbox key
	 * is a "not set up here" condition and not a defect in the code under test.
	 *
	 * <p>{@code adyenGatewayMerchantAccount} deliberately mirrors the store's own merchant account: the
	 * precondition compares the two, and this fixture asserts the happy path through it.</p>
	 */
	protected ChargebeeConfigModel createChargebeeConfig()
	{
		final Configuration configuration = configurationService.getConfiguration();
		final String site = StringUtils.trimToNull(configuration.getString("chargebee.site", null));
		final String apiKey = StringUtils.trimToNull(configuration.getString("chargebee.apiKey", null));
		final String gatewayAccountId =
				StringUtils.trimToNull(configuration.getString("chargebee.gatewayAccountId", null));

		// All three, not just the credentials: a blank gateway account id reaches Chargebee and comes back
		// as an opaque 400.
		assumeTrue("Chargebee sandbox is not configured locally (chargebee.site / chargebee.apiKey / "
				+ "chargebee.gatewayAccountId) — this test talks to the live sandbox",
				site != null && apiKey != null && gatewayAccountId != null);

		final ChargebeeConfigModel config = modelService.create(ChargebeeConfigModel.class);
		config.setSubscriptionSiteId(site);
		config.setSubscriptionApiKey(apiKey);
		config.setSubscriptionGatewayAccountId(gatewayAccountId);
		config.setAdyenGatewayMerchantAccount(REAL_ADYEN_MERCHANT_ACCOUNT);
		// Mandatory on ChargebeeConfig and unused by this test: nothing here receives a webhook.
		config.setChargebeeWebhookUsername("integration-test");
		config.setChargebeeWebhookPassword("integration-test");
		modelService.save(config);

		return config;
	}

	@Test
	@Ignore("This scenario was verified live on 2026-07-22 (passed). Re-running is blocked on a live-data expiry, not a "
			+ "code defect: activateSubscription's first step imports the Adyen token into Chargebee, which "
			+ "Chargebee re-validates against Adyen on every run. Adyen's TEST platform has since pruned the stored "
			+ "payment method behind the fixed shopperReference/storedPaymentMethodId pair, so the import now returns "
			+ "400 [invalid_request] Invalid Reference ID. Re-enable after a fresh storefront checkout produces a new "
			+ "RECURRING token, then update REAL_SHOPPER_REFERENCE/REAL_ADYEN_TOKEN. Update+cancel don't touch "
			+ "Adyen and are covered live by updatesAndCancelsSubscriptionAgainstLiveChargebeeSandbox.")
	public void activatesSubscriptionAgainstLiveChargebeeSandboxAndIsIdempotentOnReplay() throws Exception
	{
		final BillingSubscriptionRefModel first = subscriptionBillingService.activateSubscription(order, subProduct);

		assertNotNull(first);
		assertEquals(BillingPlatform.CHARGEBEE, first.getPlatform());
		assertNotNull("Chargebee should have returned a real subscription id", first.getExternalSubscriptionId());
		assertNotNull("Chargebee should have returned a real customer id", first.getExternalCustomerId());
		assertNotNull("Chargebee should have returned a real payment source id", first.getExternalPaymentMethodId());
		assertEquals(order.getCode(), first.getIdempotencyKey());
		assertEquals("ACTIVE", first.getStatus());

		// activateSubscription persists the refs via the inverse (BillingCustomerRef.customer) side of the
		// relation, and the in-memory collections loaded earlier do not auto-refresh.
		final CustomerModel customer = (CustomerModel) order.getUser();
		modelService.refresh(customer);
		final PaymentInfoModel paymentInfo = order.getPaymentInfo();
		modelService.refresh(paymentInfo);

		assertTrue("Expected a persisted BillingCustomerRef for CHARGEBEE",
				customer.getBillingCustomerRefs().stream().anyMatch(ref -> ref.getPlatform() == BillingPlatform.CHARGEBEE));
		assertTrue("Expected a persisted BillingPaymentMethodRef for CHARGEBEE",
				paymentInfo.getBillingPaymentMethodRefs().stream()
						.anyMatch(ref -> ref.getPlatform() == BillingPlatform.CHARGEBEE));

		// Idempotency: re-activating the same order must return the same ref, not create a second
		// Chargebee subscription.
		final BillingSubscriptionRefModel second = subscriptionBillingService.activateSubscription(order, subProduct);
		assertEquals(first.getPk(), second.getPk());
		assertEquals(first.getExternalSubscriptionId(), second.getExternalSubscriptionId());
	}

	/**
	 * Exercises {@code updateSubscription} and {@code cancelSubscription} against the live Chargebee sandbox
	 * through the real {@link SubscriptionBillingConnector} bean and its
	 * {@link com.adyen.commerce.connector.chargebee.client.impl.DefaultChargebeeApiClient}.
	 *
	 * <p>It deliberately does not go through {@code activateSubscription}, whose first step imports the Adyen
	 * token ({@code create_using_permanent_token}) and which Chargebee validates live against Adyen: Adyen's
	 * TEST platform prunes stored payment methods over time, so a fixed
	 * {@code shopperReference/storedPaymentMethodId} pair eventually returns
	 * {@code 400 [invalid_request] Invalid Reference ID}. Update and cancel only mutate an existing
	 * Chargebee subscription, so this creates one directly on the sandbox customer for
	 * {@link #REAL_SHOPPER_REFERENCE}, which already carries a primary payment source that
	 * {@code subscription_for_items} uses automatically.</p>
	 *
	 * <p>Every call goes to Chargebee over HTTPS and {@code requireSuccess} throws a
	 * {@code BillingException} on any non-2xx, so reaching the end without an exception is the verification
	 * that create, update and cancel were accepted. A per-run unique idempotency key keeps the test
	 * re-runnable, cancel being terminal.</p>
	 */
	@Test
	public void updatesAndCancelsSubscriptionAgainstLiveChargebeeSandbox() throws Exception
	{
		final String idem = "p23-update-cancel-" + System.nanoTime();

		// The Chargebee customer for REAL_SHOPPER_REFERENCE already has a primary payment source, which
		// createSubscription uses automatically, so no Adyen token import is needed. paymentMethod is
		// required by the DTO but not sent: subscription_for_items references the customer, not a source id.
		final BillingCustomerRef customerRef = new BillingCustomerRef(BillingPlatform.CHARGEBEE, REAL_SHOPPER_REFERENCE);
		final BillingPaymentMethodRef paymentMethodRef = new BillingPaymentMethodRef(BillingPlatform.CHARGEBEE, "primary");
		final PlanRef plan = new PlanRef(TEST_ITEM_PRICE_ID, null);

		final BillingSubscriptionRef created = chargebeeConnector.createSubscription(new SubscriptionCreateRequest(
				customerRef, paymentMethodRef, plan, 1, null, "EUR", null, null, Map.of(), idem));
		assertNotNull("Chargebee should have returned a real subscription id", created.externalId());

		// Update via item price (quantity=null): TEST_ITEM_PRICE_ID is an "on_off" addon type, which carries
		// no quantity — Chargebee answers "subscription_items[quantity][0]: This param should not be sent
		// for on_off addon type" — so the item price is the lever that exercises update_for_items here.
		// Quantity-bearing updates are unit-covered in ChargebeeSubscriptionBillingConnectorTest.
		chargebeeConnector.updateSubscription(new SubscriptionUpdateRequest(created, plan, null, null, Map.of(), idem));

		// Cancel immediately (atPeriodEnd=false -> cancel_option=immediately).
		chargebeeConnector.cancelSubscription(
				new SubscriptionCancelRequest(created, CancelReason.REQUESTED_BY_CUSTOMER,
						CancellationTiming.IMMEDIATELY, idem));
	}
}
