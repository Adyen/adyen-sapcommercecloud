package com.adyen.backoffice.service;

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

}
