package com.adyen.backoffice.service;

import com.adyen.backoffice.dto.ProvisionReportWsDTO;
import com.adyen.backoffice.dto.ProvisionRequestWsDTO;

import de.hybris.platform.store.BaseStoreModel;

/**
 * Creates, at Adyen, everything this store needs in order to take payments, and records the result on the
 * store.
 *
 * <p>Uses the store's Management API key, which the setup wizard has already validated. What it produces -
 * a Checkout credential, a client key, a webhook and its HMAC key - exists at Adyen afterwards whether or
 * not the whole run succeeded, so the report says step by step what happened rather than answering with a
 * single boolean.</p>
 */
public interface AdyenProvisioningService {

    ProvisionReportWsDTO provision(BaseStoreModel store, ProvisionRequestWsDTO request);
}
