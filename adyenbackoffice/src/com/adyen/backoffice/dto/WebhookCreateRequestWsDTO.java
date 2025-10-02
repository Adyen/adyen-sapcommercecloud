package com.adyen.backoffice.dto;

import java.util.List;

public class WebhookCreateRequestWsDTO {

    private String type;
    private String description;
    private String url;
    private Boolean active;
    private String communicationFormat;
    private List<String> includeEventCodes;
    private String username;
    private String password;

    public WebhookCreateRequestWsDTO() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getCommunicationFormat() {
        return communicationFormat;
    }

    public void setCommunicationFormat(String communicationFormat) {
        this.communicationFormat = communicationFormat;
    }

    public List<String> getIncludeEventCodes() {
        return includeEventCodes;
    }

    public void setIncludeEventCodes(List<String> includeEventCodes) {
        this.includeEventCodes = includeEventCodes;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}