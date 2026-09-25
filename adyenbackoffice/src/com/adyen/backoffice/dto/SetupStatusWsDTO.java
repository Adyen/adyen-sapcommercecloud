package com.adyen.backoffice.dto;

import java.util.List;

/** Whether the wizard needs to show itself, and for which stores. */
public class SetupStatusWsDTO {

    private boolean setupRequired;
    private List<StoreSetupWsDTO> stores;

    public boolean isSetupRequired() { return setupRequired; }

    public void setSetupRequired(final boolean setupRequired) { this.setupRequired = setupRequired; }

    public List<StoreSetupWsDTO> getStores() { return stores; }

    public void setStores(final List<StoreSetupWsDTO> stores) { this.stores = stores; }
}
