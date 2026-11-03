package com.adyen.commerce.services.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adyen.model.checkout.PaymentResponse;
import com.adyen.v6.model.AdyenStoredCardAuthorisationModel;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;

@UnitTest
public class DefaultAdyenStoredCardAuthorisationServiceTest
{
    @Mock
    private ModelService modelService;
    @Mock
    private FlexibleSearchService flexibleSearchService;

    private DefaultAdyenStoredCardAuthorisationService service;
    private CustomerModel customer;
    /** Mocked, not constructed: a generated model needs an item context a unit test has no way to give. */
    private AdyenStoredCardAuthorisationModel row;

    @Before
    public void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new DefaultAdyenStoredCardAuthorisationService();
        service.setModelService(modelService);
        service.setFlexibleSearchService(flexibleSearchService);

        customer = mock(CustomerModel.class);
        when(customer.getCustomerID()).thenReturn("shopper-1");
        givenNoExistingRow();
        row = mock(AdyenStoredCardAuthorisationModel.class);
        when(modelService.create(AdyenStoredCardAuthorisationModel.class)).thenReturn(row);
    }

    @Test
    public void keepsTheTokenAndTheReferenceTheAuthorisationProduced()
    {
        service.recordFrom(customer, "MERCHANT", responseWith(Map.of(
                "tokenization.storedPaymentMethodId", "token-1",
                "networkTxReference", "NTID-42")));

        verify(row).setStoredPaymentMethodId("token-1");
        verify(row).setNetworkTxReference("NTID-42");
        verify(row).setMerchantAccount("MERCHANT");
        verify(row).setCustomer(customer);
        verify(modelService).save(row);
    }

    /** Older responses name the token differently; the plugin treats the two keys as the same fact. */
    @Test
    public void acceptsTheLegacyTokenKey()
    {
        service.recordFrom(customer, "MERCHANT", responseWith(Map.of(
                "recurring.recurringDetailReference", "token-legacy",
                "networkTxReference", "NTID-42")));

        verify(row).setStoredPaymentMethodId("token-legacy");
    }

    /**
     * Half the pair is worth nothing: a token with no reference cannot be imported into a platform that
     * charges it as a merchant-initiated transaction, and a reference with no token names no card.
     */
    @Test
    public void writesNothingWhenEitherHalfOfThePairIsMissing()
    {
        service.recordFrom(customer, "MERCHANT",
                responseWith(Map.of("tokenization.storedPaymentMethodId", "token-1")));
        service.recordFrom(customer, "MERCHANT", responseWith(Map.of("networkTxReference", "NTID-42")));
        service.recordFrom(customer, "MERCHANT", responseWith(null));
        service.recordFrom(null, "MERCHANT", responseWith(Map.of("networkTxReference", "NTID-42")));

        verify(modelService, never()).save(any());
    }

    /** A card is vaulted at Adyen whatever happens here, so a failed write must not surface as a failure. */
    @Test
    public void survivesAFailedWrite()
    {
        when(modelService.create(AdyenStoredCardAuthorisationModel.class))
                .thenThrow(new IllegalStateException("boom"));

        service.recordFrom(customer, "MERCHANT", responseWith(Map.of(
                "tokenization.storedPaymentMethodId", "token-1",
                "networkTxReference", "NTID-42")));
    }

    private void givenNoExistingRow()
    {
        final SearchResult<AdyenStoredCardAuthorisationModel> empty = mock(SearchResult.class);
        when(empty.getResult()).thenReturn(java.util.List.of());
        when(flexibleSearchService.<AdyenStoredCardAuthorisationModel> search(any(FlexibleSearchQuery.class)))
                .thenReturn(empty);
    }

    private PaymentResponse responseWith(final Map<String, String> additionalData)
    {
        final PaymentResponse response = new PaymentResponse();
        response.setAdditionalData(additionalData);
        return response;
    }
}
