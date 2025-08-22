package com.adyen.backoffice.service;

import com.adyen.backoffice.dto.MerchantDataWsDTO;
import com.adyen.backoffice.dto.MerchantResponseWsDTO;
import com.adyen.backoffice.dto.PaymentMethodResponseWsDTO;
import com.adyen.backoffice.dto.StoreResponseWsDTO;

public interface AdyenManagementService {

    /**
     * Retrieves a list of merchant accounts from the Adyen Management API.
     *
     * @param pageSize The number of items to have on a page.
     * @param pageNumber The number of the page to fetch.
     * @return A {@link MerchantResponseWsDTO} containing the list of merchants.
     */
    MerchantResponseWsDTO getMerchants(Integer pageSize, Integer pageNumber);

    /**
     * Retrieves a single merchant account by ID from the Adyen Management API.
     *
     * @param merchantId The ID of the merchant to retrieve.
     * @return A {@link MerchantDataWsDTO} containing the merchant details.
     */
    MerchantDataWsDTO getMerchantById(String merchantId);

    /**
     * Retrieves all stores for a specific merchant from the Adyen Management API.
     *
     * @param merchantId The ID of the merchant to retrieve stores for.
     * @param pageSize The number of items to have on a page (optional).
     * @param pageNumber The number of the page to fetch (optional).
     * @return A {@link StoreResponseWsDTO} containing the list of stores.
     */
    StoreResponseWsDTO getStoresByMerchantId(String merchantId, Integer pageSize, Integer pageNumber);

    /**
     * Retrieves all payment methods for a specific merchant from the Adyen Management API.
     *
     * @param merchantId The ID of the merchant to retrieve payment methods for.
     * @param storeId The unique identifier of the store for which to return the payment methods (optional).
     * @param businessLineId The unique identifier of the Business Line for which to return the payment methods (optional).
     * @param pageSize The number of items to have on a page, maximum 100. The default is 10 items on a page (optional).
     * @param pageNumber The number of the page to fetch (optional).
     * @return A {@link PaymentMethodResponseWsDTO} containing the list of payment methods.
     */
    PaymentMethodResponseWsDTO getAllPaymentMethods(String merchantId, String storeId, String businessLineId, Integer pageSize, Integer pageNumber);

}
