package com.adyen.backoffice.dto;

import java.util.List;

/**
 * What a Management API key turned out to be, as the setup wizard reports it back to the merchant.
 *
 * <p>Carries the roles it is missing rather than a bare yes/no, because the wizard fails several steps
 * later and much less legibly if a key is accepted that cannot create credentials or webhooks.</p>
 */
public class AdyenCredentialCheckWsDTO {

    private boolean usable;
    private boolean active;
    private String companyName;
    private String username;
    private List<String> roles;
    private List<String> missingRoles;

    public boolean isUsable() {
        return usable;
    }

    public void setUsable(final boolean usable) {
        this.usable = usable;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(final String companyName) {
        this.companyName = companyName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(final List<String> roles) {
        this.roles = roles;
    }

    public List<String> getMissingRoles() {
        return missingRoles;
    }

    public void setMissingRoles(final List<String> missingRoles) {
        this.missingRoles = missingRoles;
    }
}
