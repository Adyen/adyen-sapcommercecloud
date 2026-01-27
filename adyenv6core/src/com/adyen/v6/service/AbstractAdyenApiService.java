package com.adyen.v6.service;

import com.adyen.Client;
import com.adyen.Config;
import com.adyen.commerce.services.AdyenRequestService;
import com.adyen.enums.Environment;
import de.hybris.platform.store.BaseStoreModel;
import org.springframework.retry.support.RetryTemplate;

import static com.adyen.v6.constants.Adyenv6coreConstants.PLUGIN_NAME;
import static com.adyen.v6.constants.Adyenv6coreConstants.PLUGIN_VERSION;

public abstract class AbstractAdyenApiService {

    protected BaseStoreModel baseStore;
    protected String merchantAccount;
    protected AdyenRequestService adyenRequestService;
    protected Config config;
    protected Client client;
    protected RetryTemplate adyenCustomerInteractionRetryTemplate;
    protected RetryTemplate adyenBackgroundProcessRetryTemplate;

    private AbstractAdyenApiService() {
    }

    public AbstractAdyenApiService(final BaseStoreModel baseStore, final String merchantAccount, final AdyenRequestService adyenRequestService, final RetryTemplate adyenCustomerInteractionRetryTemplate, final RetryTemplate adyenBackgroundProcessRetryTemplate) {
        this.baseStore = baseStore;
        this.merchantAccount = merchantAccount;
        this.adyenRequestService = adyenRequestService;
        this.adyenCustomerInteractionRetryTemplate = adyenCustomerInteractionRetryTemplate;
        this.adyenBackgroundProcessRetryTemplate = adyenBackgroundProcessRetryTemplate;
        config = new Config();
        if (Boolean.TRUE.equals(baseStore.getAdyenTestMode())) {
            config.setEnvironment(Environment.TEST);
        } else {
            config.setEnvironment(Environment.LIVE);
        }
        config.setApiKey(baseStore.getAdyenAPIKey());
        config.setApplicationName(PLUGIN_NAME + " v" + PLUGIN_VERSION);
        client = new Client(config);

        if (Boolean.TRUE.equals(baseStore.getAdyenTestMode())) {
            client.setEnvironment(Environment.TEST, null);
        } else {
            this.config.setEnvironment(Environment.LIVE);
            this.config.setTerminalApiCloudEndpoint(Client.TERMINAL_API_ENDPOINT_LIVE);
            this.config.setLiveEndpointUrlPrefix(baseStore.getAdyenAPIEndpointPrefix());
        }
    }


    public BaseStoreModel getBaseStore() {
        return baseStore;
    }

    public void setBaseStore(BaseStoreModel baseStore) {
        this.baseStore = baseStore;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public Config getConfig() {
        return config;
    }
}
