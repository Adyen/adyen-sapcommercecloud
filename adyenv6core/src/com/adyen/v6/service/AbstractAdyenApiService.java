package com.adyen.v6.service;

import com.adyen.Client;
import com.adyen.Config;
import com.adyen.commerce.services.AdyenRequestService;
import com.adyen.enums.Environment;
import com.adyen.v6.factory.AdyenRequestFactory;
import de.hybris.platform.store.BaseStoreModel;

import static com.adyen.v6.constants.Adyenv6coreConstants.PLUGIN_NAME;
import static com.adyen.v6.constants.Adyenv6coreConstants.PLUGIN_VERSION;

public abstract class AbstractAdyenApiService {

    protected BaseStoreModel baseStore;
    protected String merchantAccount;
    protected AdyenRequestFactory adyenRequestFactory;
    protected AdyenRequestService adyenRequestService;
    protected Config config;
    protected Client client;
    protected Config posConfig;
    protected Client posClient;

    private static final int POS_REQUEST_TIMEOUT = 60000;

    private static final String CHECKOUT_ENDPOINT_LIVE_IN_SUFFIX = "-checkout-live-in.adyenpayments.com/checkout";

    private AbstractAdyenApiService() {
    }

    public AbstractAdyenApiService(final BaseStoreModel baseStore, final String merchantAccount, final AdyenRequestService adyenRequestService) {
        this.baseStore = baseStore;
        this.merchantAccount = merchantAccount;
        this.adyenRequestService = adyenRequestService;

        if (Boolean.TRUE.equals(baseStore.getAdyenPosEnabled())) {
            posConfig = new Config();
            posConfig.setApiKey(baseStore.getAdyenPosApiKey());
            posConfig.setReadTimeoutMillis(POS_REQUEST_TIMEOUT);
            posConfig.setApplicationName(PLUGIN_NAME + " v" + PLUGIN_VERSION);
            posClient = new Client(posConfig);

            if (Boolean.TRUE.equals(baseStore.getAdyenTestMode())) {
                posClient.setEnvironment(Environment.TEST, null);
            } else {
                posClient.setEnvironment(Environment.LIVE, null);
            }
        }

        config = new Config();
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

    public AdyenRequestFactory getAdyenRequestFactory() {
        return adyenRequestFactory;
    }

    public void setAdyenRequestFactory(AdyenRequestFactory adyenRequestFactory) {
        this.adyenRequestFactory = adyenRequestFactory;
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
