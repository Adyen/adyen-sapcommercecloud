package com.adyen.backoffice.dto;

import java.util.List;

/**
 * The part of Adyen's {@code GET /me} response the setup wizard reads: what the submitted credential is
 * and what it is allowed to do. Other fields of that response are deliberately absent.
 */
public class MeApiCredentialWsDTO {

    private String id;
    private String username;
    private String companyName;
    private boolean active;
    private List<String> roles;

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(final String companyName) {
        this.companyName = companyName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(final List<String> roles) {
        this.roles = roles;
    }
}
