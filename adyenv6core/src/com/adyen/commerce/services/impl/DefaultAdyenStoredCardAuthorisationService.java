package com.adyen.commerce.services.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adyen.commerce.services.AdyenStoredCardAuthorisationService;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.model.AdyenStoredCardAuthorisationModel;

import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;

/**
 * Default implementation. Reads the same {@code additionalData} keys the order path reads, so a card
 * vaulted outside an order ends up described exactly as one vaulted by a purchase.
 */
public class DefaultAdyenStoredCardAuthorisationService implements AdyenStoredCardAuthorisationService
{
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAdyenStoredCardAuthorisationService.class);

    /** The modern key and the one older responses use; the plugin treats them as the same fact. */
    private static final String TOKEN_KEY = "tokenization.storedPaymentMethodId";
    private static final String LEGACY_TOKEN_KEY = "recurring.recurringDetailReference";
    private static final String NTID_KEY = "networkTxReference";

    private ModelService modelService;
    private FlexibleSearchService flexibleSearchService;

    @Override
    public void recordFrom(final CustomerModel customer, final String merchantAccount,
                           final PaymentResponse response)
    {
        if (customer == null || response == null)
        {
            return;
        }
        final Map<String, String> additionalData = response.getAdditionalData();
        if (additionalData == null)
        {
            LOG.info("Authorisation for shopper '{}' carried no additionalData at all; no network "
                    + "transaction id to keep. Check the Adyen Customer Area additional-data settings.",
                    customer.getCustomerID());
            return;
        }

        final String token = StringUtils.defaultIfBlank(additionalData.get(TOKEN_KEY),
                additionalData.get(LEGACY_TOKEN_KEY));
        final String networkTxReference = additionalData.get(NTID_KEY);
        if (StringUtils.isBlank(token) || StringUtils.isBlank(networkTxReference))
        {
            // Presence only, never the value. The pair is what matters: a token without a reference cannot
            // be imported into a platform that charges it as a merchant-initiated transaction.
            LOG.info("Authorisation for shopper '{}' produced token: {}, network transaction id: {}. Both "
                    + "are needed before a vaulted card can be offered to such a platform.",
                    customer.getCustomerID(), Boolean.valueOf(StringUtils.isNotBlank(token)),
                    Boolean.valueOf(StringUtils.isNotBlank(networkTxReference)));
            return;
        }

        try
        {
            final AdyenStoredCardAuthorisationModel stored = findOwn(customer, token)
                    .orElseGet(() -> modelService.create(AdyenStoredCardAuthorisationModel.class));
            stored.setCustomer(customer);
            stored.setStoredPaymentMethodId(token);
            stored.setNetworkTxReference(networkTxReference);
            stored.setMerchantAccount(merchantAccount);
            modelService.save(stored);
        }
        catch (final RuntimeException e)
        {
            // The card is vaulted at Adyen either way; failing to keep the reference must not fail the
            // shopper's action, it only means the card cannot be offered to an NTID-requiring platform.
            LOG.warn("Could not keep the network transaction id for shopper '{}'.", customer.getCustomerID(), e);
        }
    }

    @Override
    public Map<String, String> networkTxReferencesFor(final CustomerModel customer)
    {
        final Map<String, String> byToken = new HashMap<>();
        if (customer == null)
        {
            return byToken;
        }
        // Orders first, then what was captured outside one, so a card re-authorised here wins over a
        // reference an older order left behind.
        addAll(byToken, fromOrders(customer));
        addAll(byToken, fromVaultedAuthorisations(customer));
        return byToken;
    }

    protected void addAll(final Map<String, String> target, final Map<String, String> source)
    {
        target.putAll(source);
    }

    /** What orders already captured, on the PaymentInfo the checkout wrote. */
    protected Map<String, String> fromOrders(final CustomerModel customer)
    {
        final FlexibleSearchQuery query = new FlexibleSearchQuery(
                "SELECT {adyenSelectedReference}, {adyenNetworkTxReference} FROM {PaymentInfo} "
                        + "WHERE {user} = ?customer AND {adyenSelectedReference} IS NOT NULL "
                        + "AND {adyenNetworkTxReference} IS NOT NULL");
        query.addQueryParameter("customer", customer);
        query.setResultClassList(List.of(String.class, String.class));
        return pairs(query);
    }

    protected Map<String, String> fromVaultedAuthorisations(final CustomerModel customer)
    {
        final FlexibleSearchQuery query = new FlexibleSearchQuery(
                "SELECT {storedPaymentMethodId}, {networkTxReference} FROM {AdyenStoredCardAuthorisation} "
                        + "WHERE {customer} = ?customer");
        query.addQueryParameter("customer", customer);
        query.setResultClassList(List.of(String.class, String.class));
        return pairs(query);
    }

    protected Map<String, String> pairs(final FlexibleSearchQuery query)
    {
        final Map<String, String> byToken = new HashMap<>();
        try
        {
            for (final List<String> row : flexibleSearchService.<List<String>> search(query).getResult())
            {
                if (row != null && row.size() == 2
                        && StringUtils.isNotBlank(row.get(0)) && StringUtils.isNotBlank(row.get(1)))
                {
                    byToken.put(row.get(0), row.get(1));
                }
            }
        }
        catch (final RuntimeException e)
        {
            LOG.warn("Could not read stored network transaction ids; treating them as unknown.", e);
        }
        return byToken;
    }

    protected Optional<AdyenStoredCardAuthorisationModel> findOwn(final CustomerModel customer,
                                                                           final String storedPaymentMethodId)
    {
        final FlexibleSearchQuery query = new FlexibleSearchQuery(
                "SELECT {pk} FROM {AdyenStoredCardAuthorisation} WHERE {customer} = ?customer "
                        + "AND {storedPaymentMethodId} = ?token");
        query.addQueryParameter("customer", customer);
        query.addQueryParameter("token", storedPaymentMethodId);
        return flexibleSearchService.<AdyenStoredCardAuthorisationModel> search(query).getResult()
                .stream().findFirst();
    }

    public void setModelService(final ModelService modelService)
    {
        this.modelService = modelService;
    }

    public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
    {
        this.flexibleSearchService = flexibleSearchService;
    }
}
