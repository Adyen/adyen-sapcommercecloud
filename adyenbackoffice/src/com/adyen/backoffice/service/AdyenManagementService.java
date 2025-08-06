package com.adyen.backoffice.service;

import com.adyen.backoffice.dto.MerchantDataWsDTO;
import com.adyen.backoffice.dto.MerchantResponseWsDTO;

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

}
